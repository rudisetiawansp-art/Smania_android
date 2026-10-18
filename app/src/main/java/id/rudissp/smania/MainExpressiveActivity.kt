package id.rudissp.smania

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import id.rudissp.smania.buku.BukuActivity
import id.rudissp.smania.ibadah.Ibadah
import id.rudissp.smania.jadwal.Jadwal
import id.rudissp.smania.jurusan.Jurusan
import id.rudissp.smania.lms.LMS
import id.rudissp.smania.pemilihanmapel.PemilihanMapelActivity
import id.rudissp.smania.presensi.Presensi
import id.rudissp.smania.rapor.ActivityRapor
import id.rudissp.smania.rekomendasi.RekomendasiActivity
import java.net.Inet4Address
import java.net.NetworkInterface
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import androidx.core.net.toUri
import kotlin.jvm.java

class MainExpressiveActivity : AppCompatActivity() {
    lateinit var prefs: SharedPreferences

    private var handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private val apiUrl = AppConstants.BASE_URL + "getevent.php"

    var userName by mutableStateOf("")
    var userClassName by mutableStateOf("")
    var userPhotoUrl by mutableStateOf("")
    var isCountdownVisible by mutableStateOf(false)
    var eventName by mutableStateOf("Nama Event")
    var eventDate by mutableStateOf("Tanggal Event")
    var eventStatus by mutableStateOf("")
    var days by mutableStateOf("--")
    var hours by mutableStateOf("--")
    var minutes by mutableStateOf("--")
    var seconds by mutableStateOf("--")
    var eventNote by mutableStateOf("--")
    var kaldikEventText by mutableStateOf("Memuat...")
    var kaldikEventNextText by mutableStateOf("")
    var isKaldikNextVisible by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("PREFS", MODE_PRIVATE)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                MainExpressiveScreen(
                    userName = userName,
                    userClassName = userClassName,
                    userPhotoUrl = userPhotoUrl,
                    isCountdownVisible = isCountdownVisible,
                    eventName = eventName,
                    eventDate = eventDate,
                    eventStatus = eventStatus,
                    days = days,
                    hours = hours,
                    minutes = minutes,
                    seconds = seconds,
                    eventNote = eventNote,
                    kaldikEventText = kaldikEventText,
                    kaldikEventNextText = kaldikEventNextText,
                    isKaldikNextVisible = isKaldikNextVisible,
                    onPresensiClick = { startActivity(Intent(this, Presensi::class.java)) },
                    onJadwalClick = { startActivity(Intent(this, Jadwal::class.java)) },
                    onLmsClick = { startActivity(Intent(this, LMS::class.java)) },
                    onUjianClick = { onSsoLoginClicked(this, prefs.getString("nis", "")) },
                    onRaporClick = { startActivity(Intent(this, ActivityRapor::class.java)) },
                    onRekomendasiClick = { startActivity(Intent(this, RekomendasiActivity::class.java)) },
                    onIbadahClick = { startActivity(Intent(this, Ibadah::class.java)) },
                    onJurusanClick = { startActivity(Intent(this, Jurusan::class.java)) },
                    onBukuClick = { startActivity(Intent(this, BukuActivity::class.java)) },
                    onPemilihanMapelClick = { cektingkat() },
                    onProfilClick = { startActivity(Intent(this, Profil::class.java)) },
                    onKaldikClick = { startActivity(Intent(this, id.rudissp.smania.kaldik.Kaldik::class.java)) }
                )
            }
        }

        loadUserData()
        loadEventTerdekatDashboard()
        fetchEventData()
    }

    private fun cektingkat(){
        val tingkat= prefs.getString("tingkat", "")
        if (tingkat == "X"){
            startActivity(Intent(this, PemilihanMapelActivity::class.java))
        } else {
            Toast.makeText(this, "Pemilihan Mapel Hanya Untuk Kelas X", Toast.LENGTH_SHORT).show()
        }

    }

    private fun fetchEventData() {
        val requestQueue = Volley.newRequestQueue(this)
        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET, apiUrl, null,
            { response ->
                try {
                    val status = response.getBoolean("status")
                    if (status) {
                        isCountdownVisible = true
                        val data = response.getJSONObject("data")
                        val eventNameVal = data.getString("event_name")
                        val dateStartStr = data.getString("event_date_start")
                        val dateEndStr = data.getString("event_date_end")
                        val note = data.getString("event_note")

                        eventName = eventNameVal
                        eventDate = formatTanggalIndo(dateStartStr, dateEndStr)
                        eventNote = note
                        startCountdown(dateStartStr, dateEndStr)
                    } else {
                        isCountdownVisible = false
                    }
                } catch (_: Exception) {
                    isCountdownVisible = false
                }
            },
            { _ -> isCountdownVisible = false }
        )
        requestQueue.add(jsonObjectRequest)
    }

    private fun formatTanggalIndo(dateStartStr: String, dateEndStr: String): String {
        val rawFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val displayFormat = SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("id-ID"))
        return try {
            val startDate = rawFormat.parse(dateStartStr)
            val endDate = rawFormat.parse(dateEndStr)
            if (startDate != null && endDate != null) {
                "${displayFormat.format(startDate)} - ${displayFormat.format(endDate)}"
            } else {
                dateStartStr
            }
        } catch (_: Exception) {
            dateStartStr
        }
    }

    private fun startCountdown(dateStartStr: String, dateEndStr: String) {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        format.timeZone = TimeZone.getDefault()
        try {
            val targetDate = format.parse(dateStartStr)?.time ?: return
            val endDate = format.parse(dateEndStr)?.time ?: return

            runnable = object : Runnable {
                override fun run() {
                    val now = System.currentTimeMillis()
                    val diffStart = targetDate - now
                    if (diffStart <= 0) {
                        days = "00"
                        hours = "00"
                        minutes = "00"
                        seconds = "00"
                        eventStatus = if (now < endDate) "🎉 Event Sedang Berlangsung!" else "✅ Event Selesai"
                        handler.removeCallbacks(this)
                        return
                    }
                    val totalSec = diffStart / 1000
                    val s = totalSec % 60
                    val m = (totalSec / 60) % 60
                    val h = (totalSec / 3600) % 24
                    val d = totalSec / 86400
                    days = String.format(Locale.getDefault(), "%02d", d)
                    hours = String.format(Locale.getDefault(), "%02d", h)
                    minutes = String.format(Locale.getDefault(), "%02d", m)
                    seconds = String.format(Locale.getDefault(), "%02d", s)
                    handler.postDelayed(this, 1000)
                }
            }
            handler.post(runnable!!)
        } catch (_: Exception) {
        }
    }

    private fun loadUserData() {
        userName = prefs.getString("namalengkap", "") ?: ""
        userClassName = prefs.getString("kelas", "") ?: ""
        val fotonyaaa = prefs.getString("foto", "") ?: ""
        userPhotoUrl = if (fotonyaaa.isNotEmpty()) "https://sman1sukorejo.sch.id/smania/fotoprofil/$fotonyaaa" else ""
    }

    private fun loadEventTerdekatDashboard() {
        val queue = Volley.newRequestQueue(this)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val timeMinRaw = sdf.format(Date())
        val timeMinEncoded = java.net.URLEncoder.encode(timeMinRaw, "UTF-8")
        val url = "https://www.googleapis.com/calendar/v3/calendars/2c13dee8396be174383342ba6abeff67b6aee566be14c1d99b4b2f9e2750a55a@group.calendar.google.com/events?key=AIzaSyDMUXOGXDwtz5-yhcV2Oo8NpB9FqpGF3vw&timeMin=$timeMinEncoded&orderBy=startTime&singleEvents=true"

        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    val items = response.getJSONArray("items")
                    val hariIni = LocalDate.now()
                    var eventHariIni: String? = null
                    var eventMendatang: String? = null

                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val judul = item.getString("summary")
                        val startObj = item.getJSONObject("start")
                        val endObj = item.getJSONObject("end")
                        val startStr = if (startObj.has("date")) startObj.getString("date") else startObj.getString("dateTime").substring(0, 10)
                        val endStr = if (endObj.has("date")) endObj.getString("date") else endObj.getString("dateTime").substring(0, 10)
                        val startDate = LocalDate.parse(startStr)
                        val endDate = LocalDate.parse(endStr)
                        val isAllDay = startObj.has("date")
                        val actualEndDate = if (isAllDay) endDate.minusDays(1) else endDate

                        if (eventHariIni == null && !hariIni.isBefore(startDate) && !hariIni.isAfter(actualEndDate)) {
                            eventHariIni = judul
                        }
                        if (eventMendatang == null && startDate.isAfter(hariIni)) {
                            val formatTanggal = startDate.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.forLanguageTag("id-ID")))
                            eventMendatang = "$judul ($formatTanggal)"
                        }
                        if (eventHariIni != null && eventMendatang != null) break
                    }

                    if (eventHariIni != null && eventMendatang != null) {
                        kaldikEventText = eventHariIni
                        isKaldikNextVisible = true
                        kaldikEventNextText = eventMendatang
                    } else if (eventHariIni != null) {
                        kaldikEventText = eventHariIni
                        isKaldikNextVisible = false
                    } else if (eventMendatang != null) {
                        kaldikEventText = eventMendatang
                        isKaldikNextVisible = false
                    } else {
                        kaldikEventText = "Tidak ada agenda terdekat"
                        isKaldikNextVisible = false
                    }
                } catch (_: Exception) {
                    kaldikEventText = "Gagal memuat agenda"
                }
            },
            { _ -> kaldikEventText = "Gagal terhubung" }
        )
        queue.add(jsonObjectRequest)
    }

    private fun onSsoLoginClicked(context: Context, nis: String?) {
        val baseUrl = if (isLocalNetwork()) "http://192.168.188.80/smania/" else "http://36.93.181.53/smania/"
        val timestamp = System.currentTimeMillis() / 1000L
        val signature = generateHmacSha256(nis, timestamp)
        val ssoUrl = "${baseUrl}sso_login.php?nis=$nis&timestamp=$timestamp&signature=$signature"
        context.startActivity(Intent(Intent.ACTION_VIEW, ssoUrl.toUri()))
    }

    private fun isLocalNetwork(): Boolean {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val ip = address.hostAddress
                        if (ip != null && ip.startsWith("192.168.188.")) return true
                    }
                }
            }
        } catch (_: Exception) {
        }
        return false
    }

    private fun generateHmacSha256(nis: String?, timestamp: Long): String {
        val secret = "KunciRahasiaEsmansaSuperSangatAman123!"
        val message = "$nis|$timestamp"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }

    override fun onDestroy() {
        super.onDestroy()
        runnable?.let { handler.removeCallbacks(it) }
    }
}

