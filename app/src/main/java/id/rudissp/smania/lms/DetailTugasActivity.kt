package id.rudissp.smania.lms

import id.rudissp.smania.LoadingIndicator
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.android.volley.NetworkResponse
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import id.rudissp.smania.AppConstants
import id.rudissp.smania.AppTheme
import id.rudissp.smania.VolleyMultipartRequest
import id.rudissp.smania.VolleySingleton
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class DetailTugasActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences

    private var idTugas = ""
    private var nisSiswa = ""
    private var kelasSiswa = ""

    var detailResponse by mutableStateOf<DetailResponse?>(null)
    var isLoading by mutableStateOf(false)
    var pesanInput by mutableStateOf("")
    var showDeleteDialogForId by mutableStateOf<Int?>(null)

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            prosesUploadFile(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("PREFS", MODE_PRIVATE)
        enableEdgeToEdge()

        idTugas = intent.getStringExtra("ID_TUGAS") ?: ""
        nisSiswa = prefs.getString("nis", "").toString()
        kelasSiswa = prefs.getString("kelas", "").toString()

        if (idTugas.isEmpty()) {
            Toast.makeText(this, "ID Tugas tidak valid", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            AppTheme {
                DetailTugasScreen(
                    detailResponse = detailResponse,
                    isLoading = isLoading,
                    pesanInput = pesanInput,
                    nisSaya = nisSiswa,
                    onPesanChanged = { pesanInput = it },
                    onKirimPesan = {
                        if (pesanInput.trim().isNotEmpty()) {
                            kirimPesan(pesanInput.trim())
                        }
                    },
                    onUploadClick = {
                        filePickerLauncher.launch("*/*")
                    },
                    onGantiFileClick = {
                        filePickerLauncher.launch("*/*")
                    },
                    onLihatFileTugasClick = { fileName ->
                        val baseUrl = "https://sman1sukorejo.sch.id/esmansa/lms/"
                        openUrl(baseUrl + fileName)
                    },
                    onLihatFileJawabanClick = { fileJawaban ->
                        val baseUrlTugas = "https://sman1sukorejo.sch.id/smania/lms/tugasterkumpul/"
                        openUrl(baseUrlTugas + fileJawaban)
                    },
                    onDeleteChat = { idDiskusi ->
                        showDeleteDialogForId = idDiskusi
                    },
                    onBack = { finish() }
                )

                if (showDeleteDialogForId != null) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialogForId = null },
                        title = { Text("Hapus Pesan") },
                        text = { Text("Apakah Anda yakin ingin menghapus pesan ini?") },
                        confirmButton = {
                            TextButton(onClick = {
                                val id = showDeleteDialogForId!!
                                showDeleteDialogForId = null
                                hapusPesan(id)
                            }) {
                                Text("Hapus", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteDialogForId = null }) {
                                Text("Batal")
                            }
                        }
                    )
                }
            }
        }

        loadDetailData()
        Toast.makeText(this, "Tekan dan tahan pesan Anda untuk menghapus", Toast.LENGTH_LONG).show()
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membuka tautan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadDetailData() {
        isLoading = true
        val url = AppConstants.LMS_DETAIL_URL

        val stringRequest = object : StringRequest(
            Method.POST, url,
            { response ->
                isLoading = false
                try {
                    val parsed = Gson().fromJson(response, DetailResponse::class.java)
                    if (parsed.status == "success" && parsed.tugas != null) {
                        detailResponse = parsed
                    } else {
                        Toast.makeText(this, "Gagal memuat data", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error parsing: ${e.message}", Toast.LENGTH_LONG).show()
                }
            },
            {
                isLoading = false
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

        VolleySingleton.getInstance(this).addToRequestQueue(stringRequest)
    }

    private fun kirimPesan(pesan: String) {
        val url = AppConstants.LMS_SUBMIT_DISKUSI

        val stringRequest = object : StringRequest(
            Method.POST, url,
            { response ->
                try {
                    val jsonObject = JSONObject(response)
                    if (jsonObject.getString("status") == "success") {
                        pesanInput = ""
                        loadDetailData()
                    } else {
                        val errorMsg = jsonObject.optString("pesan", "Gagal mengirim pesan")
                        Toast.makeText(this, "Gagal: $errorMsg", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error memproses balasan server", Toast.LENGTH_SHORT).show()
                }
            },
            {
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

        VolleySingleton.getInstance(this).addToRequestQueue(stringRequest)
    }

    private fun prosesUploadFile(uri: Uri) {
        isLoading = true
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
            isLoading = false
            Toast.makeText(this, "Gagal membaca file dari HP", Toast.LENGTH_SHORT).show()
            return
        }

        val url = AppConstants.LMS_SUBMIT_FILE

        val multipartRequest = object : VolleyMultipartRequest(
            Method.POST, url,
            { response: NetworkResponse ->
                isLoading = false
                try {
                    val jsonString = String(response.data)
                    val jsonObject = JSONObject(jsonString)
                    if (jsonObject.getString("status") == "success") {
                        Toast.makeText(this, jsonObject.optString("pesan", "Berhasil upload"), Toast.LENGTH_SHORT).show()
                        loadDetailData()
                    } else {
                        Toast.makeText(this, jsonObject.optString("pesan", "Gagal upload"), Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error membaca respon server", Toast.LENGTH_SHORT).show()
                }
            },
            {
                isLoading = false
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
            60000,
            0,
            com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        VolleySingleton.getInstance(this).addToRequestQueue(multipartRequest)
    }

    private fun hapusPesan(idDiskusi: Int) {
        val url = AppConstants.LMS_HAPUS_DISKUSI

        val stringRequest = object : StringRequest(
            Method.POST, url,
            { response ->
                try {
                    val jsonObject = JSONObject(response)
                    if (jsonObject.getString("status") == "success") {
                        loadDetailData()
                        Toast.makeText(this, "Pesan dihapus", Toast.LENGTH_SHORT).show()
                    } else {
                        val errorMsg = jsonObject.optString("pesan", "Gagal menghapus")
                        Toast.makeText(this, "Gagal: $errorMsg", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error memproses balasan server", Toast.LENGTH_SHORT).show()
                }
            },
            {
                Toast.makeText(this, "Koneksi Gagal", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["nis"] = nisSiswa
                params["id_diskusi"] = idDiskusi.toString()
                return params
            }
        }

        VolleySingleton.getInstance(this).addToRequestQueue(stringRequest)
    }
}

private fun formatChatTime(createdAt: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val date = inputFormat.parse(createdAt)
        if (date != null) outputFormat.format(date) else createdAt
    } catch (_: Exception) {
        createdAt
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailTugasScreen(
    detailResponse: DetailResponse?,
    isLoading: Boolean,
    pesanInput: String,
    nisSaya: String,
    onPesanChanged: (String) -> Unit,
    onKirimPesan: () -> Unit,
    onUploadClick: () -> Unit,
    onGantiFileClick: () -> Unit,
    onLihatFileTugasClick: (String) -> Unit,
    onLihatFileJawabanClick: (String) -> Unit,
    onDeleteChat: (Int) -> Unit,
    onBack: () -> Unit
) {
    val tugas = detailResponse?.tugas
    val pengumpulan = detailResponse?.pengumpulan
    val diskusiList = detailResponse?.diskusi ?: emptyList()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentWindowInsets = WindowInsets.ime, // Memasukkan insets IME agar terdorong keyboard
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (tugas != null) "Detail ${tugas.jenis}" else "Detail Tugas",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
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
        },
        bottomBar = {
            // Chat Input Bar (Menerapkan Navigation Bars Insets)
            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars) // MENCEGAH TERTUTUP NAVBAR
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = pesanInput,
                        onValueChange = onPesanChanged,
                        placeholder = { Text("Ketik pesan diskusi...") },
                        modifier = Modifier.weight(1f),
                        shape = CircleShape, // Shape membulat Expressive
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        ),
                        maxLines = 3
                    )

                    FilledIconButton(
                        onClick = onKirimPesan,
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Kirim Pesan",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (tugas == null) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Data tugas tidak tersedia.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
                ) {
                    // 1. Assignment Info Expressive Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(32.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Mapel Badge
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (tugas.jenis.equals("materi", ignoreCase = true)) Icons.Default.Book else Icons.AutoMirrored.Filled.Assignment,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = tugas.mapel,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Text(
                                    text = tugas.judul,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 28.sp
                                )

                                Text(
                                    text = "Pengajar: ${tugas.namaGuru}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                Text(
                                    text = tugas.deskripsi,
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Lampiran Tugas
                                if (!tugas.fileLampiran.isNullOrEmpty()) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable { onLihatFileTugasClick(tugas.fileLampiran) },
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                modifier = Modifier.size(42.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Attachment,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = tugas.fileLampiran,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Ketuk untuk melihat lampiran",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Status & Submission Card (jika bukan materi & isOnline)
                    val isMateri = tugas.jenis.equals("materi", ignoreCase = true)
                    val isOnline = tugas.isOnline == 1

                    if (!isMateri && isOnline) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(32.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    if (pengumpulan != null) {
                                        Text(
                                            text = "Tugas Diserahkan pada ${pengumpulan.waktuKumpul}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )

                                        if (pengumpulan.nilai > 0) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary
                                            ) {
                                                Text(
                                                    text = "Nilai Akhir: ${pengumpulan.nilai}",
                                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                        }

                                        // Feedback Guru
                                        if (!pengumpulan.komentar_guru.isNullOrEmpty()) {
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(20.dp),
                                                color = MaterialTheme.colorScheme.surface
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Feedback,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Column {
                                                        Text(
                                                            text = "Feedback Guru",
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Text(
                                                            text = pengumpulan.komentar_guru,
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Button(
                                                onClick = { onLihatFileJawabanClick(pengumpulan.fileJawaban) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(52.dp),
                                                shape = CircleShape,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                )
                                            ) {
                                                Icon(imageVector = Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Lihat Jawaban", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }

                                            if (pengumpulan.nilai <= 0) {
                                                OutlinedButton(
                                                    onClick = onGantiFileClick,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(52.dp),
                                                    shape = CircleShape,
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                ) {
                                                    Text("Ganti Jawaban", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                    } else {
                                        Text(
                                            text = "Tenggat: ${tugas.deadline}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )

                                        Button(
                                            onClick = onUploadClick,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp),
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Icon(imageVector = Icons.Default.Attachment, contentDescription = null, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Serahkan File (Upload)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Judul Diskusi
                    item {
                        Text(
                            text = "Ruang Diskusi",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }

                    // 4. Daftar Pesan Diskusi
                    if (diskusiList.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Belum ada diskusi. Mulai percakapan di bawah!",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(diskusiList) { chat ->
                            ChatBubbleItem(
                                chat = chat,
                                nisSaya = nisSaya,
                                onDelete = { onDeleteChat(chat.idDiskusi) }
                            )
                        }
                    }
                }
            }

            if (isLoading && detailResponse != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubbleItem(
    chat: DiskusiModel,
    nisSaya: String,
    onDelete: () -> Unit
) {
    val isMyMessage = chat.pengirimId == nisSaya

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isMyMessage) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .then(
                    if (isMyMessage) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { onDelete() }
                            )
                        }
                    } else {
                        Modifier
                    }
                ),
            shape = RoundedCornerShape(
                topStart = 24.dp,
                topEnd = 24.dp,
                bottomStart = if (isMyMessage) 24.dp else 4.dp,
                bottomEnd = if (isMyMessage) 4.dp else 24.dp
            ),
            color = if (isMyMessage) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!isMyMessage) {
                    val senderName = if (chat.pengirimTipe == "guru") "${chat.pengirimNama} (Guru)" else chat.pengirimNama
                    Text(
                        text = senderName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (chat.pengirimTipe == "guru") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = chat.pesan,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = if (isMyMessage) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = formatChatTime(chat.createdAt),
                    fontSize = 10.sp,
                    color = if (isMyMessage) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DetailTugasScreenPreview() {
    AppTheme {
        DetailTugasScreen(
            detailResponse = DetailResponse(
                status = "success",
                tugas = TugasModel(
                    idTugas = 1,
                    judul = "Tugas Matematika Bab 1",
                    mapel = "Matematika",
                    deskripsi = "Kerjakan soal latihan halaman 10-15 di buku catatan masing-masing lalu upload foto jawabannya.",
                    namaGuru = "Bapak Budi, S.Pd.",
                    jenis = "Tugas",
                    isOnline = 1,
                    deadline = "2026-05-10 23:59:59",
                    fileLampiran = "materi_matematika.pdf"
                ),
                pengumpulan = null,
                diskusi = listOf(
                    DiskusiModel(
                        idDiskusi = 1,
                        pengirimId = "12345",
                        pengirimNama = "Bapak Budi, S.Pd.",
                        pengirimTipe = "guru",
                        pesan = "Jangan lupa dikumpul tepat waktu ya anak-anak.",
                        createdAt = "2026-05-01 08:00:00"
                    ),
                    DiskusiModel(
                        idDiskusi = 2,
                        pengirimId = "99999",
                        pengirimNama = "Rudi",
                        pengirimTipe = "siswa",
                        pesan = "Baik Pak, terima kasih infonya.",
                        createdAt = "2026-05-01 08:30:00"
                    )
                )
            ),
            isLoading = false,
            pesanInput = "",
            nisSaya = "99999",
            onPesanChanged = {},
            onKirimPesan = {},
            onUploadClick = {},
            onGantiFileClick = {},
            onLihatFileTugasClick = {},
            onLihatFileJawabanClick = {},
            onDeleteChat = {},
            onBack = {}
        )
    }
}