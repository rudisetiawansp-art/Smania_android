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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.rudissp.smania.AppTheme

class LihatHasilUjianActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var repository: UjianRepository

    private var idUjian = ""
    private var judulUjian = ""
    private var mapel = ""
    private var tipeUjian = "ujian"
    private var nisSiswa = ""
    private var kelasSiswa = ""

    private var hasilData by mutableStateOf<HasilUjianData?>(null)
    private var showRepeatDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefs = getSharedPreferences("PREFS", Context.MODE_PRIVATE)
        repository = UjianRepository(this)

        idUjian = intent.getStringExtra("ID_UJIAN") ?: ""
        judulUjian = intent.getStringExtra("JUDUL_UJIAN") ?: "Hasil Ujian"
        mapel = intent.getStringExtra("MAPEL") ?: ""
        tipeUjian = intent.getStringExtra("TIPE") ?: "ujian"

        nisSiswa = prefs.getString("nis", "") ?: ""
        kelasSiswa = prefs.getString("kelas", "") ?: ""

        // Load saved results
        val saved = UjianStorage.getHasilUjian(this, idUjian, nisSiswa)
        if (saved != null) {
            hasilData = saved
        } else {
            // Default placeholder if just finished
            hasilData = HasilUjianData(
                idUjian = idUjian,
                judulUjian = judulUjian,
                mapel = mapel,
                kelas = kelasSiswa,
                tipe = tipeUjian,
                nilaiTotal = 0.0,
                jawabanBenar = 0,
                jawabanSalah = 0,
                totalSoal = 0,
                bisaDiulang = (tipeUjian == "latihan")
            )
        }

        setContent {
            AppTheme {
                HasilUjianScreen(
                    hasil = hasilData,
                    showRepeatConfirm = showRepeatDialog,
                    onBack = { finish() },
                    onRequestRepeat = { showRepeatDialog = true },
                    onDismissRepeat = { showRepeatDialog = false },
                    onConfirmRepeat = {
                        showRepeatDialog = false
                        ulangiLatihan()
                    }
                )
            }
        }
    }

    private fun ulangiLatihan() {
        repository.ulangiLatihan(idUjian, nisSiswa, object : UjianRepository.Callback<Boolean> {
            override fun onSuccess(result: Boolean) {
                val intent = Intent(this@LihatHasilUjianActivity, KerjakanUjianActivity::class.java).apply {
                    putExtra("ID_UJIAN", idUjian)
                    putExtra("JUDUL_UJIAN", judulUjian)
                    putExtra("MAPEL", mapel)
                    putExtra("TIPE", "latihan")
                }
                startActivity(intent)
                finish()
            }

            override fun onError(message: String) {
                Toast.makeText(this@LihatHasilUjianActivity, message, Toast.LENGTH_SHORT).show()
            }
        })
    }
}

