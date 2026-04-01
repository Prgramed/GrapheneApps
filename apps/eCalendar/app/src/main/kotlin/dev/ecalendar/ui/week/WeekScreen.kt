package dev.ecalendar.ui.week

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ecalendar.domain.model.CalendarEvent
import dev.ecalendar.ui.CalendarViewModel
import dev.ecalendar.ui.components.CalendarHeader
import dev.ecalendar.util.ColorPalette
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private const val PAGE_COUNT = Int.MAX_VALUE
private const val INITIAL_PAGE = PAGE_COUNT / 2
private val HOUR_HEIGHT = 60.dp
private val HOUR_LABEL_WIDTH = 48.dp
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun WeekScreen(
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
    val baseMonday = remember { today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }

    val pagerState = rememberPagerState(
        initialPage = INITIAL_PAGE,
        pageCount = { PAGE_COUNT },
    )

    // Sync pager with activeDate
    LaunchedEffect(activeDate) {
        val targetMonday = activeDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekOffset = ((targetMonday.toEpochDay() - baseMonday.toEpochDay()) / 7).toInt()
        val targetPage = INITIAL_PAGE + weekOffset
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    // Update ViewModel when pager settles
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val weekOffset = page - INITIAL_PAGE
                val monday = baseMonday.plusWeeks(weekOffset.toLong())
                if (activeDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) != monday) {
                    viewModel.navigate(monday)
                }
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        CalendarHeader(
            activeDate = activeDate,
            activeView = activeView,
            syncState = syncState,
            onPrevious = { viewModel.navigate(activeDate.minusWeeks(1)) },
            onNext = { viewModel.navigate(activeDate.plusWeeks(1)) },
            onToday = { viewModel.goToToday() },
            onViewSelected = { viewModel.setView(it) },
            onAccounts = onAccounts,
        )

        // Week pages
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            val weekOffset = page - INITIAL_PAGE
            val monday = baseMonday.plusWeeks(weekOffset.toLong())
            val weekDays = (0L until 7L).map { monday.plusDays(it) }

            WeekPage(
                weekDays = weekDays,
                today = today,
                activeDate = activeDate,
                viewModel = viewModel,
                onEventClick = onEventClick,
                onCreateEvent = onCreateEvent,
            )
        }
    }
}

@Composable
private fun WeekPage(
    weekDays: List<LocalDate>,
    today: LocalDate,
    activeDate: LocalDate,
    viewModel: CalendarViewModel,
    onEventClick: (String, Long) -> Unit,
    onCreateEvent: (Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val rangeStart = weekDays.first().atStartOfDay(zone).toInstant().toEpochMilli()
    val rangeEnd = weekDays.last().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val events by viewModel.eventsForRange(rangeStart, rangeEnd)
        .collectAsStateWithLifecycle(emptyList())

    // Separate all-day vs timed events
    val allDayEvents = events.filter { it.isAllDay }
    val timedEvents = events.filter { !it.isAllDay }

    // Group timed events by day
    val eventsByDay = remember(timedEvents) {
        timedEvents.groupBy { event ->
            java.time.Instant.ofEpochMilli(event.instanceStart)
                .atZone(zone).toLocalDate()
        }
    }

    val density = LocalDensity.current
    val scrollState = rememberScrollState(
        initial = with(density) { (HOUR_HEIGHT * 8).roundToPx() }, // Start at 8am
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Day headers
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(HOUR_LABEL_WIDTH))
            weekDays.forEach { date ->
                val isToday = date == today
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = date.dayOfWeek.name.take(3),
                        fontSize = 11.sp,
                        color = if (isToday) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = date.dayOfMonth.toString(),
                        fontSize = 16.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // All-day events strip
        if (allDayEvents.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = HOUR_LABEL_WIDTH, end = 2.dp, top = 2.dp, bottom = 2.dp),
            ) {
                // Simplified: show all-day event chips
                allDayEvents.take(3).forEach { event ->
                    val isDark = isSystemInDarkTheme()
                    val color = ColorPalette.forTheme(event.colorHex ?: "#4285F4", isDark)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(1.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color.copy(alpha = 0.8f))
                            .clickable { onEventClick(event.uid, event.instanceStart) }
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = event.title,
                            fontSize = 10.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                        // Hour label
                        Text(
                            text = "%02d:00".format(hour),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .width(HOUR_LABEL_WIDTH)
                                .padding(end = 4.dp, top = 0.dp),
                            textAlign = TextAlign.End,
                        )
                        // Hour line
                        HorizontalDivider(
                            modifier = Modifier.padding(start = HOUR_LABEL_WIDTH),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )
                    }
                }
            }

            // Event blocks per day column
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalHeight)
                    .padding(start = HOUR_LABEL_WIDTH),
            ) {
                weekDays.forEach { date ->
                    val dayEvents = eventsByDay[date] ?: emptyList()
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        dayEvents.forEach { event ->
                            key(event.uid, event.instanceStart) {
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
                                    .padding(horizontal = 1.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(color.copy(alpha = 0.85f))
                                    .clickable { onEventClick(event.uid, event.instanceStart) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            ) {
                                Column {
                                    Text(
                                        text = event.title,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (blockHeight > 30f) {
                                        val startTime = java.time.Instant.ofEpochMilli(event.instanceStart)
                                            .atZone(zone).toLocalTime().format(TIME_FORMAT)
                                        Text(
                                            text = startTime,
                                            fontSize = 9.sp,
                                            color = Color.White.copy(alpha = 0.7f),
                                        )
                                    }
                                }
                            }
                            }
                        }
                    }
                }
            }

            // Current time line
            val now = LocalTime.now()
            val nowMinutes = now.hour * 60 + now.minute
            val nowOffset = (nowMinutes.toFloat() / 60f) * HOUR_HEIGHT.value
            if (weekDays.contains(today)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .offset(y = nowOffset.dp)
                        .padding(start = HOUR_LABEL_WIDTH),
                ) {
                    drawLine(
                        color = Color.Red,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 2f,
                    )
                    drawCircle(
                        color = Color.Red,
                        radius = 4f,
                        center = Offset(0f, 0f),
                    )
                }
            }
        }
    }
}
