package id.rudissp.smania

import android.content.Intent
import android.content.SharedPreferences
import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
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
import id.rudissp.smania.ibadah.Ibadah
import id.rudissp.smania.jadwal.Jadwal
import id.rudissp.smania.jurusan.Jurusan
import id.rudissp.smania.lms.LMS
import id.rudissp.smania.presensi.Presensi
import id.rudissp.smania.rapor.ActivityRapor
import id.rudissp.smania.unduh.Unduh
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
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
                MainScreen(
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
                    onRaporClick = { startActivity(Intent(this, ActivityRapor::class.java)) },
                    onIbadahClick = { startActivity(Intent(this, Ibadah::class.java)) },
                    onUjianClick = { startActivity(Intent(this, id.rudissp.smania.ujian.Ujian::class.java)) },
                    onJurusanClick = { startActivity(Intent(this, Jurusan::class.java)) },
                    onUnduhClick = { startActivity(Intent(this, Unduh::class.java)) },
                    onProfilClick = { startActivity(Intent(this, Profil::class.java)) },
                    onKaldikClick = { startActivity(Intent(this, id.rudissp.smania.kaldik.Kaldik::class.java)) }
                )
            }
        }

        loadUserData()
        loadEventTerdekatDashboard()
        fetchEventData()
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

                        eventName = "Hitung Mundur $eventNameVal"
                        eventDate = formatTanggalIndo(dateStartStr, dateEndStr)
                        eventNote = note

                        startCountdown(dateStartStr, dateEndStr)
                    } else {
                        isCountdownVisible = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    isCountdownVisible = false
                }
            },
            { _ ->
                Toast.makeText(this, "Gagal mengambil data", Toast.LENGTH_SHORT).show()
                isCountdownVisible = false
            }
        )

        requestQueue.add(jsonObjectRequest)
    }

    private fun formatTanggalIndo(dateStartStr: String, dateEndStr: String): String {
        val rawFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val displayFormat = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))

        return try {
            val startDate = rawFormat.parse(dateStartStr)
            val endDate = rawFormat.parse(dateEndStr)

            if (startDate != null && endDate != null) {
                val startFormatted = displayFormat.format(startDate)
                val endFormatted = displayFormat.format(endDate)
                "$startFormatted - $endFormatted"
            } else {
                dateStartStr
            }
        } catch (e: Exception) {
            e.printStackTrace()
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

                        if (now < endDate) {
                            eventStatus = "🎉 Event Sedang Berlangsung!"
                        } else {
                            eventStatus = "✅ Event Telah Selesai!"
                        }

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

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadUserData() {
        val namaLengkap = prefs.getString("namalengkap", "") ?: ""
        val kelas = prefs.getString("kelas", "") ?: ""
        userName = namaLengkap
        userClassName = kelas

        val fotonyaaa = prefs.getString("foto", "") ?: ""
        userPhotoUrl = if (fotonyaaa.isNotEmpty()) {
            "https://sman1sukorejo.sch.id/smania/fotoprofil/$fotonyaaa"
        } else {
            ""
        }
    }

    private fun loadEventTerdekatDashboard() {
        val queue = Volley.newRequestQueue(this)

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val timeMinRaw = sdf.format(Date())
        val timeMinEncoded = java.net.URLEncoder.encode(timeMinRaw, "UTF-8")

        val url =
            "https://www.googleapis.com/calendar/v3/calendars/2c13dee8396be174383342ba6abeff67b6aee566be14c1d99b4b2f9e2750a55a@group.calendar.google.com/events?key=AIzaSyDMUXOGXDwtz5-yhcV2Oo8NpB9FqpGF3vw&timeMin=$timeMinEncoded&orderBy=startTime&singleEvents=true"

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

                        val startStr = if (startObj.has("date")) {
                            startObj.getString("date")
                        } else {
                            startObj.getString("dateTime").substring(0, 10)
                        }

                        val endStr = if (endObj.has("date")) {
                            endObj.getString("date")
                        } else {
                            endObj.getString("dateTime").substring(0, 10)
                        }

                        val startDate = LocalDate.parse(startStr)
                        val endDate = LocalDate.parse(endStr)

                        val isAllDay = startObj.has("date")
                        val actualEndDate = if (isAllDay) endDate.minusDays(1) else endDate

                        if (eventHariIni == null && !hariIni.isBefore(startDate) && !hariIni.isAfter(actualEndDate)) {
                            eventHariIni = judul
                        }

                        if (eventMendatang == null && startDate.isAfter(hariIni)) {
                            val formatTanggal = startDate.format(DateTimeFormatter.ofPattern("dd MMMM YYYY"))
                            eventMendatang = "$judul ($formatTanggal)"
                        }

                        if (eventHariIni != null && eventMendatang != null) {
                            break
                        }
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

                } catch (e: Exception) {
                    kaldikEventText = "Gagal memuat agenda"
                    Log.e("DASHBOARD_KALENDER", "Error parsing: ${e.message}")
                }
            },
            { error ->
                kaldikEventText = "Gagal terhubung"
                val statusCode = error.networkResponse?.statusCode
                Log.e(
                    "DASHBOARD_KALENDER",
                    "Error Volley: ${error.message}, Status Code: $statusCode"
                )
            }
        )

        queue.add(jsonObjectRequest)
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