data class QuadMenu(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun MainExpressiveScreen(
    userName: String,
    userClassName: String,
    userPhotoUrl: String,
    isCountdownVisible: Boolean,
    eventName: String,
    eventDate: String,
    eventStatus: String,
    days: String,
    hours: String,
    minutes: String,
    seconds: String,
    eventNote: String,
    kaldikEventText: String,
    kaldikEventNextText: String,
    isKaldikNextVisible: Boolean,
    onPresensiClick: () -> Unit,
    onJadwalClick: () -> Unit,
    onLmsClick: () -> Unit,
    onUjianClick: () -> Unit,
    onRaporClick: () -> Unit,
    onRekomendasiClick: () -> Unit,
    onIbadahClick: () -> Unit,
    onJurusanClick: () -> Unit,
    onBukuClick: () -> Unit = {},
    onPemilihanMapelClick: () -> Unit,
    onProfilClick: () -> Unit,
    onKaldikClick: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Expressive Greeting & Profile Hero Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onProfilClick() },
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
                                text = "Portal Smania",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (userName.isNotEmpty()) "Halo, $userName" else "Halo, Rudi Setiawan",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Kelas: ${userClassName.ifEmpty { "XII IPA 1" }}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .border(
                                4.dp,
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                                CircleShape
                            )
                            .clickable { onProfilClick() }
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                            },
                            update = { imageView ->
                                if (userPhotoUrl.isNotEmpty()) {
                                    Glide.with(imageView.context).load(userPhotoUrl).circleCrop().into(imageView)
                                } else {
                                    Glide.with(imageView.context).load(R.drawable.icm_person).circleCrop().into(imageView)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // 2. Kaldik Expressive Card (Tanpa Garis Batas)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onKaldikClick() },
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = "Kaldik",
                                modifier = Modifier.size(28.dp),
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Kalender Akademik",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Agenda: $kaldikEventText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isKaldikNextVisible && kaldikEventNextText.isNotEmpty()) {
                            Text(
                                text = "Next: $kaldikEventNextText",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3. Countdown Expressive Card (Responsive Widths)
            if (isCountdownVisible) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "⏳ Hitung Mundur $eventName",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = eventDate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (eventStatus.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = eventStatus,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        // Perbaikan Layout Row: Menggunakan spacedBy dan modifier weight
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExpressiveCountdownUnit(modifier = Modifier.weight(1f), value = days, label = "Hari")
                            CountdownSeparator()
                            ExpressiveCountdownUnit(modifier = Modifier.weight(1f), value = hours, label = "Jam")
                            CountdownSeparator()
                            ExpressiveCountdownUnit(modifier = Modifier.weight(1f), value = minutes, label = "Menit")
                            CountdownSeparator()
                            ExpressiveCountdownUnit(modifier = Modifier.weight(1f), value = seconds, label = "Detik")
                        }

                        if (eventNote.isNotEmpty() && eventNote != "--") {
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = eventNote,
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // 4. Expressive Bento Menu Grid
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Menu Utama",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp)
            )

            // Featured Hero Action Banner (Rekomendasi PTN)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRekomendasiClick() },
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Fitur Unggulan",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Rekomendasi PTN",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Matching Engine SNBP & SNBT berdasarkan rapor. Sumber data: Portal SNPMB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Recommend,
                                contentDescription = "Rekomendasi",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            // Bento Grid Items (2 columns)
            val menuBentoItems = listOf(
                QuadMenu("Presensi", "Absensi harian & lokasi", Icons.Filled.Fingerprint, onPresensiClick),
                QuadMenu("Jadwal KBM", "Jadwal pelajaran kelas", Icons.Filled.CalendarMonth, onJadwalClick),
                QuadMenu("LMS", "Tugas & materi belajar", Icons.Filled.School, onLmsClick),
                QuadMenu("Ujian Online", "CBT & ujian sekolah", Icons.Filled.Quiz, onUjianClick),
                QuadMenu("Nilai Rapor", "Rekap nilai semester", Icons.Filled.Assessment, onRaporClick),
                QuadMenu("Ibadah", "Jurnal & rekap ibadah", Icons.Filled.Mosque, onIbadahClick),
                QuadMenu("Info Prodi", "Panduan jurusan PTN", Icons.AutoMirrored.Filled.MenuBook, onJurusanClick),
                QuadMenu("Buku Digital", "Katalog & PDF SMA", Icons.AutoMirrored.Filled.LibraryBooks, onBukuClick),
                QuadMenu("Pemilihan Mapel", "Pilih paket mapel kelas X",
                    Icons.AutoMirrored.Filled.Assignment, onPemilihanMapelClick)
            )

            menuBentoItems.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowItems.forEach { item ->
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(130.dp)
                                .clickable { item.onClick() },
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.title,
                                            modifier = Modifier.size(22.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer Version Badge
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "Smania v2.0",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun ExpressiveCountdownUnit(modifier: Modifier = Modifier, value: String, label: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = modifier.height(76.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun CountdownSeparator() {
    Text(
        text = ":",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 2.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun MainExpressiveScreenPreview() {
    AppTheme {
        MainExpressiveScreen(
            userName = "Rudi Setiawan",
            userClassName = "XII IPA 1",
            userPhotoUrl = "",
            isCountdownVisible = true,
            eventName = "Ujian Akhir Semester",
            eventDate = "10 Desember 2024 - 15 Desember 2024",
            eventStatus = "🎉 Event Berlangsung",
            days = "03",
            hours = "12",
            minutes = "45",
            seconds = "30",
            eventNote = "Harap mempersiapkan kartu ujian.",
            kaldikEventText = "Libur Semester",
            kaldikEventNextText = "Ujian Sekolah (20 Januari 2025)",
            isKaldikNextVisible = true,
            onPresensiClick = {},
            onJadwalClick = {},
            onLmsClick = {},
            onUjianClick = {},
            onRaporClick = {},
            onRekomendasiClick = {},
            onIbadahClick = {},
            onJurusanClick = {},
            onPemilihanMapelClick = {},
            onProfilClick = {},
            onKaldikClick = {}
        )
    }
}