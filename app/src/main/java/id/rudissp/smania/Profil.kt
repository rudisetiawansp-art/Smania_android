package id.rudissp.smania

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import coil.compose.AsyncImage
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder

class Profil : AppCompatActivity() {
    lateinit var prefs: SharedPreferences
    private var currentPhotoUri: Uri? = null

    var nama by mutableStateOf("")
    var nis by mutableStateOf("")
    var nisn by mutableStateOf("")
    var kelas by mutableStateOf("")
    var waliKelas by mutableStateOf("")
    var guruWali by mutableStateOf("")
    var fotoUrl by mutableStateOf("")
    var passLama by mutableStateOf("")
    var passBaru by mutableStateOf("")
    var showLightbox by mutableStateOf(false)

    var siswaData by mutableStateOf(Datasiswa(nis = ""))

    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uriContent = result.uriContent
            currentPhotoUri = uriContent
            uploadPhotoToServer(uriContent)
        } else {
            Toast.makeText(this, "Gagal memotong gambar", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = getSharedPreferences("PREFS", MODE_PRIVATE)

        val nisVal = prefs.getString("nis", "").toString()
        nis = nisVal

        setContent {
            AppTheme {
                ProfilScreen(
                    nama = nama,
                    nis = nis,
                    nisn = nisn,
                    kelas = kelas,
                    waliKelas = waliKelas,
                    guruWali = guruWali,
                    fotoUrl = fotoUrl,
                    currentPhotoUri = currentPhotoUri,
                    passLama = passLama,
                    passBaru = passBaru,
                    siswaData = siswaData,
                    showLightbox = showLightbox,
                    onPassLamaChange = { passLama = it },
                    onPassBaruChange = { passBaru = it },
                    onUpdatePass = { updatePassword() },
                    onAvatarClick = { showLightbox = true },
                    onDismissLightbox = { showLightbox = false },
                    onEditPhotoClick = { startImageCropper() },
                    onLogoutClick = { showLogoutDialog() },
                    onBack = { finish() }
                )
            }
        }

        loadUserData()
        loaddatasisaa(nisVal)
        loadprofil(nisVal)
        fetchWalikelas(prefs.getString("kelas", "").toString())
    }

    private fun loadUserData() {
        nama = prefs.getString("namalengkap", "").toString()
        nis = prefs.getString("nis", "").toString()
        val fotonyaaa = prefs.getString("foto", "")
        fotoUrl = if (fotonyaaa.isNullOrEmpty()) "" else "https://sman1sukorejo.sch.id/smania/fotoprofil/$fotonyaaa"
    }

    private fun fetchWalikelas(kelasStr: String) {
        val encodedKelas = URLEncoder.encode(kelasStr, "UTF-8")
        val url = "https://sman1sukorejo.sch.id/smania/android/api_wakel.php?kelas=$encodedKelas"

        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    val status = response.getString("status")
                    if (status == "success") {
                        val data = response.getJSONObject("data")
                        val wakel = data.getString("walikelas")
                        prefs.edit { putString("walikelas", wakel) }
                        waliKelas = wakel
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            { error -> error.printStackTrace() }
        )
        Volley.newRequestQueue(this).add(request)
    }

    private fun updatePassword() {
        if (passLama.isEmpty() || passBaru.isEmpty()) {
            Toast.makeText(this, "Mohon lengkapi formulir keamanan", Toast.LENGTH_SHORT).show()
            return
        }

        val apiUrl = AppConstants.PROFIL_URL
        val queue = Volley.newRequestQueue(this)

        val stringRequest = object : StringRequest(Method.POST, apiUrl,
            Response.Listener { response ->
                try {
                    val jsonObject = JSONObject(response)
                    val status = jsonObject.getString("status")
                    val message = jsonObject.getString("message")

                    if (status == "sukses") {
                        Toast.makeText(this, "Keamanan diperbarui!", Toast.LENGTH_SHORT).show()
                        passLama = passBaru
                        passBaru = ""
                        prefs.edit { putString("pass", passLama) }
                    } else {
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                } catch (_: JSONException) {
                    Toast.makeText(this, "Format respon tidak valid", Toast.LENGTH_SHORT).show()
                }
            },
            Response.ErrorListener { error ->
                Toast.makeText(this, "Koneksi Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["nis"] = nis
                params["update_pass"] = "1"
                params["pass_lama"] = passLama
                params["pass_baru"] = passBaru
                return params
            }
        }
        queue.add(stringRequest)
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

    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(
            this,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered
        )
            .setTitle("Konfirmasi")
            .setMessage("Akhiri sesi dan keluar dari aplikasi?")
            .setNegativeButton("Batal") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("Keluar") { _, _ ->
                prefs.edit { clear() }
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .show()
    }

    private fun uploadPhotoToServer(uri: Uri?) {
        if (uri == null) return

        val apiUrl = AppConstants.PROFIL_URL
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
                            prefs.edit { putString("foto", "$nis.jpg") }
                            fotoUrl = "https://sman1sukorejo.sch.id/smania/fotoprofil/$nis.jpg"
                            Toast.makeText(this, "Foto berhasil diperbarui", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: JSONException) {
                        Toast.makeText(this, "Format respon tidak valid", Toast.LENGTH_SHORT).show()
                    }
                },
                Response.ErrorListener { error ->
                    Toast.makeText(this, "Koneksi Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }) {
                override fun getParams(): MutableMap<String, String> {
                    val params = HashMap<String, String>()
                    params["nis"] = nis
                    params["foto_base64"] = finalBase64
                    return params
                }
            }
            queue.add(stringRequest)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loaddatasisaa(nisVal: String) {
        val url = AppConstants.BASE_URL + "api_profil_siswa.php?nis=$nisVal"
        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    val status = response.getString("status")
                    if (status == "success") {
                        val data = response.getJSONObject("data")
                        kelas = data.getString("kelas")
                        nisn = data.getString("nisn")
                        guruWali = data.getString("guruwali")
                        passLama = data.getString("pass")
                        waliKelas = prefs.getString("walikelas", "") ?: ""
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            { error -> error.printStackTrace() }
        )
        Volley.newRequestQueue(this).add(request)
    }

    private fun loadprofil(nisVal: String) {
        val url = AppConstants.BASE_URL + "api_siswa.php?nis=$nisVal"
        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                val status = response.optString("status")
                if (status == "success") {
                    val dataObj = response.optJSONObject("data")
                    if (dataObj != null) {
                        siswaData = parseSiswaFromJson(dataObj)
                    } else {
                        Toast.makeText(this, "Data baru, silakan isi", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val msg = response.optString("message", "Data perwalian belum ada")
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Toast.makeText(this, "Gagal memuat data: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        )
        Volley.newRequestQueue(this).add(request)
    }

    private fun parseSiswaFromJson(obj: JSONObject): Datasiswa {
        return Datasiswa(
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

data class Datasiswa(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilScreen(
    nama: String,
    nis: String,
    nisn: String,
    kelas: String,
    waliKelas: String,
    guruWali: String,
    fotoUrl: String,
    currentPhotoUri: Uri?,
    passLama: String,
    passBaru: String,
    siswaData: Datasiswa,
    showLightbox: Boolean,
    onPassLamaChange: (String) -> Unit,
    onPassBaruChange: (String) -> Unit,
    onUpdatePass: () -> Unit,
    onAvatarClick: () -> Unit,
    onDismissLightbox: () -> Unit,
    onEditPhotoClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onBack: () -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    if (showLightbox) {
        LightboxDialog(
            currentPhotoUri = currentPhotoUri,
            fotoUrl = fotoUrl,
            onDismiss = onDismissLightbox
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Profil",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 1. Hero Profile Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val avatarInteractionSource = remember { MutableInteractionSource() }
                        val isAvatarPressed by avatarInteractionSource.collectIsPressedAsState()
                        val avatarScale by animateFloatAsState(
                            targetValue = if (isAvatarPressed) 0.9f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                        )

                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .scale(avatarScale)
                                .clip(CircleShape)
                                .border(4.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f), CircleShape)
                                .clickable(
                                    interactionSource = avatarInteractionSource,
                                    indication = null,
                                    onClick = onAvatarClick
                                )
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                                },
                                update = { imageView ->
                                    val context = imageView.context
                                    if (currentPhotoUri != null) {
                                        Glide.with(context).load(currentPhotoUri).circleCrop().into(imageView)
                                    } else if (fotoUrl.isNotEmpty()) {
                                        Glide.with(context).load(fotoUrl).circleCrop().into(imageView)
                                    } else {
                                        Glide.with(context).load(R.drawable.icm_person).circleCrop().into(imageView)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = nama.ifEmpty { "Memuat Profil..." },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${kelas.ifEmpty { "-" }} • $nis",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = onEditPhotoClick,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(imageVector = Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ganti Foto", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 2. Info Akademik
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Akademik",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        ProfilInfoRow(label = "NISN", value = nisn.ifEmpty { "-" })
                        ProfilInfoRow(label = "Wali Kelas", value = waliKelas.ifEmpty { "-" })
                        ProfilInfoRow(label = "Guru BK / Wali", value = guruWali.ifEmpty { "-" })
                    }
                }

                // 3. Karier
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Karier",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        ProfilInfoRow(label = "Pasca Studi", value = siswaData.rencanaPascaStudi ?: "-")
                        ProfilInfoRow(label = "Pilihan Jurusan 1", value = siswaData.jurusanImpian1 ?: "-")
                        ProfilInfoRow(label = "Pilihan Jurusan 2", value = siswaData.jurusanImpian2 ?: "-")
                        ProfilInfoRow(label = "Target Kampus 1", value = siswaData.kampusImpian1 ?: "-")
                        ProfilInfoRow(label = "Target Kampus 2", value = siswaData.kampusImpian2 ?: "-")
                        ProfilInfoRow(label = "Bidang Karier", value = siswaData.bidangKarier ?: "-")
                        ProfilInfoRow(label = "Mata Pelajaran", value = siswaData.mapelFavorit ?: "-")
                        ProfilInfoRow(label = "Keterampilan", value = "${siswaData.skillDimiliki ?: "-"} / ${siswaData.skillDipelajari ?: "-"}")
                    }
                }

                // 4. Keamanan
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Keamanan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        OutlinedTextField(
                            value = passLama,
                            onValueChange = onPassLamaChange,
                            label = { Text("Sandi Lama") },
                            shape = RoundedCornerShape(20.dp),
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(imageVector = image, contentDescription = "Tampilkan Sandi")
                                }
                            }
                        )

                        OutlinedTextField(
                            value = passBaru,
                            onValueChange = onPassBaruChange,
                            label = { Text("Sandi Baru") },
                            shape = RoundedCornerShape(20.dp),
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(imageVector = image, contentDescription = "Tampilkan Sandi")
                                }
                            }
                        )

                        val btnSaveInteractionSource = remember { MutableInteractionSource() }
                        val isBtnSavePressed by btnSaveInteractionSource.collectIsPressedAsState()
                        val btnSaveScale by animateFloatAsState(
                            targetValue = if (isBtnSavePressed) 0.96f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                        )

                        Button(
                            onClick = onUpdatePass,
                            interactionSource = btnSaveInteractionSource,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .scale(btnSaveScale),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Simpan Perubahan", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 5. Tombol Keluar
                val btnLogoutInteractionSource = remember { MutableInteractionSource() }
                val isBtnLogoutPressed by btnLogoutInteractionSource.collectIsPressedAsState()
                val btnLogoutScale by animateFloatAsState(
                    targetValue = if (isBtnLogoutPressed) 0.96f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                )

                Button(
                    onClick = onLogoutClick,
                    interactionSource = btnLogoutInteractionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .scale(btnLogoutScale),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Keluar", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun LightboxDialog(
    currentPhotoUri: Uri?,
    fotoUrl: String,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            val model = currentPhotoUri ?: fotoUrl.ifEmpty { R.drawable.icm_person }
            AsyncImage(
                model = model,
                contentDescription = "Foto Profil Diperbesar",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentScale = ContentScale.Fit
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Tutup",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun ProfilInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.5f)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProfilScreenPreview() {
    AppTheme {
        ProfilScreen(
            nama = "Rudi Setiawan",
            nis = "12345",
            nisn = "0051234567",
            kelas = "XII IPA 1",
            waliKelas = "Drs. H. Ahmad",
            guruWali = "Bu Siti, S.Pd",
            fotoUrl = "",
            currentPhotoUri = null,
            passLama = "******",
            passBaru = "",
            siswaData = Datasiswa(
                nis = "12345",
                rencanaPascaStudi = "Kuliah di PTN",
                jurusanImpian1 = "Teknik Informatika",
                jurusanImpian2 = "Sistem Informasi",
                kampusImpian1 = "Universitas Indonesia",
                kampusImpian2 = "Universitas Gadjah Mada",
                bidangKarier = "Software Engineer",
                mapelFavorit = "Matematika",
                skillDimiliki = "Kotlin, Java",
                skillDipelajari = "Jetpack Compose"
            ),
            showLightbox = false,
            onPassLamaChange = {},
            onPassBaruChange = {},
            onUpdatePass = {},
            onAvatarClick = {},
            onDismissLightbox = {},
            onEditPhotoClick = {},
            onLogoutClick = {},
            onBack = {}
        )
    }
}