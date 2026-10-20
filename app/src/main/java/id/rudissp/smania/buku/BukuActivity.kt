package id.rudissp.smania.buku

import id.rudissp.smania.LoadingIndicator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import id.rudissp.smania.AppTheme
import id.rudissp.smania.VolleySingleton
import kotlinx.coroutines.delay
import org.json.JSONException
import kotlin.time.Duration.Companion.milliseconds

data class BukuItem(
    val slug: String,
    val judul: String,
    val kategori: String,
    val kurikulum: String,
    val jenjang: String,
    val penulis: String,
    val coverUrl: String,
    val unduhUrl: String
)

class BukuActivity : AppCompatActivity() {

    private var bukuList by mutableStateOf<List<BukuItem>>(emptyList())
    private var isLoading by mutableStateOf(false)
    private var totalBuku by mutableStateOf(0)
    private var refreshTrigger by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                BukuScreen(
                    bukuList = bukuList,
                    isLoading = isLoading,
                    refreshTrigger = refreshTrigger,
                    onFetchData = { query, kategori ->
                        fetchDataBuku(query, kategori)
                    },
                    onCardClick = { slug ->
                        val intent = Intent(this@BukuActivity, DetailBukuActivity::class.java).apply {
                            putExtra("SLUG", slug)
                        }
                        startActivity(intent)
                    },
                    onReadClick = { url, slug, judul ->
                        if (url.isNotEmpty() && url != "null") {
                            val intent = Intent(this@BukuActivity, BacaActivity::class.java).apply {
                                putExtra("PDF_URL", url)
                                putExtra("SLUG", slug)
                                putExtra("JUDUL_BUKU", judul)
                            }
                            startActivity(intent)
                        } else {
                            Toast.makeText(this@BukuActivity, "File buku tidak tersedia", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTrigger++
    }

    private fun fetchDataBuku(query: String, kategori: String) {
        isLoading = true
        val limit = if (query.isEmpty() && kategori.isEmpty()) 20 else 100
        var url = "https://sman1sukorejo.sch.id/smania/android/api_sibi.php/api/katalog?limit=$limit"

        if (query.isNotBlank()) url += "&q=${Uri.encode(query.trim())}"
        if (kategori.isNotBlank() && kategori != "terunduh") url += "&kategori=${Uri.encode(kategori)}"

        val queue = VolleySingleton.getInstance(this).requestQueue
        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                isLoading = false
                val newList = mutableListOf<BukuItem>()
                try {
                    totalBuku = response.optInt("total", 0)
                    val hasilArray = response.getJSONArray("hasil")
                    for (i in 0 until hasilArray.length()) {
                        val item = hasilArray.getJSONObject(i)
                        newList.add(
                            BukuItem(
                                slug = item.optString("slug", ""),
                                judul = item.optString("judul", ""),
                                kategori = item.optString("kategori", ""),
                                kurikulum = item.optString("kurikulum", ""),
                                jenjang = item.optString("jenjang", ""),
                                penulis = item.optString("penulis", ""),
                                coverUrl = item.optString("cover_url", ""),
                                unduhUrl = item.optString("unduh_url", "")
                            )
                        )
                    }
                    bukuList = newList
                } catch (e: JSONException) {
                    Log.e("JSON_ERROR", "Error parsing data SIBI: ${e.message}")
                }
            },
            {
                isLoading = false
                Toast.makeText(this, "Gagal mengambil data katalog", Toast.LENGTH_SHORT).show()
            }
        )

        jsonObjectRequest.tag = "BUKU_REQUEST"
        queue.cancelAll("BUKU_REQUEST")
        queue.add(jsonObjectRequest)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BukuScreen(
    bukuList: List<BukuItem>,
    isLoading: Boolean,
    refreshTrigger: Int,
    onFetchData: (String, String) -> Unit,
    onCardClick: (String) -> Unit,
    onReadClick: (url: String, slug: String, judul: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedKategori by remember { mutableStateOf("") }

    var showStorageDialog by remember { mutableStateOf(false) }
    var totalCacheSize by remember { mutableLongStateOf(0L) }
    var downloadedCount by remember { mutableIntStateOf(0) }

    val kategoriList = listOf(
        Pair("", "Semua"),
        Pair("terunduh", "Tersedia Offline"),
        Pair("teks", "Buku Teks"),
        Pair("nonteks", "Nonteks"),
        Pair("modul", "Modul Ajar"),
        Pair("sastra", "Sastra")
    )

    LaunchedEffect(searchQuery, selectedKategori) {
        delay(600.milliseconds)
        onFetchData(searchQuery, selectedKategori)
    }

    LaunchedEffect(refreshTrigger, showStorageDialog) {
        totalCacheSize = BukuStorageManager.getTotalCacheSize(context)
        downloadedCount = BukuStorageManager.getDownloadedBookCount(context)
    }

    val filteredList = remember(bukuList, selectedKategori, refreshTrigger) {
        if (selectedKategori == "terunduh") {
            bukuList.filter { item ->
                BukuStorageManager.isBookDownloaded(context, item.slug, item.unduhUrl)
            }
        } else {
            bukuList
        }
    }

    if (showStorageDialog) {
        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            title = { Text("Penyimpanan Offline", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Total buku: $downloadedCount", style = MaterialTheme.typography.bodyMedium)
                    Text("Ruang terpakai: ${BukuStorageManager.formatFileSize(totalCacheSize)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Buku disimpan di perangkat agar dapat dibaca tanpa internet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                if (downloadedCount > 0) {
                    Button(
                        onClick = {
                            val cleared = BukuStorageManager.clearAllCache(context)
                            showStorageDialog = false
                            if (cleared) {
                                totalCacheSize = 0L
                                downloadedCount = 0
                                Toast.makeText(context, "Unduhan berhasil dihapus", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Gagal menghapus unduhan", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hapus Semua")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showStorageDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "E-Library",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { showStorageDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Kelola Penyimpanan",
                            tint = if (downloadedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Seluruh UI dibungkus dalam LazyColumn agar terscroll bersama
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. Expressive Hero Card
                item {
                    ExpressiveBukuHeroCard()
                }

                // 2. Pencarian
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari judul, penulis, jenjang...", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Hapus", modifier = Modifier.size(20.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                // 3. Filter Chip (Bisa scroll horizontal di dalam item vertical)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        kategoriList.forEach { (kode, label) ->
                            FilterChip(
                                selected = selectedKategori == kode,
                                onClick = { selectedKategori = kode },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = if (kode == "terunduh") {
                                    { Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                shape = CircleShape
                            )
                        }
                    }
                }

                // 4. Daftar Buku / Status Kosong
                if (filteredList.isEmpty() && !isLoading) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SearchOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (selectedKategori == "terunduh") "Belum Ada Buku Offline" else "Buku Tidak Ditemukan",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Ubah kata kunci atau kategori pencarian.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(filteredList, key = { it.slug }) { item ->
                        BukuCardItem(
                            item = item,
                            refreshTrigger = refreshTrigger,
                            onCardClick = { onCardClick(item.slug) },
                            onReadClick = { onReadClick(item.unduhUrl, item.slug, item.judul) }
                        )
                    }
                }
            }

            // 5. Loading Overlay
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f)),
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

@Composable
fun ExpressiveBukuHeroCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Katalog SIBI",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Perpustakaan Digital",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Akses ratusan buku teks dan modul pembelajaran secara gratis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.LibraryBooks,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BukuCardItem(
    item: BukuItem,
    refreshTrigger: Int,
    onCardClick: () -> Unit,
    onReadClick: () -> Unit
) {
    val context = LocalContext.current
    val isDownloaded = remember(item, refreshTrigger) {
        BukuStorageManager.isBookDownloaded(context, item.slug, item.unduhUrl)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onCardClick
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (item.coverUrl.isNotEmpty() && item.coverUrl != "null") {
                    SubcomposeAsyncImage(
                        model = item.coverUrl,
                        contentDescription = "Cover ${item.judul}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp, 96.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        loading = { Box(contentAlignment = Alignment.Center) { LoadingIndicator(modifier = Modifier.size(24.dp)) } },
                        error = { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Book, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(72.dp, 96.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Book, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.judul,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (item.penulis.isNotEmpty()) item.penulis else "Penulis anonim",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.jenjang.isNotEmpty()) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                                Text(
                                    text = item.jenjang.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        if (isDownloaded) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Offline",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Button(
                onClick = onReadClick,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = CircleShape,
                enabled = item.unduhUrl.isNotEmpty() && item.unduhUrl != "null",
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isDownloaded) "Baca (Tersimpan)" else "Baca Buku",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}