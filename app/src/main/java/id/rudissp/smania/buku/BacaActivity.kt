package id.rudissp.smania.buku

import id.rudissp.smania.LoadingIndicator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import id.rudissp.smania.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BacaActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FITUR KEAMANAN: Mencegah Screenshot dan Screen Recording
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        enableEdgeToEdge()

        val pdfUrl = intent.getStringExtra("PDF_URL") ?: ""
        val judulBuku = intent.getStringExtra("JUDUL_BUKU") ?: "Membaca Buku"
        val slug = intent.getStringExtra("SLUG") ?: ""

        if (pdfUrl.isEmpty()) {
            Toast.makeText(this, "Tautan file tidak valid", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            AppTheme {
                BacaScreen(
                    pdfUrl = pdfUrl,
                    slug = slug,
                    judul = judulBuku,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacaScreen(
    pdfUrl: String,
    slug: String = "",
    judul: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var pdfFile by remember { mutableStateOf<File?>(null) }

    var isDownloading by remember { mutableStateOf(true) }
    var downloadError by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedMb by remember { mutableStateOf("0.0") }
    var totalMb by remember { mutableStateOf("0.0") }

    // State Fullscreen
    var isFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(isFullscreen) {
        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(pdfUrl, slug) {
        val localFile = BukuStorageManager.getDownloadedFile(context, slug, pdfUrl)
        if (localFile != null) {
            pdfFile = localFile
            isDownloading = false
        } else {
            val result = BukuStorageManager.downloadBook(
                context = context,
                slug = slug,
                pdfUrl = pdfUrl,
                onProgress = { progress, currentBytes, totalBytes ->
                    if (progress >= 0f) {
                        downloadProgress = progress
                        downloadedMb = String.format("%.1f", currentBytes / (1024f * 1024f))
                        totalMb = String.format("%.1f", totalBytes / (1024f * 1024f))
                    } else {
                        downloadedMb = String.format("%.1f", currentBytes / (1024f * 1024f))
                        totalMb = "0.0"
                    }
                }
            )

            result.onSuccess { file ->
                pdfFile = file
                isDownloading = false
            }.onFailure {
                downloadError = true
                isDownloading = false
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            AnimatedVisibility(
                visible = !isFullscreen,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = judul,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullscreen) PaddingValues(0.dp) else paddingValues)
        ) {
            when {
                isDownloading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(96.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Mengunduh Buku...",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (totalMb != "0.0") {
                            Text(
                                text = "$downloadedMb MB / $totalMb MB",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "${(downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                            LoadingIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
                downloadError -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Gagal Memuat Buku",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Pastikan koneksi internet stabil saat pertama kali mengunduh.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = onBack,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Kembali")
                                }
                            }
                        }
                    }
                }
                pdfFile != null -> {
                    NativePdfViewer(
                        file = pdfFile!!,
                        isFullscreen = isFullscreen,
                        onFullscreenToggle = { isFullscreen = !isFullscreen }
                    )
                }
            }
        }
    }
}

@Composable
fun NativePdfViewer(
    file: File,
    isFullscreen: Boolean,
    onFullscreenToggle: () -> Unit
) {
    val context = LocalContext.current
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpPageInput by remember { mutableStateOf("") }

    var isToolbarVisible by remember { mutableStateOf(true) }
    var previousIndex by remember { mutableIntStateOf(0) }
    var previousOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val currentIndex = listState.firstVisibleItemIndex
        val currentOffset = listState.firstVisibleItemScrollOffset
        if (currentIndex > previousIndex || (currentIndex == previousIndex && currentOffset > previousOffset)) {
            isToolbarVisible = false
        } else if (currentIndex < previousIndex || currentOffset < previousOffset) {
            isToolbarVisible = true
        }
        previousIndex = currentIndex
        previousOffset = currentOffset
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val currentVisiblePage = if (pageCount > 0) {
        listState.firstVisibleItemIndex + 1
    } else {
        0
    }

    DisposableEffect(file) {
        val parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(parcelFileDescriptor)
        pdfRenderer = renderer
        pageCount = renderer.pageCount

        onDispose {
            renderer.close()
            parcelFileDescriptor.close()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceDim)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 4f)
                    if (scale == 1f) {
                        offsetX = 0f
                        offsetY = 0f
                    } else {
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            isToolbarVisible = !isToolbarVisible
                        },
                        onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2.5f
                            offsetX = 0f
                            offsetY = 0f
                        }
                    )
                },
            contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(pageCount) { index ->
                PdfPage(pdfRenderer = pdfRenderer, pageIndex = index)
            }
        }

        // Floating Toolbar Ekspresif
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = isToolbarVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    tonalElevation = 4.dp,
                    shadowElevation = 12.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ExpressiveIconButton(
                            onClick = {
                                val target = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                                coroutineScope.launch {
                                    listState.animateScrollToItem(target)
                                }
                            },
                            enabled = currentVisiblePage > 1,
                            icon = Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = "Sebelumnya"
                        )

                        TextButton(
                            onClick = {
                                jumpPageInput = currentVisiblePage.toString()
                                showJumpDialog = true
                            }
                        ) {
                            Text(
                                text = "$currentVisiblePage / $pageCount",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }

                        ExpressiveIconButton(
                            onClick = {
                                val target = (listState.firstVisibleItemIndex + 1).coerceAtMost(pageCount - 1)
                                coroutineScope.launch {
                                    listState.animateScrollToItem(target)
                                }
                            },
                            enabled = currentVisiblePage < pageCount,
                            icon = Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Selanjutnya"
                        )

                        VerticalDivider(
                            modifier = Modifier
                                .height(28.dp)
                                .padding(horizontal = 6.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f)
                        )

                        ExpressiveIconButton(
                            onClick = onFullscreenToggle,
                            icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen"
                        )

                        ExpressiveIconButton(
                            onClick = {
                                jumpPageInput = currentVisiblePage.toString()
                                showJumpDialog = true
                            },
                            icon = Icons.Default.MoreVert, // Menggantikan ikon burger
                            contentDescription = "Menu"
                        )
                    }
                }
            }
        }

        if (showJumpDialog) {
            AlertDialog(
                onDismissRequest = { showJumpDialog = false },
                title = {
                    Text(
                        text = "Lompat Halaman",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Masukkan nomor halaman (1 - $pageCount)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = jumpPageInput,
                            onValueChange = { jumpPageInput = it },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            label = { Text("Halaman") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val pageNum = jumpPageInput.toIntOrNull()
                            if (pageNum != null && pageNum in 1..pageCount) {
                                coroutineScope.launch {
                                    listState.scrollToItem(pageNum - 1)
                                }
                                showJumpDialog = false
                            } else {
                                Toast.makeText(context, "Nomor halaman tidak valid", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Lompat")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showJumpDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }
    }
}

@Composable
fun ExpressiveIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: ImageVector,
    contentDescription: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = Modifier.scale(scale)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun PdfPage(pdfRenderer: PdfRenderer?, pageIndex: Int) {
    if (pdfRenderer == null) return
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex, pdfRenderer) {
        withContext(Dispatchers.IO) {
            try {
                val page = pdfRenderer.openPage(pageIndex)

                val displayMetrics = context.resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels

                val scale = screenWidth.toFloat() / page.width.toFloat()
                val bitmapWidth = (page.width * scale).toInt()
                val bitmapHeight = (page.height * scale).toInt()

                val bmp = createBitmap(bitmapWidth, bitmapHeight)
                bmp.eraseColor(android.graphics.Color.WHITE)

                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (bitmap != null) {
        Surface(
            modifier = Modifier.padding(horizontal = 8.dp),
            shadowElevation = 2.dp
        ) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Halaman ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .padding(horizontal = 8.dp)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}