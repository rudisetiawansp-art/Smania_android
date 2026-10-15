package id.rudissp.smania.ujian

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.rudissp.smania.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Ujian : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var repository: UjianRepository

    private var allUjianList by mutableStateOf<List<UjianItem>>(emptyList())
    private var activeFilter by mutableStateOf("tersedia")
    private var isLoading by mutableStateOf(false)
    private var nisSiswa by mutableStateOf("")
    private var namaSiswa by mutableStateOf("")
    private var kelasSiswa by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefs = getSharedPreferences("PREFS", Context.MODE_PRIVATE)
        repository = UjianRepository(this)

        nisSiswa = prefs.getString("nis", "") ?: ""
        namaSiswa = prefs.getString("namalengkap", "") ?: ""
        kelasSiswa = prefs.getString("kelas", "") ?: ""

        setContent {
            AppTheme {
                UjianDashboardScreen(
                    nama = namaSiswa,
                    kelas = kelasSiswa,
                    ujianList = allUjianList,
                    activeFilter = activeFilter,
                    isLoading = isLoading,
                    onFilterChange = { activeFilter = it },
                    onRefresh = { refreshData() },
                    onBack = { finish() },
                    onActionClick = { ujian, actionType ->
                        handleExamAction(ujian, actionType)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    private fun refreshData() {
        isLoading = true
        repository.loadDaftarUjian(nisSiswa, kelasSiswa, object : UjianRepository.Callback<UjianResponse> {
            override fun onSuccess(result: UjianResponse) {
                isLoading = false
                allUjianList = result.data
            }

            override fun onError(message: String) {
                isLoading = false
                Toast.makeText(this@Ujian, message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun handleExamAction(ujian: UjianItem, actionType: String) {
        when (actionType) {
            "LIHAT_HASIL" -> {
                val intent = Intent(this, LihatHasilUjianActivity::class.java).apply {
                    putExtra("ID_UJIAN", ujian.id)
                    putExtra("JUDUL_UJIAN", ujian.judulUjian)
                    putExtra("MAPEL", ujian.mapel)
                    putExtra("TIPE", ujian.tipe)
                }
                startActivity(intent)
            }
            "ULANGI_LATIHAN" -> {
                repository.ulangiLatihan(ujian.id, nisSiswa, object : UjianRepository.Callback<Boolean> {
                    override fun onSuccess(result: Boolean) {
                        val intent = Intent(this@Ujian, KerjakanUjianActivity::class.java).apply {
                            putExtra("ID_UJIAN", ujian.id)
                            putExtra("JUDUL_UJIAN", ujian.judulUjian)
                            putExtra("MAPEL", ujian.mapel)
                            putExtra("TIPE", ujian.tipe)
                        }
                        startActivity(intent)
                    }

                    override fun onError(message: String) {
                        Toast.makeText(this@Ujian, message, Toast.LENGTH_SHORT).show()
                    }
                })
            }
            "KERJAKAN", "LANJUTKAN" -> {
                if (ujian.tipe == "latihan") {
                    val intent = Intent(this, KerjakanUjianActivity::class.java).apply {
                        putExtra("ID_UJIAN", ujian.id)
                        putExtra("JUDUL_UJIAN", ujian.judulUjian)
                        putExtra("MAPEL", ujian.mapel)
                        putExtra("TIPE", ujian.tipe)
                    }
                    startActivity(intent)
                } else {
                    // For official exams, verify access code first
                    val intent = Intent(this, VerifikasiKodeActivity::class.java).apply {
                        putExtra("ID_UJIAN", ujian.id)
                        putExtra("JUDUL_UJIAN", ujian.judulUjian)
                        putExtra("MAPEL", ujian.mapel)
                        putExtra("DURASI", ujian.durasi)
                    }
                    startActivity(intent)
                }
            }
        }
    }
}

// ============================================================
// DASHBOARD COMPOSE SCREEN
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UjianDashboardScreen(
    nama: String,
    kelas: String,
    ujianList: List<UjianItem>,
    activeFilter: String,
    isLoading: Boolean,
    onFilterChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onActionClick: (UjianItem, String) -> Unit
) {
    // Categorize
    val countTersedia = ujianList.count { it.kategori == "tersedia" }
    val countSelesai = ujianList.count { it.kategori == "selesai" }
    val countBerjalan = ujianList.count { it.kategori == "berjalan" }
    val countLatihan = ujianList.count { it.tipe == "latihan" }

    val filteredList = remember(ujianList, activeFilter) {
        if (activeFilter == "latihan") {
            ujianList.filter { it.tipe == "latihan" }
        } else {
            ujianList.filter { it.kategori == activeFilter }
        }
    }

    val filterTitle = when (activeFilter) {
        "tersedia" -> "Ujian Tersedia"
        "selesai" -> "Ujian Selesai"
        "berjalan" -> "Ujian Berjalan"
        "latihan" -> "Daftar Latihan"
        else -> "Daftar Ujian"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Ujian Online",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (kelas.isNotEmpty()) {
                            Text(
                                text = "Kelas $kelas • $nama",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Muat Ulang"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Stat Cards Grid (Material 3 Expressive)
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExpressiveStatCard(
                        title = "Tersedia",
                        value = countTersedia,
                        icon = Icons.Rounded.Assignment,
                        iconTint = ExpressiveBlue,
                        containerColor = ExpressiveBlueContainer,
                        isSelected = activeFilter == "tersedia",
                        onClick = { onFilterChange("tersedia") },
                        modifier = Modifier.weight(1f)
                    )
                    ExpressiveStatCard(
                        title = "Selesai",
                        value = countSelesai,
                        icon = Icons.Rounded.CheckCircle,
                        iconTint = ExpressiveGreen,
                        containerColor = ExpressiveGreenContainer,
                        isSelected = activeFilter == "selesai",
                        onClick = { onFilterChange("selesai") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExpressiveStatCard(
                        title = "Berjalan",
                        value = countBerjalan,
                        icon = Icons.Rounded.HourglassTop,
                        iconTint = ExpressiveOrange,
                        containerColor = ExpressiveOrangeContainer,
                        isSelected = activeFilter == "berjalan",
                        onClick = { onFilterChange("berjalan") },
                        modifier = Modifier.weight(1f)
                    )
                    ExpressiveStatCard(
                        title = "Latihan",
                        value = countLatihan,
                        icon = Icons.Rounded.School,
                        iconTint = ExpressivePurple,
                        containerColor = ExpressivePurpleContainer,
                        isSelected = activeFilter == "latihan",
                        onClick = { onFilterChange("latihan") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = filterTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${filteredList.size} item",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Loading Indicator
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                }
            }

            // Empty State
            if (!isLoading && filteredList.isEmpty()) {
                item {
                    EmptyUjianState(filter = activeFilter)
                }
            }

            // Exam Items
            items(filteredList, key = { it.id }) { ujian ->
                UjianCard(
                    ujian = ujian,
                    onAction = { actionType -> onActionClick(ujian, actionType) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ============================================================
// EXAM CARD COMPONENT
// ============================================================
@Composable
fun UjianCard(
    ujian: UjianItem,
    onAction: (String) -> Unit
) {
    val isLatihan = ujian.tipe == "latihan"

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Mapel tag & Status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Type badge
                    ExpressiveBadge(
                        text = if (isLatihan) "LATIHAN" else "UJIAN",
                        containerColor = if (isLatihan) ExpressivePurpleContainer else ExpressiveBlueContainer,
                        contentColor = if (isLatihan) ExpressivePurple else ExpressiveBlue,
                        icon = if (isLatihan) Icons.Rounded.School else Icons.Rounded.Assignment
                    )

                    // Mapel Chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = ujian.mapel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Status Badge
                val (badgeBg, badgeText) = when {
                    ujian.isSelesai -> ExpressiveGreenContainer to ExpressiveGreen
                    ujian.isSedang -> ExpressiveOrangeContainer to ExpressiveOrange
                    ujian.lewat -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                    ujian.statusLabel == "Belum Mulai" -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                    else -> ExpressiveBlueContainer to ExpressiveBlue
                }

                ExpressiveBadge(
                    text = ujian.statusLabel,
                    containerColor = badgeBg,
                    contentColor = badgeText
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = ujian.judulUjian,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Meta Info: Waktu & Jumlah Soal
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${ujian.durasi} Menit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "PG: ${ujian.jmlPg} | Ur: ${ujian.jmlUraian}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (ujian.isSelesai) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { onAction("LIHAT_HASIL") },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = ExpressiveGreenContainer,
                                contentColor = ExpressiveGreen
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Analytics,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lihat Hasil", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        if (isLatihan && !ujian.lewat) {
                            OutlinedButton(
                                onClick = { onAction("ULANGI_LATIHAN") },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ulangi", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                } else if (ujian.isSedang && !ujian.lewat) {
                    Button(
                        onClick = { onAction("LANJUTKAN") },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ExpressiveOrange)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lanjutkan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else if (ujian.lewat) {
                    Text(
                        text = "Waktu Habis",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                } else if (ujian.statusLabel == "Belum Mulai") {
                    Text(
                        text = "Belum Mulai",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Button(
                        onClick = { onAction("KERJAKAN") },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kerjakan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ============================================================
// EMPTY STATE COMPONENT
// ============================================================
@Composable
fun EmptyUjianState(filter: String) {
    val message = when (filter) {
        "tersedia" -> "Belum ada ujian yang tersedia untuk dikerjakan."
        "selesai" -> "Belum ada ujian yang telah diselesaikan."
        "berjalan" -> "Tidak ada ujian yang sedang berlangsung."
        "latihan" -> "Belum ada latihan mandiri yang tersedia."
        else -> "Tidak ada ujian ditemukan."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Inbox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
