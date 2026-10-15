package id.rudissp.smania.ujian

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.rudissp.smania.AppTheme

class KerjakanUjianActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var repository: UjianRepository

    private var idUjian = ""
    private var judulUjian = ""
    private var mapel = ""
    private var durasiMenit = 90
    private var tipeUjian = "ujian"
    private var nisSiswa = ""
    private var namaSiswa = ""
    private var kelasSiswa = ""

    // State
    private var soalList by mutableStateOf<List<SoalUjian>>(emptyList())
    private var currentQuestionIndex by mutableStateOf(0)
    private var userAnswers by mutableStateOf<Map<Int, String>>(emptyMap())
    private var raguSet by mutableStateOf<Set<Int>>(emptySet())
    private var sisaWaktuDetik by mutableStateOf(0L)
    private var violationsCount by mutableStateOf(0)
    private var isLocked by mutableStateOf(false)
    private var isLoading by mutableStateOf(true)
    private var showNavigatorSheet by mutableStateOf(false)
    private var showSubmitConfirmDialog by mutableStateOf(false)
    private var showViolationAlert by mutableStateOf(false)
    private var showExitConfirmDialog by mutableStateOf(false)

    private var countDownTimer: CountDownTimer? = null
    private var isSubmitting = false
    private var lastViolationTimestamp = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ANTI-CHEAT: Block screenshots and screen capture
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        prefs = getSharedPreferences("PREFS", Context.MODE_PRIVATE)
        repository = UjianRepository(this)

        idUjian = intent.getStringExtra("ID_UJIAN") ?: "1"
        judulUjian = intent.getStringExtra("JUDUL_UJIAN") ?: "Ujian Online"
        mapel = intent.getStringExtra("MAPEL") ?: ""
        durasiMenit = intent.getIntExtra("DURASI", 90)
        tipeUjian = intent.getStringExtra("TIPE") ?: "ujian"

        nisSiswa = prefs.getString("nis", "") ?: ""
        namaSiswa = prefs.getString("namalengkap", "") ?: ""
        kelasSiswa = prefs.getString("kelas", "") ?: ""

        // Restore saved answers and violations
        userAnswers = UjianStorage.getJawaban(this, idUjian, nisSiswa)
        raguSet = UjianStorage.getRaguRagu(this, idUjian, nisSiswa)
        violationsCount = UjianStorage.getPelanggaran(this, idUjian, nisSiswa)

        if (tipeUjian != "latihan" && violationsCount >= 3) {
            isLocked = true
        }

        // Back press confirmation
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isLocked) {
                    finish()
                } else {
                    showExitConfirmDialog = true
                }
            }
        })

        setContent {
            AppTheme {
                if (isLocked) {
                    LockedExamScreen(
                        onBackToDashboard = { finish() }
                    )
                } else if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Menyiapkan lembar ujian...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    ExamWorkspaceScreen(
                        judulUjian = judulUjian,
                        mapel = mapel,
                        namaSiswa = namaSiswa,
                        soalList = soalList,
                        currentIndex = currentQuestionIndex,
                        userAnswers = userAnswers,
                        raguSet = raguSet,
                        sisaDetik = sisaWaktuDetik,
                        violationsCount = violationsCount,
                        tipeUjian = tipeUjian,
                        showNavigatorSheet = showNavigatorSheet,
                        showSubmitDialog = showSubmitConfirmDialog,
                        showViolationAlert = showViolationAlert,
                        showExitDialog = showExitConfirmDialog,
                        onOpenNavigator = { showNavigatorSheet = true },
                        onCloseNavigator = { showNavigatorSheet = false },
                        onSelectQuestion = { currentQuestionIndex = it },
                        onAnswerChanged = { idSoal, answer ->
                            val updated = userAnswers.toMutableMap()
                            updated[idSoal] = answer
                            userAnswers = updated
                            UjianStorage.saveJawaban(this@KerjakanUjianActivity, idUjian, nisSiswa, updated)
                        },
                        onToggleRagu = { idSoal ->
                            val updated = raguSet.toMutableSet()
                            if (updated.contains(idSoal)) updated.remove(idSoal)
                            else updated.add(idSoal)
                            raguSet = updated
                            UjianStorage.saveRaguRagu(this@KerjakanUjianActivity, idUjian, nisSiswa, updated)
                        },
                        onPrevQuestion = {
                            if (currentQuestionIndex > 0) currentQuestionIndex--
                        },
                        onNextQuestion = {
                            if (currentQuestionIndex < soalList.size - 1) currentQuestionIndex++
                        },
                        onRequestSubmit = { showSubmitConfirmDialog = true },
                        onDismissSubmit = { showSubmitConfirmDialog = false },
                        onConfirmSubmit = {
                            showSubmitConfirmDialog = false
                            submitUjian()
                        },
                        onDismissViolation = { showViolationAlert = false },
                        onDismissExit = { showExitConfirmDialog = false },
                        onConfirmExit = {
                            showExitConfirmDialog = false
                            finish()
                        }
                    )
                }
            }
        }

        loadQuestions()
    }

    private fun loadQuestions() {
        isLoading = true
        repository.loadSoalUjian(idUjian, nisSiswa, object : UjianRepository.Callback<UjianDetailResponse> {
            override fun onSuccess(result: UjianDetailResponse) {
                isLoading = false
                soalList = result.soal
                sisaWaktuDetik = if (result.sisaDetik > 0) result.sisaDetik else (durasiMenit * 60L)
                violationsCount = result.pelanggaran
                UjianStorage.setPelanggaran(this@KerjakanUjianActivity, idUjian, nisSiswa, result.pelanggaran)
                if (tipeUjian != "latihan" && (result.pelanggaran >= 3 || result.statusUjian == "diblokir")) {
                    isLocked = true
                }
                startTimer(sisaWaktuDetik)
            }

            override fun onError(message: String) {
                isLoading = false
                Toast.makeText(this@KerjakanUjianActivity, message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun startTimer(totalSeconds: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(totalSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                sisaWaktuDetik = millisUntilFinished / 1000L
            }

            override fun onFinish() {
                sisaWaktuDetik = 0
                Toast.makeText(this@KerjakanUjianActivity, "Waktu ujian telah berakhir!", Toast.LENGTH_LONG).show()
                submitUjian()
            }
        }.start()
    }

    // ============================================================
    // ANTI-CHEAT LIFECYCLE DETECTION
    // ============================================================
    override fun onPause() {
        super.onPause()
        if (!isSubmitting && !isLocked && tipeUjian != "latihan" && !isLoading) {
            val now = System.currentTimeMillis()
            if (now - lastViolationTimestamp >= 2000L) {
                lastViolationTimestamp = now
                repository.catatPelanggaran(idUjian, nisSiswa, userAnswers, object : UjianRepository.Callback<PelanggaranResult> {
                    override fun onSuccess(result: PelanggaranResult) {
                        violationsCount = result.pelanggaran
                        UjianStorage.setPelanggaran(this@KerjakanUjianActivity, idUjian, nisSiswa, result.pelanggaran)
                        if (result.pelanggaran >= 3 || result.statusUjian == "diblokir") {
                            isLocked = true
                        } else {
                            showViolationAlert = true
                        }
                    }

                    override fun onError(message: String) {
                        val newCount = UjianStorage.incrementPelanggaran(this@KerjakanUjianActivity, idUjian, nisSiswa)
                        violationsCount = newCount
                        if (newCount >= 3) {
                            isLocked = true
                        } else {
                            showViolationAlert = true
                        }
                    }
                })
            }
        }
    }

    private fun submitUjian() {
        if (isSubmitting) return
        isSubmitting = true
        countDownTimer?.cancel()

        val ujianItem = UjianItem(
            id = idUjian,
            judulUjian = judulUjian,
            mapel = mapel,
            durasi = durasiMenit,
            tipe = tipeUjian,
            isSelesai = true
        )

        repository.submitUjian(
            idUjian = idUjian,
            nis = nisSiswa,
            nama = namaSiswa,
            kelas = kelasSiswa,
            soalList = soalList,
            jawabanMap = userAnswers,
            ujianItem = ujianItem,
            callback = object : UjianRepository.Callback<HasilUjianData> {
                override fun onSuccess(result: HasilUjianData) {
                    val intent = Intent(this@KerjakanUjianActivity, LihatHasilUjianActivity::class.java).apply {
                        putExtra("ID_UJIAN", idUjian)
                        putExtra("JUDUL_UJIAN", judulUjian)
                        putExtra("MAPEL", mapel)
                        putExtra("TIPE", tipeUjian)
                    }
                    startActivity(intent)
                    finish()
                }

                override fun onError(message: String) {
                    isSubmitting = false
                    Toast.makeText(this@KerjakanUjianActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}

// ============================================================
// EXAM WORKSPACE COMPOSE SCREEN
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamWorkspaceScreen(
    judulUjian: String,
    mapel: String,
    namaSiswa: String,
    soalList: List<SoalUjian>,
    currentIndex: Int,
    userAnswers: Map<Int, String>,
    raguSet: Set<Int>,
    sisaDetik: Long,
    violationsCount: Int,
    tipeUjian: String,
    showNavigatorSheet: Boolean,
    showSubmitDialog: Boolean,
    showViolationAlert: Boolean,
    showExitDialog: Boolean,
    onOpenNavigator: () -> Unit,
    onCloseNavigator: () -> Unit,
    onSelectQuestion: (Int) -> Unit,
    onAnswerChanged: (Int, String) -> Unit,
    onToggleRagu: (Int) -> Unit,
    onPrevQuestion: () -> Unit,
    onNextQuestion: () -> Unit,
    onRequestSubmit: () -> Unit,
    onDismissSubmit: () -> Unit,
    onConfirmSubmit: () -> Unit,
    onDismissViolation: () -> Unit,
    onDismissExit: () -> Unit,
    onConfirmExit: () -> Unit
) {
    val totalSoal = soalList.size
    val currentSoal = soalList.getOrNull(currentIndex)
    val answeredCount = remember(userAnswers, soalList) {
        soalList.count { (userAnswers[it.id] ?: "").isNotBlank() }
    }
    val progress = if (totalSoal > 0) answeredCount.toFloat() / totalSoal.toFloat() else 0f

    // Timer formatting
    val minutes = sisaDetik / 60
    val seconds = sisaDetik % 60
    val timerText = String.format("%02d:%02d", minutes, seconds)
    val isTimerUrgent = sisaDetik < 300 // < 5 mins

    // Current question answer
    val currentAnswer = currentSoal?.let { userAnswers[it.id] ?: "" } ?: ""
    val isRaguCurrent = currentSoal?.let { raguSet.contains(it.id) } ?: false

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    // Top row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = judulUjian,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "$mapel • $namaSiswa",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Timer Pill
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isTimerUrgent) ExpressiveRedContainer else ExpressiveOrangeContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Schedule,
                                        contentDescription = null,
                                        tint = if (isTimerUrgent) ExpressiveRed else ExpressiveOrange,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = timerText,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isTimerUrgent) ExpressiveRed else ExpressiveOrange
                                        )
                                    )
                                }
                            }

                            // Navigator Grid Button
                            FilledTonalIconButton(
                                onClick = onOpenNavigator,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.GridView, contentDescription = "Nomor Soal")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Progress bar & Anti-cheat violations badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Text(
                            text = "$answeredCount / $totalSoal",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (tipeUjian != "latihan") {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (violationsCount > 0) ExpressiveRedContainer else ExpressiveGreenContainer
                            ) {
                                Text(
                                    text = "Pelanggaran: $violationsCount/3",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (violationsCount > 0) ExpressiveRed else ExpressiveGreen,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Prev Button
                    OutlinedButton(
                        onClick = onPrevQuestion,
                        enabled = currentIndex > 0,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sebelumnya")
                    }

                    // Ragu-ragu checkbox
                    if (currentSoal != null) {
                        FilterChip(
                            selected = isRaguCurrent,
                            onClick = { onToggleRagu(currentSoal.id) },
                            label = { Text("Ragu-ragu", fontWeight = FontWeight.SemiBold) },
                            leadingIcon = {
                                if (isRaguCurrent) {
                                    Icon(Icons.Rounded.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ExpressiveOrangeContainer,
                                selectedLabelColor = ExpressiveOrange,
                                selectedLeadingIconColor = ExpressiveOrange
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Next or Finish Button
                    if (currentIndex == totalSoal - 1) {
                        Button(
                            onClick = onRequestSubmit,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ExpressiveGreen)
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Kumpulkan", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onNextQuestion,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Selanjutnya")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (currentSoal != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Question Header Card
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "Soal ${currentIndex + 1} dari $totalSoal",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = currentSoal.tipeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "${currentSoal.skor.toInt()} Poin",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        // Question Body
                        HtmlText(
                            html = currentSoal.pertanyaan,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Answer Input Section
                when (currentSoal.tipeSoal) {
                    "pg" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            currentSoal.optionsList.forEach { (letter, optionText) ->
                                OptionCard(
                                    letter = letter,
                                    text = optionText,
                                    isSelected = currentAnswer.equals(letter, ignoreCase = true),
                                    isMultiSelect = false,
                                    onClick = { onAnswerChanged(currentSoal.id, letter) }
                                )
                            }
                        }
                    }

                    "pg_kompleks" -> {
                        val selectedList = remember(currentAnswer) {
                            if (currentAnswer.isBlank()) emptyList()
                            else currentAnswer.split(",").map { it.trim().uppercase() }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Pilih satu atau lebih jawaban yang benar:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            currentSoal.optionsList.forEach { (letter, optionText) ->
                                val isSelected = selectedList.contains(letter)
                                OptionCard(
                                    letter = letter,
                                    text = optionText,
                                    isSelected = isSelected,
                                    isMultiSelect = true,
                                    onClick = {
                                        val mutable = selectedList.toMutableList()
                                        if (isSelected) mutable.remove(letter)
                                        else mutable.add(letter)
                                        mutable.sort()
                                        onAnswerChanged(currentSoal.id, mutable.joinToString(","))
                                    }
                                )
                            }
                        }
                    }

                    "benar_salah" -> {
                        TrueFalseOptionCards(
                            selectedAnswer = currentAnswer,
                            onAnswerSelected = { onAnswerChanged(currentSoal.id, it) }
                        )
                    }

                    "benar_salah_tabel" -> {
                        val currentList = remember(currentAnswer, currentSoal.statements.size) {
                            val parts = currentAnswer.split(",").map { it.trim() }
                            List(currentSoal.statements.size) { idx -> parts.getOrNull(idx) ?: "" }
                        }

                        TableTrueFalseCard(
                            statements = currentSoal.statements,
                            currentAnswers = currentList,
                            onAnswerChanged = { idx, value ->
                                val mut = currentList.toMutableList()
                                mut[idx] = value
                                onAnswerChanged(currentSoal.id, mut.joinToString(","))
                            }
                        )
                    }

                    "uraian" -> {
                        OutlinedTextField(
                            value = currentAnswer,
                            onValueChange = { onAnswerChanged(currentSoal.id, it) },
                            placeholder = { Text("Ketik jawaban uraian Anda di sini...") },
                            minLines = 6,
                            maxLines = 15,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = {
                                Text("${currentAnswer.length} karakter")
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // Modal BottomSheet for question grid navigation
    if (showNavigatorSheet) {
        val answeredIndices = remember(userAnswers, soalList) {
            soalList.mapIndexedNotNull { index, s ->
                if ((userAnswers[s.id] ?: "").isNotBlank()) index else null
            }.toSet()
        }
        val raguIndices = remember(raguSet, soalList) {
            soalList.mapIndexedNotNull { index, s ->
                if (raguSet.contains(s.id)) index else null
            }.toSet()
        }

        QuestionGridSheet(
            totalQuestions = totalSoal,
            currentIndex = currentIndex,
            answeredIndices = answeredIndices,
            raguIndices = raguIndices,
            onSelectQuestion = onSelectQuestion,
            onDismiss = onCloseNavigator
        )
    }

    // Anti-Cheat Violation Alert Dialog
    if (showViolationAlert) {
        AntiCheatViolationDialog(
            violationCount = violationsCount,
            maxViolations = 3,
            onDismiss = onDismissViolation
        )
    }

    // Confirm Exit Dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = onDismissExit,
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = ExpressiveOrange) },
            title = { Text("Batalkan Ujian?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Keluar dari halaman ujian saat sedang berlangsung dapat dihitung sebagai pelanggaran anti-cheat. Apakah Anda yakin ingin keluar?")
            },
            confirmButton = {
                Button(
                    onClick = onConfirmExit,
                    colors = ButtonDefaults.buttonColors(containerColor = ExpressiveRed)
                ) {
                    Text("Keluar")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissExit) {
                    Text("Tetap di Ujian")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Confirm Submit Dialog
    if (showSubmitDialog) {
        val unansweredCount = totalSoal - answeredCount
        val raguCount = raguSet.size

        AlertDialog(
            onDismissRequest = onDismissSubmit,
            icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = ExpressiveGreen) },
            title = { Text("Kumpulkan Lembar Ujian?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pastikan Anda telah memeriksa kembali seluruh jawaban.")
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("• Sudah dijawab: $answeredCount soal", fontWeight = FontWeight.SemiBold)
                            if (unansweredCount > 0) {
                                Text("• Belum dijawab: $unansweredCount soal ⚠️", color = ExpressiveRed, fontWeight = FontWeight.Bold)
                            }
                            if (raguCount > 0) {
                                Text("• Masih ditandai ragu: $raguCount soal", color = ExpressiveOrange, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmSubmit,
                    colors = ButtonDefaults.buttonColors(containerColor = ExpressiveGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Ya, Kumpulkan Sekarang", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissSubmit) {
                    Text("Periksa Lagi")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}
