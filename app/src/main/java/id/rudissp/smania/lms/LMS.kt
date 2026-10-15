package id.rudissp.smania.lms

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import id.rudissp.smania.AppConstants
import id.rudissp.smania.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LMS : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private var originalLmsList by mutableStateOf<List<LmsItem>>(emptyList())
    private var selectedMapel by mutableStateOf("Semua Mapel")
    private var titleText by mutableStateOf("LMS")
    private var isLoading by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = getSharedPreferences("PREFS", MODE_PRIVATE)

        val nisSiswa = prefs.getString("nis", "").toString()
        val kelasSiswa = prefs.getString("kelas", "").toString()

        titleText = if (kelasSiswa.isNotEmpty()) "LMS Kelas $kelasSiswa" else "LMS"

        setContent {
            AppTheme {
                val mapelList = remember(originalLmsList) {
                    val unique = originalLmsList.map { it.mapel }.distinct().sorted()
                    listOf("Semua Mapel") + unique
                }

                val filteredList = remember(originalLmsList, selectedMapel) {
                    if (selectedMapel == "Semua Mapel") {
                        originalLmsList
                    } else {
                        originalLmsList.filter { it.mapel == selectedMapel }
                    }
                }

                LMSScreen(
                    title = titleText,
                    mapelList = mapelList,
                    selectedMapel = selectedMapel,
                    lmsItems = filteredList,
                    isLoading = isLoading,
                    onMapelSelected = { selectedMapel = it },
                    onItemClick = { item ->
                        val intent = Intent(this@LMS, DetailTugasActivity::class.java).apply {
                            putExtra("ID_TUGAS", item.idTugas)
                        }
                        startActivity(intent)
                    },
                    onBack = { finish() }
                )
            }
        }

        loadLmsData(nisSiswa, kelasSiswa)
    }

    private fun loadLmsData(nis: String, kelas: String) {
        isLoading = true
        val url = AppConstants.LMS_URL

        val stringRequest = object : StringRequest(
            Method.POST, url,
            { response ->
                isLoading = false
                try {
                    val gson = Gson()
                    val lmsResponse = gson.fromJson(response, LmsResponse::class.java)

                    if (lmsResponse.status == "success") {
                        val listData = lmsResponse.data
                        if (listData.isNotEmpty()) {
                            originalLmsList = listData
                        } else {
                            Toast.makeText(this@LMS, "Belum ada tugas atau materi.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this@LMS, "Status error dari server", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@LMS, "Gagal memproses data: ${e.message}", Toast.LENGTH_LONG).show()
                }
            },
            { error ->
                isLoading = false
                Toast.makeText(this@LMS, "Koneksi Gagal: ${error.message}", Toast.LENGTH_LONG).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["nis"] = nis
                params["kelas"] = kelas
                return params
            }
        }

        Volley.newRequestQueue(this).add(stringRequest)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LMSScreen(
    title: String,
    mapelList: List<String>,
    selectedMapel: String,
    lmsItems: List<LmsItem>,
    isLoading: Boolean,
    onMapelSelected: (String) -> Unit,
    onItemClick: (LmsItem) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
                modifier = Modifier.fillMaxSize()
            ) {
                // Filter Bar Mapel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pilih Mapel: ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        mapelList.forEach { mapel ->
                            FilterChip(
                                selected = (mapel == selectedMapel),
                                onClick = { onMapelSelected(mapel) },
                                label = { Text(mapel) }
                            )
                        }
                    }
                }

                HorizontalDivider()

                // List LMS
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(lmsItems) { item ->
                        LmsCardItem(
                            item = item,
                            onClick = { onItemClick(item) }
                        )
                    }
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

@Composable
private fun LmsCardItem(
    item: LmsItem,
    onClick: () -> Unit
) {
    val isMateri = item.jenis.equals("materi", ignoreCase = true)

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Row Judul & Badge Jenis
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = item.judul,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp)
                )

                if (isMateri) {
                    BadgeChip(
                        text = "Materi",
                        containerColor = Color(0xFFE8F5E9),
                        contentColor = Color(0xFF2E7D32)
                    )
                } else {
                    BadgeChip(
                        text = "Penugasan",
                        containerColor = Color(0xFFFFF3E0),
                        contentColor = Color(0xFFE65100)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Badge Mapel
            BadgeChip(
                text = item.mapel,
                containerColor = Color(0xFFE3F2FD),
                contentColor = Color(0xFF1565C0)
            )

            if (!isMateri) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                // Status Container
                val isOnline = item.isOnline == 1
                val sudahKumpul = item.idPengumpulan != null

                var statusText: String
                var statusColor: Color
                var deadlineText: String? = null

                val locale = remember { Locale.getDefault() }
                val dbDateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale) }
                val inputFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale) }
                val outputFormat = remember { SimpleDateFormat("dd/MM/yy HH:mm", locale) }

                if (isOnline) {
                    if (sudahKumpul) {
                        statusText = if (item.nilai > 0) {
                            "Sudah Upload Tugas (Nilai: ${item.nilai})"
                        } else {
                            "Sudah Upload Tugas"
                        }
                        statusColor = Color(0xFF2E7D32)
                    } else {
                        try {
                            val deadlineDate = dbDateFormat.parse(item.deadline)
                            val now = Date()
                            if (deadlineDate != null && now.after(deadlineDate)) {
                                statusText = "Terlambat"
                                statusColor = Color(0xFFC62828)
                            } else {
                                statusText = "Belum Mengumpulkan"
                                statusColor = Color(0xFF455A64)
                            }
                        } catch (_: Exception) {
                            statusText = "Belum Mengumpulkan"
                            statusColor = Color(0xFF455A64)
                        }
                    }

                    try {
                        val date = inputFormat.parse(item.deadline)
                        if (date != null) {
                            deadlineText = "Tenggat: ${outputFormat.format(date)}"
                        }
                    } catch (_: Exception) {
                        deadlineText = "Tenggat: ${item.deadline}"
                    }
                } else {
                    statusText = "Tugas Tatap Muka"
                    statusColor = Color(0xFF0277BD)
                }

                Text(
                    text = statusText,
                    fontSize = 16.sp,
                    color = statusColor
                )

                if (deadlineText != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = deadlineText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LMSScreenPreview() {
    AppTheme {
        LMSScreen(
            title = "LMS Kelas XII 6",
            mapelList = listOf("Semua Mapel", "Matematika", "Pendidikan Pancasila", "Fisika"),
            selectedMapel = "Semua Mapel",
            lmsItems = listOf(
                LmsItem(
                    idTugas = "1",
                    judul = "Tugas Trigonometri",
                    mapel = "Matematika",
                    jenis = "penugasan",
                    isOnline = 1,
                    deadline = "2026-05-30 12:26:00",
                    createdAt = "2026-05-20 08:00:00",
                    idPengumpulan = "101",
                    nilai = 85
                ),
                LmsItem(
                    idTugas = "2",
                    judul = "Materi Hak dan Kewajiban Warga Negara",
                    mapel = "Pendidikan Pancasila",
                    jenis = "materi",
                    isOnline = 1,
                    deadline = "",
                    createdAt = "2026-05-21 09:00:00",
                    idPengumpulan = null,
                    nilai = 0
                ),
                LmsItem(
                    idTugas = "3",
                    judul = "Latihan Soal Hukum Newton",
                    mapel = "Fisika",
                    jenis = "penugasan",
                    isOnline = 0,
                    deadline = "",
                    createdAt = "2026-05-22 10:00:00",
                    idPengumpulan = null,
                    nilai = 0
                )
            ),
            isLoading = false,
            onMapelSelected = {},
            onItemClick = {},
            onBack = {}
        )
    }
}
