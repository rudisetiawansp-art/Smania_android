package id.rudissp.smania.ibadah

import android.R.attr.strokeColor
import android.R.attr.textColor
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.icu.util.Calendar
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import id.rudissp.smania.R
import id.rudissp.smania.databinding.ActivityRekapIbadahBinding
import id.rudissp.smania.databinding.ItemRekapHariBinding
import org.json.JSONObject
import androidx.core.graphics.toColorInt
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import id.rudissp.smania.AppConstants
import org.json.JSONArray

class RekapIbadah : AppCompatActivity() {

    private lateinit var binding: ActivityRekapIbadahBinding
    private lateinit var adapter: RekapAdapter
    private val listData = ArrayList<RekapData>()

    private lateinit var prefs: SharedPreferences

    private val namaBulan = arrayOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = getSharedPreferences("PREFS", MODE_PRIVATE)
        binding = ActivityRekapIbadahBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupDropdowns() // Disesuaikan untuk AutoCompleteTextView
        setupRecyclerView()

        binding.btnCek.setOnClickListener {
            // Mengambil teks dari AutoCompleteTextView
            val teksBulan = binding.spinnerBulan.text.toString()
            val teksTahun = binding.spinnerTahun.text.toString()

            // Mencari index bulan (1-12) berdasarkan teks
            val bulanDipilih = namaBulan.indexOf(teksBulan) + 1

            if (bulanDipilih > 0 && teksTahun.isNotEmpty()) {
                val tahunDipilih = teksTahun.toInt()
                binding.tvLabelBulan.text = "Bulan $teksBulan"

                fetchDataRekap(bulanDipilih, tahunDipilih)
            } else {
                Toast.makeText(this, "Mohon pilih bulan dan tahun yang valid", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCek.performClick()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupDropdowns() {
        // Setup Adapter untuk Bulan
        val adapterBulan = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, namaBulan)
        binding.spinnerBulan.setAdapter(adapterBulan)

        // Setup Adapter untuk Tahun
        val tahunSekarang = Calendar.getInstance().get(Calendar.YEAR)
        val listTahun = arrayOf(tahunSekarang.toString(), (tahunSekarang - 1).toString())
        val adapterTahun = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, listTahun)
        binding.spinnerTahun.setAdapter(adapterTahun)

        // Set nilai default (Bulan dan Tahun saat ini)
        val cal = Calendar.getInstance()
        val indexBulanSaatIni = cal.get(Calendar.MONTH)

        // Parameter 'false' mencegah AutoCompleteTextView melakukan filter/menghapus opsi lain
        binding.spinnerBulan.setText(namaBulan[indexBulanSaatIni], false)
        binding.spinnerTahun.setText(tahunSekarang.toString(), false)
    }

    private fun setupRecyclerView() {
        adapter = RekapAdapter(listData)
        binding.rvRekap.layoutManager = LinearLayoutManager(this)
        binding.rvRekap.adapter = adapter
    }

    private fun fetchDataRekap(bulan: Int, tahun: Int) {
        val queue = Volley.newRequestQueue(this)
        val nis = prefs.getString("nis","").toString()

        // Pastikan URL API sesuai dengan server Anda
        val url = AppConstants.IBADAH_REKAP + "?nis=" +nis + "&bulan=$bulan&tahun=$tahun"

        val request = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    listData.clear()
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

                    for (d in 1..jumlahHari) {
                        cal.set(Calendar.DAY_OF_MONTH, d)
                        val hariMinggu = cal.get(Calendar.DAY_OF_WEEK)
                        val isWeekend = (hariMinggu == Calendar.SATURDAY || hariMinggu == Calendar.SUNDAY)

                        val namaHari = when(hariMinggu) {
                            Calendar.SUNDAY -> "Minggu"; Calendar.MONDAY -> "Senin"
                            Calendar.TUESDAY -> "Selasa"; Calendar.WEDNESDAY -> "Rabu"
                            Calendar.THURSDAY -> "Kamis"; Calendar.FRIDAY -> "Jumat"
                            else -> "Sabtu"
                        }

                        val dataHariIni = mapDataServer[d] ?: emptyList()
                        listData.add(RekapData(d, namaBulan[bulan - 1], namaHari, isWeekend, dataHariIni))
                    }
                    adapter.notifyDataSetChanged()

                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Gagal parsing data", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Toast.makeText(this, "Koneksi Error: ${error.message}", Toast.LENGTH_SHORT).show()
            })

        queue.add(request)
    }
}

// Model Data dan Adapter (Sama persis seperti sebelumnya)
data class RekapData(
    val tanggal: Int,
    val namaBulan: String,
    val namaHari: String,
    val isWeekend: Boolean,
    val listAktivitas: List<JSONObject>
)

class RekapAdapter(private val list: List<RekapData>) : RecyclerView.Adapter<RekapAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemRekapHariBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: RekapData) {
            binding.tvTanggal.text = "${data.tanggal} ${data.namaBulan}"
            binding.tvHari.text = data.namaHari
            binding.containerBadges.removeAllViews()
            val context = binding.root.context

            if (data.isWeekend) {
                binding.cardHari.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.md_theme_errorContainer))
                binding.cardHari.strokeColor = ContextCompat.getColor(context, R.color.md_theme_error)
                addBadge("Libur", (R.color.md_theme_error), (R.color.md_theme_surfaceContainerLow), (R.color.md_theme_errorContainer))
            } else {
                binding.cardHari.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.md_theme_secondaryContainer))
                binding.cardHari.strokeColor =  ContextCompat.getColor(context, R.color.md_theme_onPrimary)

                if (data.listAktivitas.isEmpty()) {
                    val tvKosong = TextView(binding.root.context).apply {
                        text = "Kosong"
                        textSize = 11f
                        setTextColor(ContextCompat.getColor(context, R.color.md_theme_error_highContrast))
                        setTypeface(null, Typeface.ITALIC)
                    }
                    binding.containerBadges.addView(tvKosong)
                } else {
                    for (act in data.listAktivitas) {
                        val isHalangan = act.getString("is_halangan") == "Ya"
                        val namaShalat = act.getString("nama_shalat")

                        if (isHalangan) {
                            addBadge("Halangan", R.color.md_theme_tertiary, R.color.md_theme_onTertiary, R.color.md_theme_tertiary)
                        } else {
                            if (namaShalat.contains("Dzuhur", ignoreCase = true)) {
                                addBadge(namaShalat, R.color.md_theme_secondary, R.color.md_theme_onSecondary)
                            } else {
                                addBadge(namaShalat, R.color.md_theme_secondary, R.color.md_theme_onSecondary)
                            }
                        }
                    }
                }
            }
        }

        private fun addBadge(
            text: String,
            bgRes: Int,
            textRes: Int,
            strokeRes: Int = bgRes
        ) {
            val context = binding.root.context
            val textView = TextView(context).apply {
                this.text = text
                textSize = 10f
                setPadding(24, 10, 24, 10)
                setTextColor(ContextCompat.getColor(context, textRes))  // <- perbaikan di sini
                setTypeface(null, Typeface.BOLD)

                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 16f
                    setColor(ContextCompat.getColor(context, bgRes))
                    setStroke(2, ContextCompat.getColor(context, strokeRes))
                }
                background = drawable
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 0, 0, 0) }

            binding.containerBadges.addView(textView, params)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRekapHariBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(list[position])
    override fun getItemCount() = list.size
}