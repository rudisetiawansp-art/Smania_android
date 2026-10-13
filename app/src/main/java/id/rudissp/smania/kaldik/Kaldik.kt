package id.rudissp.smania.kaldik

import android.graphics.Color
import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import id.rudissp.smania.R
import id.rudissp.smania.databinding.ActivityKaldikBinding
import id.rudissp.smania.databinding.CalendarDayLayoutBinding
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDate.now
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class Kaldik : AppCompatActivity() {

    private lateinit var binding: ActivityKaldikBinding

    // Konfigurasi API
    private val API_KEY = "AIzaSyDMUXOGXDwtz5-yhcV2Oo8NpB9FqpGF3vw"
    private val CALENDAR_ID = "2c13dee8396be174383342ba6abeff67b6aee566be14c1d99b4b2f9e2750a55a@group.calendar.google.com"

    private val semuaAgenda = mutableMapOf<LocalDate, MutableList<AgendaModel>>()
    private var tanggalTerpilih: LocalDate? = now()
    private val formatterDaftar = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale("id", "ID"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityKaldikBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupCalendarStructure()
        fetchCalendarData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarKalender)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarKalender.setNavigationOnClickListener { finish() }
    }

    private fun setupCalendarStructure() {
        val hariDalamSeminggu = daysOfWeek()

        // Setup Header Hari (Senin - Minggu)
        binding.layoutNamaHari.weightSum = 7f
        hariDalamSeminggu.forEach { hari ->
            val textView = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                text = hari.getDisplayName(TextStyle.SHORT, Locale("id", "ID"))
                setTextColor(Color.DKGRAY)
                textSize = 12f
            }
            binding.layoutNamaHari.addView(textView)
        }

        // Setup Day Binder (Desain per Kotak Tanggal)
        binding.calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, data: CalendarDay) {
                container.day = data
                val bindingDay = container.binding

                bindingDay.tvDayText.text = data.date.dayOfMonth.toString()

                if (data.position == DayPosition.MonthDate) {
                    // Warna teks tanggal aktif (Bulan ini)
                    bindingDay.tvDayText.setTextColor(Color.BLACK)

                    // Logika Titik Penanda Event
                    if (semuaAgenda.containsKey(data.date)) {
                        bindingDay.viewEventDot.visibility = View.VISIBLE
                    } else {
                        bindingDay.viewEventDot.visibility = View.INVISIBLE
                    }

                    // Logika Latar Belakang Tanggal Dipilih
                    if (data.date == tanggalTerpilih) {
                        bindingDay.tvDayText.setBackgroundResource(R.drawable.bg_dot)
                        bindingDay.tvDayText.setTextColor(Color.WHITE)
                    } else {
                        bindingDay.tvDayText.background = null
                    }
                } else {
                    // Warna teks tanggal redup (Bulan sebelum/sesudahnya)
                    bindingDay.tvDayText.setTextColor(Color.LTGRAY)
                    bindingDay.viewEventDot.visibility = View.INVISIBLE
                    bindingDay.tvDayText.background = null
                }
            }
        }

        // Setup Rentang Waktu Kalender
        val bulanSaatIni = YearMonth.now()
        val bulanAwal = bulanSaatIni.minusMonths(3)
        val bulanAkhir = bulanSaatIni.plusMonths(12)

        binding.calendarView.setup(bulanAwal, bulanAkhir, hariDalamSeminggu.first())
        binding.calendarView.scrollToMonth(bulanSaatIni)
        binding.tvBulanTahun.text = bulanSaatIni.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("id", "ID")))

        // Aksi Navigasi Bulan
        binding.btnBulanSebelumnya.setOnClickListener {
            binding.calendarView.findFirstVisibleMonth()?.yearMonth?.minusMonths(1)?.let {
                binding.calendarView.smoothScrollToMonth(it)
            }
        }

        binding.btnBulanBerikutnya.setOnClickListener {
            binding.calendarView.findFirstVisibleMonth()?.yearMonth?.plusMonths(1)?.let {
                binding.calendarView.smoothScrollToMonth(it)
            }
        }

        binding.calendarView.monthScrollListener = { info ->
            binding.tvBulanTahun.text = info.yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("id", "ID")))
        }
    }

    // Inner Class untuk Container ViewBinding Kizitonwose
    inner class DayViewContainer(view: View) : ViewContainer(view) {
        val binding = CalendarDayLayoutBinding.bind(view)
        lateinit var day: CalendarDay

        init {
            view.setOnClickListener {
                if (day.position == DayPosition.MonthDate) {
                    val tanggalLama = tanggalTerpilih
                    tanggalTerpilih = day.date

                    this@Kaldik.binding.calendarView.notifyDateChanged(day.date)
                    tanggalLama?.let { this@Kaldik.binding.calendarView.notifyDateChanged(it) }

                    updateDaftarAgendaHarian(day.date)
                }
            }
        }
    }

    private fun fetchCalendarData() {
        binding.rvAgendaHarian.visibility = View.GONE

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.MONTH, -2)
        val timeMinRaw = sdf.format(cal.time)

        val timeMinEncoded = URLEncoder.encode(timeMinRaw, "UTF-8")

        val url = "https://www.googleapis.com/calendar/v3/calendars/$CALENDAR_ID/events?" +
                "key=$API_KEY&singleEvents=true&orderBy=startTime&timeMin=$timeMinEncoded&maxResults=200"

        Log.d("DEBUG_KALD", "URL API: $url")

        val queue = Volley.newRequestQueue(this)
        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->

                try {
                    val items = response.getJSONArray("items")
                    semuaAgenda.clear()

                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val judul = item.getString("summary")

                        val startObj = item.getJSONObject("start")
                        val endObj = item.getJSONObject("end")

// LOGIKA AMAN: Cek dulu apakah dia event seharian (date) atau event dengan jam (dateTime)
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

                        var currentDate = startDate
                        while (!currentDate.isAfter(actualEndDate)) {
                            val waktuTampil = if (!isAllDay) {
                                startObj.getString("dateTime").substring(11, 16) + " WIB"
                            } else {
                                "Seharian"
                            }

                            if (!semuaAgenda.containsKey(currentDate)) {
                                semuaAgenda[currentDate] = mutableListOf()
                            }

                            if (semuaAgenda[currentDate]?.none { it.judul == judul } == true) {
                                semuaAgenda[currentDate]?.add(AgendaModel(judul, waktuTampil))
                            }

                            currentDate = currentDate.plusDays(1)
                        }
                    }

                    // 4. Update UI
                    binding.calendarView.post {
                        binding.calendarView.notifyCalendarChanged()

                        semuaAgenda.keys.forEach { tanggalAgenda ->
                            binding.calendarView.notifyDateChanged(tanggalAgenda)
                        }
                        tanggalTerpilih?.let { updateDaftarAgendaHarian(it) }
                    }

                } catch (e: Exception) {
                    Log.e("DEBUG_KALD", "Error parsing JSON: ${e.message}", e)

                }
            },
            { error ->
                error.networkResponse?.let { "Error Code: ${it.statusCode}" } ?: error.message
            }
        )
        queue.add(jsonObjectRequest)
    }

    private fun updateDaftarAgendaHarian(date: LocalDate) {
        binding.tvTanggalDipilih.text = "Agenda: ${date.format(formatterDaftar)}"

        val agendaHariIni = semuaAgenda[date]

        if (!agendaHariIni.isNullOrEmpty()) {
            //binding.tvEmptyKaldik.visibility = View.GONE
            binding.rvAgendaHarian.visibility = View.VISIBLE

            binding.rvAgendaHarian.layoutManager = LinearLayoutManager(this)
            binding.rvAgendaHarian.adapter = AgendaHarianAdapter(agendaHariIni)
        } else {
            binding.rvAgendaHarian.visibility = View.GONE
            // binding.tvEmptyKaldik.visibility = View.VISIBLE
        }
    }
}