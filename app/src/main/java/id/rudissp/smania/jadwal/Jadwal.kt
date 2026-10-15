package id.rudissp.smania.jadwal

import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

class Jadwal : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private var titleText by mutableStateOf("Jadwal Pelajaran")
    private var listDataJadwal by mutableStateOf<List<HariModel>>(emptyList())
    private var isLoading by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        prefs = getSharedPreferences("PREFS", MODE_PRIVATE)

        val kelasSiswa = prefs.getString("kelas", "") ?: ""
        titleText = if (kelasSiswa.isNotEmpty()) "Jadwal Kelas $kelasSiswa" else "Jadwal Pelajaran"

        setContent {
            AppTheme {
                JadwalScreen(
                    title = titleText,
                    maxJam = 10,
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
    maxJam: Int,
    listDataJadwal: List<HariModel>,
    isLoading: Boolean,
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
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column {
                    // Header Row: "Hari", "Jam 1", "Jam 2", ...
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "Hari",
                            modifier = Modifier.width(60.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        for (i in 1..maxJam) {
                            Text(
                                text = "Jam $i",
                                modifier = Modifier.width(94.dp),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Data Rows
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        items(listDataJadwal) { hari ->
                            JadwalRow(hari = hari)
                        }
                    }
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun JadwalRow(hari: HariModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = hari.namaHari,
            modifier = Modifier.width(60.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            hari.listJadwal.forEach { item ->
                JadwalCard(item = item)
            }
        }
    }
}

@Composable
private fun JadwalCard(item: JadwalItemModel) {
    val isKosong = item.status == "kosong"
    val containerColor = if (isKosong) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (isKosong) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        modifier = Modifier
            .width(90.dp)
            .height(60.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isKosong) {
                Text(
                    text = "-",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = item.mapel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.infoBawah.isNotEmpty()) {
                    Text(
                        text = item.infoBawah,
                        fontSize = 8.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Preview(
    showBackground = true,
    device = "spec:width=1280dp,height=800dp,orientation=landscape"
)
@Composable
fun JadwalScreenPreview() {
    AppTheme {
        JadwalScreen(
            title = "Jadwal Kelas XII 6",
            maxJam = 10,
            listDataJadwal = listOf(
                HariModel(
                    namaHari = "Senin",
                    listJadwal = listOf(
                        JadwalItemModel(1, "isi", "Matematika", "Pak Budi"),
                        JadwalItemModel(2, "isi", "Matematika", "Pak Budi"),
                        JadwalItemModel(3, "kosong", "", ""),
                        JadwalItemModel(4, "isi", "B. Indonesia", "Bu Siti"),
                        JadwalItemModel(5, "isi", "B. Indonesia", "Bu Siti"),
                        JadwalItemModel(6, "isi", "Fisika", "Pak Agus"),
                        JadwalItemModel(7, "isi", "Fisika", "Pak Agus"),
                        JadwalItemModel(8, "kosong", "", ""),
                        JadwalItemModel(9, "isi", "Kimia", "Bu Lina"),
                        JadwalItemModel(10, "isi", "Kimia", "Bu Lina")
                    )
                ),
                HariModel(
                    namaHari = "Selasa",
                    listJadwal = listOf(
                        JadwalItemModel(1, "isi", "B. Inggris", "Mr. John"),
                        JadwalItemModel(2, "isi", "B. Inggris", "Mr. John"),
                        JadwalItemModel(3, "isi", "Biologi", "Bu Ani"),
                        JadwalItemModel(4, "isi", "Biologi", "Bu Ani"),
                        JadwalItemModel(5, "kosong", "", ""),
                        JadwalItemModel(6, "isi", "Sejarah", "Pak Doni"),
                        JadwalItemModel(7, "isi", "Sejarah", "Pak Doni"),
                        JadwalItemModel(8, "isi", "PJOK", "Pak Eko"),
                        JadwalItemModel(9, "isi", "PJOK", "Pak Eko"),
                        JadwalItemModel(10, "kosong", "", "")
                    )
                )
            ),
            isLoading = false,
            onBack = {}
        )
    }
}
