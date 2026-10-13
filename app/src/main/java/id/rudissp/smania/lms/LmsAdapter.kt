package id.rudissp.smania.lms

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import id.rudissp.smania.databinding.ItemLmsBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LmsAdapter(
    private val items: List<LmsItem>,
    private val onItemClick: (LmsItem) -> Unit
) : RecyclerView.Adapter<LmsAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemLmsBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLmsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        with(holder.binding) {
            // 1. Set text dasar
            tvTitle.text = item.judul
            tvSubjectBadge.text = item.mapel

            // 2. Logika Jenis Item (Materi vs Penugasan)
            val isMateri = item.jenis.equals("materi", ignoreCase = true)
            if (isMateri) {
                tvTypeBadge.text = "Materi"
                tvTypeBadge.setTextColor(Color.parseColor("#2E7D32")) // Hijau Muted
                tvTypeBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E8F5E9"))
                clStatusContainer.visibility = View.GONE // Materi tidak butuh container status tugas
            } else {
                tvTypeBadge.text = "Penugasan"
                tvTypeBadge.setTextColor(Color.parseColor("#E65100")) // Orange Muted
                tvTypeBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFF3E0"))
                clStatusContainer.visibility = View.VISIBLE
            }

            // 3. Logika Status Pengumpulan & Tenggat Waktu (Jika Bukan Materi)
            if (!isMateri) {
                val isOnline = item.isOnline == 1
                val sudahKumpul = item.idPengumpulan != null

                if (isOnline) {
                    if (sudahKumpul) {
                        var statusTxt = "Sudah Upload Tugas"
                        if (item.nilai > 0) {
                            statusTxt += " (Nilai: ${item.nilai})"
                        }
                        tvStatus.text = statusTxt
                        tvStatus.setTextColor(Color.parseColor("#2E7D32")) // Hijau jika selesai
                    } else {
                        // Cek apakah waktu sekarang melewati deadline database
                        val dbDateFormat =
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        try {
                            val deadlineDate = dbDateFormat.parse(item.deadline)
                            val now = Date()

                            if (deadlineDate != null && now.after(deadlineDate)) {
                                tvStatus.text = "Terlambat"
                                tvStatus.setTextColor(Color.parseColor("#C62828")) // Merah
                            } else {
                                tvStatus.text = "Belum Mengumpulkan"
                                tvStatus.setTextColor(Color.parseColor("#455A64")) // Warna netral/Muted
                            }
                        } catch (e: Exception) {
                            tvStatus.text = "Belum Mengumpulkan"
                        }
                    }

                    // Format Tampilan Tanggal Tenggat (Format: d/m/y H:i)
                    try {
                        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        val outputFormat = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
                        val date = inputFormat.parse(item.deadline)
                        if (date != null) {
                            tvDeadline.text = "Tenggat: ${outputFormat.format(date)}"
                            tvDeadline.visibility = View.VISIBLE
                        }
                    } catch (e: Exception) {
                        tvDeadline.text = "Tenggat: ${item.deadline}"
                    }

                } else {
                    // Tugas Tatap Muka / Offline
                    tvStatus.text = "Tugas Tatap Muka"
                    tvStatus.setTextColor(Color.parseColor("#0277BD")) // Biru Muted
                    tvDeadline.visibility = View.GONE
                }
            }

            root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}

