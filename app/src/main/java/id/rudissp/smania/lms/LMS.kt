package id.rudissp.smania.lms

import id.rudissp.smania.LoadingIndicator
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Schedule
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
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import id.rudissp.smania.AppConstants
import id.rudissp.smania.AppTheme
import id.rudissp.smania.VolleySingleton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LMS : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private var originalLmsList by mutableStateOf<List<LmsItem>>(emptyList())
    private var selectedMapel by mutableStateOf("Semua Mapel")
    private var titleText by mutableStateOf("LMS Akademik")
    private var isLoading by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = getSharedPreferences("PREFS", MODE_PRIVATE)

        val nisSiswa = prefs.getString("nis", "").toString()
        val kelasSiswa = prefs.getString("kelas", "").toString()

        titleText = if (kelasSiswa.isNotEmpty()) "LMS Kelas $kelasSiswa" else "LMS Akademik"

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

        VolleySingleton.getInstance(this).addToRequestQueue(stringRequest)
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    scrolledContainerColor = Color.Unspecified,
                    navigationIconContentColor = Color.Unspecified,
                    titleContentColor = Color.Unspecified,
                    actionIconContentColor = Color.Unspecified
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
                // Expressive Hero Summary Banner Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 0.dp
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
                                    imageVector = Icons.Filled.Class,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pembelajaran Daring",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${lmsItems.size} Modul & Tugas Aktif",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Pilih mata pelajaran melalui menu filter di bawah untuk mempersempit daftar.",
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                // Expressive Dropdown Filter Card (Mapel)
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
                            .padding(20.dp)
                    ) {
                        MapelDropdownFilter(
                            mapelList = mapelList,
                            selectedMapel = selectedMapel,
                            onMapelSelected = onMapelSelected
                        )
                    }
                }

                // List LMS Items
                if (lmsItems.isEmpty() && !isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada materi atau penugasan untuk mata pelajaran ini.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        items(lmsItems) { item ->
                            LmsCardItem(
                                item = item,
                                onClick = { onItemClick(item) }
                            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapelDropdownFilter(
    mapelList: List<String>,
    selectedMapel: String,
    onMapelSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Filter Mata Pelajaran",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedMapel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Pilih Mata Pelajaran") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                mapelList.forEach { mapel ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = mapel,
                                fontSize = 14.sp,
                                fontWeight = if (mapel == selectedMapel) FontWeight.Bold else FontWeight.Normal,
                                color = if (mapel == selectedMapel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            onMapelSelected(mapel)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

data class LmsStatusInfo(
    val statusText: String,
    val containerColor: @Composable () -> Color,
    val contentColor: @Composable () -> Color,
    val deadlineText: String?
)

@Composable
private fun getLmsStatusInfo(item: LmsItem): LmsStatusInfo {
    val isOnline = item.isOnline == 1
    val sudahKumpul = item.idPengumpulan != null

    if (!isOnline) {
        return LmsStatusInfo(
            statusText = "Tatap Muka",
            containerColor = { MaterialTheme.colorScheme.secondaryContainer },
            contentColor = { MaterialTheme.colorScheme.onSecondaryContainer },
            deadlineText = null
        )
    }

    if (sudahKumpul) {
        val text = if (item.nilai > 0) "Sudah Upload (Nilai: ${item.nilai})" else "Sudah Upload"
        val deadlineText = formatDeadline(item.deadline)
        return LmsStatusInfo(
            statusText = text,
            containerColor = { MaterialTheme.colorScheme.primaryContainer },
            contentColor = { MaterialTheme.colorScheme.onPrimaryContainer },
            deadlineText = deadlineText
        )
    }

    var statusText = "Belum Mengumpulkan"
    var isTerlambat = false

    try {
        val dbDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val deadlineDate = dbDateFormat.parse(item.deadline)
        val now = Date()
        if (deadlineDate != null && now.after(deadlineDate)) {
            statusText = "Terlambat"
            isTerlambat = true
        }
    } catch (_: Exception) {
    }

    val deadlineText = formatDeadline(item.deadline)
    return if (isTerlambat) {
        LmsStatusInfo(
            statusText = statusText,
            containerColor = { MaterialTheme.colorScheme.errorContainer },
            contentColor = { MaterialTheme.colorScheme.onErrorContainer },
            deadlineText = deadlineText
        )
    } else {
        LmsStatusInfo(
            statusText = statusText,
            containerColor = { MaterialTheme.colorScheme.tertiaryContainer },
            contentColor = { MaterialTheme.colorScheme.onTertiaryContainer },
            deadlineText = deadlineText
        )
    }
}

private fun formatDeadline(deadlineStr: String): String? {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
        val date = inputFormat.parse(deadlineStr)
        if (date != null) "Tenggat: ${outputFormat.format(date)}" else "Tenggat: $deadlineStr"
    } catch (_: Exception) {
        if (deadlineStr.isNotBlank()) "Tenggat: $deadlineStr" else null
    }
}

@Composable
private fun LmsCardItem(
    item: LmsItem,
    onClick: () -> Unit
) {
    val isMateri = item.jenis.equals("materi", ignoreCase = true)
    val statusInfo = getLmsStatusInfo(item)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Row: Mapel Badge & Jenis Badge (Expressive Pill Shapes)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isMateri) Icons.Filled.Book else Icons.Default.Assignment,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = item.mapel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isMateri) {
                    BadgeChip(
                        text = "Materi",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                } else {
                    BadgeChip(
                        text = "Penugasan",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            // Judul Tugas / Materi
            Text(
                text = item.judul,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )

            if (!isMateri) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = statusInfo.containerColor()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (item.idPengumpulan != null) Icons.Filled.CheckCircle else Icons.Filled.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = statusInfo.contentColor()
                            )
                            Text(
                                text = statusInfo.statusText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusInfo.contentColor()
                            )
                        }
                    }

                    if (statusInfo.deadlineText != null) {
                        Text(
                            text = statusInfo.deadlineText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
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