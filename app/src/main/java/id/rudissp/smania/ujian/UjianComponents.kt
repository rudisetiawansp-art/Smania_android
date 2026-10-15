package id.rudissp.smania.ujian

import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat

// ============================================================
// MATERIAL 3 EXPRESSIVE COLORS & UTILS
// ============================================================
val ExpressiveGreen = Color(0xFF2E7D32)
val ExpressiveGreenContainer = Color(0xFFE8F5E9)
val ExpressiveBlue = Color(0xFF1565C0)
val ExpressiveBlueContainer = Color(0xFFE3F2FD)
val ExpressiveOrange = Color(0xFFE65100)
val ExpressiveOrangeContainer = Color(0xFFFFF3E0)
val ExpressivePurple = Color(0xFF6A1B9A)
val ExpressivePurpleContainer = Color(0xFFF3E5F5)
val ExpressiveRed = Color(0xFFC62828)
val ExpressiveRedContainer = Color(0xFFFFEBEE)

// ============================================================
// EXPRESSIVE STAT CARD (Tersedia, Selesai, Berjalan, Latihan)
// ============================================================
@Composable
fun ExpressiveStatCard(
    title: String,
    value: Int,
    icon: ImageVector,
    iconTint: Color,
    containerColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1.0f,
        animationSpec = spring(),
        label = "stat_scale"
    )

    Card(
        modifier = modifier
            .scale(scale)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) containerColor else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) iconTint else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 3.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ============================================================
// EXPRESSIVE BADGES
// ============================================================
@Composable
fun ExpressiveBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

// ============================================================
// HTML TEXT RENDERER
// ============================================================
@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = 15.sp
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                setTextColor(color.toArgb())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.value)
                movementMethod = LinkMovementMethod.getInstance()
                setLineSpacing(6f, 1.15f)
            }
        },
        update = { textView ->
            textView.setTextColor(color.toArgb())
            textView.text = HtmlCompat.fromHtml(
                html.ifBlank { "—" },
                HtmlCompat.FROM_HTML_MODE_COMPACT
            )
        }
    )
}

