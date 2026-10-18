package id.rudissp.smania.jadwal

import id.rudissp.smania.LoadingIndicator
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import id.rudissp.smania.AppConstants
import id.rudissp.smania.AppTheme
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.abs

class Jadwal : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private var titleText by mutableStateOf("Jadwal Pelajaran")
    private var listDataJadwal by mutableStateOf<List<HariModel>>(emptyList())
    private var isLoading by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        prefs = getSharedPreferences("PREFS", MODE_PRIVATE)

        val kelasSiswa = prefs.getString("kelas", "") ?: ""
        titleText = if (kelasSiswa.isNotEmpty()) "Jadwal Kelas $kelasSiswa" else "Jadwal Pelajaran"

        setContent {
            AppTheme {
                JadwalScreen(
                    title = titleText,
                    listDataJadwal = listDataJadwal,
                    isLoading = isLoading,
                    onBack = { finish() }
                )
            }
        }

        if (kelasSiswa.isNotEmpty()) {
            fetchJadwalKelas(kelasSiswa)
        } else {
            Toast.makeText(this, "Data kelas tidak ditemukan di sesi login", Toast.LENGTH_LONG).show()
        }
    }

    private fun fetchJadwalKelas(namaKelas: String) {
        isLoading = true
        val url = AppConstants.API_JADWAL_KELAS + "?kelas=$namaKelas"

        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                isLoading = false
                try {
                    val jsonResponse = JSONObject(response)
                    if (jsonResponse.getString("status") == "success") {
                        val jsonArrayData = jsonResponse.getJSONArray("data")
                        val newList = mutableListOf<HariModel>()

                        for (i in 0 until jsonArrayData.length()) {
                            val hariObj = jsonArrayData.getJSONObject(i)
                            val namaHari = hariObj.getString("hari")
                            val jadwalArray = hariObj.getJSONArray("jadwal")

                            val listJadwalSementara = mutableListOf<JadwalItemModel>()

                            for (j in 0 until jadwalArray.length()) {
                                val jd = jadwalArray.getJSONObject(j)
                                listJadwalSementara.add(
                                    JadwalItemModel(
                                        jamKe = jd.getInt("jam_ke"),
                                        status = jd.getString("status"),
                                        mapel = jd.getString("mapel"),
                                        infoBawah = jd.optString("nama_guru", "")
                                    )
                                )
                            }
                            newList.add(HariModel(namaHari, listJadwalSementara))
                        }
                        listDataJadwal = newList
                    } else {
                        Toast.makeText(this, "Jadwal belum tersedia", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            { _ ->
                isLoading = false
                Toast.makeText(this, "Koneksi gagal", Toast.LENGTH_SHORT).show()
            }
        )
        Volley.newRequestQueue(this).add(request)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JadwalScreen(
    title: String,
    listDataJadwal: List<HariModel>,
    isLoading: Boolean,
    onBack: () -> Unit
) {
    val calendar = Calendar.getInstance()
    val currentDayIndex = calendar.get(Calendar.DAY_OF_WEEK)
    val defaultDay = when (currentDayIndex) {
        Calendar.MONDAY -> "Senin"
        Calendar.TUESDAY -> "Selasa"
        Calendar.WEDNESDAY -> "Rabu"
        Calendar.THURSDAY -> "Kamis"
        Calendar.FRIDAY -> "Jumat"
        Calendar.SATURDAY -> "Sabtu"
        else -> "Senin"
    }

    var selectedHari by remember { mutableStateOf(defaultDay) }

    LaunchedEffect(listDataJadwal) {
        if (listDataJadwal.isNotEmpty() && listDataJadwal.none { it.namaHari.equals(selectedHari, ignoreCase = true) }) {
            selectedHari = listDataJadwal.first().namaHari
        }
    }

    val currentHariModel = listDataJadwal.find { it.namaHari.equals(selectedHari, ignoreCase = true) }

    // --- LOGIKA PEMETAAN WARNA UNIK PER GURU/MAPEL PADA HARI DILIHAT ---
    val expressivePalette = listOf(
        Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer),
        Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer),
        Pair(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer),
        Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    )

    // Memetakan setiap kombinasi unik (Mapel + Guru) di hari ini ke warna tertentu
    val colorMap = remember(currentHariModel) {
        val map = mutableMapOf<String, Pair<Color, Color>>()
        var colorIndex = 0

        currentHariModel?.listJadwal?.forEach { item ->
            val isKosong = item.status.equals("kosong", ignoreCase = true) || item.mapel.isBlank()
            if (!isKosong) {
                // Key unik gabungan mapel dan guru
                val key = "${item.mapel}_${item.infoBawah}"
                if (!map.containsKey(key)) {
                    map[key] = expressivePalette[colorIndex % expressivePalette.size]
                    colorIndex++
                }
            }
        }
        map
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = title,
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
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Hero Banner Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
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
                                    imageVector = Icons.Filled.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Informasi KBM",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tertib & Tepat Waktu",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Warna badge jam dan kartu yang sama menunjukkan blok jam pelajaran berurutan dari guru/mapel yang sama.",
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                // Filter Chip Hari
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Pilih Hari",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    val daysList = if (listDataJadwal.isNotEmpty()) {
                        listDataJadwal.map { it.namaHari }
                    } else {
                        listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        daysList.forEach { hari ->
                            FilterChip(
                                selected = hari.equals(selectedHari, ignoreCase = true),
                                onClick = { selectedHari = hari },
                                label = {
                                    Text(
                                        text = hari,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = null
                            )
                        }
                    }
                }

                // Grouped Timeline Cards
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (currentHariModel != null && currentHariModel.listJadwal.isNotEmpty()) {
                        val listSize = currentHariModel.listJadwal.size

                        itemsIndexed(currentHariModel.listJadwal) { index, item ->
                            val itemShape = when {
                                listSize == 1 -> RoundedCornerShape(28.dp)
                                index == 0 -> RoundedCornerShape(
                                    topStart = 28.dp, topEnd = 28.dp,
                                    bottomStart = 4.dp, bottomEnd = 4.dp
                                )
                                index == listSize - 1 -> RoundedCornerShape(
                                    topStart = 4.dp, topEnd = 4.dp,
                                    bottomStart = 28.dp, bottomEnd = 28.dp
                                )
                                else -> RoundedCornerShape(4.dp)
                            }

                            val isKosong = item.status.equals("kosong", ignoreCase = true) || item.mapel.isBlank()
                            val key = "${item.mapel}_${item.infoBawah}"
                            val colorPair = if (isKosong) {
                                Pair(
                                    MaterialTheme.colorScheme.surfaceContainer,
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                colorMap[key] ?: Pair(
                                    MaterialTheme.colorScheme.surfaceContainer,
                                    MaterialTheme.colorScheme.onSurface
                                )
                            }

                            JadwalTimelineItem(
                                item = item,
                                shape = itemShape,
                                containerColor = if (isKosong) MaterialTheme.colorScheme.surfaceContainer else colorPair.first.copy(alpha = 0.35f),
                                badgeColor = if (isKosong) MaterialTheme.colorScheme.surfaceContainerHighest else colorPair.first,
                                badgeTextColor = if (isKosong) MaterialTheme.colorScheme.onSurfaceVariant else colorPair.second,
                                accentTextColor = if (isKosong) MaterialTheme.colorScheme.onSurfaceVariant else colorPair.second
                            )
                        }
                    } else {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isLoading) "Memuat jadwal..." else "Tidak ada jadwal untuk hari $selectedHari",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
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

@Composable
private fun JadwalTimelineItem(
    item: JadwalItemModel,
    shape: androidx.compose.ui.graphics.Shape,
    containerColor: Color,
    badgeColor: Color,
    badgeTextColor: Color,
    accentTextColor: Color
) {
    val isKosong = item.status.equals("kosong", ignoreCase = true) || item.mapel.isBlank()

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Badge Jam Ke
            Surface(
                shape = CircleShape,
                color = badgeColor,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "J${item.jamKe}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeTextColor
                    )
                }
            }

            // Mapel & Guru
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (isKosong) {
                    Text(
                        text = "Jam Kosong / Istirahat",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                } else {
                    Text(
                        text = item.mapel,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.infoBawah.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = accentTextColor
                            )
                            Text(
                                text = item.infoBawah,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accentTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// Skema Warna Dinamis dari MaterialTheme M3
@Composable
private fun getExpressiveColorPair(teacherName: String): Pair<Color, Color> {
    if (teacherName.isBlank()) {
        return Pair(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val expressivePairs = listOf(
        Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer),
        Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer),
        Pair(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer),
        Pair(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface)
    )

    val index = abs(teacherName.hashCode()) % expressivePairs.size
    return expressivePairs[index]
}


@Preview(showBackground = true)
@Composable
fun JadwalScreenPreview() {
    AppTheme {
        JadwalScreen(
            title = "Jadwal Kelas XII 6",
            listDataJadwal = listOf(
                HariModel(
                    namaHari = "Senin",
                    listJadwal = listOf(
                        JadwalItemModel(1, "isi", "Matematika", "Pak Budi"),
                        JadwalItemModel(2, "isi", "Matematika", "Pak Budi"),
                        JadwalItemModel(3, "kosong", "", ""),
                        JadwalItemModel(4, "isi", "Pendidikan Pancasila", "Rudi Setiawan S.P., S.Pd"),
                        JadwalItemModel(5, "isi", "Geografi", "Anggero Hadi Wicaksono, S.Pd")
                    )
                ),
                HariModel(
                    namaHari = "Selasa",
                    listJadwal = listOf(
                        JadwalItemModel(1, "isi", "B. Inggris", "Mr. John"),
                        JadwalItemModel(2, "isi", "B. Inggris", "Mr. John"),
                        JadwalItemModel(3, "isi", "Biologi", "Bu Ani")
                    )
                )
            ),
            isLoading = false,
            onBack = {}
        )
    }
}