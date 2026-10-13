package id.rudissp.smania.jadwal

import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import id.rudissp.smania.AppConstants
import id.rudissp.smania.R
import id.rudissp.smania.databinding.ActivityJadwalBinding
import org.json.JSONObject

class Jadwal : AppCompatActivity() {
    lateinit var binding: ActivityJadwalBinding
    private val listDataJadwal = ArrayList<HariModel>()
    private lateinit var adapterJadwal: JadwalRowAdapter
    private lateinit var prefs: SharedPreferences


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        prefs = getSharedPreferences("PREFS", MODE_PRIVATE)
        binding = ActivityJadwalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val kelasSiswa = prefs.getString("kelas", "") ?: ""

        // Set judul toolbar menjadi nama kelas
        binding.jadwalv2Toolbar.title = "Jadwal Kelas $kelasSiswa"
        binding.jadwalv2Toolbar.setNavigationOnClickListener { finish() }

        if (kelasSiswa.isNotEmpty()) {
            fetchJadwalKelas(kelasSiswa)
        } else {
            Toast.makeText(this, "Data kelas tidak ditemukan di sesi login", Toast.LENGTH_LONG).show()
        }
    }

    private fun fetchJadwalKelas(namaKelas: String) {
        val url = AppConstants.API_JADWAL_KELAS + "?kelas=$namaKelas"

        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                try {
                    val jsonResponse = JSONObject(response)
                    if (jsonResponse.getString("status") == "success") {
                        listDataJadwal.clear()
                        val jsonArrayData = jsonResponse.getJSONArray("data")

                        generateHeaderJam(10) // Panggil header Jam 1-10

                        for (i in 0 until jsonArrayData.length()) {
                            val hariObj = jsonArrayData.getJSONObject(i)
                            val namaHari = hariObj.getString("hari")
                            val jadwalArray = hariObj.getJSONArray("jadwal")

                            val listJadwalSementara = ArrayList<JadwalItemModel>()

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
                            listDataJadwal.add(HariModel(namaHari, listJadwalSementara))
                        }

                        // Identifier "kelas" agar layout mendeteksi mode kelas
                        adapterJadwal = JadwalRowAdapter(listDataJadwal, "kelas")
                        binding.rvJadwalUtama.adapter = adapterJadwal
                        binding.rvJadwalUtama.layoutManager =
                            LinearLayoutManager(this@Jadwal)
                    } else {
                        Toast.makeText(this, "Jadwal belum tersedia", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            { error -> Toast.makeText(this, "Koneksi gagal", Toast.LENGTH_SHORT).show() }
        )
        Volley.newRequestQueue(this).add(request)
    }

    // --- GENERATE HEADER HARI DI ATAS TABEL ---
    private fun generateHeaderJam(maxJam: Int) {
        binding.containerHeaderAngka.removeAllViews()
        for (i in 1..maxJam) {
            val tvHeader = TextView(this).apply {
                val widthPx = (94 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.WRAP_CONTENT)
                text = "Jam $i"
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setTypeface(null, Typeface.BOLD)
                textSize = 12f
            }
            binding.containerHeaderAngka.addView(tvHeader)
        }
    }
}