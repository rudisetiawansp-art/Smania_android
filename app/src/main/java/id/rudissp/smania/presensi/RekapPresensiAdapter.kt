package id.rudissp.smania.presensi

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import id.rudissp.smania.R

class RekapPresensiAdapter(private val items: List<RekapPresensia>) :
    RecyclerView.Adapter<RekapPresensiAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtTanggal: TextView = view.findViewById(R.id.tvTanggal)
        val txtMasuk: TextView = view.findViewById(R.id.tvMasuk)
        val txtPulang: TextView = view.findViewById(R.id.tvPulang)
        val txtKeterangan: TextView = view.findViewById(R.id.tvjenis)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rekap_presensi, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.txtTanggal.text = item.tanggal
        holder.txtMasuk.text = item.masuk
        holder.txtPulang.text = item.pulang

        if (item.shift == "malam") {
            holder.txtKeterangan.text = "Piket"

            val color = ContextCompat.getColor(holder.itemView.context, R.color.md_theme_tertiary_highContrast)
            holder.txtKeterangan.setTextColor(color)
            holder.txtMasuk.setTextColor(color)
            holder.txtPulang.setTextColor(color)

        } else{
            holder.txtKeterangan.text = item.jenis
        }
    }

    override fun getItemCount(): Int = items.size
}



data class RekapPresensia(
    val tanggal: String,
    val masuk: String,
    val pulang: String,
    val jenis: String,
    val shift: String
)


