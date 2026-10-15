package id.rudissp.smania.ibadah

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import id.rudissp.smania.AppConstants
import java.net.URLEncoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.core.graphics.toColorInt
import id.rudissp.smania.AppTheme

class Ibadah : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService

    // Session
    lateinit var prefs: SharedPreferences

    // State
    private var isLoading by mutableStateOf(false)
    private var previewViewInstance: PreviewView? = null

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
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            MaterialTheme {
                AppTheme {
                    IbadahScreen(
                        isLoading = isLoading,
                        onBack = { finish() },
                        onHalanganClick = { konfirmasiHalangan() },
                        onRekapClick = {
                            startActivity(Intent(this, RekapIbadah::class.java))
                        },
                        onPreviewViewCreated = { previewView ->
                            previewViewInstance = previewView
                            checkCameraPermissionAndStart()
                        }
                    )
                }
            }
        }
    }

    private fun checkCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val pv = previewViewInstance ?: return
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Setup Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = pv.surfaceProvider
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
                                    previewViewInstance?.postDelayed({ isScanning = true }, 2000)
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
        val nisSiswa = prefs.getString("nis", "").toString()

        val encodedIbadah = URLEncoder.encode(ibadah, "UTF-8")
        val encodedNis = URLEncoder.encode(nisSiswa, "UTF-8")
        val encodedToken = URLEncoder.encode(token, "UTF-8")

        val urlApi = AppConstants.IBADAH_PROSES +
                "?token=$encodedToken&ibadah=$encodedIbadah&nis=$encodedNis"

        isLoading = true

        val request = StringRequest(Request.Method.GET, urlApi,
            { response ->
                isLoading = false
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
                isLoading = false
                tampilkanDialogHasil(false, "Gagal", "Koneksi ke server bermasalah")
            }
        )
        Volley.newRequestQueue(this).add(request)
    }

    private fun tampilkanDialogHasil(isSuccess: Boolean, judul: String, pesan: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(judul)
        builder.setMessage(pesan)
        builder.setCancelable(false)

        builder.setPositiveButton("Tutup") { dialog, _ ->
            dialog.dismiss()
            if (isSuccess) {
                finish()
            } else {
                isScanning = true
            }
        }

        val dialog = builder.create()
        dialog.show()

        val titleId = resources.getIdentifier("alertTitle", "id", "android")
        val titleTextView = dialog.findViewById<android.widget.TextView>(titleId)
        if (isSuccess) {
            titleTextView?.setTextColor("#2E7D32".toColorInt()) // Hijau
        } else {
            titleTextView?.setTextColor("#D32F2F".toColorInt()) // Merah
        }
    }

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
        isLoading = true
        val url = "https://sman1sukorejo.sch.id/smania/ibadah/proses_halangan.php"
        val stringRequest = object : StringRequest(
            Method.POST, url,
            { response ->
                isLoading = false
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
                isLoading = false
                Toast.makeText(this, "Koneksi Gagal", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["nis"] = prefs.getString("nis", "").toString()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IbadahScreen(
    isLoading: Boolean,
    onBack: () -> Unit,
    onHalanganClick: () -> Unit,
    onRekapClick: () -> Unit,
    onPreviewViewCreated: (PreviewView) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ibadah") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // QR Scanner Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { context ->
                                PreviewView(context).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                    onPreviewViewCreated(this)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "Arahkan ke QR Code Absen",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Laporan Halangan Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(2.dp, Color(0xFFFFEEBA))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Laporan Halangan",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Gunakan tombol ini jika Anda berhalangan ibadah untuk hari ini.",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onHalanganClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE9ECEF),
                                contentColor = Color(0xFF495057)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFCED4DA))
                        ) {
                            Text("⚠️ Saya Sedang Berhalangan Hari Ini")
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Lihat Rekap Button
                Button(
                    onClick = onRekapClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Lihat Rekap Ibadah Saya", fontSize = 16.sp)
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun IbadahScreenPreview() {
    AppTheme {
        IbadahScreen(
            isLoading = false,
            onBack = {},
            onHalanganClick = {},
            onRekapClick = {},
            onPreviewViewCreated = {}
        )
    }
}
