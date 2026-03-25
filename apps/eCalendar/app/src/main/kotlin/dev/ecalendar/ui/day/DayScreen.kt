package dev.ecalendar.ui.day

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ecalendar.domain.model.CalendarEvent
import dev.ecalendar.ui.CalendarViewModel
import dev.ecalendar.ui.components.CalendarHeader
import dev.ecalendar.util.ColorPalette
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PAGE_COUNT = Int.MAX_VALUE
private const val INITIAL_PAGE = PAGE_COUNT / 2
private val HOUR_HEIGHT = 64.dp
private val HOUR_LABEL_WIDTH = 48.dp
private val DAY_HEADER_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun DayScreen(
    viewModel: CalendarViewModel,
    onEventClick: (String, Long) -> Unit = { _, _ -> },
    onCreateEvent: (Long) -> Unit = {},
    onAccounts: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val activeDate by viewModel.activeDate.collectAsStateWithLifecycle()
    val activeView by viewModel.activeView.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    val baseDate = remember { today }

    val pagerState = rememberPagerState(
        initialPage = INITIAL_PAGE,
        pageCount = { PAGE_COUNT },
    )

    LaunchedEffect(activeDate) {
        val dayOffset = (activeDate.toEpochDay() - baseDate.toEpochDay()).toInt()
        val targetPage = INITIAL_PAGE + dayOffset
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val dayOffset = (page - INITIAL_PAGE).toLong()
                val date = baseDate.plusDays(dayOffset)
                if (date != activeDate) viewModel.navigate(date)
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        CalendarHeader(
            activeDate = activeDate,
            activeView = activeView,
            syncState = syncState,
            onPrevious = { viewModel.navigate(activeDate.minusDays(1)) },
            onNext = { viewModel.navigate(activeDate.plusDays(1)) },
            onToday = { viewModel.goToToday() },
            onViewSelected = { viewModel.setView(it) },
            onAccounts = onAccounts,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { page ->
            val dayOffset = (page - INITIAL_PAGE).toLong()
            val date = baseDate.plusDays(dayOffset)
            DayPage(
                date = date,
                today = today,
                viewModel = viewModel,
                onEventClick = onEventClick,
                onCreateEvent = onCreateEvent,
            )
        }
    }
}

@Composable
private fun DayPage(
    date: LocalDate,
    today: LocalDate,
    viewModel: CalendarViewModel,
    onEventClick: (String, Long) -> Unit,
    onCreateEvent: (Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val events by viewModel.eventsForRange(dayStart, dayEnd)
        .collectAsStateWithLifecycle(emptyList())

    val allDayEvents = events.filter { it.isAllDay }
    val timedEvents = events.filter { !it.isAllDay }
    val isToday = date == today

    val density = LocalDensity.current
    val scrollState = rememberScrollState(
        initial = with(density) { (HOUR_HEIGHT * 8).roundToPx() },
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Day header
        Text(
            text = date.format(DAY_HEADER_FORMAT),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // All-day strip
        if (allDayEvents.isNotEmpty()) {
            Column(modifier = Modifier.padding(start = HOUR_LABEL_WIDTH, end = 8.dp)) {
                allDayEvents.forEach { event ->
                    val isDark = isSystemInDarkTheme()
                    val color = ColorPalette.forTheme(event.colorHex ?: "#4285F4", isDark)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color.copy(alpha = 0.85f))
                            .clickable { onEventClick(event.uid, event.instanceStart) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(event.title, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }
            HorizontalDivider()
        }

        // Time grid
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            val totalHeight = HOUR_HEIGHT * 24

            // Hour lines + labels
            Column(modifier = Modifier.height(totalHeight)) {
                repeat(24) { hour ->
                    Box(modifier = Modifier.height(HOUR_HEIGHT)) {
                        Text(
                            text = "%02d:00".format(hour),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.width(HOUR_LABEL_WIDTH).padding(end = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = HOUR_LABEL_WIDTH),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )
                    }
                }
            }

            // Event blocks
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalHeight)
                    .padding(start = HOUR_LABEL_WIDTH, end = 8.dp),
            ) {
                if (timedEvents.isEmpty()) {
                    // Friendly empty state centered in working hours
                    val workStart = 8 * HOUR_HEIGHT.value
                    val workEnd = 20 * HOUR_HEIGHT.value
                    val centerY = (workStart + workEnd) / 2
                    Text(
                        text = "Nothing scheduled",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = centerY.dp),
                    )
                }

                timedEvents.forEach { event ->
                    val startMinutes = java.time.Instant.ofEpochMilli(event.instanceStart)
                        .atZone(zone).toLocalTime().let { it.hour * 60 + it.minute }
                    val endMinutes = java.time.Instant.ofEpochMilli(event.instanceEnd)
                        .atZone(zone).toLocalTime().let { it.hour * 60 + it.minute }
                    val durationMinutes = (endMinutes - startMinutes).coerceAtLeast(15)

                    val topOffset = (startMinutes.toFloat() / 60f) * HOUR_HEIGHT.value
                    val blockHeight = (durationMinutes.toFloat() / 60f) * HOUR_HEIGHT.value

                    val isDark = isSystemInDarkTheme()
                    val color = ColorPalette.forTheme(event.colorHex ?: "#4285F4", isDark)

                    Box(
                        modifier = Modifier
                            .offset(y = topOffset.dp)
                            .height(blockHeight.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(color.copy(alpha = 0.85f))
                            .clickable { onEventClick(event.uid, event.instanceStart) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Column {
                            Text(
                                text = event.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!event.location.isNullOrBlank() && blockHeight > 40f) {
                                Text(
                                    text = event.location,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            val startTime = java.time.Instant.ofEpochMilli(event.instanceStart)
                                .atZone(zone).toLocalTime().format(TIME_FORMAT)
                            val endTime = java.time.Instant.ofEpochMilli(event.instanceEnd)
                                .atZone(zone).toLocalTime().format(TIME_FORMAT)
                            Text(
                                text = "$startTime – $endTime",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }

            // Current time line
            if (isToday) {
                val now = LocalTime.now()
                val nowMinutes = now.hour * 60 + now.minute
                val nowOffset = (nowMinutes.toFloat() / 60f) * HOUR_HEIGHT.value
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .offset(y = nowOffset.dp)
                        .padding(start = HOUR_LABEL_WIDTH),
                ) {
                    drawLine(Color.Red, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 2f)
                    drawCircle(Color.Red, radius = 5f, center = Offset(0f, 0f))
                }
            }
        }
    }
}
