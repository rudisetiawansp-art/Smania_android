package id.rudissp.smania.jurusan

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filter.FilterResults
import android.widget.Filterable
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.color.MaterialColors
import id.rudissp.smania.AppConstants
import id.rudissp.smania.R
import id.rudissp.smania.databinding.ActivityJurusanBinding
import id.rudissp.smania.databinding.ItemJurusanBinding

class Jurusan : AppCompatActivity() {
    private lateinit var binding: ActivityJurusanBinding
    private var jurusanList = ArrayList<JurusanItem>()
    private lateinit var adapter: JurusanAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityJurusanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarJur)
        binding.toolbarJur.setNavigationOnClickListener { finish() }

        jurusanList = ArrayList()
        adapter = JurusanAdapter(jurusanList)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        fetchDataFromPhp()

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_search, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        searchView.queryHint = "Cari jurusan atau mapel pendukung..."
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                adapter.filter.filter(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter.filter(newText)
                return true
            }
        })

        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                adapter.filter.filter("")
                return true
            }
        })

        return true
    }

    private fun fetchDataFromPhp() {
        val url = "https://sman1sukorejo.sch.id/esmansa/api_jurusan_all.php"
        val queue = Volley.newRequestQueue(this)

        val jsonArrayRequest = JsonArrayRequest(
            Request.Method.GET, url, null,
            { response ->
                jurusanList.clear()
                try {
                    // Looping melalui JSON Array
                    for (i in 0 until response.length()) {
                        val item = response.getJSONObject(i)

                        val jurusan = JurusanItem(
                            kelompokProgramStudi = item.getString("kelompok_program_studi"),
                            rumpunIlmu = item.getString("rumpun_ilmu"),
                            kurikulumMerdeka = item.getString("kurikulum_merdeka")
                        )
                        jurusanList.add(jurusan)
                    }

                    adapter.updateData(jurusanList)

                } catch (e: Exception) {
                    Log.e("JSON_ERROR", "Error parsing data: ${e.message}")
                }
            },
            { error ->
                Toast.makeText(this, "Gagal mengambil data: ${error.message}", Toast.LENGTH_SHORT)
                    .show()
                Log.e("VOLLEY_ERROR", error.toString())
            }
        )

        queue.add(jsonArrayRequest)
    }
}

data class JurusanItem(
    val kelompokProgramStudi: String,
    val rumpunIlmu: String,
    val kurikulumMerdeka: String
)

class JurusanAdapter(
    private var jurusanList: ArrayList<JurusanItem>
) :
    RecyclerView.Adapter<JurusanAdapter.ViewHolder>(), Filterable {

    private var listfull: ArrayList<JurusanItem> = ArrayList(jurusanList)

    class ViewHolder(val binding: ItemJurusanBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemJurusanBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val jurusan = jurusanList[position]

        holder.binding.apply {
            tvRumpunIlmu.text = jurusan.rumpunIlmu
            tvKelompok.text = jurusan.kelompokProgramStudi

            val isMerdeka = jurusan.kurikulumMerdeka.equals("Ya", ignoreCase = true)

            val context = holder.itemView.context

            val bgAttr =
                if (isMerdeka) com.google.android.material.R.attr.colorTertiaryContainer else com.google.android.material.R.attr.colorErrorContainer
            val textAttr =
                if (isMerdeka) com.google.android.material.R.attr.colorOnTertiaryContainer else com.google.android.material.R.attr.colorOnErrorContainer

            val bgColor = MaterialColors.getColor(context, bgAttr, "Error")
            val textColor = MaterialColors.getColor(context, textAttr, "Error")

            chipKurikulum.text = jurusan.kurikulumMerdeka
            chipKurikulum.chipBackgroundColor = ColorStateList.valueOf(bgColor)
            chipKurikulum.setTextColor(textColor)
        }
    }

    override fun getItemCount(): Int = jurusanList.size

    fun updateData(newList: ArrayList<JurusanItem>) {
        jurusanList = newList
        listfull = ArrayList(newList)
        notifyDataSetChanged()
    }


    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filteredList = mutableListOf<JurusanItem>()
                if (constraint.isNullOrEmpty()) {
                    filteredList.addAll(listfull)
                } else {
                    val filterPattern = constraint.toString().lowercase().trim()
                    for (item in listfull) {
                        if (item.kurikulumMerdeka.lowercase().contains(filterPattern) ||
                            item.kelompokProgramStudi.lowercase().contains(filterPattern)
                        ) {
                            filteredList.add(item)
                        }
                    }
                }

                val results = FilterResults()
                results.values = filteredList
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                jurusanList.clear()
                jurusanList.addAll(results?.values as ArrayList<JurusanItem>)
                notifyDataSetChanged()
            }
        }
    }
}