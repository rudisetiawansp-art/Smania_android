package id.rudissp.smania.unduh

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import id.rudissp.smania.R
import id.rudissp.smania.VolleySingleton
import id.rudissp.smania.databinding.ActivityUnduhBinding
import org.json.JSONException
import androidx.core.net.toUri

class Unduh : AppCompatActivity() {
    lateinit var binding: ActivityUnduhBinding
    lateinit var prefs: SharedPreferences

    private lateinit var adapter: BerkasAdapter
    private val berkasList = mutableListOf<Berkas>()

    // Ganti dengan URL server Anda
    private val BASE_URL = "https://sman1sukorejo.sch.id/smania/"
    private var levelUser: String = "user"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityUnduhBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("user_session", MODE_PRIVATE)
        levelUser = prefs.getString("level", "user") ?: "user"

        binding.rvBerkas.layoutManager = LinearLayoutManager(this)
        adapter = BerkasAdapter(this, berkasList, levelUser, BASE_URL)
        binding.rvBerkas.adapter = adapter

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Ambil data
        fetchDataBerkas()

    }

    private fun fetchDataBerkas() {
        val url = BASE_URL + "android/api_berkas.php"
        val queue = VolleySingleton.getInstance(this).requestQueue

        val jsonArrayRequest = JsonArrayRequest(
            Request.Method.GET, url, null,
            { response ->
                berkasList.clear()
                try {
                    for (i in 0 until response.length()) {
                        val obj = response.getJSONObject(i)
                        val berkas = Berkas(
                            id_berkas = obj.getInt("id_berkas"),
                            judul_berkas = obj.getString("judul_berkas"),
                            deskripsi = obj.optString("deskripsi", ""),
                            nama_file = obj.getString("nama_file"),
                            ukuran_file = obj.getString("ukuran_file"),
                            tgl_upload = obj.getString("tgl_upload")
                        )
                        berkasList.add(berkas)
                    }
                    adapter.notifyDataSetChanged()
                } catch (e: JSONException) {
                    Toast.makeText(this, "Error parsing data", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Toast.makeText(
                    this,
                    "Gagal mengambil data: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            })

        queue.add(jsonArrayRequest)
    }

    override fun onResume() {
        super.onResume()
        fetchDataBerkas()
    }
}

class BerkasAdapter(
    private val context: Context,
    private val list: MutableList<Berkas>,
    private val levelUser: String, // "admin" atau "user"
    private val baseUrl: String
) : RecyclerView.Adapter<BerkasAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView = itemView.findViewById(R.id.ivFileIcon)
        val tvJudul: TextView = itemView.findViewById(R.id.tvJudul)
        val tvDeskripsi: TextView = itemView.findViewById(R.id.tvDeskripsi)
        val tvUkuran: TextView = itemView.findViewById(R.id.tvUkuran)
        val tvTanggal: TextView = itemView.findViewById(R.id.tvTanggal)
        val btnDownload: ImageView = itemView.findViewById(R.id.btnDownload)
        val btnDelete: ImageView = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_unduh, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val berkas = list[position]

        holder.tvJudul.text = berkas.judul_berkas
        if (berkas.deskripsi.isNullOrEmpty()) {
            holder.tvDeskripsi.visibility = View.GONE
        } else {
            holder.tvDeskripsi.visibility = View.VISIBLE
            holder.tvDeskripsi.text = berkas.deskripsi
        }
        holder.tvUkuran.text = berkas.ukuran_file
        holder.tvTanggal.text = formatTanggal(berkas.tgl_upload)

        // Set ikon berdasarkan ekstensi
        val ext = berkas.nama_file.substringAfterLast('.', "")
        val iconRes = when (ext.lowercase()) {
            "pdf" -> R.drawable.imc_pdf
            "doc", "docx" -> R.drawable.icm_dok
            "xls", "xlsx" -> R.drawable.icm_dok
            "jpg", "jpeg", "png" -> R.drawable.icm_jgp
            else -> R.drawable.icm_dok
        }
        Glide.with(context).load(iconRes).into(holder.ivIcon)

        // Tombol download
        holder.btnDownload.setOnClickListener {
            val fileUrl = baseUrl + "berkas/" + berkas.nama_file
            val intent = Intent(Intent.ACTION_VIEW, fileUrl.toUri())
            context.startActivity(Intent.createChooser(intent, "Buka Berkas"))
        }

        // Tombol hapus (hanya admin)
        if (levelUser == "admin") {
            holder.btnDelete.visibility = View.VISIBLE
            holder.btnDelete.setOnClickListener {
                AlertDialog.Builder(context)
                    .setTitle("Hapus Berkas")
                    .setMessage("Yakin ingin menghapus \"${berkas.judul_berkas}\"?")
                    .setPositiveButton("Hapus") { _, _ ->
                        hapusBerkas(berkas.id_berkas, position)
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        } else {
            holder.btnDelete.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = list.size

    private fun hapusBerkas(idBerkas: Int, position: Int) {
        val url = baseUrl + "downloadfile.php?hapus=$idBerkas"
        val queue = VolleySingleton.getInstance(context).requestQueue
        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                // Hapus dari list dan notify
                list.removeAt(position)
                notifyItemRemoved(position)
                Toast.makeText(context, "Berkas dihapus", Toast.LENGTH_SHORT).show()
            },
            { error ->
                Toast.makeText(context, "Gagal menghapus: ${error.message}", Toast.LENGTH_SHORT)
                    .show()
            })
        queue.add(request)
    }

    private fun formatTanggal(tgl: String): String {
        // Contoh: "2026-01-12 10:30:00" -> "12 Jan 2026"
        return try {
            val parts = tgl.split(" ")
            val dateParts = parts[0].split("-")
            val bulan = when (dateParts[1]) {
                "01" -> "Jan"
                "02" -> "Feb"
                "03" -> "Mar"
                "04" -> "Apr"
                "05" -> "Mei"
                "06" -> "Jun"
                "07" -> "Jul"
                "08" -> "Agu"
                "09" -> "Sep"
                "10" -> "Okt"
                "11" -> "Nov"
                "12" -> "Des"
                else -> ""
            }
            "${dateParts[2]} $bulan ${dateParts[0]}"
        } catch (e: Exception) {
            tgl
        }
    }
}

data class Berkas(
    val id_berkas: Int,
    val judul_berkas: String,
    val deskripsi: String?,
    val nama_file: String,
    val ukuran_file: String,
    val tgl_upload: String
)