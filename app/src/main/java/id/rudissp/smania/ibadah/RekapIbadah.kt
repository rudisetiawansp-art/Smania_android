package id.rudissp.smania.ibadah

import id.rudissp.smania.LoadingIndicator
import android.content.SharedPreferences
import android.icu.util.Calendar
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.common.math.LinearTransformation.horizontal
import id.rudissp.smania.AppConstants
import id.rudissp.smania.AppTheme
import id.rudissp.smania.VolleySingleton
import org.json.JSONArray
import org.json.JSONObject

data class AktivitasItem(
    val isHalangan: Boolean,
    val namaShalat: String
)

data class RekapData(
    val tanggal: Int,
    val namaBulan: String,
    val namaHari: String,
    val isWeekend: Boolean,
    val listAktivitas: List<AktivitasItem>
)

class RekapIbadah : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private val namaBulan = arrayOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    )

    private var selectedBulan by mutableStateOf("")
    private var selectedTahun by mutableStateOf("")
    private var labelBulan by mutableStateOf("Bulan ...")
    private var listData by mutableStateOf<List<RekapData>>(emptyList())
    private var isLoading by mutableStateOf(false)

    private lateinit var listTahun: Array<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = getSharedPreferences("PREFS", MODE_PRIVATE)

        val cal = Calendar.getInstance()
        val indexBulanSaatIni = cal.get(Calendar.MONTH)
        val tahunSekarang = cal.get(Calendar.YEAR)

        selectedBulan = namaBulan[indexBulanSaatIni]
        selectedTahun = tahunSekarang.toString()
        listTahun = arrayOf(tahunSekarang.toString(), (tahunSekarang - 1).toString())

        setContent {
            AppTheme {
                RekapIbadahScreen(
                    selectedBulan = selectedBulan,
                    selectedTahun = selectedTahun,
                    bulanList = namaBulan.toList(),
                    tahunList = listTahun.toList(),
                    labelBulan = labelBulan,
                    listData = listData,
                    isLoading = isLoading,
                    onBulanSelected = { selectedBulan = it },
                    onTahunSelected = { selectedTahun = it },
                    onCekClick = { performCek() },
                    onBack = { finish() }
                )
            }
        }

        performCek()
    }

    private fun performCek() {
        val bulanDipilih = namaBulan.indexOf(selectedBulan) + 1
        if (bulanDipilih > 0 && selectedTahun.isNotEmpty()) {
            val tahunDipilih = selectedTahun.toInt()
            labelBulan = "Bulan $selectedBulan $selectedTahun"
            fetchDataRekap(bulanDipilih, tahunDipilih)
        } else {
            Toast.makeText(this, "Mohon pilih bulan dan tahun yang valid", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchDataRekap(bulan: Int, tahun: Int) {
        isLoading = true
        val nis = prefs.getString("nis", "").toString()
        val url = AppConstants.IBADAH_REKAP + "?nis=$nis&bulan=$bulan&tahun=$tahun"

        val queue = VolleySingleton.getInstance(this).requestQueue
        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                isLoading = false
                try {
                    val jsonArray = JSONArray(response)
                    val mapDataServer = HashMap<Int, MutableList<JSONObject>>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val tglStr = obj.getString("tgl")
                        val hariIndex = tglStr.substring(8, 10).toInt()

                        if (!mapDataServer.containsKey(hariIndex)) {
                            mapDataServer[hariIndex] = mutableListOf()
                        }
                        mapDataServer[hariIndex]?.add(obj)
                    }

                    val cal = Calendar.getInstance()
                    cal.set(Calendar.YEAR, tahun)
                    cal.set(Calendar.MONTH, bulan - 1)
                    val jumlahHari = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

                    val newList = mutableListOf<RekapData>()
                    for (d in 1..jumlahHari) {
                        cal.set(Calendar.DAY_OF_MONTH, d)
                        val hariMinggu = cal.get(Calendar.DAY_OF_WEEK)
                        val isWeekend = (hariMinggu == Calendar.SATURDAY || hariMinggu == Calendar.SUNDAY)

                        val namaHari = when (hariMinggu) {
                            Calendar.SUNDAY -> "Minggu"; Calendar.MONDAY -> "Senin"
                            Calendar.TUESDAY -> "Selasa"; Calendar.WEDNESDAY -> "Rabu"
                            Calendar.THURSDAY -> "Kamis"; Calendar.FRIDAY -> "Jumat"
                            else -> "Sabtu"
                        }

                        val rawList = mapDataServer[d] ?: emptyList()
                        val aktivitasList = rawList.map { act ->
                            AktivitasItem(
                                isHalangan = act.optString("is_halangan") == "Ya",
                                namaShalat = act.optString("nama_shalat", "")
                            )
                        }

                        newList.add(
                            RekapData(
                                tanggal = d,
                                namaBulan = namaBulan[bulan - 1],
                                namaHari = namaHari,
                                isWeekend = isWeekend,
                                listAktivitas = aktivitasList
                            )
                        )
                    }
                    listData = newList

                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Gagal parsing data", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                isLoading = false
                Toast.makeText(this, "Koneksi Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        )

        queue.add(request)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RekapIbadahScreen(
    selectedBulan: String,
    selectedTahun: String,
    bulanList: List<String>,
    tahunList: List<String>,
    labelBulan: String,
    listData: List<RekapData>,
    isLoading: Boolean,
    onBulanSelected: (String) -> Unit,
    onTahunSelected: (String) -> Unit,
    onCekClick: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        // Background utama yang paling terang di M3
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Rekap Ibadah",
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
                    .padding(horizontal = 20.dp), // Spasi M3 lebih lapang
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Filter Card (Bulan & Tahun) - Menggunakan Surface Container untuk elevasi warna
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp), // Radius lebih besar khas M3
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DropdownMenuField(
                            label = "Bulan",
                            options = bulanList,
                            selectedOption = selectedBulan,
                            onOptionSelected = onBulanSelected,
                            modifier = Modifier.fillMaxWidth()
                        )

                        DropdownMenuField(
                            label = "Tahun",
                            options = tahunList,
                            selectedOption = selectedTahun,
                            onOptionSelected = onTahunSelected,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = onCekClick,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = "Cek Rekap",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                // Month Label
                Text(
                    text = labelBulan,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                // List of Rekap
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    val listSize = listData.size

                    itemsIndexed(listData) { index, data ->
                        val itemShape = when {
                            listSize == 1 -> RoundedCornerShape(24.dp)
                            index == 0 -> RoundedCornerShape(
                                topStart = 24.dp, topEnd = 24.dp,
                                bottomStart = 4.dp, bottomEnd = 4.dp
                            )
                            index == listSize - 1 -> RoundedCornerShape(
                                topStart = 4.dp, topEnd = 4.dp,
                                bottomStart = 24.dp, bottomEnd = 24.dp
                            )
                            else -> RoundedCornerShape(4.dp)
                        }

                        RekapHariCard(data = data, shape = itemShape)
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
private fun DropdownMenuField(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(16.dp), // Radius field input M3
            // PERBAIKAN: Menambahkan fillMaxWidth langsung di OutlinedTextField
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
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun RekapHariCard(
    data: RekapData,
    shape: androidx.compose.ui.graphics.Shape
) {
    // Penggunaan SurfaceContainer khas M3 untuk item kartu
    val cardBg = if (data.isWeekend) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp), // Padding lebih lapang
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${data.tanggal} ${data.namaBulan}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = data.namaHari,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // FlowRow atau Row biasa untuk membungkus badge
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (data.isWeekend) {
                    BadgeItem(
                        text = "Libur",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                } else {
                    if (data.listAktivitas.isEmpty()) {
                        Text(
                            text = "Belum Ada Data",
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        data.listAktivitas.forEach { act ->
                            if (act.isHalangan) {
                                BadgeItem(
                                    text = "Halangan",
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            } else {
                                BadgeItem(
                                    text = act.namaShalat,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeItem(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = CircleShape, // Pill shape untuk badge sangat M3
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RekapIbadahScreenPreview() {
    AppTheme {
        RekapIbadahScreen(
            selectedBulan = "Mei",
            selectedTahun = "2025",
            bulanList = listOf("Januari", "Februari", "Maret", "April", "Mei", "Juni"),
            tahunList = listOf("2025", "2024"),
            labelBulan = "Bulan Mei 2025",
            listData = listOf(
                RekapData(
                    tanggal = 1,
                    namaBulan = "Mei",
                    namaHari = "Kamis",
                    isWeekend = false,
                    listAktivitas = listOf(
                        AktivitasItem(isHalangan = false, namaShalat = "Dzuhur"),
                        AktivitasItem(isHalangan = false, namaShalat = "Ashar")
                    )
                ),
                RekapData(
                    tanggal = 2,
                    namaBulan = "Mei",
                    namaHari = "Jumat",
                    isWeekend = false,
                    listAktivitas = listOf(
                        AktivitasItem(isHalangan = true, namaShalat = "")
                    )
                ),
                RekapData(
                    tanggal = 3,
                    namaBulan = "Mei",
                    namaHari = "Sabtu",
                    isWeekend = true,
                    listAktivitas = emptyList()
                )
            ),
            isLoading = false,
            onBulanSelected = {},
            onTahunSelected = {},
            onCekClick = {},
            onBack = {}
        )
    }
}