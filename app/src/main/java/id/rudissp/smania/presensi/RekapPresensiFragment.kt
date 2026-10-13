package id.rudissp.smania.presensi

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.textfield.TextInputEditText
import id.rudissp.smania.AppConstants
import id.rudissp.smania.R
import id.rudissp.smania.databinding.FragmentRekapPresensiBinding
import org.json.JSONArray
import org.json.JSONException
import java.util.Calendar


class RekapPresensiFragment : Fragment() {

    lateinit var binding: FragmentRekapPresensiBinding
    private lateinit var adapter: RekapPresensiAdapter

    private val daftarRekap = mutableListOf<RekapPresensia>()
    private lateinit var sharedPref: SharedPreferences

    private val bulanMap = mapOf(
        "Januari" to 1, "Februari" to 2, "Maret" to 3, "April" to 4,
        "Mei" to 5, "Juni" to 6, "Juli" to 7, "Agustus" to 8,
        "September" to 9, "Oktober" to 10, "November" to 11, "Desember" to 12
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRekapPresensiBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        sharedPref = requireActivity().getSharedPreferences("PREFS", Context.MODE_PRIVATE)


        adapter = RekapPresensiAdapter(daftarRekap)
        binding.recyclerViewRiwayat.adapter = adapter
        binding.recyclerViewRiwayat.layoutManager = LinearLayoutManager(requireContext())

        val calendar = Calendar.getInstance()
        val bulanSekarang = calendar.get(Calendar.MONTH) // 0-based
        val tahunSekarang = calendar.get(Calendar.YEAR)

        val bulanArray = resources.getStringArray(R.array.months)
        binding.bulannya.setText(bulanArray[bulanSekarang], false)
        binding.tahunnya.setText(tahunSekarang.toString())
        ambilRekap()

        binding.bulannya.setOnItemClickListener { _, _, _, _ -> ambilRekap() }
        binding.tahunnya.setOnEditorActionListener { _, _, _ ->
            ambilRekap()
            true
        }

    }

    private fun ambilRekap() {
        val bulan = binding.bulannya.text.toString()
        val bulanAngka = bulanMap[bulan]
        if (bulanAngka == null) {
            Toast.makeText(requireContext(), "Bulan tidak valid", Toast.LENGTH_SHORT).show()
            return
        }
        val tahun = binding.tahunnya.text.toString()

        if (bulan.isEmpty() || tahun.isEmpty()) {
            Toast.makeText(requireContext(), "Bulan dan Tahun wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }

        //val bulanAngka = resources.getStringArray(R.array.months).indexOf(bulan) + 1
        val queue = Volley.newRequestQueue(requireContext())
        val request = object : StringRequest(Method.POST, AppConstants.REKAP_PRESENSI,
            { response ->
                try {
                    val jsonArray = JSONArray(response)
                    daftarRekap.clear()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val data = RekapPresensia(
                            obj.getString("tanggal"),
                            obj.getString("masuk"),
                            obj.getString("pulang"),
                            obj.getString("status"),
                            obj.getString("shift")

                        )
                        daftarRekap.add(data)
                    }
                    adapter.notifyDataSetChanged()
                } catch (e: JSONException) {
                    Toast.makeText(requireContext(), "Data tidak valid", Toast.LENGTH_SHORT).show()
                }
            },
            {
                Toast.makeText(requireContext(), "Gagal koneksi: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): Map<String, String> {
                val nis = sharedPref.getString("nis", "") ?: ""
                return mapOf(
                    "nis" to nis,
                    "bulan" to bulanAngka.toString(),
                    "tahun" to tahun
                )
            }
        }

        queue.add(request)
    }
}

