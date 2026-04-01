package dev.ecalendar.ui.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ecalendar.domain.model.CalendarEvent
import dev.ecalendar.domain.model.CalendarSource
import dev.ecalendar.sync.SyncState
import dev.ecalendar.ui.CalendarViewModel
import dev.ecalendar.ui.components.CalendarHeader
import dev.ecalendar.util.ColorPalette
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val LOAD_DAYS = 30
private const val LOAD_THRESHOLD = 5

private val DATE_HEADER_FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

private sealed class AgendaItem {
    data class DateHeader(val date: LocalDate) : AgendaItem()
    data class EventRow(val event: CalendarEvent, val date: LocalDate) : AgendaItem()
    data class EmptyDays(val from: LocalDate, val to: LocalDate) : AgendaItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    viewModel: CalendarViewModel,
    onEventClick: (String, Long) -> Unit = { _, _ -> },
    onCreateEvent: (Long) -> Unit = {},
    onAccounts: (() -> Unit)? = null,
    onScrollDirectionChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val activeDate by viewModel.activeDate.collectAsStateWithLifecycle()
    val activeView by viewModel.activeView.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()

    var rangeStart by remember { mutableStateOf(activeDate.minusDays(LOAD_DAYS.toLong())) }
    var rangeEnd by remember { mutableStateOf(activeDate.plusDays(LOAD_DAYS.toLong())) }

    val startMillis = rangeStart.atStartOfDay(zone).toInstant().toEpochMilli()
    val endMillis = rangeEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val events by viewModel.eventsForRange(startMillis, endMillis)
        .collectAsStateWithLifecycle(emptyList())

    val calendarMap = remember(calendars) {
        calendars.associateBy { it.id }
    }

    // Group events by day
    val eventsByDay = remember(events) {
        events.groupBy { event ->
            java.time.Instant.ofEpochMilli(event.instanceStart)
                .atZone(zone).toLocalDate()
        }
    }

    // Build agenda items
    val agendaItems = remember(eventsByDay, rangeStart, rangeEnd) {
        buildAgendaItems(rangeStart, rangeEnd, eventsByDay, today)
    }

    // Find initial scroll index (today or activeDate)
    val initialIndex = remember(agendaItems, activeDate) {
        agendaItems.indexOfFirst { item ->
            when (item) {
                is AgendaItem.DateHeader -> item.date == activeDate
                is AgendaItem.EventRow -> item.date == activeDate
                is AgendaItem.EmptyDays -> activeDate in item.from..item.to
            }
        }.coerceAtLeast(0)
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    // Load more when scrolling near edges
    LaunchedEffect(listState) {
        snapshotFlow {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val firstVisible = listState.firstVisibleItemIndex
            Triple(firstVisible, lastVisible, total)
        }.distinctUntilChanged().collect { (first, last, total) ->
            if (first < LOAD_THRESHOLD) {
                rangeStart = rangeStart.minusDays(LOAD_DAYS.toLong())
            }
            if (total > 0 && last > total - LOAD_THRESHOLD) {
                rangeEnd = rangeEnd.plusDays(LOAD_DAYS.toLong())
            }
        }
    }

    // Track scroll direction for FAB visibility
    LaunchedEffect(listState) {
        var lastIndex = listState.firstVisibleItemIndex
        var lastOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val scrollingDown = index > lastIndex || (index == lastIndex && offset > lastOffset)
                onScrollDirectionChanged?.invoke(scrollingDown)
                lastIndex = index
                lastOffset = offset
            }
    }

    // Scroll to activeDate when it changes externally
    LaunchedEffect(activeDate) {
        val idx = agendaItems.indexOfFirst { item ->
            when (item) {
                is AgendaItem.DateHeader -> item.date == activeDate
                is AgendaItem.EventRow -> item.date == activeDate
                is AgendaItem.EmptyDays -> activeDate in item.from..item.to
            }
        }
        if (idx >= 0) listState.animateScrollToItem(idx)
    }

