package id.rudissp.smania.lms

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import id.rudissp.smania.databinding.ItemChatBinding
import java.text.SimpleDateFormat
import java.util.Locale

class LMSDiskusiAdapter(
    private val listChat: List<DiskusiModel>,
    private val nisSaya: String,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<LMSDiskusiAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemChatBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = listChat.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chat = listChat[position]

        with(holder.binding) {
            tvChatMessage.text = chat.pesan

            // Format Waktu dari YYYY-MM-DD HH:MM:SS ke HH:MM
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val date = inputFormat.parse(chat.createdAt)
                tvChatTime.text = if (date != null) outputFormat.format(date) else chat.createdAt
            } catch (e: Exception) {
                tvChatTime.text = chat.createdAt
            }

            // Logika Penataan: Pesan Saya vs Pesan Orang Lain
            val params = chatBubbleContainer.layoutParams as LinearLayout.LayoutParams

            if (chat.pengirimId == nisSaya) {
                // PESAN SAYA (Rata Kanan)
                params.gravity = Gravity.END
                chatBubbleContainer.layoutParams = params

                tvChatName.visibility = View.GONE

                // Set warna hijau/biru primary
                chatBubbleBackground.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#006d39"))
                tvChatMessage.setTextColor(Color.WHITE)
                tvChatTime.setTextColor(Color.WHITE)

                chatBubbleContainer.setOnLongClickListener {
                    onDeleteClick(chat.idDiskusi) // Panggil fungsi penghapusan
                    true // Kembalikan nilai true agar sentuhan tidak dianggap sebagai klik biasa
                }

            } else {
                // PESAN ORANG LAIN (Rata Kiri)
                params.gravity = Gravity.START
                chatBubbleContainer.layoutParams = params

                tvChatName.visibility = View.VISIBLE
                if (chat.pengirimTipe == "guru") {
                    tvChatName.text = "${chat.pengirimNama} (Guru)"
                    tvChatName.setTextColor(Color.parseColor("#1565C0")) // Biru jika guru
                } else {
                    tvChatName.text = chat.pengirimNama
                    tvChatName.setTextColor(Color.parseColor("#757575")) // Abu-abu jika siswa lain
                }

                // Set warna abu-abu terang untuk background
                chatBubbleBackground.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F5F5F5"))
                tvChatMessage.setTextColor(Color.parseColor("#212121"))
                tvChatTime.setTextColor(Color.parseColor("#757575"))

                chatBubbleContainer.setOnLongClickListener(null)
            }
        }
    }
}