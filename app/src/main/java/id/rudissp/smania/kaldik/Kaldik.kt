package id.rudissp.smania.kaldik

import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import id.rudissp.smania.AppConstants
import id.rudissp.smania.AppTheme
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class AgendaModel(
    val judul: String,
    val waktu: String
)

class Kaldik : ComponentActivity() {

    private val API_KEY = AppConstants.APIkaldik
    private val CALENDAR_ID = AppConstants.APIkaldikID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    KaldikScreen(
                        onBackClick = { finish() },
                        apiKey = API_KEY,
                        calendarId = CALENDAR_ID
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaldikScreen(
    onBackClick: () -> Unit,
    apiKey: String,
    calendarId: String
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var semuaAgenda by remember { mutableStateOf(mapOf<LocalDate, List<AgendaModel>>()) }
    var tanggalTerpilih by remember { mutableStateOf(LocalDate.now()) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }

    val startMonth = remember { currentMonth.minusMonths(3) }
    val endMonth = remember { currentMonth.plusMonths(12) }
    val daysOfWeek = remember { daysOfWeek() }

    val calendarState = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = currentMonth,
        firstDayOfWeek = daysOfWeek.first()
    )

    val formatterDaftar = remember { DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale("id", "ID")) }
    val monthTitleFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale("id", "ID")) }

    // Fetch Calendar Data via Volley
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.MONTH, -2)
        val timeMinRaw = sdf.format(cal.time)

        val timeMinEncoded = URLEncoder.encode(timeMinRaw, "UTF-8")
        val url = "https://www.googleapis.com/calendar/v3/calendars/$calendarId/events?" +
                "key=$apiKey&singleEvents=true&orderBy=startTime&timeMin=$timeMinEncoded&maxResults=200"

        Log.d("DEBUG_KALD", "URL API: $url")

        val queue = Volley.newRequestQueue(context)
        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    val items = response.getJSONArray("items")
                    val newAgendaMap = mutableMapOf<LocalDate, MutableList<AgendaModel>>()

                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val judul = item.getString("summary")

                        val startObj = item.getJSONObject("start")
                        val endObj = item.getJSONObject("end")

                        val startStr = if (startObj.has("date")) {
                            startObj.getString("date")
                        } else {
                            startObj.getString("dateTime").substring(0, 10)
                        }

                        val endStr = if (endObj.has("date")) {
                            endObj.getString("date")
                        } else {
                            endObj.getString("dateTime").substring(0, 10)
                        }

                        val startDate = LocalDate.parse(startStr)
                        val endDate = LocalDate.parse(endStr)

                        val isAllDay = startObj.has("date")
                        val actualEndDate = if (isAllDay) endDate.minusDays(1) else endDate

                        var currentDateIter = startDate
                        while (!currentDateIter.isAfter(actualEndDate)) {
                            val waktuTampil = if (!isAllDay) {
                                startObj.getString("dateTime").substring(11, 16) + " WIB"
                            } else {
                                "Seharian"
                            }

                            if (!newAgendaMap.containsKey(currentDateIter)) {
                                newAgendaMap[currentDateIter] = mutableListOf()
                            }

                            if (newAgendaMap[currentDateIter]?.none { it.judul == judul } == true) {
                                newAgendaMap[currentDateIter]?.add(AgendaModel(judul, waktuTampil))
                            }

                            currentDateIter = currentDateIter.plusDays(1)
                        }
                    }

                    semuaAgenda = newAgendaMap
                } catch (e: Exception) {
                    Log.e("DEBUG_KALD", "Error parsing JSON: ${e.message}", e)
                }
            },
            { error ->
                Log.e("DEBUG_KALD", "Volley Error: ${error.message}")
            }
        )
        queue.add(jsonObjectRequest)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Kalender Akademik",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            // Month Navigation Header (Expressive)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpressiveNavButton(
                        onClick = {
                            coroutineScope.launch {
                                val target = calendarState.firstVisibleMonth.yearMonth.minusMonths(1)
                                if (!target.isBefore(startMonth)) {
                                    calendarState.animateScrollToMonth(target)
                                }
                            }
                        },
                        icon = Icons.Default.ChevronLeft,
                        contentDescription = "Bulan Sebelumnya"
                    )

                    Text(
                        text = calendarState.firstVisibleMonth.yearMonth.format(monthTitleFormatter).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    ExpressiveNavButton(
                        onClick = {
                            coroutineScope.launch {
                                val target = calendarState.firstVisibleMonth.yearMonth.plusMonths(1)
                                if (!target.isAfter(endMonth)) {
                                    calendarState.animateScrollToMonth(target)
                                }
                            }
                        },
                        icon = Icons.Default.ChevronRight,
                        contentDescription = "Bulan Berikutnya"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Days of Week Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                daysOfWeek.forEach { dayOfWeek ->
                    Text(
                        modifier = Modifier.weight(1f),
                        text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("id", "ID")),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Horizontal Calendar View
            Surface(
                modifier = Modifier.padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                HorizontalCalendar(
                    state = calendarState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(vertical = 12.dp),
                    dayContent = { day ->
                        val isSelected = day.date == tanggalTerpilih
                        val hasEvent = semuaAgenda.containsKey(day.date)
                        val isCurrentMonth = day.position == DayPosition.MonthDate

                        ExpressiveCalendarDay(
                            day = day.date.dayOfMonth.toString(),
                            isSelected = isSelected,
                            isCurrentMonth = isCurrentMonth,
                            hasEvent = hasEvent,
                            onClick = {
                                if (isCurrentMonth) {
                                    tanggalTerpilih = day.date
                                }
                            }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Selected Date Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Agenda",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        text = tanggalTerpilih.format(formatterDaftar),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Agenda Harian List
            val agendaHariIni = semuaAgenda[tanggalTerpilih] ?: emptyList()

            if (agendaHariIni.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(agendaHariIni) { agenda ->
                        AgendaItemCard(agenda = agenda)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.EventNote,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tidak ada agenda",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Anda bebas beraktivitas pada hari ini.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveNavButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f),
        modifier = Modifier
            .size(40.dp)
            .scale(scale)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun ExpressiveCalendarDay(
    day: String,
    isSelected: Boolean,
    isCurrentMonth: Boolean,
    hasEvent: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && isCurrentMonth) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(4.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> Color.Transparent
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isCurrentMonth,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = day,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected || isPressed) FontWeight.ExtraBold else FontWeight.Medium,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    isCurrentMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                }
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Indikator Titik Event
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasEvent && isCurrentMonth) {
                            if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        }
                    )
            )
        }
    }
}

@Composable
fun AgendaItemCard(agenda: AgendaModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.EventNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = agenda.waktu,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = agenda.judul,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}