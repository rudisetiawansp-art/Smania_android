package id.rudissp.smania

import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.Window
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.rudissp.smania.databinding.ActivityProfilBinding
import androidx.core.graphics.drawable.toDrawable
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import androidx.core.content.edit
import com.android.volley.Request
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import id.rudissp.smania.jurusan.JurusanItem
import java.net.URLEncoder

class Profil : AppCompatActivity() {
    private lateinit var binding: ActivityProfilBinding
    private var currentPhotoUri: Uri? = null
    lateinit var prefs: SharedPreferences

    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uriContent = result.uriContent
            currentPhotoUri = uriContent
            Glide.with(this).load(uriContent).into(binding.ivAvatar)


            uploadPhotoToServer(uriContent)
        } else {
            Toast.makeText(this, "Gagal memotong gambar", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfilBinding.inflate(layoutInflater)
        prefs = getSharedPreferences("PREFS", MODE_PRIVATE)
        enableEdgeToEdge()
        setContentView(binding.root)

        val nis = prefs.getString("nis", "").toString()
        setupToolbar()
        loadUserData()
        setupListeners()
        loaddatasisaa(nis)
        loadprofil(nis)
        fetchWalikelas(prefs.getString("kelas", "").toString())

    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed() // Kembali ke Dashboard
        }
    }

    private fun fetchWalikelas(kelas: String) {
        val encodedKelas = URLEncoder.encode(kelas, "UTF-8")
        val url = "https://sman1sukorejo.sch.id/smania/android/api_wakel.php?kelas=$encodedKelas"

        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    val status = response.getString("status")
                    if (status == "success") {
                        val data = response.getJSONObject("data")
                        val walikelas = data.getString("walikelas")
                        prefs.edit { putString("walikelas", walikelas) }
                        binding.tvWaliKelas.text = walikelas
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            { error ->
                error.printStackTrace()
            }
        )

        Volley.newRequestQueue(this).add(request)
    }

    private fun loadUserData() {

        binding.tvNama.text = prefs.getString("namalengkap", "").toString()
        binding.tvNis.text = prefs.getString("nis", "").toString()
        val fotonyaaa = prefs.getString("foto", "")
        val foto = "https://sman1sukorejo.sch.id/smania/fotoprofil/$fotonyaaa"

        if (fotonyaaa == "")
            Glide.with(this)
                .load(R.drawable.icm_person)
                .circleCrop()
                .into(binding.ivAvatar)
        else {
            Glide.with(this)
                .load(foto)
                .circleCrop()
                .into(binding.ivAvatar)
        }
    }

    private fun setupListeners() {
        binding.ivAvatar.setOnClickListener {
            showLightboxDialog()
        }

        // Buka Galeri & Cropper saat tombol kamera ditekan
        binding.fabEditPhoto.setOnClickListener {
            startImageCropper()
        }

        // Update Password
        binding.btnUpdatePass.setOnClickListener {

            val apiUrl = AppConstants.PROFIL_URL
            val nisUser = prefs.getString("nis", "").toString()
            val passLama = binding.etPassLama.text.toString()
            val passBaru = binding.etPassBaru.text.toString()

            if (passLama.isEmpty() || passBaru.isEmpty()) {
                Toast.makeText(this, "Mohon isi semua form kata sandi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val queue = Volley.newRequestQueue(this)

            val stringRequest = object : StringRequest(Method.POST, apiUrl,
                Response.Listener { response ->
                    try {
                        val jsonObject = JSONObject(response)
                        val status = jsonObject.getString("status")
                        val message = jsonObject.getString("message")

                        if (status == "sukses") {
                            Toast.makeText(this@Profil, "Password berhasil diupdate!", Toast.LENGTH_SHORT).show()
                            binding.etPassLama.setText(passBaru)
                            binding.etPassBaru.text?.clear()
                            prefs.edit { putString("pass", passBaru) }
                        } else {
                            Toast.makeText(this@Profil, message, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: JSONException) {
                        Toast.makeText(this@Profil, "Format respon tidak valid", Toast.LENGTH_SHORT).show()
                    }
                },
                Response.ErrorListener { error ->
                    Toast.makeText(this@Profil, "Koneksi Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }) {

                override fun getParams(): MutableMap<String, String> {
                    val params = HashMap<String, String>()
                    params["nis"] = nisUser
                    params["update_pass"] = "1"
                    params["pass_lama"] = passLama
                    params["pass_baru"] = passBaru
                    return params
                }
            }

            queue.add(stringRequest)
        }

        // Dialog Logout
        binding.btnLogout.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun startImageCropper() {
        cropImage.launch(
            CropImageContractOptions(
                uri = null,
                cropImageOptions = CropImageOptions(
                    aspectRatioX = 1,
                    aspectRatioY = 1,
                    fixAspectRatio = true,
                    imageSourceIncludeCamera = true,
                    imageSourceIncludeGallery = true
                )
            )
        )
    }

    private fun showLightboxDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_lightbox)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )

        val ivLightbox = dialog.findViewById<ImageView>(R.id.ivLightbox)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnCloseLightbox)

        // Load gambar ke Lightbox
        if (currentPhotoUri != null) {
            Glide.with(this).load(currentPhotoUri).into(ivLightbox)
        } else {
            Glide.with(this).load(R.drawable.icm_person).into(ivLightbox)
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(
            this,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered
        )
            .setTitle("Konfirmasi Keluar")
            .setMessage("Apakah Anda yakin ingin mengakhiri sesi dan keluar dari aplikasi?")
            .setNegativeButton("Batal") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Ya, Keluar") { dialog, _ ->
                prefs.edit { clear() }
                val intent = Intent(this, LoginActivity::class.java) // Ganti ke Activity Login Anda
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .show()
    }


    private fun uploadPhotoToServer(uri: Uri?) {
        if (uri == null) return

        val apiUrl = AppConstants.PROFIL_URL
        val nisUser = prefs.getString("nis", "").toString()

        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val byteArray = outputStream.toByteArray()

            val base64String = Base64.encodeToString(byteArray, Base64.DEFAULT)
            val finalBase64 = "data:image/jpeg;base64,$base64String"

            val queue = Volley.newRequestQueue(this)

            val stringRequest = object : StringRequest(Method.POST, apiUrl,
                Response.Listener { response ->
                    try {
                        val jsonObject = JSONObject(response)
                        val status = jsonObject.getString("status")
                        val message = jsonObject.getString("message")

                        if (status == "sukses") {
                            prefs.edit { putString("foto", "$nisUser.jpg") }
                            Toast.makeText(this@Profil, "Foto Profil Berhasil Diperbarui!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@Profil, message, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: JSONException) {
                        Toast.makeText(this@Profil, "Format respon tidak valid", Toast.LENGTH_SHORT).show()
                    }
                },
                Response.ErrorListener { error ->
                    Toast.makeText(this@Profil, "Koneksi Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }) {

                override fun getParams(): MutableMap<String, String> {
                    val params = HashMap<String, String>()
                    params["nis"] = nisUser
                    params["foto_base64"] = finalBase64
                    return params
                }
            }

            queue.add(stringRequest)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loaddatasisaa(nis: String){

        val url = AppConstants.BASE_URL + "api_profil_siswa.php?nis=$nis"
        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    val status = response.getString("status")
                    if (status == "success") {
                        val data = response.getJSONObject("data")
                        val kelas = data.getString("kelas")
                        val nisn = data.getString("nisn")
                        val guruwali = data.getString("guruwali")
                        val passlama =  data.getString("pass")

                        binding.etPassLama.setText(passlama)
                        binding.tvNama.text = prefs.getString("namalengkap", "").toString() + " (" + kelas + ")"
                        binding.tvNisn.text = nisn
                        binding.tvNis.text = prefs.getString("nis", "").toString()
                        binding.tvWaliKelas.text = prefs.getString("walikelas", "")
                        binding.tvGuruWali.text = guruwali


                    }
                } catch (e: Exception) {
                    e.printStackTrace()

                }
            },
            { error ->
                error.printStackTrace()

            }
        )

        Volley.newRequestQueue(this).add(request)

    }

    // ============================================
    // GET: Load data rencana studi berdasarkan NIS
    // ============================================
    private fun loadprofil(nis: String) {
        val url = AppConstants.BASE_URL + "api_siswa.php?nis=$nis"
        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                val status = response.optString("status")
                if (status == "success") {
                    val dataObj = response.optJSONObject("data")
                    if (dataObj != null) {
                        val siswa = parseSiswaFromJson(dataObj)
                        fillFormWithSiswa(siswa)

                    } else {

                        Toast.makeText(this, "Data baru, silakan isi", Toast.LENGTH_SHORT).show()
                    }
                } else {

                    val msg = response.optString("message", "Data perwalian belum ada, silahkah hubungi guru wali")
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Toast.makeText(this, "Gagal memuat data: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        )
        Volley.newRequestQueue(this).add(request)
    }

    private fun fillFormWithSiswa(siswa: datasiswa) {
        binding.tvRencanaPascaStudi.text = siswa.rencanaPascaStudi
        binding.tvJurusan1.text = siswa.jurusanImpian1
        binding.tvJurusan2.text = siswa.jurusanImpian2
        binding.tvKampus1.text = siswa.kampusImpian1
        binding.tvKampus2.text = siswa.kampusImpian2
        binding.tvBidangKarier.text = siswa.bidangKarier
        binding.tvMapelFavorit.text = siswa.mapelFavorit
        binding.tvSkill.text = siswa.skillDimiliki + " / " + siswa.skillDipelajari
        binding.tvPengalaman.text = siswa.pengalaman
        binding.tvProgram.text = siswa.programSekolah
        binding.tvinput.text = siswa.editedBy
        binding.tvupdate.text = siswa.editTime
    }

    // ============================================
    // Parse JSONObject -> datasiswa
    // ============================================
    private fun parseSiswaFromJson(obj: JSONObject): datasiswa {
        return datasiswa(
            nis = obj.optString("nis", ""),
            rencanaPascaStudi = obj.optString("rencana_pasca_studi", ""),
            jurusanImpian1 = obj.optString("jurusan_impian_1", ""),
            jurusanImpian2 = obj.optString("jurusan_impian_2", ""),
            kampusImpian1 = obj.optString("kampus_impian_1", ""),
            kampusImpian2 = obj.optString("kampus_impian_2", ""),
            bidangKarier = obj.optString("bidang_karier", ""),
            mapelFavorit = obj.optString("mapel_favorit", ""),
            skillDimiliki = obj.optString("skill_dimiliki", ""),
            skillDipelajari = obj.optString("skill_dipelajari", ""),
            pengalaman = obj.optString("pengalaman", ""),
            programSekolah = obj.optString("program_sekolah", ""),
            editedBy = obj.optString("edited_by", ""),
            editTime = obj.optString("edit_time", "")
        )
    }

}
data class datasiswa(
    var nis: String,
    var rencanaPascaStudi: String? = null,
    var jurusanImpian1: String? = null,
    var jurusanImpian2: String? = null,
    var kampusImpian1: String? = null,
    var kampusImpian2: String? = null,
    var bidangKarier: String? = null,
    var mapelFavorit: String? = null,
    var skillDimiliki: String? = null,
    var skillDipelajari: String? = null,
    var pengalaman: String? = null,
    var programSekolah: String? = null,
    var editedBy: String? = null,
    var editTime: String? = null
)