@Composable
fun MainScreen(
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
    onRaporClick: () -> Unit,
    onIbadahClick: () -> Unit,
    onUjianClick: () -> Unit,
    onJurusanClick: () -> Unit,
    onUnduhClick: () -> Unit,
    onProfilClick: () -> Unit,
    onKaldikClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                    )
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Halo👋",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (userName.isNotEmpty()) "$userName ($userClassName)" else "Rudi Setiawan S.P",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.onPrimary, CircleShape)
                            .clickable { onProfilClick() }
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                ImageView(ctx).apply {
                                    scaleType = ImageView.ScaleType.CENTER_CROP
                                }
                            },
                            update = { imageView ->
                                if (userPhotoUrl.isNotEmpty()) {
                                    Glide.with(imageView.context)
                                        .load(userPhotoUrl)
                                        .circleCrop()
                                        .into(imageView)
                                } else {
                                    Glide.with(imageView.context)
                                        .load(R.drawable.icm_person)
                                        .circleCrop()
                                        .into(imageView)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Kaldik Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { onKaldikClick() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.icm_kalender),
                        contentDescription = "Kaldik",
                        modifier = Modifier.size(40.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Kaldik SMAN 1 Sukorejo",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row {
                            Text(
                                text = "Event Aktif: ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = kaldikEventText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (isKaldikNextVisible && kaldikEventNextText.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Row {
                                Text(
                                    text = "Next Event: ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = kaldikEventNextText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Countdown Card
            if (isCountdownVisible) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = eventName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = eventDate,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (eventStatus.isNotEmpty()) {
                            Text(
                                text = eventStatus,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            CountdownUnit(value = days, label = "Hari")
                            CountdownUnit(value = hours, label = "Jam")
                            CountdownUnit(value = minutes, label = "Menit")
                            CountdownUnit(value = seconds, label = "Detik")
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = eventNote,
                            fontSize = 10.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Menu Grid
            val menuItems = listOf(
                Triple("Presensi", R.drawable.icm_presensi, onPresensiClick),
                Triple("Jadwal KBM", R.drawable.icm_jadwal, onJadwalClick),
                Triple("LMS", R.drawable.icm_lms, onLmsClick),
                Triple("Nilai Rapor", R.drawable.icm_rapor, onRaporClick),
                Triple("Ibadah", R.drawable.icm_ibadah, onIbadahClick),
                Triple("Ujian Online", R.drawable.icm_ujian, onUjianClick),
                Triple("Info Prodi Kuliah", R.drawable.icm_lms, onJurusanClick),
                Triple("Pusat Unduhan", R.drawable.icm_dok, onUnduhClick)
            )

            menuItems.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { (title, iconRes, onClick) ->
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onClick() },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = title,
                                    modifier = Modifier.size(50.dp),
                                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = title,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Footer Version
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Smania v1.2",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun CountdownUnit(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    AppTheme {
        MainScreen(
            userName = "Rudi Setiawan",
            userClassName = "XII IPA 1",
            userPhotoUrl = "",
            isCountdownVisible = true,
            eventName = "Ujian Akhir Semester",
            eventDate = "10 Desember 2024 - 15 Desember 2024",
            eventStatus = "🎉 Event Sedang Berlangsung!",
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
            onRaporClick = {},
            onIbadahClick = {},
            onUjianClick = {},
            onJurusanClick = {},
            onUnduhClick = {},
            onProfilClick = {},
            onKaldikClick = {}
        )
    }
}
