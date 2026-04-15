package dev.ecalendar.ical

import dev.ecalendar.domain.model.CalendarEvent
import dev.ecalendar.domain.model.EventSeries
import dev.ecalendar.util.TimeZoneHelper
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Period
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.RRule
import java.io.StringReader
import java.time.LocalDate
import java.time.ZoneId

object RecurrenceExpander {

    private const val MAX_INSTANCES = 500

    /**
     * Expands an EventSeries into concrete CalendarEvent instances within the given range.
     * Non-recurring events return a single instance.
     * Recurring events are expanded using ical4j's recurrence support.
     * Hard cap: max 500 instances per series.
     */
    fun expand(
        series: EventSeries,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
    ): List<CalendarEvent> {
        // WARNING: do not share CalendarBuilder — not thread-safe
        val builder = CalendarBuilder()
        val calendar = builder.build(StringReader(series.rawIcs))
        val event = calendar.getComponents<VEvent>(Component.VEVENT).firstOrNull()
            ?: return emptyList()

        val dtStart = event.getProperty<DtStart>(Property.DTSTART) ?: return emptyList()
        val isAllDay = dtStart.date !is DateTime
        val tzId = dtStart.getParameter<net.fortuna.ical4j.model.parameter.TzId>("TZID")?.value

        // Calculate event duration
        val durationMillis = getEventDurationMillis(event, dtStart)

        val rrule = event.getProperty<RRule>(Property.RRULE)

        // Collect EXDATE values for filtering
        val exDates = event.getProperties<net.fortuna.ical4j.model.property.ExDate>(Property.EXDATE)
            .flatMap { it.dates }
            .map { it.time }
            .toSet()

        val zone = ZoneId.systemDefault()
        val periodStart = DateTime(rangeStart.atStartOfDay(zone).toInstant().toEpochMilli())
        val periodEnd = DateTime(rangeEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())

        return if (rrule != null) {
            // Recurring event — expand
            val period = Period(periodStart, periodEnd)
            val periods = event.calculateRecurrenceSet(period)

            periods.take(MAX_INSTANCES).mapNotNull { p ->
                val startMillis = p.start.time
                // Filter EXDATE
                if (startMillis in exDates) return@mapNotNull null

                CalendarEvent(
                    uid = series.uid,
                    instanceStart = startMillis,
                    instanceEnd = startMillis + durationMillis,
                    title = event.summary?.value ?: "",
                    location = event.location?.value,
                    notes = event.description?.value,
                    url = event.getProperty<net.fortuna.ical4j.model.property.Url>(Property.URL)?.value,
                    isAllDay = isAllDay,
                    calendarSourceId = series.calendarSourceId,
                    travelTimeMins = parseTravelTime(event),
                )
            }
        } else {
            // Non-recurring — single instance
            val startMillis = TimeZoneHelper.toLocalMillis(dtStart.date, tzId)
            val endMillis = startMillis + durationMillis

            // Check if within range
            if (endMillis < periodStart.time || startMillis > periodEnd.time) {
                emptyList()
            } else {
                listOf(
                    CalendarEvent(
                        uid = series.uid,
                        instanceStart = startMillis,
                        instanceEnd = endMillis,
                        title = event.summary?.value ?: "",
                        location = event.location?.value,
                        notes = event.description?.value,
                        url = event.getProperty<net.fortuna.ical4j.model.property.Url>(Property.URL)?.value,
                        isAllDay = isAllDay,
                        calendarSourceId = series.calendarSourceId,
                        travelTimeMins = parseTravelTime(event),
                    ),
                )
            }
        }
    }

    private fun getEventDurationMillis(event: VEvent, dtStart: DtStart): Long {
        // Try DTEND first
        val dtEnd = event.getProperty<DtEnd>(Property.DTEND)
        if (dtEnd != null) {
            val raw = dtEnd.date.time - dtStart.date.time
            val isAllDay = dtStart.date !is DateTime
            // Defensive: older app versions (and some third-party clients) emit
            // DTEND == DTSTART for single-day all-day events, which violates
            // RFC 5545 and makes the instance zero-length → invisible in the
            // timeline query. Treat any non-positive duration on an all-day
            // event as one full day.
            return if (isAllDay && raw <= 0L) 24L * 60 * 60 * 1000 else raw
        }

        // Try DURATION — parse the value string directly
        val duration = event.getProperty<Duration>(Property.DURATION)
        if (duration != null) {
            val value = duration.value ?: ""
            val clean = value.removePrefix("-").removePrefix("+")
            var totalSecs = 0L
            Regex("(\\d+)W").find(clean)?.let { totalSecs += it.groupValues[1].toLong() * 7 * 24 * 60 * 60 }
            Regex("(\\d+)D").find(clean)?.let { totalSecs += it.groupValues[1].toLong() * 24 * 60 * 60 }
            Regex("(\\d+)H").find(clean)?.let { totalSecs += it.groupValues[1].toLong() * 60 * 60 }
            Regex("(\\d+)M").find(clean)?.let { totalSecs += it.groupValues[1].toLong() * 60 }
            Regex("(\\d+)S").find(clean)?.let { totalSecs += it.groupValues[1].toLong() }
            return totalSecs * 1000L
        }

        // All-day with no end = 1 day; timed with no end = 0
        return if (dtStart.date !is DateTime) 24L * 60 * 60 * 1000 else 0L
    }

    private fun parseTravelTime(event: VEvent): Int? {
        val prop = event.getProperty<net.fortuna.ical4j.model.property.XProperty>("X-APPLE-TRAVEL-DURATION")
            ?: return null
        // Value like "PT30M" → 30
        val value = prop.value ?: return null
        val match = Regex("PT(\\d+)M").find(value) ?: return null
        return match.groupValues[1].toIntOrNull()
    }
}
