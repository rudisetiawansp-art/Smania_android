package id.rudissp.smania.lms

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.chip.Chip
import com.google.gson.Gson
import id.rudissp.smania.AppConstants
import id.rudissp.smania.R
import id.rudissp.smania.databinding.ActivityLmsBinding
import kotlin.jvm.java

class LMS : AppCompatActivity() {
    private lateinit var binding: ActivityLmsBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var lmsAdapter: LmsAdapter

    private var originalLmsList: List<LmsItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = getSharedPreferences("PREFS", MODE_PRIVATE)
        binding = ActivityLmsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvLms.layoutManager = LinearLayoutManager(this)
        binding.rvLms.setHasFixedSize(true)

        // Data Session
        val nisSiswa = prefs.getString("nis","").toString()
        val kelasSiswa = prefs.getString("kelas", "").toString()

        supportActionBar?.title = "LMS Kelas $kelasSiswa"
        binding.toolbarLms.setOnClickListener { finish() }

        loadLmsData(nisSiswa, kelasSiswa)
    }

    private fun loadLmsData(nis: String, kelas: String) {
        //binding.progressBar.visibility = View.VISIBLE

        // URL API Esmansa Anda
        val url = AppConstants.LMS_URL

        val stringRequest = object : StringRequest(
            Method.POST, url,
            { response ->
                // Sembunyikan loading ketika berhasil
                //binding.progressBar.visibility = View.GONE

                try {
                    // Konversi String JSON dari Volley menjadi Objek Kotlin menggunakan Gson
                    val gson = Gson()
                    val lmsResponse = gson.fromJson(response, LmsResponse::class.java)

                    // Di dalam try-catch saat Volley sukses:
                    if (lmsResponse.status == "success") {
                        val listData = lmsResponse.data

                        if (listData.isNotEmpty()) {
                            originalLmsList = listData

                            setupChipFilter(listData)

                            updateRecyclerView(listData) // 3. Tampilkan semua data untuk pertama kali
                        } else {
                            Toast.makeText(this@LMS, "Belum ada tugas atau materi.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this@LMS, "Status error dari server", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@LMS, "Gagal memproses data: ${e.message}", Toast.LENGTH_LONG).show()
                }
            },
            { error ->
                // Sembunyikan loading jika error jaringan
                //binding.progressBar.visibility = View.GONE
                Toast.makeText(this@LMS, "Koneksi Gagal: ${error.message}", Toast.LENGTH_LONG).show()
            }
        ) {
            // Mengirim parameter POST (nis dan kelas)
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["nis"] = nis
                params["kelas"] = kelas
                return params
            }
        }

        Volley.newRequestQueue(this).add(stringRequest)
    }

    private fun setupChipFilter(items: List<LmsItem>) {
        binding.chipGroupMapel.removeAllViews()

        // 1. Buat Chip "Semua Mapel" sebagai pilihan default
        val chipSemua = createChip("Semua Mapel")
        binding.chipGroupMapel.addView(chipSemua)
        chipSemua.isChecked = true // Pilih ini secara default

        // 2. Ambil daftar nama mapel yang unik (tidak duplikat) dan urutkan abjad
        // Misal data dari server ada 3 tugas Pendidikan Pancasila, list ini hanya akan menyimpan 1 teks "Pendidikan Pancasila"
        val listMapelUnik = items.map { it.mapel }.distinct().sorted()

        // 3. Looping untuk membuat chip dari setiap mapel unik
        for (mapel in listMapelUnik) {
            val chip = createChip(mapel)
            binding.chipGroupMapel.addView(chip)
        }

        // 4. Pasang Listener ketika chip diklik/dipilih
        binding.chipGroupMapel.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                // Ambil chip yang sedang diklik berdasarkan ID-nya
                val selectedChip = group.findViewById<Chip>(checkedIds.first())
                val mapelDipilih = selectedChip.text.toString()

                // Jalankan fungsi filter
                filterDataByMapel(mapelDipilih)
            }
        }
    }

    private fun createChip(text: String): Chip {
        return Chip(this).apply {
            this.text = text
            isCheckable = true
            isClickable = true
            // Opsional: atur style warna chip agar menyesuaikan tema aplikasi
            //setChipBackgroundColorResource(R.color.md_theme_primary)
        }
    }

    private fun filterDataByMapel(mapel: String) {
        val filteredList = if (mapel == "Semua Mapel") {
            // Jika pilih Semua Mapel, kembalikan ke list asli utuh
            originalLmsList
        } else {
            // Jika pilih mapel tertentu, saring list yang mapelnya cocok
            originalLmsList.filter { it.mapel == mapel }
        }

        // Update tampilan RecyclerView dengan list yang sudah disaring
        updateRecyclerView(filteredList)
    }

    private fun updateRecyclerView(listToShow: List<LmsItem>) {
        lmsAdapter = LmsAdapter(listToShow) { item ->
            val intent = Intent(this@LMS, DetailTugasActivity::class.java).apply {
                putExtra("ID_TUGAS", item.idTugas)
            }
            startActivity(intent)
        }
        binding.rvLms.adapter = lmsAdapter
    }
}