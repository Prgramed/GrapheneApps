package dev.ecalendar.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ecalendar.domain.model.CalendarEvent
import dev.ecalendar.sync.SyncState
import dev.ecalendar.ui.CalendarViewModel
import dev.ecalendar.util.ColorPalette
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import dev.ecalendar.ui.components.CalendarHeader
import dev.ecalendar.ui.components.EventChip
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private const val PAGE_COUNT = Int.MAX_VALUE
private const val INITIAL_PAGE = PAGE_COUNT / 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthScreen(
    viewModel: CalendarViewModel,
    onDayClick: (LocalDate) -> Unit = {},
    onEventClick: (String, Long) -> Unit = { _, _ -> },
    onAccounts: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val activeDate by viewModel.activeDate.collectAsStateWithLifecycle()
    val activeView by viewModel.activeView.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()

    // Day-events popup state
    var popupDate by remember { mutableStateOf<LocalDate?>(null) }
    var popupEvents by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }

    if (popupDate != null) {
        DayEventsSheet(
            date = popupDate!!,
            events = popupEvents,
            onDismiss = { popupDate = null },
            onGoToDay = {
                popupDate = null
                onDayClick(it)
            },
            onEventClick = { uid, instanceStart ->
                popupDate = null
                onEventClick(uid, instanceStart)
            },
        )
    }

    val today = LocalDate.now()
    val baseMonth = remember { YearMonth.from(today) }

    val pagerState = rememberPagerState(
        initialPage = INITIAL_PAGE,
        pageCount = { PAGE_COUNT },
    )

    // Sync pager with activeDate changes from outside
    LaunchedEffect(activeDate) {
        val targetMonth = YearMonth.from(activeDate)
        val offset = ((targetMonth.year - baseMonth.year) * 12) + (targetMonth.monthValue - baseMonth.monthValue)
        val targetPage = INITIAL_PAGE + offset
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    // Update ViewModel when pager settles
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val offset = page - INITIAL_PAGE
                val month = baseMonth.plusMonths(offset.toLong())
                val newDate = if (YearMonth.from(activeDate) != month) {
                    month.atDay(activeDate.dayOfMonth.coerceAtMost(month.lengthOfMonth()))
                } else {
                    activeDate
                }
                viewModel.navigate(newDate)
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // clipToBounds on the header Column prevents the view-switcher's 48dp
        // minimum touch targets from leaking downward into the month grid cells.
        Column(modifier = Modifier.clipToBounds()) {
            CalendarHeader(
                activeDate = activeDate,
                activeView = activeView,
                syncState = syncState,
                onPrevious = {
                    val prev = YearMonth.from(activeDate).minusMonths(1)
                    viewModel.navigate(prev.atDay(1))
                },
                onNext = {
                    val next = YearMonth.from(activeDate).plusMonths(1)
                    viewModel.navigate(next.atDay(1))
                },
                onToday = { viewModel.goToToday() },
                onViewSelected = { viewModel.setView(it) },
                onAccounts = onAccounts,
            )
        }

        // Day-of-week labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        ) {
            val daysOfWeek = listOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
            )
            daysOfWeek.forEach { dow ->
                Text(
                    text = dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Month pages — no PullToRefreshBox here because the month grid isn't
        // scrollable, and PullToRefreshBox on non-scrollable content eats touch
        // events (some date cells become non-clickable). Sync is available via
        // Day/Week/Agenda views or Settings.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            val offset = page - INITIAL_PAGE
            val month = baseMonth.plusMonths(offset.toLong())
            MonthGrid(
                month = month,
                activeDate = activeDate,
                today = today,
                viewModel = viewModel,
                onDayClick = { date, events ->
                    viewModel.navigate(date)
                    popupDate = date
                    popupEvents = events
                },
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    activeDate: LocalDate,
    today: LocalDate,
    viewModel: CalendarViewModel,
    onDayClick: (LocalDate, List<CalendarEvent>) -> Unit,
) {
    // Calculate grid days (6 weeks)
    val firstOfMonth = month.atDay(1)
    val firstDayOfWeek = DayOfWeek.MONDAY
    val daysBeforeMonth = (firstOfMonth.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val gridStart = firstOfMonth.minusDays(daysBeforeMonth.toLong())

    val days = (0 until 42).map { gridStart.plusDays(it.toLong()) }

    // Load events for this month range
    val zone = ZoneId.systemDefault()
    val rangeStart = gridStart.atStartOfDay(zone).toInstant().toEpochMilli()
    val rangeEnd = gridStart.plusDays(42).atStartOfDay(zone).toInstant().toEpochMilli()
    val events by viewModel.eventsForRange(rangeStart, rangeEnd)
        .collectAsStateWithLifecycle(emptyList())

    // Group events by day
    val eventsByDay = remember(events) {
        events.groupBy { event ->
            java.time.Instant.ofEpochMilli(event.instanceStart)
                .atZone(zone).toLocalDate()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        for (week in 0 until 6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 2.dp),
            ) {
                for (dayIdx in 0 until 7) {
                    val date = days[week * 7 + dayIdx]
                    val isCurrentMonth = YearMonth.from(date) == month
                    val isToday = date == today
                    val isSelected = date == activeDate
                    val dayEvents = eventsByDay[date] ?: emptyList()

                    DayCell(
                        date = date,
                        isCurrentMonth = isCurrentMonth,
                        isToday = isToday,
                        isSelected = isSelected,
                        events = dayEvents,
                        onClick = { onDayClick(date, dayEvents) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isCurrentMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    events: List<CalendarEvent>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = if (isCurrentMonth) 1f else 0.4f

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.surface
                },
            )
            .clickable(onClick = onClick)
            .padding(2.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Day number
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .then(
                    if (isToday) Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                    else Modifier,
                ),
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                fontSize = 13.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Light,
                color = when {
                    isToday -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                },
            )
        }

        // Event dots (max 3)
        val visibleEvents = events.take(3)
        if (visibleEvents.isNotEmpty()) {
            Spacer(Modifier.height(1.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 2.dp),
            ) {
                visibleEvents.forEach { event ->
                    EventChip(event = event, isCompact = true)
                }
            }
            if (events.size > 3) {
                Text(
                    text = "+${events.size - 3}",
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
    }
    } // Box
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayEventsSheet(
    date: LocalDate,
    events: List<CalendarEvent>,
    onDismiss: () -> Unit,
    onGoToDay: (LocalDate) -> Unit,
    onEventClick: (String, Long) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val tomorrow = remember(today) { today.plusDays(1) }
    val dateLabel = when (date) {
        today -> "Today"
        tomorrow -> "Tomorrow"
        else -> {
            val dow = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            val month = date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            "$dow, $month ${date.dayOfMonth}"
        }
    }
    val isDark = isSystemInDarkTheme()
    val timeFormat = remember { java.time.format.DateTimeFormatter.ofPattern("HH:mm") }
    val zone = remember { ZoneId.systemDefault() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))

            if (events.isEmpty()) {
                Text(
                    text = "No events",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                events.forEach { event ->
                    val color = ColorPalette.forTheme(event.colorHex ?: "#4285F4", isDark)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onEventClick(event.uid, event.instanceStart) }
                            .padding(vertical = 8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(color),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = event.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!event.isAllDay) {
                                val start = java.time.Instant.ofEpochMilli(event.instanceStart)
                                    .atZone(zone).toLocalTime().format(timeFormat)
                                val end = java.time.Instant.ofEpochMilli(event.instanceEnd)
                                    .atZone(zone).toLocalTime().format(timeFormat)
                                Text(
                                    text = "$start – $end",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    text = "All day",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onGoToDay(date) }) {
                    Text("Go to Day")
                }
            }
        }
    }
}