// ============================================================
// HASIL UJIAN COMPOSE SCREEN
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HasilUjianScreen(
    hasil: HasilUjianData?,
    showRepeatConfirm: Boolean,
    onBack: () -> Unit,
    onRequestRepeat: () -> Unit,
    onDismissRepeat: () -> Unit,
    onConfirmRepeat: () -> Unit
) {
    val isLatihan = (hasil?.tipe == "latihan")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isLatihan) "Hasil Latihan" else "Hasil Evaluasi",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Hero Card
            item {
                Spacer(modifier = Modifier.height(4.dp))
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Large celebration icon
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(if (isLatihan) ExpressivePurpleContainer else ExpressiveGreenContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isLatihan) Icons.Rounded.School else Icons.Rounded.TaskAlt,
                                contentDescription = null,
                                tint = if (isLatihan) ExpressivePurple else ExpressiveGreen,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (isLatihan) "Latihan Selesai! 🎉" else "Ujian Selesai! 🎉",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = hasil?.judulUjian ?: "Evaluasi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${hasil?.mapel ?: ""} • Kelas ${hasil?.kelas ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isLatihan) {
                            Spacer(modifier = Modifier.height(12.dp))
                            ExpressiveBadge(
                                text = "LATIHAN MANDIRI",
                                containerColor = ExpressivePurpleContainer,
                                contentColor = ExpressivePurple,
                                icon = Icons.Rounded.School
                            )
                        }
                    }
                }
            }

            // Stat Cards (for Latihan)
            if (isLatihan && hasil != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScoreMetricCard(
                            title = "Nilai",
                            value = "${hasil.nilaiTotal.toInt()}",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        ScoreMetricCard(
                            title = "Benar",
                            value = "${hasil.jawabanBenar}",
                            containerColor = ExpressiveGreenContainer,
                            contentColor = ExpressiveGreen,
                            modifier = Modifier.weight(1f)
                        )
                        ScoreMetricCard(
                            title = "Salah",
                            value = "${hasil.jawabanSalah}",
                            containerColor = ExpressiveRedContainer,
                            contentColor = ExpressiveRed,
                            modifier = Modifier.weight(1f)
                        )
                        ScoreMetricCard(
                            title = "Total Soal",
                            value = "${hasil.totalSoal}",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Motivational Guidance Card
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ExpressivePurpleContainer.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ExpressivePurple.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Ini adalah latihan mandiri, sehingga hasilnya dapat langsung kamu pelajari. Review kembali butir soal yang belum tepat, lalu coba lagi hingga tuntas! 💪",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(14.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Official Exam Appreciation Message
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ExpressiveBlueContainer.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ExpressiveBlue.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Terima kasih telah mengerjakan evaluasi ini dengan jujur dan penuh tanggung jawab.\n\nLembar jawaban Anda telah tersimpan dengan aman di server dan sedang menunggu proses verifikasi dan penilaian akhir oleh Bapak/Ibu Guru Pengajar.\n\nTetap semangat belajar!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }

            // Action Buttons Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isLatihan && hasil?.bisaDiulang == true) {
                        Button(
                            onClick = onRequestRepeat,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ExpressivePurple),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Kerjakan Ulang", fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onBack,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kembali", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Detailed Question Review (For Latihan)
            if (isLatihan && hasil != null && hasil.daftarSoal.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Rincian Pekerjaanmu",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                itemsIndexed(hasil.daftarSoal) { index, soal ->
                    val jwbItem = hasil.detailJawaban[soal.id]
                    QuestionReviewCard(
                        noSoal = index + 1,
                        soal = soal,
                        jwbItem = jwbItem
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Confirm Repeat Dialog
    if (showRepeatConfirm) {
        AlertDialog(
            onDismissRequest = onDismissRepeat,
            icon = { Icon(Icons.Rounded.Refresh, contentDescription = null, tint = ExpressivePurple) },
            title = { Text("Kerjakan Ulang Latihan?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Percobaan sebelumnya akan direset dan Anda akan mengerjakan soal latihan ini dari awal kembali. Apakah Anda ingin melanjutkan?")
            },
            confirmButton = {
                Button(
                    onClick = onConfirmRepeat,
                    colors = ButtonDefaults.buttonColors(containerColor = ExpressivePurple),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Ya, Mulai Ulang", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRepeat) {
                    Text("Batal")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

// ============================================================
// SCORE METRIC CARD
// ============================================================
@Composable
private fun ScoreMetricCard(
    title: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.8f),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

// ============================================================
// QUESTION REVIEW CARD
// ============================================================
@Composable
private fun QuestionReviewCard(
    noSoal: Int,
    soal: SoalUjian,
    jwbItem: JawabanItem?
) {
    val isUraian = (soal.tipeSoal == "uraian")
    val isBenar = jwbItem?.benar ?: false
    val borderColor = when {
        isUraian -> MaterialTheme.colorScheme.outlineVariant
        isBenar -> ExpressiveGreen
        else -> ExpressiveRed
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$noSoal",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = soal.tipeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Result Badge
                if (isUraian) {
                    ExpressiveBadge(
                        text = "Menunggu Koreksi",
                        containerColor = ExpressiveOrangeContainer,
                        contentColor = ExpressiveOrange
                    )
                } else if (isBenar) {
                    ExpressiveBadge(
                        text = "Benar (+${soal.skor.toInt()})",
                        containerColor = ExpressiveGreenContainer,
                        contentColor = ExpressiveGreen,
                        icon = Icons.Rounded.CheckCircle
                    )
                } else {
                    ExpressiveBadge(
                        text = "Salah (0)",
                        containerColor = ExpressiveRedContainer,
                        contentColor = ExpressiveRed,
                        icon = Icons.Rounded.Cancel
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Question Stem
            HtmlText(html = soal.pertanyaan, fontSize = 14.sp)

            Spacer(modifier = Modifier.height(10.dp))

            // Student Answer Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "JAWABANMU:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = (jwbItem?.jawaban ?: "").ifBlank { "(Tidak dijawab)" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (jwbItem?.jawaban.isNullOrBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Answer Key Box (if not uraian)
            if (!isUraian && !soal.kunciJawaban.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ExpressiveGreenContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "KUNCI JAWABAN:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ExpressiveGreen
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = soal.kunciJawaban,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ExpressiveGreen
                        )
                    }
                }
            }
        }
    }
}
