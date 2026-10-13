package id.rudissp.smania.kaldik

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import id.rudissp.smania.databinding.ItemAgendaHarianBinding

class AgendaHarianAdapter(private val listAgenda: List<AgendaModel>) :
    RecyclerView.Adapter<AgendaHarianAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemAgendaHarianBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(agenda: AgendaModel) {
            binding.tvJudulHarian.text = agenda.judul
            binding.tvWaktuHarian.text = agenda.waktu
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAgendaHarianBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(listAgenda[position])
    }

    override fun getItemCount(): Int = listAgenda.size
}
data class AgendaModel(
    val judul: String,
    val waktu: String
)