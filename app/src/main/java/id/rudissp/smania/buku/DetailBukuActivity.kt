package id.rudissp.smania.buku

import id.rudissp.smania.LoadingIndicator
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import id.rudissp.smania.AppTheme
import id.rudissp.smania.VolleySingleton
import kotlinx.coroutines.launch
import org.json.JSONException

data class DetailBukuItem(
    val slug: String = "",
    val judul: String = "",
    val penulis: String = "",
    val penerbit: String = "",
    val tahun: String = "",
    val isbn: String = "",
    val jenjang: String = "",
    val kurikulum: String = "",
    val coverUrl: String = "",
    val unduhUrl: String = "",
    val catatan: String = "",
    val ukuranByte: Long = 0,
    val urlAsal: String = "",
    val siapUnduh: Boolean = false
)

class DetailBukuActivity : AppCompatActivity() {

    private var detailBuku by mutableStateOf(DetailBukuItem())
    private var isLoading by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val slug = intent.getStringExtra("SLUG") ?: ""

        if (slug.isEmpty()) {
            Toast.makeText(this, "Data buku tidak valid", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            AppTheme {
                DetailBukuScreen(
                    detail = detailBuku,
                    isLoading = isLoading,
                    onBack = { finish() },
                    onReadClick = { url, bookSlug, judul ->
                        if (url.isNotEmpty() && url != "null") {
                            val intent = Intent(this@DetailBukuActivity, BacaActivity::class.java).apply {
                                putExtra("PDF_URL", url)
                                putExtra("SLUG", bookSlug)
                                putExtra("JUDUL_BUKU", judul)
                            }
                            startActivity(intent)
                        } else {
                            Toast.makeText(this@DetailBukuActivity, "File buku belum tersedia untuk dibaca", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        fetchDetailBuku(slug)
    }

    override fun onResume() {
        super.onResume()
        // Memicu recomposition/refresh status saat kembali dari BacaActivity
        detailBuku = detailBuku.copy()
    }

    private fun fetchDetailBuku(slug: String) {
        isLoading = true
        val url = "https://sman1sukorejo.sch.id/smania/android/api_sibi.php/api/buku/$slug"
        val queue = VolleySingleton.getInstance(this).requestQueue

        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                isLoading = false
                try {
                    detailBuku = DetailBukuItem(
                        slug = response.optString("slug", ""),
                        judul = response.optString("judul", ""),
                        penulis = response.optString("penulis", ""),
                        penerbit = response.optString("penerbit", ""),
                        tahun = response.optString("tahun", ""),
                        isbn = response.optString("isbn", ""),
                        jenjang = response.optString("jenjang", ""),
                        kurikulum = response.optString("kurikulum", ""),
                        coverUrl = response.optString("cover_url", ""),
                        unduhUrl = response.optString("unduh_url", ""),
                        catatan = response.optString("catatan", ""),
                        urlAsal = response.optString("url_asal", ""),
                        ukuranByte = response.optLong("ukuran_byte", 0),
                        siapUnduh = response.optBoolean("siap_unduh", false)
                    )
                } catch (e: JSONException) {
                    Log.e("JSON_ERROR", "Error parsing detail buku: ${e.message}")
                    Toast.makeText(this, "Terjadi kesalahan format data", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                isLoading = false
                Toast.makeText(this, "Gagal mengambil detail buku", Toast.LENGTH_SHORT).show()
                Log.e("VOLLEY_ERROR", error.toString())
            }
        )

        queue.add(request)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailBukuScreen(
    detail: DetailBukuItem,
    isLoading: Boolean,
    onBack: () -> Unit,
    onReadClick: (url: String, slug: String, judul: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val targetUrl = remember(detail) {
        if (detail.urlAsal.isNotEmpty() && detail.urlAsal != "null") detail.urlAsal else detail.unduhUrl
    }

    var isDownloaded by remember { mutableStateOf(false) }
    var downloadedSize by remember { mutableLongStateOf(0L) }

    var isPreDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedMb by remember { mutableStateOf("0.0") }
    var totalMb by remember { mutableStateOf("0.0") }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Mencek ketersediaan file lokal
    LaunchedEffect(detail, targetUrl, isPreDownloading) {
        isDownloaded = BukuStorageManager.isBookDownloaded(context, detail.slug, targetUrl)
        downloadedSize = BukuStorageManager.getDownloadedFileSize(context, detail.slug, targetUrl)
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(text = "Hapus Buku Offline?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = "File buku \"${detail.judul}\" akan dihapus dari penyimpanan internal HP. Anda dapat mengunduhnya kembali kapan saja.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        val deleted = BukuStorageManager.deleteDownloadedBook(context, detail.slug, targetUrl)
                        if (deleted) {
                            isDownloaded = false
                            downloadedSize = 0L
                            Toast.makeText(context, "Buku berhasil dihapus dari penyimpanan lokal", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Gagal menghapus file buku", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        },
        bottomBar = {
            if (!isLoading) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shadowElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isPreDownloading) {
                            // Progres Pre-downloading
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Mengunduh untuk dibaca offline... ($downloadedMb MB / $totalMb MB)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearWavyProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            }
                        } else {
                            // Tombol Baca Utama
                            Button(
                                onClick = { onReadClick(targetUrl, detail.slug, detail.judul) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = CircleShape,
                                enabled = detail.siapUnduh && targetUrl.isNotEmpty() && targetUrl != "null",
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (isDownloaded) "Baca Buku (Offline)" else "Mulai Baca Buku",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Tombol Opsi Tambahan (Unduh Offline / Hapus Unduhan)
                            if (isDownloaded) {
                                OutlinedButton(
                                    onClick = { showDeleteConfirmDialog = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Hapus dari Penyimpanan Lokal",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else if (detail.siapUnduh && targetUrl.isNotEmpty() && targetUrl != "null") {
                                OutlinedButton(
                                    onClick = {
                                        isPreDownloading = true
                                        coroutineScope.launch {
                                            val result = BukuStorageManager.downloadBook(
                                                context = context,
                                                slug = detail.slug,
                                                pdfUrl = targetUrl,
                                                onProgress = { progress, currentBytes, totalBytes ->
                                                    if (progress >= 0f) {
                                                        downloadProgress = progress
                                                        downloadedMb = String.format("%.1f", currentBytes / (1024f * 1024f))
                                                        totalMb = String.format("%.1f", totalBytes / (1024f * 1024f))
                                                    }
                                                }
                                            )
                                            isPreDownloading = false
                                            result.onSuccess {
                                                isDownloaded = true
                                                downloadedSize = BukuStorageManager.getDownloadedFileSize(context, detail.slug, targetUrl)
                                                Toast.makeText(context, "Buku berhasil diunduh untuk dibaca offline!", Toast.LENGTH_SHORT).show()
                                            }.onFailure {
                                                Toast.makeText(context, "Gagal mengunduh buku", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Unduh Dulu untuk Dibaca Offline",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Cover Image
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    if (detail.coverUrl.isNotEmpty() && detail.coverUrl != "null") {
                        SubcomposeAsyncImage(
                            model = detail.coverUrl,
                            contentDescription = "Cover ${detail.judul}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(180.dp)
                                .aspectRatio(0.7f)
                                .shadow(elevation = 12.dp, shape = RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            loading = {
                                Box(contentAlignment = Alignment.Center) {
                                    LoadingIndicator(modifier = Modifier.size(24.dp))
                                }
                            },
                            error = {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                                    Icon(Icons.Default.Book, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .width(180.dp)
                                .aspectRatio(0.7f)
                                .shadow(elevation = 12.dp, shape = RoundedCornerShape(16.dp))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Book, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 2. Status Tersedia Offline / Belum Diunduh Badge
                item {
                    Surface(
                        shape = CircleShape,
                        color = if (isDownloaded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = if (isDownloaded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isDownloaded) {
                                    "Tersedia Offline (${BukuStorageManager.formatFileSize(downloadedSize)})"
                                } else {
                                    "Belum Diunduh (${if (detail.ukuranByte > 0) BukuStorageManager.formatFileSize(detail.ukuranByte) else "PDF"})"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDownloaded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 3. Judul dan Penulis
                item {
                    Text(
                        text = detail.judul,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        lineHeight = 28.sp,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (detail.penulis.isNotEmpty()) "Oleh: ${detail.penulis}" else "Penulis tidak diketahui",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // 4. Badges (Kurikulum & Jenjang)
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (detail.jenjang.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = detail.jenjang.uppercase(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                        if (detail.kurikulum.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = detail.kurikulum,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }

                // 5. Informasi Detail Grid
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Informasi Buku",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            MetadataRow(label = "Penerbit", value = detail.penerbit.ifEmpty { "-" })
                            MetadataRow(label = "Tahun Terbit", value = detail.tahun.ifEmpty { "-" })
                            MetadataRow(label = "ISBN", value = detail.isbn.ifEmpty { "-" })

                            val ukuranString = if (detail.ukuranByte > 0) {
                                BukuStorageManager.formatFileSize(detail.ukuranByte)
                            } else {
                                "-"
                            }
                            MetadataRow(label = "Ukuran File", value = ukuranString)
                            MetadataRow(
                                label = "Status Penyimpanan",
                                value = if (isDownloaded) "Tersimpan di HP" else "Belum Diunduh"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 6. Catatan / Deskripsi Singkat
                if (detail.catatan.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "Catatan",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = detail.catatan,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.5f),
            textAlign = TextAlign.End
        )
    }
}