// ============================================================
// OPTION CARD FOR PG (SINGLE CHOICE) & PG KOMPLEKS (MULTI CHOICE)
// ============================================================
@Composable
fun OptionCard(
    letter: String,
    text: String,
    isSelected: Boolean,
    isMultiSelect: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        label = "option_border"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        label = "option_bg"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(if (isSelected) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Letter circle badge or checkbox
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(if (isMultiSelect) RoundedCornerShape(8.dp) else CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isMultiSelect && isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = letter,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Option text (formatted HTML)
            Box(modifier = Modifier.weight(1f)) {
                HtmlText(
                    html = text,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ============================================================
// TRUE / FALSE CARDS (BENAR / SALAH)
// ============================================================
@Composable
fun TrueFalseOptionCards(
    selectedAnswer: String,
    onAnswerSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Button BENAR
        val isBenarSelected = selectedAnswer.equals("B", ignoreCase = true) || selectedAnswer.equals("Benar", ignoreCase = true)
        Card(
            modifier = Modifier
                .weight(1f)
                .clickable { onAnswerSelected("B") },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isBenarSelected) ExpressiveGreenContainer else MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(
                width = if (isBenarSelected) 2.dp else 1.dp,
                color = if (isBenarSelected) ExpressiveGreen else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Benar",
                    tint = if (isBenarSelected) ExpressiveGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "BENAR",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isBenarSelected) ExpressiveGreen else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Button SALAH
        val isSalahSelected = selectedAnswer.equals("S", ignoreCase = true) || selectedAnswer.equals("Salah", ignoreCase = true)
        Card(
            modifier = Modifier
                .weight(1f)
                .clickable { onAnswerSelected("S") },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSalahSelected) ExpressiveRedContainer else MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(
                width = if (isSalahSelected) 2.dp else 1.dp,
                color = if (isSalahSelected) ExpressiveRed else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Salah",
                    tint = if (isSalahSelected) ExpressiveRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SALAH",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSalahSelected) ExpressiveRed else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ============================================================
// TABLE TRUE / FALSE (BENAR / SALAH TABEL)
// ============================================================
@Composable
fun TableTrueFalseCard(
    statements: List<Pair<String, String>>,
    currentAnswers: List<String>,
    onAnswerChanged: (index: Int, value: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            statements.forEachIndexed { index, (label, statementText) ->
                val answer = currentAnswers.getOrNull(index) ?: ""
                val isB = answer.equals("B", ignoreCase = true)
                val isS = answer.equals("S", ignoreCase = true)

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            HtmlText(
                                html = statementText,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Segmented choice Benar / Salah
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Option B
                        FilterChip(
                            selected = isB,
                            onClick = { onAnswerChanged(index, "B") },
                            label = { Text("Benar (B)", fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                if (isB) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ExpressiveGreenContainer,
                                selectedLabelColor = ExpressiveGreen,
                                selectedLeadingIconColor = ExpressiveGreen
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )

                        // Option S
                        FilterChip(
                            selected = isS,
                            onClick = { onAnswerChanged(index, "S") },
                            label = { Text("Salah (S)", fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                if (isS) Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ExpressiveRedContainer,
                                selectedLabelColor = ExpressiveRed,
                                selectedLeadingIconColor = ExpressiveRed
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    if (index < statements.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// QUESTION GRID BOTTOM SHEET (NAVIGATOR)
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionGridSheet(
    totalQuestions: Int,
    currentIndex: Int,
    answeredIndices: Set<Int>,
    raguIndices: Set<Int>,
    onSelectQuestion: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daftar Nomor Soal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                }
            }

            // Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                LegendItem(color = ExpressiveGreen, label = "Dijawab (${answeredIndices.size})")
                LegendItem(color = ExpressiveOrange, label = "Ragu (${raguIndices.size})")
                LegendItem(color = MaterialTheme.colorScheme.surfaceVariant, label = "Belum (${totalQuestions - answeredIndices.size})")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Grid of questions
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 52.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(totalQuestions) { index ->
                    val isCurrent = (index == currentIndex)
                    val isAnswered = answeredIndices.contains(index)
                    val isRagu = raguIndices.contains(index)

                    val bgColor = when {
                        isRagu -> ExpressiveOrange
                        isAnswered -> ExpressiveGreen
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val textColor = when {
                        isRagu || isAnswered -> Color.White
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(bgColor)
                            .then(
                                if (isCurrent) Modifier.border(
                                    3.dp,
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(14.dp)
                                ) else Modifier
                            )
                            .clickable {
                                onSelectQuestion(index)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ============================================================
// ANTI-CHEAT VIOLATION DIALOG
// ============================================================
@Composable
fun AntiCheatViolationDialog(
    violationCount: Int,
    maxViolations: Int = 3,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(ExpressiveRedContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = ExpressiveRed,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        title = {
            Text(
                text = "Peringatan Pelanggaran!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ExpressiveRed,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Anda terdeteksi keluar dari layar ujian atau berpindah aplikasi.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ExpressiveRedContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Pelanggaran $violationCount dari $maxViolations kali\nPelanggaran ke-$maxViolations akan otomatis MENGUNCI ujian Anda!",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = ExpressiveRed,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ExpressiveRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Saya Mengerti & Lanjutkan", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

// ============================================================
// LOCKED EXAM SCREEN (PELANGGARAN >= 3)
// ============================================================
@Composable
fun LockedExamScreen(
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ExpressiveRedContainer.copy(alpha = 0.5f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(ExpressiveRedContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = ExpressiveRed,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Ujian Anda Dikunci",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = ExpressiveRed
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Sistem mendeteksi 3 kali pelanggaran anti-cheat (keluar dari aplikasi ujian, berpindah tab/aplikasi, atau mencoba mengambil tangkapan layar).",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Akses ujian ini hanya bisa dibuka kembali oleh Guru Pengawas setelah di-reset.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onBackToDashboard,
                    colors = ButtonDefaults.buttonColors(containerColor = ExpressiveRed),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Kembali ke Halaman Utama", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
