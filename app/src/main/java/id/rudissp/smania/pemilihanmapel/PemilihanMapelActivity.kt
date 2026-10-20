package id.rudissp.smania.pemilihanmapel

import id.rudissp.smania.LoadingIndicator
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import id.rudissp.smania.AppConstants
import id.rudissp.smania.AppTheme
import id.rudissp.smania.VolleySingleton
import kotlinx.coroutines.delay
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class PemilihanMapelActivity : ComponentActivity() {

    lateinit var prefs: SharedPreferences
    var nisnya by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("PREFS", MODE_PRIVATE)
        enableEdgeToEdge()

        val nisSiswa = prefs.getString("nis", "") ?: ""
        nisnya = nisSiswa

        setContent {
            AppTheme {
                PemilihanMapelScreen(
                    nis = nisSiswa,
                    onBack = { finish() }
                )
            }
        }
    }
}

fun formatWaktuBuka(waktuDb: String): String {
    return try {
        val formatAsli = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formatBaru = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
        val date = formatAsli.parse(waktuDb)
        if (date != null) "${formatBaru.format(date)} WIB" else waktuDb
    } catch (e: Exception) {
        waktuDb
    }
}

fun isWaktuBelumBuka(waktuBukaDb: String): Boolean {
    if (waktuBukaDb.isEmpty()) return false
    return try {
        val formatAsli = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateBuka = formatAsli.parse(waktuBukaDb)
        val now = Date()
        now.before(dateBuka)
    } catch (_: Exception) {
        false
    }
}

