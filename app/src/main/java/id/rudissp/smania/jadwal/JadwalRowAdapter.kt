package id.rudissp.smania.jadwal

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import id.rudissp.smania.R

class JadwalRowAdapter(
    private val listHari: List<HariModel>,
    private val modePencarian: String
) : RecyclerView.Adapter<JadwalRowAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Asumsi menggunakan layout baris yang kiri teks (Hari), kanan container kotak
        val tvNamaHari: TextView = view.findViewById(R.id.tv_jam_ke) // ID disesuaikan dengan XML baris Bapak
        val containerJadwal: LinearLayout = view.findViewById(R.id.container_jadwal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_baris_jam, parent, false) // Pakai XML baris yang ada
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val dataHari = listHari[position]
        val context = holder.itemView.context

        holder.tvNamaHari.text = dataHari.namaHari // Set teks "Senin", "Selasa", dll

        holder.containerJadwal.removeAllViews()

        for (jadwal in dataHari.listJadwal) {
            val viewJadwal = LayoutInflater.from(context).inflate(R.layout.item_jadwal_kbm, holder.containerJadwal, false)

            val cardStatus = viewJadwal.findViewById<MaterialCardView>(R.id.card_status)
            val tvAtas = viewJadwal.findViewById<TextView>(R.id.tv_nama_guru)
            val tvTengah = viewJadwal.findViewById<TextView>(R.id.tv_mapel)
            val tvBawah = viewJadwal.findViewById<TextView>(R.id.tv_kelas)
            val tvKeterangan = viewJadwal.findViewById<TextView>(R.id.tv_keterangan)

            tvAtas.visibility = View.GONE
            tvKeterangan.visibility = View.GONE

            tvTengah.textSize = 12f
            tvTengah.setTypeface(null, Typeface.BOLD)

            if (jadwal.status == "kosong") {
                cardStatus.setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_error))
                tvTengah.text = "-"
                tvBawah.text = ""
            } else {
                cardStatus.setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_primaryContainer))
                tvTengah.setTextColor(ContextCompat.getColor(context, R.color.md_theme_onPrimaryContainer))
                tvBawah.setTextColor(ContextCompat.getColor(context, R.color.md_theme_onPrimaryContainer))

                tvTengah.text = jadwal.mapel
                tvBawah.text = jadwal.infoBawah
            }
            holder.containerJadwal.addView(viewJadwal)
        }
    }

    override fun getItemCount(): Int = listHari.size
}