package id.rudissp.smania.ibadah

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import id.rudissp.smania.AppConstants
import id.rudissp.smania.databinding.ActivityIbadahBinding
import java.net.URLEncoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.core.graphics.toColorInt

class Ibadah : AppCompatActivity() {
    lateinit var binding: ActivityIbadahBinding
    private lateinit var cameraExecutor: ExecutorService

    // Session Dummy
    private var nisSiswa = ""
    lateinit var prefs: SharedPreferences


    // Mencegah scan berulang kali setelah berhasil (debounce)
    private var isScanning = true

    // Launcher untuk meminta izin kamera
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Izin kamera dibutuhkan untuk memindai QR", Toast.LENGTH_LONG)
                .show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = getSharedPreferences("PREFS", MODE_PRIVATE)
        binding = ActivityIbadahBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.toolbarIbadah.setOnClickListener { finish() }

        // Meminta izin kamera saat activity dibuka
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.btnHalangan.setOnClickListener {
            konfirmasiHalangan()
        }

        binding.btnRekap.setOnClickListener {
            startActivity(Intent(this, RekapIbadah::class.java))


        }


    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Setup Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = binding.viewFinder.surfaceProvider
                }

            // Setup Analyzer (ML Kit)
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            // Gunakan kamera belakang
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("IbadahActivity", "Gagal membuka kamera", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        if (!isScanning) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            // Konfigurasi ML Kit khusus QR Code agar lebih cepat
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()

            val scanner = BarcodeScanning.getClient(options)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        if (rawValue != null && isScanning) {
                            // Hentikan proses scan agar Volley tidak dipanggil berulang kali
                            isScanning = false

                            if (rawValue.contains("proses_absen.php")) {
                                prosesAbsenQr(rawValue)
                            } else {
                                runOnUiThread {
                                    Toast.makeText(
                                        this@Ibadah,
                                        "QR Code Absen tidak valid",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    // Nyalakan lagi setelah delay
                                    binding.viewFinder.postDelayed({ isScanning = true }, 2000)
                                }
                            }
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close() // Wajib ditutup agar frame kamera selanjutnya bisa diproses
                }
        } else {
            imageProxy.close()
        }
    }

    private fun prosesAbsenQr(urlScanned: String) {
        val uri = urlScanned.toUri()
        val token = uri.getQueryParameter("token") ?: ""
        val ibadah = uri.getQueryParameter("ibadah") ?: ""
        val nisSiswa = prefs.getString("nis","").toString()

        // --- PERBAIKAN DI SINI ---
        // Menggunakan URLEncoder agar 'Shalat Dzuhur' menjadi 'Shalat%20Dzuhur'
        val encodedIbadah = URLEncoder.encode(ibadah, "UTF-8")
        val encodedNis = URLEncoder.encode(nisSiswa, "UTF-8")
        val encodedToken = URLEncoder.encode(token, "UTF-8")

        val urlApi = AppConstants.IBADAH_PROSES +
                "?token=$encodedToken&ibadah=$encodedIbadah&nis=$encodedNis"

        binding.progressBar.visibility = View.VISIBLE

        val request = StringRequest(Request.Method.GET, urlApi,
            { response ->
                binding.progressBar.visibility = View.GONE
                try {
                    val json = org.json.JSONObject(response)
                    if (json.getString("status") == "success") {
                        tampilkanDialogHasil(true, "Berhasil!", json.getString("pesan"))
                    } else {
                        tampilkanDialogHasil(false, "Gagal", json.getString("pesan"))
                    }
                } catch (_: Exception) {
                    tampilkanDialogHasil(false, "Error", "Respon server tidak valid")
                }
            },
            {
                binding.progressBar.visibility = View.GONE
                tampilkanDialogHasil(false, "Gagal", "Koneksi ke server bermasalah")
            }
        )
        Volley.newRequestQueue(this).add(request)
    }

    // Fungsi untuk menampilkan pop-up hasil presensi mirip versi HTML web
    private fun tampilkanDialogHasil(isSuccess: Boolean, judul: String, pesan: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(judul)
        builder.setMessage(pesan)
        builder.setCancelable(false)

        builder.setPositiveButton("Tutup") { dialog, _ ->
            dialog.dismiss()
            if (isSuccess) {
                // Jika sukses, bisa langsung kembali ke menu utama atau refresh halaman
                finish()
            } else {
                // Jika gagal, nyalakan kembali kamera untuk mencoba scan ulang
                isScanning = true
            }
        }

        val dialog = builder.create()
        dialog.show()

        // Opsional: Beri warna teks judul sesuai status (Hijau = Sukses, Merah = Gagal)
        val titleId = resources.getIdentifier("alertTitle", "id", "android")
        val titleTextView = dialog.findViewById<android.widget.TextView>(titleId)
        if (isSuccess) {
            titleTextView?.setTextColor("#2E7D32".toColorInt()) // Hijau
        } else {
            titleTextView?.setTextColor("#D32F2F".toColorInt()) // Merah
        }
    }

    // ... (Fungsi konfirmasiHalangan() dan kirimDataHalangan() tetap sama persis seperti sebelumnya) ...
    private fun konfirmasiHalangan() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Konfirmasi")
        builder.setMessage("Apakah Anda yakin sedang berhalangan ibadah hari ini?")
        builder.setPositiveButton("Ya, Benar") { dialog, _ ->
            kirimDataHalangan()
            dialog.dismiss()
        }
        builder.setNegativeButton("Batal") { dialog, _ -> dialog.dismiss() }
        builder.create().show()
    }

    private fun kirimDataHalangan() {
        binding.progressBar.visibility = View.VISIBLE
        val url = "https://sman1sukorejo.sch.id/smania/ibadah/proses_halangan.php"
        val stringRequest = object : StringRequest(
            Method.POST, url,
            { response ->
                binding.progressBar.visibility = View.GONE
                try {
                    val jsonObject = org.json.JSONObject(response)
                    if (jsonObject.getString("status") == "success") {
                        Toast.makeText(
                            this,
                            "Laporan halangan berhasil dicatat.",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    } else {
                        Toast.makeText(this, jsonObject.getString("pesan"), Toast.LENGTH_LONG)
                            .show()
                    }
                } catch (_: Exception) {
                    Toast.makeText(this, "Error memproses data", Toast.LENGTH_SHORT).show()
                }
            },
            { _ ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Koneksi Gagal", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["nis"] = prefs.getString("nis","").toString()
                params["status_halangan"] = "1"
                return params
            }
        }
        Volley.newRequestQueue(this).add(stringRequest)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}