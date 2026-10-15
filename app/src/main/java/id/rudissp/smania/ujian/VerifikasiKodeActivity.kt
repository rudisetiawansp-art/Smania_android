package id.rudissp.smania.ujian

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.rudissp.smania.AppTheme

class VerifikasiKodeActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var repository: UjianRepository

    private var idUjian = ""
    private var judulUjian = ""
    private var mapel = ""
    private var durasi = 0
    private var nisSiswa = ""
    private var namaSiswa = ""
    private var kelasSiswa = ""

    private var inputKode by mutableStateOf("")
    private var isLoading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefs = getSharedPreferences("PREFS", Context.MODE_PRIVATE)
        repository = UjianRepository(this)

        idUjian = intent.getStringExtra("ID_UJIAN") ?: ""
        judulUjian = intent.getStringExtra("JUDUL_UJIAN") ?: "Ujian Online"
        mapel = intent.getStringExtra("MAPEL") ?: ""
        durasi = intent.getIntExtra("DURASI", 90)

        nisSiswa = prefs.getString("nis", "") ?: ""
        namaSiswa = prefs.getString("namalengkap", "") ?: ""
        kelasSiswa = prefs.getString("kelas", "") ?: ""

        // If previously verified or resumed, can proceed
        if (UjianStorage.isTokenVerified(this, idUjian, nisSiswa)) {
            startExam()
            return
        }

        setContent {
            AppTheme {
                VerifikasiKodeScreen(
                    judulUjian = judulUjian,
                    mapel = mapel,
                    namaSiswa = namaSiswa,
                    kelasSiswa = kelasSiswa,
                    kodeInput = inputKode,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onKodeChanged = {
                        inputKode = it.uppercase().take(10)
                        errorMessage = null
                    },
                    onSubmit = { prosesVerifikasi() },
                    onBack = { finish() }
                )
            }
        }
    }

    private fun prosesVerifikasi() {
        if (inputKode.trim().isEmpty()) {
            errorMessage = "Silakan masukkan kode akses dari pengawas."
            return
        }

        isLoading = true
        errorMessage = null

        repository.verifikasiKode(idUjian, inputKode.trim(), nisSiswa, object : UjianRepository.Callback<Boolean> {
            override fun onSuccess(result: Boolean) {
                isLoading = false
                UjianStorage.setTokenVerified(this@VerifikasiKodeActivity, idUjian, nisSiswa, true)
                startExam()
            }

            override fun onError(message: String) {
                isLoading = false
                errorMessage = message
            }
        })
    }

    private fun startExam() {
        val intent = Intent(this, KerjakanUjianActivity::class.java).apply {
            putExtra("ID_UJIAN", idUjian)
            putExtra("JUDUL_UJIAN", judulUjian)
            putExtra("MAPEL", mapel)
            putExtra("DURASI", durasi)
            putExtra("TIPE", "ujian")
        }
        startActivity(intent)
        finish()
    }
}

// ============================================================
// VERIFIKASI KODE COMPOSE SCREEN
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifikasiKodeScreen(
    judulUjian: String,
    mapel: String,
    namaSiswa: String,
    kelasSiswa: String,
    kodeInput: String,
    isLoading: Boolean,
    errorMessage: String?,
    onKodeChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Verifikasi Akses Ujian",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Lock Icon
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Verifikasi Kode Ujian",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = judulUjian,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    if (mapel.isNotEmpty()) {
                        Text(
                            text = "$mapel • Kelas $kelasSiswa",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Error Alert Box
                    if (!errorMessage.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ExpressiveRedContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Error,
                                    contentDescription = null,
                                    tint = ExpressiveRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ExpressiveRed,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Anti-Cheat Rules Box (Material 3 Expressive)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ExpressiveRedContainer.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ExpressiveRed.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = ExpressiveRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Aturan Penting & Anti-Cheat",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ExpressiveRed
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            RuleBullet(text = "Dilarang berpindah aplikasi atau meminimalkan layar saat ujian berlangsung.")
                            RuleBullet(text = "Layar ujian tidak boleh tidak aktif lebih dari ±1,5 detik.")
                            RuleBullet(text = "Dilarang menekan tombol back atau menutup aplikasi sebelum menekan Kumpulkan.")
                            RuleBullet(text = "Tangkapan layar (screenshot) dan rekaman layar otomatis diblokir.")

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Pelanggaran ke-3: Ujian OTOMATIS TERKUNCI dan hanya dapat dibuka oleh guru pengawas.",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ExpressiveRed
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Input Kode Akses
                    Text(
                        text = "MASUKKAN KODE AKSES DARI GURU",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = kodeInput,
                        onValueChange = onKodeChanged,
                        placeholder = {
                            Text(
                                "XXXXXX",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 6.sp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 6.sp,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                onSubmit()
                            }
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Submit Button
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            onSubmit()
                        },
                        enabled = !isLoading && kodeInput.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Verifikasi & Mulai Ujian",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Kembali ke Dashboard",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Jika Anda belum menerima kode akses, tanyakan kepada guru pengajar atau pengawas ujian.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun RuleBullet(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("•", color = ExpressiveRed, fontWeight = FontWeight.Bold)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