// Data Class Mockup (Berdasarkan struktur database PHP)
data class NilaiRapor(val mapel: String, val nilai: String)
data class PaketMapel(val id: String, val nama: String, val kuota: Int, val detailMapel: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PemilihanMapelScreen(
    nis: String = "12345",
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val baseUrl = AppConstants.MAPILURL

    // State UI & Loading
    var isLoading by remember { mutableStateOf(true) }
    var pilihan1 by remember { mutableStateOf<String?>(null) }
    var pilihan2 by remember { mutableStateOf<String?>(null) }
    var batasWaktu by remember { mutableStateOf("Memuat...") }
    var waktuBukaAsli by remember { mutableStateOf("") }
    var waktuTutupAsli by remember { mutableStateOf("") }
    var rataRataNilai by remember { mutableStateOf(0.0) }

    var statusSeleksi by remember { mutableStateOf("") }
    var namaPaketFinal by remember { mutableStateOf("") }

    val daftarNilai = remember { mutableStateListOf<NilaiRapor>() }
    val daftarPaket = remember { mutableStateListOf<PaketMapel>() }

    val isError = pilihan1 == pilihan2 && pilihan1 != null
    val isSubmitEnabled = pilihan1 != null && pilihan2 != null && !isError && !isLoading

    // ==========================================
    // 1. FETCH DATA DARI API (GET)
    // ==========================================
    LaunchedEffect(Unit) {
        val queue = VolleySingleton.getInstance(context).requestQueue
        val url = "$baseUrl/pemilihanmapel_getdata.php?nis=$nis"

        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    if (response.getString("status") == "success") {
                        // A. Parsing Jadwal
                        if (response.has("jadwal") && !response.isNull("jadwal")) {
                            val jadwalObj = response.getJSONObject("jadwal")
                            batasWaktu = jadwalObj.optString("waktu_tutup", "-")
                            waktuBukaAsli = jadwalObj.optString("waktu_buka", "")
                            waktuTutupAsli = jadwalObj.optString("waktu_tutup", "")
                        }

                        // B. Parsing Nilai Rapor
                        if (response.has("nilai") && !response.isNull("nilai")) {
                            val nilaiObj = response.getJSONObject("nilai")
                            daftarNilai.clear()
                            daftarNilai.add(NilaiRapor("Agama", nilaiObj.optString("pendidikan_agama", "0")))
                            daftarNilai.add(NilaiRapor("Pancasila", nilaiObj.optString("pendidikan_pancasila", "0")))
                            daftarNilai.add(NilaiRapor("B. Indo", nilaiObj.optString("bahasa_indonesia", "0")))
                            daftarNilai.add(NilaiRapor("B. Ing", nilaiObj.optString("bahasa_inggris", "0")))
                            daftarNilai.add(NilaiRapor("Matematika", nilaiObj.optString("matematika", "0")))
                            daftarNilai.add(NilaiRapor("IPA", nilaiObj.optString("ipa", "0")))
                            daftarNilai.add(NilaiRapor("IPS", nilaiObj.optString("ips", "0")))
                            daftarNilai.add(NilaiRapor("PJOK", nilaiObj.optString("pjok", "0")))
                            daftarNilai.add(NilaiRapor("Informatika", nilaiObj.optString("informatika", "0")))
                            daftarNilai.add(NilaiRapor("Senbud", nilaiObj.optString("seni_budaya_prakarya", "0")))
                            daftarNilai.add(NilaiRapor("B. Jawa", nilaiObj.optString("bahasa_jawa", "0")))
                            daftarNilai.add(NilaiRapor("KKA", nilaiObj.optString("kka", "0")))
                            rataRataNilai = nilaiObj.optDouble("rata_rata", 0.0)
                        }

                        //hasil selsksi
                        if (response.has("pilihan_saat_ini") && !response.isNull("pilihan_saat_ini")) {
                            val pilihanObj = response.getJSONObject("pilihan_saat_ini")
                            pilihan1 = pilihanObj.optString("pilihan_1")
                            pilihan2 = pilihanObj.optString("pilihan_2")
                            statusSeleksi = pilihanObj.optString("status_seleksi", "")
                            namaPaketFinal = pilihanObj.optString("nama_paket_final", "")
                        }

                        // C. Parsing Pilihan yang Sudah Dipilih Sebelumnya
                        if (response.has("pilihan_saat_ini") && !response.isNull("pilihan_saat_ini")) {
                            val pilihanObj = response.getJSONObject("pilihan_saat_ini")
                            pilihan1 = pilihanObj.optString("pilihan_1")
                            pilihan2 = pilihanObj.optString("pilihan_2")
                        }

                        // D. Parsing Daftar Paket Mapel
                        if (response.has("daftar_paket")) {
                            val paketArray = response.getJSONArray("daftar_paket")
                            daftarPaket.clear()
                            for (i in 0 until paketArray.length()) {
                                val p = paketArray.getJSONObject(i)
                                daftarPaket.add(
                                    PaketMapel(
                                        id = p.getString("id_paket"),
                                        nama = p.getString("nama_paket"),
                                        kuota = p.optInt("kuota", 0),
                                        detailMapel = p.optString("detail_mapel", "-")
                                    )
                                )
                            }
                        }
                    } else {
                        Toast.makeText(context, response.optString("pesan"), Toast.LENGTH_LONG).show()
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                } finally {
                    isLoading = false
                }
            },
            { error ->
                Toast.makeText(context, "Gagal terhubung ke server", Toast.LENGTH_SHORT).show()
                isLoading = false
            }
        )
        queue.add(jsonObjectRequest)
    }

    // ==========================================
    // 2. SUBMIT DATA KE API (POST)
    // ==========================================
    fun simpanPilihanKeDatabase() {
        val queue = VolleySingleton.getInstance(context).requestQueue
        val url = "$baseUrl/pemilihanmapel_submit.php"

        val stringRequest = object : StringRequest(
            Method.POST, url,
            { response ->
                try {
                    val jsonResponse = JSONObject(response)
                    val status = jsonResponse.getString("status")
                    val pesan = jsonResponse.getString("pesan")

                    Toast.makeText(context, pesan, Toast.LENGTH_LONG).show()

                    if (status == "success") {
                        onBack()
                    }
                } catch (e: JSONException) {
                    Toast.makeText(context, "Format respons server tidak valid", Toast.LENGTH_SHORT).show()
                }
            },
            {
                Toast.makeText(context, "Gagal mengirim data pilihan", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["nis"] = nis
                params["pilihan_1"] = pilihan1 ?: ""
                params["pilihan_2"] = pilihan2 ?: ""
                return params
            }
        }
        queue.add(stringRequest)
    }

    val isBelumBuka = isWaktuBelumBuka(waktuBukaAsli)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Pemilihan Paket Mapel", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
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
        },
        bottomBar = {
            if (!isLoading && !isBelumBuka) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        if (isError) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = "⚠️ Pilihan 1 dan Pilihan 2 tidak boleh sama!",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                        Button(
                            onClick = { simpanPilihanKeDatabase() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = isSubmitEnabled,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = if (pilihan1 != null && pilihan2 != null) "Perbarui Pilihan Mapel" else "Simpan Pilihan Mapel",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
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
                LoadingIndicator()
            }
        } else if (isBelumBuka) {

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
            ){
                item{AksesBelumDibukaView(waktuBuka = batasWaktu, daftarNilai = daftarNilai, rataRataNilai = rataRataNilai)}
            }

        } else {
            // Tampilan Normal Pemilihan
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
            ) {
                item { CountdownCard(waktuTutupDb = waktuTutupAsli) }

                if (statusSeleksi.isNotEmpty()) {
                    item {
                        StatusPengumumanCard(
                            statusSeleksi = statusSeleksi,
                            namaPaketFinal = namaPaketFinal
                        )
                    }
                }

                item { AturanSeleksiCard() }
                item { NilaiRaporCard(daftarNilai, rataRataNilai) }
                item {
                    Text(
                        text = "Pilih Paket Kelas",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                items(daftarPaket) { paket ->
                    PaketSelectionCard(
                        paket = paket,
                        pilihan1 = pilihan1,
                        pilihan2 = pilihan2,
                        onPilihan1Change = { pilihan1 = it },
                        onPilihan2Change = { pilihan2 = it }
                    )
                }
            }
        }
    }
}


