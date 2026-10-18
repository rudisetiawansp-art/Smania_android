package id.rudissp.smania.rapor

import id.rudissp.smania.LoadingIndicator
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import id.rudissp.smania.AppConstants
import id.rudissp.smania.AppTheme
import org.json.JSONObject

class ActivityRapor : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private var tabDataList by mutableStateOf<List<SemesterTab>>(emptyList())
    private var selectedTabIndex by mutableStateOf(0)
    private var isLoading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = getSharedPreferences("PREFS", MODE_PRIVATE)

        val nis = prefs.getString("nis", "") ?: ""
        val tingkat = prefs.getString("tingkat", "") ?: ""

        setContent {
            AppTheme {
                RaporScreen(
                    tabDataList = tabDataList,
                    selectedTabIndex = selectedTabIndex,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onTabSelected = { selectedTabIndex = it },
                    onBack = { finish() }
                )
            }
        }

        fetchData(nis, tingkat)
    }

    private fun fetchData(nis: String, tingkat: String) {
        isLoading = true
        errorMessage = null
        val url = AppConstants.BASE_URL + "api_rapor.php?nis=$nis&tingkat=$tingkat"
        val queue = Volley.newRequestQueue(this)

        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                isLoading = false
                try {
                    if (response.getBoolean("status")) {
                        val data = response.getJSONObject("data")
                        parseData(data)
                    } else {
                        errorMessage = response.optString("message", "Gagal memuat data rapor")
                    }
                } catch (_: Exception) {
                    errorMessage = "Gagal memproses data rapor."
                }
            },
            {
                isLoading = false
                errorMessage = "Gagal terhubung ke server."
            }
        )
        queue.add(request)
    }

    private fun parseData(data: JSONObject) {
        val semestersObj = data.getJSONObject("semester_data")
        val keys = semestersObj.keys()
        val newList = mutableListOf<SemesterTab>()

        while (keys.hasNext()) {
            val key = keys.next()
            val smtData = semestersObj.getJSONObject(key)

            val mapelArray = smtData.getJSONArray("mapel")
            val mapelList = mutableListOf<Mapel>()
            for (i in 0 until mapelArray.length()) {
                val mapelObj = mapelArray.getJSONObject(i)
                mapelList.add(Mapel(mapelObj.getString("nama"), mapelObj.getString("nilai")))
            }

            newList.add(
                SemesterTab(
                    title = key,
                    rank = smtData.getString("rank"),
                    sum = smtData.getString("sum"),
                    rataRata = smtData.getString("rata_rata"),
                    mapelList = mapelList
                )
            )
        }

        val rataKeseluruhanArray = data.getJSONArray("rata_rata_keseluruhan")
        if (rataKeseluruhanArray.length() > 0) {
            val rataList = mutableListOf<Mapel>()
            for (i in 0 until rataKeseluruhanArray.length()) {
                val rObj = rataKeseluruhanArray.getJSONObject(i)
                rataList.add(Mapel(rObj.getString("nama"), rObj.getString("nilai")))
            }
            newList.add(
                SemesterTab(
                    title = "Rata-Rata",
                    rank = "-",
                    sum = "-",
                    rataRata = data.getString("grand_rata_rata"),
                    mapelList = rataList
                )
            )
        }

        tabDataList = newList
        if (selectedTabIndex >= newList.size) {
            selectedTabIndex = 0
        }
    }
}

data class Mapel(val nama: String, val nilai: String)

data class SemesterTab(
    val title: String,
    val rank: String,
    val sum: String,
    val rataRata: String,
    val mapelList: List<Mapel>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaporScreen(
    tabDataList: List<SemesterTab>,
    selectedTabIndex: Int,
    isLoading: Boolean,
    errorMessage: String?,
    onTabSelected: (Int) -> Unit,
    onBack: () -> Unit
) {
    val currentTab = tabDataList.getOrNull(selectedTabIndex)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Rapor Akademik",
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 40.dp),
                // Jarak vertikal difokuskan hanya untuk memisahkan grup besar
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {

                // 1. Expressive Semester Filter Chips
                if (tabDataList.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Pilih Semester",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                tabDataList.forEachIndexed { index, tab ->
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val isPressed by interactionSource.collectIsPressedAsState()
                                    val scale by animateFloatAsState(
                                        targetValue = if (isPressed) 0.9f else 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )

                                    FilterChip(
                                        selected = (index == selectedTabIndex),
                                        onClick = { onTabSelected(index) },
                                        label = {
                                            Text(
                                                text = tab.title,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                        },
                                        interactionSource = interactionSource,
                                        shape = CircleShape,
                                        modifier = Modifier.scale(scale),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Expressive Summary Stats Row
                if (currentTab != null) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RaporStatCard(
                                modifier = Modifier.weight(1f),
                                title = "Peringkat",
                                value = currentTab.rank,
                                icon = Icons.Filled.EmojiEvents
                            )
                            RaporStatCard(
                                modifier = Modifier.weight(1f),
                                title = "Total Nilai",
                                value = currentTab.sum,
                                icon = Icons.Filled.Functions
                            )
                            RaporStatCard(
                                modifier = Modifier.weight(1f),
                                title = "Rata-Rata",
                                value = currentTab.rataRata,
                                icon = Icons.AutoMirrored.Filled.ShowChart
                            )
                        }
                    }
                }


                // 3. Header Daftar Nilai
                if (currentTab != null && currentTab.mapelList.isNotEmpty()) {
                    item {
                        Text(
                            text = "Detail Nilai",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // 4. M3 Expressive Grouped List (Seamless without gaps)
                    val listSize = currentTab.mapelList.size
                    itemsIndexed(currentTab.mapelList) { index, mapel ->
                        // Sudut membulat ekstrem hanya pada tepi terluar grup
                        val itemShape = when {
                            listSize == 1 -> RoundedCornerShape(28.dp)
                            index == 0 -> RoundedCornerShape(
                                topStart = 28.dp, topEnd = 28.dp,
                                bottomStart = 0.dp, bottomEnd = 0.dp
                            )
                            index == listSize - 1 -> RoundedCornerShape(
                                topStart = 0.dp, topEnd = 0.dp,
                                bottomStart = 28.dp, bottomEnd = 28.dp
                            )
                            else -> RoundedCornerShape(0.dp)
                        }

                        Surface(
                            shape = itemShape,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                RaporMapelItem(mapel = mapel)

                            }
                        }
                    }
                } else if (!isLoading) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Assessment,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = errorMessage ?: "Belum ada data nilai rapor",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f)),
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
fun RaporStatCard(modifier: Modifier = Modifier, title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun RaporMapelItem(mapel: Mapel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = mapel.nama,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.width(16.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Text(
                text = mapel.nilai,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RaporScreenPreview() {
    AppTheme {
        RaporScreen(
            tabDataList = listOf(
                SemesterTab(
                    title = "Semester 1",
                    rank = "3",
                    sum = "2640",
                    rataRata = "88.00",
                    mapelList = listOf(
                        Mapel("Pendidikan Agama Islam", "90"),
                        Mapel("Matematika", "85"),
                        Mapel("Bahasa Indonesia", "88")
                    )
                ),
                SemesterTab(
                    title = "Semester 2",
                    rank = "2",
                    sum = "2700",
                    rataRata = "90.00",
                    mapelList = listOf(
                        Mapel("Pendidikan Agama Islam", "92"),
                        Mapel("Matematika", "88"),
                        Mapel("Bahasa Indonesia", "90")
                    )
                )
            ),
            selectedTabIndex = 0,
            isLoading = false,
            errorMessage = null,
            onTabSelected = {},
            onBack = {}
        )
    }
}