    val isRefreshing = syncState is SyncState.Syncing

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

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.syncNow() },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (events.isEmpty() && isRefreshing) {
                // Shimmer loading state
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                ) {
                    repeat(5) {
                        Spacer(Modifier.height(12.dp))
                        dev.ecalendar.ui.components.ShimmerBox(
                            Modifier.fillMaxWidth().height(16.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        dev.ecalendar.ui.components.ShimmerBox(
                            Modifier.fillMaxWidth(0.7f).height(48.dp),
                        )
                    }
                }
            } else if (events.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Your calendar is empty",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = agendaItems,
                        key = { item ->
                            when (item) {
                                is AgendaItem.DateHeader -> "h_${item.date}"
                                is AgendaItem.EventRow -> "e_${item.event.uid}_${item.event.instanceStart}"
                                is AgendaItem.EmptyDays -> "empty_${item.from}"
                            }
                        },
                    ) { item ->
                        when (item) {
                            is AgendaItem.DateHeader -> StickyDateHeader(
                                date = item.date,
                                today = today,
                            )

                            is AgendaItem.EventRow -> AgendaEventCard(
                                event = item.event,
                                calendarSource = calendarMap[item.event.calendarSourceId],
                                onClick = { onEventClick(item.event.uid, item.event.instanceStart) },
                            )

                            is AgendaItem.EmptyDays -> EmptyDaysRow(
                                from = item.from,
                                to = item.to,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildAgendaItems(
    rangeStart: LocalDate,
    rangeEnd: LocalDate,
    eventsByDay: Map<LocalDate, List<CalendarEvent>>,
    today: LocalDate,
): List<AgendaItem> {
    val items = mutableListOf<AgendaItem>()
    var date = rangeStart
    var emptyStreak: LocalDate? = null

    while (!date.isAfter(rangeEnd)) {
        val dayEvents = eventsByDay[date]
        if (dayEvents.isNullOrEmpty()) {
            if (emptyStreak == null) emptyStreak = date
        } else {
            // Flush empty streak
            if (emptyStreak != null) {
                val streakEnd = date.minusDays(1)
                items.add(AgendaItem.EmptyDays(emptyStreak, streakEnd))
                emptyStreak = null
            }
            items.add(AgendaItem.DateHeader(date))
            dayEvents.sortedBy { it.instanceStart }.forEach { event ->
                items.add(AgendaItem.EventRow(event, date))
            }
        }
        date = date.plusDays(1)
    }
    // Trailing empty streak
    if (emptyStreak != null) {
        items.add(AgendaItem.EmptyDays(emptyStreak, rangeEnd))
    }
    return items
}

@Composable
private fun StickyDateHeader(
    date: LocalDate,
    today: LocalDate,
) {
    val isToday = date == today
    val isPast = date.isBefore(today)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = if (isToday) "Today — ${date.format(DATE_HEADER_FORMAT)}"
            else date.format(DATE_HEADER_FORMAT),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
            color = when {
                isToday -> MaterialTheme.colorScheme.primary
                isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp),
            color = if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        )
    }
}

@Composable
private fun AgendaEventCard(
    event: CalendarEvent,
    calendarSource: CalendarSource?,
    onClick: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val color = ColorPalette.forTheme(event.colorHex ?: calendarSource?.colorHex ?: "#4285F4", isDark)
    val zone = ZoneId.systemDefault()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Left color bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Title
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Time
            if (event.isAllDay) {
                Text(
                    text = "All day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val startTime = java.time.Instant.ofEpochMilli(event.instanceStart)
                    .atZone(zone).toLocalTime().format(TIME_FORMAT)
                val endTime = java.time.Instant.ofEpochMilli(event.instanceEnd)
                    .atZone(zone).toLocalTime().format(TIME_FORMAT)
                Text(
                    text = "$startTime – $endTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Location
            if (!event.location.isNullOrBlank()) {
                Text(
                    text = event.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Calendar name
            if (calendarSource != null) {
                Text(
                    text = calendarSource.displayName,
                    fontSize = 11.sp,
                    color = color.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun EmptyDaysRow(
    from: LocalDate,
    to: LocalDate,
) {
    val dayCount = (to.toEpochDay() - from.toEpochDay() + 1).toInt()
    val text = if (dayCount == 1) "No events" else "No events for $dayCount days"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "— $text —",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        )
    }
}