// =========================================================
// KOMPONEN UI TAMBAHAN: AKSES BELUM DIBUKA
// =========================================================
@Composable
fun AksesBelumDibukaView(
    waktuBuka: String,
    daftarNilai: List<NilaiRapor>,
    rataRataNilai: Double
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Header Ikon Gembok (Expressive Container)
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🔒",
                    fontSize = 36.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Judul & Deskripsi
            Text(
                text = "Akses Belum Dibuka",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Halaman pemilihan paket mata pelajaran saat ini belum dapat diakses.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Informational Banner (Waktu Buka)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Dapat dibuka pada:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatWaktuBuka(waktuBuka),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 4. Section Pengecekan Nilai Rapor
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Cek Data Nilai Rapor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Nilai Semester 1 akan sebagai acuan seleksi, jika ada ketidaksesuaian silahkan hubungi Kurikulum",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Daftar Nilai Rapor

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                daftarNilai.chunked(3).forEach { baris ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        baris.forEach { item ->
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = item.mapel,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.nilai,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (daftarNilai.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                ) {
                    Text(
                        text = "Data nilai belum tersedia.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

// =========================================================
// KOMPONEN UI LAINNYA (Sama seperti sebelumnya)
// =========================================================

@Composable
fun CountdownCard(waktuTutupDb: String) {
    var timerText by remember { mutableStateOf("00 : 00 : 00 : 00") }
    var isExpired by remember { mutableStateOf(false) }

    // Coroutine Loop untuk update countdown tiap 1 detik
    LaunchedEffect(waktuTutupDb) {
        if (waktuTutupDb.isNotEmpty()) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val targetDate = try { sdf.parse(waktuTutupDb) } catch (_: Exception) { null }

            if (targetDate != null) {
                while (true) {
                    val now = Date().time
                    val diff = targetDate.time - now

                    if (diff <= 0) {
                        timerText = "WAKTU HABIS"
                        isExpired = true
                        break
                    } else {
                        val days = diff / (1000 * 60 * 60 * 24)
                        val hours = (diff / (1000 * 60 * 60)) % 24
                        val minutes = (diff / (1000 * 60)) % 60
                        val seconds = (diff / 1000) % 60

                        timerText = String.format(
                            Locale.getDefault(),
                            "%02d : %02d : %02d : %02d",
                            days, hours, minutes, seconds
                        )
                    }
                    delay(1000L.milliseconds)
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = if (isExpired) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = if (isExpired) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Timer",
                        tint = if (isExpired) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = timerText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = if (isExpired) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isExpired) "Pemilihan Paket Telah Ditutup" else "Waktu Tersisa Untuk Memilih Paket",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isExpired) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
fun AturanSeleksiCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Aturan Seleksi",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Murni menggunakan peringkat nilai dari mapel yang disyaratkan paket. Jika tergeser dari Pilihan 1, sistem otomatis mengadu nilai Anda di Pilihan 2.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
fun NilaiRaporCard(daftarNilai: List<NilaiRapor>, rataRata: Double) {
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
            Text(
                text = "Data Nilai Rapor Anda",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Berdasarkan nilai Semester 1",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                daftarNilai.chunked(3).forEach { baris ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        baris.forEach { item ->
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = item.mapel,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.nilai,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaketSelectionCard(
    paket: PaketMapel,
    pilihan1: String?,
    pilihan2: String?,
    onPilihan1Change: (String) -> Unit,
    onPilihan2Change: (String) -> Unit
) {
    val isPilihan1 = pilihan1 == paket.id
    val isPilihan2 = pilihan2 == paket.id

    Card(
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = paket.nama,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Kuota: ${paket.kuota}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Mapel: ${paket.detailMapel}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable { onPilihan1Change(paket.id) },
                    shape = CircleShape,
                    color = if (isPilihan1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isPilihan1) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = "Pilihan 1",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPilihan1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable { onPilihan2Change(paket.id) },
                    shape = CircleShape,
                    color = if (isPilihan2) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isPilihan2) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = "Pilihan 2",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPilihan2) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusPengumumanCard(
    statusSeleksi: String,
    namaPaketFinal: String
) {

    val (bgColor, txtColor, labelStatus) = when (statusSeleksi) {
        "Diterima_Pil1" -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Diterima$namaPaketFinal(Pilihan 1)"
        )
        "Diterima_Pil2" -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "Tergeser ke $namaPaketFinal (Pilihan 2)"
        )
        "Dialihkan_Sistem" -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "Dialihkan Otomatis oleh Sistem - $namaPaketFinal"
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Sedang Diproses / Pending"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Status Hasil Seleksi Sementara",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "Posisi sementara Anda saat ini:",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = if (namaPaketFinal.isNotEmpty()) namaPaketFinal else "Menunggu Pemeringkatan...",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                shape = CircleShape,
                color = bgColor
            ) {
                Text(
                    text = labelStatus,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = txtColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PemilihanMapelScreenPreview() {
    AppTheme {
        PemilihanMapelScreen(nis = "1234567890")
    }
}