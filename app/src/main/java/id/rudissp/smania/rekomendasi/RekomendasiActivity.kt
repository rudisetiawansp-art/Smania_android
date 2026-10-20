package id.rudissp.smania.rekomendasi

import id.rudissp.smania.LoadingIndicator
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import id.rudissp.smania.AppConstants
import id.rudissp.smania.AppTheme
import id.rudissp.smania.VolleySingleton
import org.json.JSONObject
import java.util.Locale

class RekomendasiActivity : AppCompatActivity() {

    private var nisPref: String = ""
    private var tingkatPref: String = ""

    private var responseData by mutableStateOf<RekomendasiResponse?>(null)
    private var isLoading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)

    // Form Filter States
    private var searchQuery by mutableStateOf("")
    private var selectedKampus by mutableStateOf("Semua")
    private var selectedPrestasi by mutableStateOf("Tidak Ada")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("PREFS", MODE_PRIVATE)
        nisPref = prefs.getString("nis", "") ?: ""
        tingkatPref = prefs.getString("tingkat", "") ?: ""

        if (tingkatPref.isEmpty()) {
            tingkatPref = "XII"
        }

        setContent {
            AppTheme {
                RekomendasiScreen(
                    response = responseData,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    searchQuery = searchQuery,
                    selectedKampus = selectedKampus,
                    selectedPrestasi = selectedPrestasi,
                    onSearchQueryChange = { searchQuery = it },
                    onKampusSelected = { selectedKampus = it },
                    onPrestasiSelected = { selectedPrestasi = it },
                    onSearchClick = { fetchRekomendasiData() },
                    onRefresh = { fetchRekomendasiData() },
                    onBack = { finish() }
                )
            }
        }
    }

    private fun fetchRekomendasiData() {
        if (nisPref.isBlank()) {
            errorMessage = "NIS tidak ditemukan. Silakan login ulang."
            return
        }

        isLoading = true
        errorMessage = null

        val queue = VolleySingleton.getInstance(this).requestQueue
        val url = AppConstants.REKOMENDASI_URL

        val stringRequest = object : StringRequest(
            Method.POST, url,
            { responseText ->
                isLoading = false
                try {
                    val json = JSONObject(responseText)
                    val status = json.optBoolean("status", false)
                    val message = json.optString("message", "")

                    val siswaObj = json.optJSONObject("siswa")
                    val siswaInfo = SiswaInfo(
                        nama = siswaObj?.optString("nama", "Siswa") ?: "Siswa",
                        kelas = siswaObj?.optString("kelas", "-") ?: "-",
                        rataRata = siswaObj?.optDouble("rata_rata", 0.0) ?: 0.0,
                        ditemukan = siswaObj?.optBoolean("ditemukan", false) ?: false
                    )

                    val listKampusArr = json.optJSONArray("list_kampus")
                    val listKampus = mutableListOf<String>()
                    if (listKampusArr != null) {
                        for (i in 0 until listKampusArr.length()) {
                            listKampus.add(listKampusArr.getString(i))
                        }
                    }

                    val dataArr = json.optJSONArray("data")
                    val recommendations = mutableListOf<RekomendasiItem>()
                    if (dataArr != null) {
                        for (i in 0 until dataArr.length()) {
                            val itemObj = dataArr.getJSONObject(i)

                            val alasanArr = itemObj.optJSONArray("alasan")
                            val alasanList = mutableListOf<String>()
                            if (alasanArr != null) {
                                for (a in 0 until alasanArr.length()) {
                                    alasanList.add(alasanArr.getString(a))
                                }
                            }

                            recommendations.add(
                                RekomendasiItem(
                                    kampus = itemObj.optString("kampus", ""),
                                    prodi = itemObj.optString("prodi", ""),
                                    jenjang = itemObj.optString("jenjang", ""),
                                    skor = itemObj.optInt("skor", 0),
                                    kuota = itemObj.optInt("kuota", 0),
                                    peminat = itemObj.optInt("peminat", 0),
                                    rasio = itemObj.optDouble("rasio", 0.0),
                                    estPeminat = itemObj.optInt("est_peminat", 0),
                                    rasioEst = itemObj.optDouble("rasio_est", 0.0),
                                    tren = itemObj.optDouble("tren", 0.0),
                                    adaSnbt = itemObj.optBoolean("ada_snbt", false),
                                    snbtKuota = itemObj.optInt("snbt_kuota", 0),
                                    snbtPeminat = itemObj.optInt("snbt_peminat", 0),
                                    snbtRasio = itemObj.optDouble("snbt_rasio", 0.0),
                                    gabungRasio = itemObj.optDouble("gabung_rasio", 0.0),
                                    alasan = alasanList
                                )
                            )
                        }
                    }

                    responseData = RekomendasiResponse(
                        status = status,
                        message = message,
                        siswa = siswaInfo,
                        listKampus = listKampus,
                        data = recommendations
                    )

                    if (!status) {
                        errorMessage = message
                    }
                } catch (e: Exception) {
                    Log.e("REKOMENDASI_PARSER", "Error parsing response", e)
                    errorMessage = "Gagal memproses data dari server."
                }
            },
            { error ->
                isLoading = false
                Log.e("REKOMENDASI_VOLLEY", "Network error: ${error.message}")
                errorMessage = "Gagal terhubung ke server. Periksa koneksi internet Anda."
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["nis"] = nisPref
                params["tingkat"] = tingkatPref
                params["keyword"] = searchQuery
                params["kampus"] = selectedKampus
                params["prestasi"] = selectedPrestasi
                return params
            }
        }

        queue.add(stringRequest)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RekomendasiScreen(
    response: RekomendasiResponse?,
    isLoading: Boolean,
    errorMessage: String?,
    searchQuery: String,
    selectedKampus: String,
    selectedPrestasi: String,
    onSearchQueryChange: (String) -> Unit,
    onKampusSelected: (String) -> Unit,
    onPrestasiSelected: (String) -> Unit,
    onSearchClick: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val filteredData = response?.data ?: emptyList()
    var showKampusSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Rekomendasi PTN",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
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
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Muat Ulang"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) { innerPadding ->
        // Bottom Sheet untuk pencarian kampus (Menghindari Lag Dropdown)
        if (showKampusSheet) {
            KampusBottomSheet(
                listKampus = response?.listKampus ?: emptyList(),
                onKampusSelected = {
                    onKampusSelected(it)
                    showKampusSheet = false
                },
                onDismiss = { showKampusSheet = false }
            )
        }

        // LazyColumn ini membungkus SEMUA elemen sehingga semuanya bisa terscroll bersamaan
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Hero Card Expressive
            item {
                if (response?.siswa != null && response.siswa.ditemukan) {
                    ExpressiveHeroCard(siswa = response.siswa)
                } else {
                    ExpressiveHeroCardDefault()
                }
            }

            // 2. Filter Section
            item {
                FilterSection(
                    searchQuery = searchQuery,
                    selectedKampus = selectedKampus,
                    selectedPrestasi = selectedPrestasi,
                    onSearchQueryChange = onSearchQueryChange,
                    onKampusClicked = { showKampusSheet = true },
                    onPrestasiSelected = onPrestasiSelected,
                    onSearchClick = onSearchClick
                )
            }

            // 3. Ringkasan Status
            item {
                if (response != null && !isLoading && errorMessage == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${filteredData.size} Hasil",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatusBadgeDot(color = Color(0xFF2E7D32), label = "Tinggi")
                            StatusBadgeDot(color = Color(0xFFED6C02), label = "Sedang")
                            StatusBadgeDot(color = Color(0xFFD32F2F), label = "Ketat")
                        }
                    }
                }
            }

            // 4. State Loading
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LoadingIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Menganalisis Keketatan Prodi...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 5. State Error
            if (errorMessage != null && !isLoading) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onRefresh,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Coba Lagi")
                            }
                        }
                    }
                }
            }

            // 6. List Rekomendasi
            if (!isLoading && errorMessage == null) {
                if (filteredData.isEmpty() && response != null) {
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
                                    text = "Tidak Ada Hasil",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Coba ubah kata kunci atau gunakan filter lain.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(filteredData) { item ->
                        RekomendasiCard(item = item)
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveHeroCard(siswa: SiswaInfo) {
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
                        text = "Rapor Akademik",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Halo, ${siswa.nama}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Kelas: ${siswa.kelas}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(start = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Rata-rata",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primaryContainer
                    )
                    Text(
                        text = String.format(Locale.ROOT, "%.2f", siswa.rataRata),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun ExpressiveHeroCardDefault() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
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
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Matching Engine",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Cari Rekomendasi PTN",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Gunakan form di bawah untuk analisis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSection(
    searchQuery: String,
    selectedKampus: String,
    selectedPrestasi: String,
    onSearchQueryChange: (String) -> Unit,
    onKampusClicked: () -> Unit,
    onPrestasiSelected: (String) -> Unit,
    onSearchClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Cari Prodi / PTN", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            shape = RoundedCornerShape(20.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchClick() })
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Box untuk menggantikan dropdown agar memicu BottomSheet
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onKampusClicked() }
            ) {
                OutlinedTextField(
                    value = selectedKampus,
                    onValueChange = {},
                    readOnly = true,
                    enabled = false, // Dinonaktifkan secara input teks agar Box menangkap klik
                    label = { Text("Kampus", style = MaterialTheme.typography.labelMedium) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                var expandedPrestasi by remember { mutableStateOf(false) }
                val opsiPrestasi = listOf("Tidak Ada", "Kabupaten", "Provinsi", "Nasional", "Internasional")

                ExposedDropdownMenuBox(
                    expanded = expandedPrestasi,
                    onExpandedChange = { expandedPrestasi = !expandedPrestasi }
                ) {
                    OutlinedTextField(
                        value = selectedPrestasi,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Prestasi", style = MaterialTheme.typography.labelMedium) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPrestasi) },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedPrestasi,
                        onDismissRequest = { expandedPrestasi = false }
                    ) {
                        opsiPrestasi.forEach { level ->
                            DropdownMenuItem(
                                text = { Text(level) },
                                onClick = {
                                    onPrestasiSelected(level)
                                    expandedPrestasi = false
                                }
                            )
                        }
                    }
                }
            }
        }

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.95f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )

        Button(
            onClick = onSearchClick,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .scale(scale),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Cari Rekomendasi",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KampusBottomSheet(
    listKampus: List<String>,
    onKampusSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchSheetQuery by remember { mutableStateOf("") }

    val filteredKampus = remember(listKampus, searchSheetQuery) {
        if (searchSheetQuery.isBlank()) {
            listKampus
        } else {
            listKampus.filter { it.contains(searchSheetQuery, ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Pilih Kampus",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = searchSheetQuery,
                onValueChange = { searchSheetQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari nama kampus...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(20.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Menggunakan LazyColumn agar list kampus ratusan/ribuan data tidak nge-lag
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false) // Memberikan ruang hingga max layar yang tersedia
            ) {
                item {
                    ListItem(
                        headlineContent = { Text("Semua", style = MaterialTheme.typography.bodyLarge) },
                        modifier = Modifier.clickable { onKampusSelected("Semua") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                items(filteredKampus) { kampus ->
                    ListItem(
                        headlineContent = { Text(kampus, style = MaterialTheme.typography.bodyLarge) },
                        modifier = Modifier.clickable { onKampusSelected(kampus) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadgeDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun RekomendasiCard(item: RekomendasiItem) {
    var expanded by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    val cardRadius by animateDpAsState(
        targetValue = if (expanded) 16.dp else 28.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { expanded = !expanded },
        shape = RoundedCornerShape(cardRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.kampus.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (item.jenjang.isNotBlank()) "${item.prodi} (${item.jenjang})" else item.prodi,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Peluang: ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${item.skor}%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = "Rasio 1:${formatRatio(item.rasio)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { item.skor.coerceIn(0, 100) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(value = formatNumber(item.kuota), label = "Kuota", modifier = Modifier.weight(1f))
                            StatItem(value = formatNumber(item.peminat), label = "Peminat", modifier = Modifier.weight(1f))
                            StatItem(value = formatNumber(item.estPeminat), label = "Estimasi", modifier = Modifier.weight(1f))
                        }
                    }

                    if (item.alasan.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item.alasan.forEach { point ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = point,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StatItem(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatNumber(number: Int): String {
    return String.format(Locale.US, "%,d", number)
}

private fun formatRatio(ratio: Double): String {
    return if (ratio % 1.0 == 0.0) {
        ratio.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", ratio)
    }
}