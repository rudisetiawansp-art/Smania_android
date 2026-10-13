package id.rudissp.smania

import android.content.Intent
import android.content.SharedPreferences
import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import id.rudissp.smania.databinding.ActivityMainBinding
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
import kotlin.jvm.java

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    lateinit var prefs: SharedPreferences

    private var handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private val apiUrl = AppConstants.BASE_URL + "getevent.php"

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)
        prefs = getSharedPreferences("PREFS", MODE_PRIVATE)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        loadUserData()
        loadEventTerdekatDashboard()
        fetchEventData()


        binding.lms.setOnClickListener {
            val intent = Intent(this, LMS::class.java)
            startActivity(intent)
        }

        binding.rapor.setOnClickListener {
            val intent = Intent(this, ActivityRapor::class.java)
            startActivity(intent)
        }

        binding.fotonya.setOnClickListener {
            val intent = Intent(this, Profil::class.java)
            startActivity(intent)
        }

        binding.jadwal.setOnClickListener {
            val intent = Intent(this, Jadwal::class.java)
            startActivity(intent)
        }

        binding.ibadah.setOnClickListener {
            val intent = Intent(this, Ibadah::class.java)
            startActivity(intent)
        }

        binding.unduh.setOnClickListener {
            val intent = Intent(this, Unduh::class.java)
            startActivity(intent)
        }

        binding.jurusan.setOnClickListener {
            val intent = Intent(this, Jurusan::class.java)
            startActivity(intent)
        }

        binding.ujian.setOnClickListener {
            val intent = Intent(this, id.rudissp.smania.ujian.Ujian::class.java)
            startActivity(intent)
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
                        binding.cardCountdown.visibility = View.VISIBLE

                        val data = response.getJSONObject("data")
                        val eventName = data.getString("event_name")
                        val dateStartStr = data.getString("event_date_start")
                        val dateEndStr = data.getString("event_date_end")
                        val note = data.getString("event_note")

                        binding.tvEventName.text = "Hitung Mundur " + eventName
                        binding.tvEventDate.text = formatTanggalIndo(dateStartStr, dateEndStr)
                        binding.tvEventNote.text = note

                        startCountdown(dateStartStr, dateEndStr)
                    } else {
                        binding.cardCountdown.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    binding.cardCountdown.visibility = View.GONE
                }
            },
            { _ ->
                Toast.makeText(this, "Gagal mengambil data", Toast.LENGTH_SHORT).show()
                binding.cardCountdown.visibility = View.GONE
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
                        binding.tvDays.text = "00"
                        binding.tvHours.text = "00"
                        binding.tvMinutes.text = "00"
                        binding.tvSeconds.text = "00"

                        if (now < endDate) {
                            binding.tvStatus.text = "🎉 Event Sedang Berlangsung!"
                        } else {
                            binding.tvStatus.text = "✅ Event Telah Selesai!"
                        }

                        handler.removeCallbacks(this)
                        return
                    }

                    val totalSec = diffStart / 1000
                    val s = totalSec % 60
                    val m = (totalSec / 60) % 60
                    val h = (totalSec / 3600) % 24
                    val d = totalSec / 86400

                    binding.tvDays.text = String.format(Locale.getDefault(), "%02d", d)
                    binding.tvHours.text = String.format(Locale.getDefault(), "%02d", h)
                    binding.tvMinutes.text = String.format(Locale.getDefault(), "%02d", m)
                    binding.tvSeconds.text = String.format(Locale.getDefault(), "%02d", s)

                    handler.postDelayed(this, 1000)
                }
            }

            // Mulai jalankan timer
            handler.post(runnable!!)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadUserData() {
        binding.idnya.text =
            prefs.getString("namalengkap", "") + " (" + prefs.getString("kelas", "") + ")"

        binding.presensi.setOnClickListener {
            val intent = Intent(this, Presensi::class.java)
            startActivity(intent)
        }

        val fotonyaaa = prefs.getString("foto", "")
        val foto = "https://sman1sukorejo.sch.id/smania/fotoprofil/$fotonyaaa"

        if (fotonyaaa == "")
            Glide.with(this)
                .load(R.drawable.icm_person)
                .circleCrop()
                .into(binding.fotonya)
        else {
            Glide.with(this)
                .load(foto)
                .circleCrop()
                .into(binding.fotonya)
        }
    }

    private fun loadEventTerdekatDashboard() {
        val queue = Volley.newRequestQueue(this)

        // Tentukan waktu mulai dari hari ini
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val timeMinRaw = sdf.format(Date())
        val timeMinEncoded = java.net.URLEncoder.encode(timeMinRaw, "UTF-8")

        // Gunakan URL yang sama dengan yang ada di Kaldik.kt
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

                    // Loop untuk mencari event
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val judul = item.getString("summary")

                        val startObj = item.getJSONObject("start")
                        val endObj = item.getJSONObject("end")

                        // LOGIKA AMAN: Parsing Tanggal
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

                        // Sesuaikan jika event seharian (all-day)
                        val isAllDay = startObj.has("date")
                        val actualEndDate = if (isAllDay) endDate.minusDays(1) else endDate

                        // 1. Cek apakah ada event HARI INI
                        if (eventHariIni == null && !hariIni.isBefore(startDate) && !hariIni.isAfter(
                                actualEndDate
                            )
                        ) {
                            eventHariIni = judul
                        }

                        // 2. Cek apakah ada event MENDATANG
                        if (eventMendatang == null && startDate.isAfter(hariIni)) {
                            val formatTanggal =
                                startDate.format(DateTimeFormatter.ofPattern("dd MMMM YYYY"))
                            eventMendatang = "$judul ($formatTanggal)"
                        }

                        // 3. Jika KEDUANYA sudah ketemu, hentikan loop
                        if (eventHariIni != null && eventMendatang != null) {
                            break
                        }
                    }

                    // --- UPDATE UI BERDASARKAN HASIL PENCARIAN ---
                    if (eventHariIni != null && eventMendatang != null) {
                        // Ada event hari ini DAN ada event mendatang
                        binding.tvLabelEvent.text = "Event Aktif: "
                        binding.tvValueEvent.text = eventHariIni
                        binding.tvLabelEventNext.visibility = View.VISIBLE
                        binding.tvValueEventNext.visibility = View.VISIBLE
                        binding.tvValueEventNext.text = eventMendatang
                    } else if (eventHariIni != null) {
                        // HANYA ada event hari ini
                        binding.tvLabelEvent.text = "Event Aktif: "
                        binding.tvValueEvent.text = eventHariIni
                    } else if (eventMendatang != null) {
                        // TIDAK ADA event hari ini, hanya event mendatang
                        binding.tvLabelEvent.text = "Event Mendatang: "
                        binding.tvValueEvent.text = eventMendatang
                    } else {
                        // Kosong sama sekali
                        binding.tvLabelEvent.text = "Event: "
                        binding.tvValueEvent.text = "Tidak ada agenda terdekat"
                    }

                } catch (e: Exception) {
                    binding.tvValueEvent.text = "Gagal memuat agenda"
                    Log.e("DASHBOARD_KALENDER", "Error parsing: ${e.message}")
                }
            },
            { error ->
                binding.tvValueEvent.text = "Gagal terhubung"
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

