package id.rudissp.smania.rapor

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.tabs.TabLayout
import id.rudissp.smania.AppConstants
import id.rudissp.smania.databinding.ActivityRaporBinding
import id.rudissp.smania.databinding.ItemMapelBinding
import org.json.JSONObject

class ActivityRapor : AppCompatActivity() {

    private lateinit var binding: ActivityRaporBinding
    private lateinit var mapelAdapter: MapelAdapter

    private lateinit var prefs : SharedPreferences

    // Simpan data array berdasarkan tab (index)
    private val tabDataList = mutableListOf<SemesterTab>()

    // Data Class internal untuk memegang data tiap tab
    data class SemesterTab(
        val title: String,
        val rank: String,
        val sum: String,
        val rataRata: String,
        val mapelList: List<Mapel>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRaporBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences("PREFS", MODE_PRIVATE)

        binding.toolbarRapor.setNavigationOnClickListener { finish() }

        mapelAdapter = MapelAdapter(emptyList())
        binding.rvMapel.layoutManager = LinearLayoutManager(this)
        binding.rvMapel.adapter = mapelAdapter

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.position?.let { updateUIForTab(it) }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // AMBIL NIS DAN TINGKAT DARI PREFERENCES
        val nis = prefs.getString("nis", "") ?: ""
        // Pastikan key "tingkat" sesuai dengan yang Anda simpan saat proses login di Android
        val tingkat = prefs.getString("tingkat", "") ?: ""

        fetchData(nis, tingkat)
    }

    // TAMBAHKAN PARAMETER TINGKAT PADA FUNGSI INI
    private fun fetchData(nis: String, tingkat: String) {
        // SERTAKAN PARAMETER TINGKAT KE DALAM URL API
        val url = AppConstants.BASE_URL + "api_rapor.php?nis=$nis&tingkat=$tingkat"
        val queue = Volley.newRequestQueue(this)

        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    if (response.getBoolean("status")) {
                        val data = response.getJSONObject("data")
                        parseData(data)
                    } else {
                        Toast.makeText(this, response.getString("message"), Toast.LENGTH_SHORT)
                            .show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            {
                Toast.makeText(this, "Gagal terhubung ke server", Toast.LENGTH_SHORT).show()
            })

        queue.add(request)
    }

    private fun parseData(data: JSONObject) {
        val semestersObj = data.getJSONObject("semester_data")
        val keys = semestersObj.keys()

        tabDataList.clear()
        binding.tabLayout.removeAllTabs()

        // Parsing tiap Semester
        while (keys.hasNext()) {
            val key = keys.next()
            val smtData = semestersObj.getJSONObject(key)

            val mapelArray = smtData.getJSONArray("mapel")
            val mapelList = mutableListOf<Mapel>()
            for (i in 0 until mapelArray.length()) {
                val mapelObj = mapelArray.getJSONObject(i)
                mapelList.add(Mapel(mapelObj.getString("nama"), mapelObj.getString("nilai")))
            }

            tabDataList.add(
                SemesterTab(
                    title = key,
                    rank = smtData.getString("rank"),
                    sum = smtData.getString("sum"),
                    rataRata = smtData.getString("rata_rata"),
                    mapelList = mapelList
                )
            )
        }

        val rataKeseluruhanArray = data.getJSONArray("rata_rata_keseluruhan")
        if (rataKeseluruhanArray.length() > 0) {
            val rataList = mutableListOf<Mapel>()
            for (i in 0 until rataKeseluruhanArray.length()) {
                val rObj = rataKeseluruhanArray.getJSONObject(i)
                rataList.add(Mapel(rObj.getString("nama"), rObj.getString("nilai")))
            }
            tabDataList.add(
                SemesterTab(
                    title = "Rata2",
                    rank = "-",
                    sum = "-",
                    rataRata = data.getString("grand_rata_rata"),
                    mapelList = rataList
                )
            )
        }

        // Tambahkan Tab ke UI
        for (tabData in tabDataList) {
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(tabData.title))
        }

        // Tampilkan tab pertama (index 0)
        if (tabDataList.isNotEmpty()) {
            updateUIForTab(0)
        }

    }
    private fun updateUIForTab(position: Int) {
        if (position < 0 || position >= tabDataList.size) return
        val currentTab = tabDataList[position]

        binding.tvRank.text = currentTab.rank
        binding.tvJumlah.text = currentTab.sum
        binding.tvRataRata.text = currentTab.rataRata

        mapelAdapter.updateData(currentTab.mapelList)
    }

}

data class Mapel(val nama: String, val nilai: String)

class MapelAdapter(private var list: List<Mapel>) : RecyclerView.Adapter<MapelAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemMapelBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMapelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.binding.tvMapelName.text = item.nama
        holder.binding.tvMapelScore.text = item.nilai
    }

    override fun getItemCount(): Int = list.size

    fun updateData(newList: List<Mapel>) {
        list = newList
        notifyDataSetChanged()
    }
}