package id.rudissp.smania.ibadah

import id.rudissp.smania.LoadingIndicator
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
import androidx.camera.core.Preview as CameraXPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
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

    lateinit var prefs: SharedPreferences

    private var isLoading by mutableStateOf(false)
    private var previewViewInstance: PreviewView? = null
    private var isScanning = true

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Izin kamera dibutuhkan untuk memindai QR", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = getSharedPreferences("PREFS", MODE_PRIVATE)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
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

            val preview = CameraXPreview.Builder()
                .build()
                .also {
                    it.surfaceProvider = pv.surfaceProvider
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

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
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()

            val scanner = BarcodeScanning.getClient(options)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        if (rawValue != null && isScanning) {
                            isScanning = false

                            if (rawValue.contains("proses_absen.php")) {
                                prosesAbsenQr(rawValue)
                            } else {
                                protocolToast("QR Code Absen tidak valid")
                            }
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun protocolToast(msg: String) {
        runOnUiThread {
            Toast.makeText(this@Ibadah, msg, Toast.LENGTH_SHORT).show()
            previewViewInstance?.postDelayed({ isScanning = true }, 2000)
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
            titleTextView?.setTextColor("#2E7D32".toColorInt())
        } else {
            titleTextView?.setTextColor("#D32F2F".toColorInt())
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Absensi Ibadah",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
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
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 1. Expressive Hero Scanner Instruction Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.QrCodeScanner,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Scanner QR Ibadah",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Arahkan kamera ke QR Code absen ibadah harian Anda.",
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                // 2. Camera Preview Card (Tanpa Border, Mengandalkan Kliping dan Elevasi Bayangan M3)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(28.dp))
                    ) {
                        AndroidView(
                            factory = { context ->
                                PreviewView(context).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                    onPreviewViewCreated(this)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 3. Laporan Halangan Card (Menggunakan SurfaceContainerLow tanpa border)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Laporan Halangan",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Gunakan tombol ini jika Anda berhalangan hadir ibadah untuk hari ini.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        // Tombol Aksi Sekunder menggunakan Tertiary Container
                        Button(
                            onClick = onHalanganClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        ) {
                            Text(
                                text = "Saya Sedang Berhalangan",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 4. Lihat Rekap Button (Aksi Utama)
                Button(
                    onClick = onRekapClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Lihat Rekap Ibadah Saya", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
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