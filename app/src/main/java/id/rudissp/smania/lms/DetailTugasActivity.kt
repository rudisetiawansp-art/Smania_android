package id.rudissp.smania.lms

import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import id.rudissp.smania.AppConstants
import id.rudissp.smania.databinding.ActivityDetailTugasBinding
import androidx.core.net.toUri
import com.android.volley.NetworkResponse
import id.rudissp.smania.VolleyMultipartRequest

class DetailTugasActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetailTugasBinding
    private lateinit var prefs: SharedPreferences

    private var idTugas = ""
    private var nisSiswa = ""
    private var kelasSiswa = ""

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            prosesUploadFile(uri)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("PREFS", MODE_PRIVATE)
        binding = ActivityDetailTugasBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        idTugas = intent.getStringExtra("ID_TUGAS") ?: ""

        if (idTugas.isEmpty()) {
            Toast.makeText(this, "ID Tugas tidak valid", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.toolbarDetaillms.setOnClickListener { finish() }


        loadDetailData()

        Toast.makeText(this, "Tekan dan tahan pesan untuk menghapus pesan", Toast.LENGTH_SHORT).show()

        binding.btnKirim.setOnClickListener {
            val pesanText = binding.etPesan.text.toString().trim()

            if (pesanText.isNotEmpty()) {
                binding.btnKirim.isEnabled = false
                kirimPesan(pesanText)
            }
        }
    }

    private fun loadDetailData() {
        binding.progressBar.visibility = View.VISIBLE

        nisSiswa = prefs.getString("nis","").toString()
        kelasSiswa = prefs.getString("kelas", "").toString()

        val url = AppConstants.LMS_DETAIL_URL

        val stringRequest = object : StringRequest(
            Method.POST, url,
            { response ->
                binding.progressBar.visibility = View.GONE
                try {
                    val detailResponse = Gson().fromJson(response, DetailResponse::class.java)
                    if (detailResponse.status == "success" && detailResponse.tugas != null) {
                        setupUI(detailResponse)
                    } else {
                        Toast.makeText(this, "Gagal memuat data", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error parsing: ${e.message}", Toast.LENGTH_LONG).show()
                }
            },
            { error ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Koneksi Gagal", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["nis"] = nisSiswa
                params["kelas"] = kelasSiswa
                params["id_tugas"] = idTugas
                return params
            }
        }

        Volley.newRequestQueue(this).add(stringRequest)
    }

    private fun setupUI(data: DetailResponse) {
        val tugas = data.tugas!!
        val pengumpulan = data.pengumpulan

        // 1. Set Info Dasar
        binding.toolbarDetaillms.title = "Detail ${tugas.jenis}"
        binding.tvMapel.text = tugas.mapel
        binding.tvJudul.text = tugas.judul
        binding.tvGuru.text = tugas.namaGuru
        binding.tvDeskripsi.text = tugas.deskripsi

        // Logika Lampiran Tugas
        if (!tugas.fileLampiran.isNullOrEmpty()) {
            binding.cvLampiran.visibility = View.VISIBLE
            binding.tvNamaLampiran.text = tugas.fileLampiran

            // Logika Lampiran Tugas
            if (!tugas.fileLampiran.isNullOrEmpty()) {
                binding.cvLampiran.visibility = View.VISIBLE
                binding.tvNamaLampiran.text = tugas.fileLampiran


                binding.cvLampiran.setOnClickListener {

                    val baseUrl = "https://sman1sukorejo.sch.id/esmansa/lms/"
                    val fileUrl = baseUrl + tugas.fileLampiran

                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                        intent.data = fileUrl.toUri()
                        startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(
                            this@DetailTugasActivity,
                            "Gagal membuka tautan lampiran",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                binding.cvLampiran.visibility = View.GONE
            }
        } else {
            binding.cvLampiran.visibility = View.GONE
        }

        val isMateri = tugas.jenis.equals("materi", ignoreCase = true)
        val isOnline = tugas.isOnline == 1


        if (isMateri) {
            binding.layoutStatus.visibility = View.GONE
        } else {
            if (isOnline) {
                binding.layoutStatus.visibility = View.VISIBLE
            } else {
                binding.layoutStatus.visibility = View.GONE
            }

            if (pengumpulan != null) {
                // KONDISI 1: SUDAH MENGUMPULKAN
                binding.tvStatusMessage.text = "Tugas Diserahkan pada ${pengumpulan.waktuKumpul}"

                // Atur visibilitas tombol
                binding.btnUpload.visibility = View.GONE
                binding.btnLihatFile.visibility = View.VISIBLE

                if (pengumpulan.komentar_guru.isNotEmpty()) {
                    binding.cvfeedback.visibility = View.VISIBLE
                    binding.txfeedback.text = pengumpulan.komentar_guru
                }else{
                    binding.cvfeedback.visibility = View.GONE
                }

                binding.btnLihatFile.setOnClickListener {
                    val baseUrlTugas = "https://sman1sukorejo.sch.id/smania/lms/tugasterkumpul/"
                    val fileUrl = baseUrlTugas + pengumpulan.fileJawaban
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                        intent.data = fileUrl.toUri()
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Tidak ada aplikasi untuk membuka file ini", Toast.LENGTH_SHORT).show()
                    }
                }

                // Cek apakah sudah dinilai
                if (pengumpulan.nilai > 0) {
                    binding.tvNilai.visibility = View.VISIBLE
                    binding.tvNilai.text = "Nilai Akhir: ${pengumpulan.nilai}"
                    binding.btnGantiFile.visibility = View.GONE
                } else {
                    binding.tvNilai.visibility = View.GONE
                    binding.btnGantiFile.visibility = View.VISIBLE

                    binding.btnGantiFile.setOnClickListener {
                        filePickerLauncher.launch("*/*")
                    }
                }

            } else {
                binding.tvStatusMessage.text = "Tenggat: ${tugas.deadline}"

                binding.btnUpload.visibility = View.VISIBLE
                binding.btnLihatFile.visibility = View.GONE
                binding.btnGantiFile.visibility = View.GONE
                binding.tvNilai.visibility = View.GONE

                // Aksi Upload File Baru
                binding.btnUpload.setOnClickListener {
                    filePickerLauncher.launch("*/*")
                }
            }
        }

        // 3. Setup RecyclerView Diskusi (Jika ada)
        val listDiskusi = data.diskusi

        if (!listDiskusi.isNullOrEmpty()) {
            binding.rvDiskusi.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
            nisSiswa = prefs.getString("nis","").toString()
            val diskusiAdapter = LMSDiskusiAdapter(listDiskusi, nisSiswa){ idDiskusi ->
                tampilkanDialogHapusChat(idDiskusi)
            }
            binding.rvDiskusi.adapter = diskusiAdapter

            binding.rvDiskusi.scrollToPosition(listDiskusi.size - 1)
        }
    }

    private fun kirimPesan(pesan: String) {
        val url = AppConstants.LMS_SUBMIT_DISKUSI

        val stringRequest = object : StringRequest(
            Method.POST, url,
            { response ->
                binding.btnKirim.isEnabled = true

                try {

                    val jsonObject = org.json.JSONObject(response)
                    val status = jsonObject.getString("status")

                    if (status == "success") {
                        // Kosongkan kolom input
                        binding.etPesan.text.clear()

                        // Muat ulang data agar pesan baru muncul di list chat
                        loadDetailData()
                    } else {
                        val errorMsg = jsonObject.getString("pesan")
                        Toast.makeText(this, "Gagal: $errorMsg", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error memproses balasan server", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                binding.btnKirim.isEnabled = true
                Toast.makeText(this, "Koneksi Gagal: Cek internet Anda", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["nis"] = nisSiswa
                params["kelas"] = kelasSiswa
                params["id_tugas"] = idTugas
                params["pesan"] = pesan
                return params
            }
        }

        Volley.newRequestQueue(this).add(stringRequest)
    }

    private fun prosesUploadFile(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE

        var namaFileAsli = "tugas_file"
        var mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) namaFileAsli = cursor.getString(nameIndex)
            }
        }

        val fileBytes: ByteArray = try {
            val inputStream = contentResolver.openInputStream(uri)
            inputStream?.readBytes() ?: ByteArray(0)
        } catch (e: Exception) {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, "Gagal membaca file dari HP", Toast.LENGTH_SHORT).show()
            return
        }

        val url = AppConstants.LMS_SUBMIT_FILE

        val multipartRequest = object : VolleyMultipartRequest(
            Method.POST, url,
            { response: NetworkResponse ->
                binding.progressBar.visibility = View.GONE
                try {
                    val jsonString = String(response.data)
                    val jsonObject = org.json.JSONObject(jsonString)

                    if (jsonObject.getString("status") == "success") {
                        Toast.makeText(this, jsonObject.getString("pesan"), Toast.LENGTH_SHORT).show()
                        loadDetailData()
                    } else {
                        Toast.makeText(this, jsonObject.getString("pesan"), Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error membaca respon server", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Upload gagal: Periksa koneksi", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["nis"] = nisSiswa
                params["id_tugas"] = idTugas
                return params
            }


            override fun getByteData(): Map<String, DataPart> {
                val params = HashMap<String, DataPart>()

                params["file_jawaban"] = DataPart(namaFileAsli, fileBytes, mimeType)
                return params
            }
        }


        multipartRequest.retryPolicy = com.android.volley.DefaultRetryPolicy(
            60000, // 60 Detik
            0,
            com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        Volley.newRequestQueue(this).add(multipartRequest)
    }

    private fun tampilkanDialogHapusChat(idDiskusi: Int) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Hapus Pesan")
        builder.setMessage("Apakah Anda yakin ingin menghapus pesan ini?")

        builder.setPositiveButton("Hapus") { dialog, _ ->
            hapusPesan(idDiskusi)
            dialog.dismiss()
        }

        builder.setNegativeButton("Batal") { dialog, _ ->
            dialog.dismiss()
        }

        builder.create().show()
    }

    private fun hapusPesan(idDiskusi: Int) {
        val url = AppConstants.LMS_HAPUS_DISKUSI

        val stringRequest = object : StringRequest(
            Method.POST, url,
            { response ->
                try {
                    val jsonObject = org.json.JSONObject(response)
                    val status = jsonObject.getString("status")

                    if (status == "success") {
                        // Muat ulang detail tugas untuk menghilangkan pesan dari layar
                        loadDetailData()
                        Toast.makeText(this, "Pesan dihapus", Toast.LENGTH_SHORT).show()
                    } else {
                        val errorMsg = jsonObject.getString("pesan")
                        Toast.makeText(this, "Gagal: $errorMsg", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error memproses balasan server", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Toast.makeText(this, "Koneksi Gagal: Cek internet Anda", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["nis"] = nisSiswa
                params["id_diskusi"] = idDiskusi.toString()
                return params
            }
        }

        Volley.newRequestQueue(this).add(stringRequest)
    }
}