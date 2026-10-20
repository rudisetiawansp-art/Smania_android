package id.rudissp.smania.presensi

import android.app.DatePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import id.rudissp.smania.AppConstants
import id.rudissp.smania.R
import id.rudissp.smania.VolleySingleton
import id.rudissp.smania.databinding.FragmentIzinBinding
import id.rudissp.smania.databinding.ItemIzinBinding
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class IzinFragment : Fragment() {

    private lateinit var binding: FragmentIzinBinding
    private lateinit var sharedPref: SharedPreferences
    private lateinit var adapter: IzinAdapter
    private val daftarIzin = mutableListOf<Izin>()

    // Data siswa untuk AutoComplete
    private val daftarSiswa = mutableListOf<Pair<String, String>>() // (nis, nama)
    private var selectedNis: String? = null

    private var kelasPetugas = ""
    private var nisPetugas = ""

    companion object {
        private const val API_CEK_PETUGAS = AppConstants.BASE_URL + "presensi_cek_petugas_izin.php"
        private const val API_GET_IZIN = AppConstants.BASE_URL + "presensi_izin.php"
        private const val API_GET_SISWA = AppConstants.BASE_URL + "get_siswa_by_kelas.php"
        private const val API_SIMPAN_IZIN = AppConstants.BASE_URL + "presensi_simpan_izin.php"
        private const val API_HAPUS_IZIN = AppConstants.BASE_URL + "presensi_hapus_izin.php"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentIzinBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedPref = requireActivity().getSharedPreferences("PREFS", Context.MODE_PRIVATE)
        nisPetugas = sharedPref.getString("nis", "") ?: ""

        // Setup RecyclerView
        adapter = IzinAdapter(daftarIzin) { izin -> hapusIzin(izin) }
        binding.rvIzin.layoutManager = LinearLayoutManager(requireContext())
        binding.rvIzin.adapter = adapter

        // Setup UI
        setupTipeSpinner()
        setupDatePicker()
        setupFilterSpinners()
        setupSiswaAutocomplete()

        // Cek petugas
        cekPetugas()

        // Event listeners
        binding.btnSimpan.setOnClickListener { simpanIzin() }
        binding.btnFilter.setOnClickListener { loadIzin() }
    }

    // --- 1. Cek Petugas ---
    private fun cekPetugas() {
        val queue = VolleySingleton.getInstance(requireContext()).requestQueue
        val request = StringRequest(
            Request.Method.GET, "$API_CEK_PETUGAS?nis=$nisPetugas",
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("is_petugas")) {
                        kelasPetugas = json.getString("kelas")
                        // Update header
                        //binding.tvDaftarIzinHeader.text = "Daftar Izin Kelas $kelasPetugas"
                        // Load data
                        loadSiswa()
                        loadIzin()
                    } else {
                        Toast.makeText(requireContext(), "Anda bukan petugas izin", Toast.LENGTH_SHORT).show()
                        requireActivity().supportFragmentManager.popBackStack()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Gagal cek petugas", Toast.LENGTH_SHORT).show()
                }
            },
            {
                Toast.makeText(requireContext(), "Gagal koneksi: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        )
        queue.add(request)
    }

    // --- 2. Setup AutoComplete Siswa ---
    private fun setupSiswaAutocomplete() {
        val siswaAdapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf())
        binding.actvSiswa.setAdapter(siswaAdapter)

        binding.actvSiswa.setOnItemClickListener { _, _, position, _ ->
            val item = siswaAdapter.getItem(position) ?: return@setOnItemClickListener
            // Format: "Nama (NIS)"
            val nis = item.substringAfter("(").substringBefore(")")
            selectedNis = nis
        }

        // Ketika focus, tampilkan dropdown jika ada data
        binding.actvSiswa.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && daftarSiswa.isNotEmpty()) {
                binding.actvSiswa.showDropDown()
            }
        }

        // Simpan adapter untuk diisi nanti
        this.siswaAdapter = siswaAdapter
    }
    private lateinit var siswaAdapter: ArrayAdapter<String>

    private fun loadSiswa() {
        val queue = VolleySingleton.getInstance(requireContext()).requestQueue
        val request = StringRequest(
            Request.Method.GET, "$API_GET_SISWA?kelas=$kelasPetugas",
            { response ->
                try {
                    val jsonArray = JSONArray(response)
                    daftarSiswa.clear()
                    val namaList = mutableListOf<String>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val nis = obj.getString("nis")
                        val nama = obj.getString("nama")
                        daftarSiswa.add(Pair(nis, nama))
                        namaList.add("$nama ($nis)")
                    }
                    siswaAdapter.clear()
                    siswaAdapter.addAll(namaList)
                    siswaAdapter.notifyDataSetChanged()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Gagal memuat siswa", Toast.LENGTH_SHORT).show()
                }
            },
            {
                Toast.makeText(requireContext(), "Gagal koneksi: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        )
        queue.add(request)
    }

    // --- 3. Setup Tipe Izin (AutoComplete) ---
    private fun setupTipeSpinner() {
        val tipeList = arrayOf("Sakit", "Izin", "Dispen")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, tipeList)
        binding.actvTipe.setAdapter(adapter)
        binding.actvTipe.setText(tipeList[0], false) // default Sakit
    }

    // --- 4. Setup Date Picker ---
    private lateinit var selectedDateStr: String

    private fun setupDatePicker() {
        val currentDate = Calendar.getInstance()
        val dateFormatDisplay = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val dateFormatApi = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val today = Date()
        selectedDateStr = dateFormatApi.format(today)
        binding.btnPilihTanggal.text = "Pilih Tanggal: ${dateFormatDisplay.format(today)}"

        binding.btnPilihTanggal.setOnClickListener {
            val datePicker = DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    val cal = Calendar.getInstance()
                    cal.set(year, month, dayOfMonth)
                    val selected = cal.time
                    selectedDateStr = dateFormatApi.format(selected)
                    binding.btnPilihTanggal.text = "Pilih Tanggal: ${dateFormatDisplay.format(selected)}"
                },
                currentDate.get(Calendar.YEAR),
                currentDate.get(Calendar.MONTH),
                currentDate.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.show()
        }
    }
    private lateinit var selectedDate: Date

    private fun getSelectedDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate)
    }

    // --- 5. Setup Filter Spinners (Bulan & Tahun) ---
    private fun setupFilterSpinners() {
        // Bulan (01-12)
        val bulanList = (1..12).map { String.format("%02d", it) }
        val bulanAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, bulanList)
        binding.spinnerBulan.setAdapter(bulanAdapter)
        val bulanSekarang = SimpleDateFormat("MM", Locale.getDefault()).format(Date())
        binding.spinnerBulan.setText(bulanSekarang, false)

        // Tahun (tahun sekarang - 2 sampai sekarang)
        val tahunSekarang = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date()).toInt()
        val tahunList = (tahunSekarang - 2..tahunSekarang).map { it.toString() }
        val tahunAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, tahunList)
        binding.spinnerTahun.setAdapter(tahunAdapter)
        binding.spinnerTahun.setText(tahunSekarang.toString(), false)
    }

    // --- 6. Load Izin ---
    private fun loadIzin() {
        val bulan = binding.spinnerBulan.text.toString()
        val tahun = binding.spinnerTahun.text.toString()
        val nisFilter = binding.etFilterNis.text.toString().trim()

        if (bulan.isEmpty() || tahun.isEmpty()) {
            Toast.makeText(requireContext(), "Pilih bulan dan tahun", Toast.LENGTH_SHORT).show()
            return
        }

        val url = "$API_GET_IZIN?nis_petugas=$nisPetugas&kelas=$kelasPetugas&bulan=$bulan&tahun=$tahun&nis=$nisFilter"

        val queue = VolleySingleton.getInstance(requireContext()).requestQueue
        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                try {
                    val jsonArray = JSONArray(response)
                    daftarIzin.clear()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val izin = Izin(
                            id = obj.getInt("id"),
                            nis = obj.getString("nis"),
                            nama = obj.getString("nama"),
                            tanggal = obj.getString("tanggal"),
                            tipe = obj.getString("tipe"),
                            keterangan = obj.getString("keterangan"),
                            kelas = obj.getString("kelas")
                        )
                        daftarIzin.add(izin)
                    }
                    adapter.notifyDataSetChanged()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Data tidak valid", Toast.LENGTH_SHORT).show()
                }
            },
            {
                Toast.makeText(requireContext(), "Gagal koneksi: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        )
        queue.add(request)
    }

    // --- 7. Simpan Izin ---
    private fun simpanIzin() {
        if (selectedNis.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Pilih siswa terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        val nisSiswa = selectedNis!!

        val tanggal = selectedDateStr
        if (tanggal.isEmpty()) {
            Toast.makeText(requireContext(), "Pilih tanggal", Toast.LENGTH_SHORT).show()
            return
        }

        val tipeForm = binding.actvTipe.text.toString()
        val tipeMap = mapOf("Sakit" to "S", "Izin" to "I", "Dispen" to "D")
        val tipe = tipeMap[tipeForm] ?: "I"
        val keterangan = binding.etKeterangan.text.toString().trim()

        val queue = VolleySingleton.getInstance(requireContext()).requestQueue
        val request = object : StringRequest(Method.POST, API_SIMPAN_IZIN,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getString("status") == "sukses") {
                        Toast.makeText(requireContext(), json.getString("pesan"), Toast.LENGTH_SHORT).show()
                        loadIzin()
                        // Reset form
                        binding.etKeterangan.setText("")
                        binding.actvSiswa.setText("")
                        selectedNis = null
                        // Reset tanggal ke hari ini
                        val today = Date()
                        selectedDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(today)
                        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                        binding.btnPilihTanggal.text = "Pilih Tanggal: ${dateFormat.format(today)}"
                    } else {
                        Toast.makeText(requireContext(), json.getString("pesan"), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Gagal parse response", Toast.LENGTH_SHORT).show()
                }
            },
            {
                Toast.makeText(requireContext(), "Gagal koneksi: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): Map<String, String> {
                return mapOf(
                    "nis_siswa" to nisSiswa,
                    "tanggal" to tanggal,
                    "tipe" to tipe,
                    "keterangan" to keterangan,
                    "nis_petugas" to nisPetugas,
                    "kelas" to kelasPetugas
                )
            }
        }
        queue.add(request)
    }

    // --- 8. Hapus Izin ---
    private fun hapusIzin(izin: Izin) {
        val url = "$API_HAPUS_IZIN?id=${izin.id}&nis_petugas=$nisPetugas&kelas=$kelasPetugas"
        val queue = VolleySingleton.getInstance(requireContext()).requestQueue
        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getString("status") == "sukses") {
                        Toast.makeText(requireContext(), json.getString("pesan"), Toast.LENGTH_SHORT).show()
                        loadIzin()
                    } else {
                        Toast.makeText(requireContext(), json.getString("pesan"), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Gagal hapus", Toast.LENGTH_SHORT).show()
                }
            },
            {
                Toast.makeText(requireContext(), "Gagal koneksi: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        )
        queue.add(request)
    }
}

data class Izin(
    val id: Int,
    val nis: String,
    val nama: String,
    val tanggal: String, // yyyy-MM-dd
    val tipe: String, // S, I, D
    val keterangan: String,
    val kelas: String
)

class IzinAdapter(
    private val items: List<Izin>,
    private val onDeleteClick: (Izin) -> Unit
) : RecyclerView.Adapter<IzinAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemIzinBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemIzinBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val izin = items[position]
        holder.binding.apply {
            tvNama.text = izin.nama
            tvNis.text = izin.nis
            tvTanggal.text = izin.tanggal

            // Tipe badge
            val tipeText = when (izin.tipe) {
                "S" -> "Sakit"
                "I" -> "Izin"
                "D" -> "Dispen"
                else -> "Lainnya"
            }
            tvTipe.text = tipeText
            val colorRes = when (izin.tipe) {
                "S" -> R.color.md_theme_error
                "I" -> R.color.md_theme_tertiary
                "D" -> R.color.md_theme_primary
                else -> R.color.md_theme_secondary
            }
            tvTipe.setTextColor(ContextCompat.getColor(root.context, colorRes))

            tvKeterangan.text = izin.keterangan.ifEmpty { "-" }

            btnHapus.setOnClickListener {
                onDeleteClick(izin)
            }
        }
    }

    override fun getItemCount() = items.size
}