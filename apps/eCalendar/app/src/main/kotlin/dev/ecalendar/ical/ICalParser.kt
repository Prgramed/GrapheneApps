package dev.ecalendar.ical

import dev.ecalendar.domain.model.EventSeries
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import java.io.StringReader

data class AlarmTrigger(
    val offsetMins: Int,
    val description: String,
)

object ICalParser {

    /**
     * Parses a raw ICS string into an EventSeries domain model.
     * Stores the raw ICS verbatim for round-trip fidelity.
     *
     * WARNING: CalendarBuilder is NOT thread-safe — this method creates
     * a new instance per call. Do not cache or share CalendarBuilder.
     */
    fun parseEventSeries(
        icsString: String,
        calendarSourceId: Long,
        etag: String,
        serverUrl: String,
    ): EventSeries {
        // WARNING: do not share CalendarBuilder — not thread-safe
        val builder = CalendarBuilder()
        val calendar = builder.build(StringReader(icsString))
        val event = calendar.getComponents<VEvent>(Component.VEVENT).firstOrNull()
            ?: throw IllegalArgumentException("No VEVENT found in ICS")

        val uid = event.getProperty<net.fortuna.ical4j.model.property.Uid>(Property.UID)?.value
            ?: throw IllegalArgumentException("VEVENT has no UID")

        return EventSeries(
            uid = uid,
            calendarSourceId = calendarSourceId,
            rawIcs = icsString,
            etag = etag,
            serverUrl = serverUrl,
            isLocal = false,
        )
    }

    /**
     * Parses VEvent fields from raw ICS into values suitable for EditableEvent.
     * Used when editing an existing event so the form pre-fills with the current values
     * instead of showing blank fields (the bug at EventEditViewModel line 66 TODO).
     */
    fun parseEditableFields(icsString: String): EditableFields {
        val builder = CalendarBuilder()
        val calendar = builder.build(StringReader(icsString))
        val event = calendar.getComponents<VEvent>(Component.VEVENT).firstOrNull()
            ?: return EditableFields()

        val dtStart = event.getProperty<net.fortuna.ical4j.model.property.DtStart>(Property.DTSTART)
        val dtEnd = event.getProperty<net.fortuna.ical4j.model.property.DtEnd>(Property.DTEND)
        val isAllDay = dtStart != null && dtStart.date !is net.fortuna.ical4j.model.DateTime

        val startMillis = dtStart?.let {
            val tzId = it.getParameter<net.fortuna.ical4j.model.parameter.TzId>("TZID")?.value
            dev.ecalendar.util.TimeZoneHelper.toLocalMillis(it.date, tzId)
        } ?: System.currentTimeMillis()

        val endMillis = dtEnd?.let {
            val tzId = it.getParameter<net.fortuna.ical4j.model.parameter.TzId>("TZID")?.value
            dev.ecalendar.util.TimeZoneHelper.toLocalMillis(it.date, tzId)
        } ?: (startMillis + 3_600_000)

        val rrule = event.getProperty<net.fortuna.ical4j.model.property.RRule>(Property.RRULE)?.value

        val alarms = event.getComponents<VAlarm>(Component.VALARM).mapNotNull { alarm ->
            val trigger = alarm.trigger ?: return@mapNotNull null
            parseDurationToMinutes(trigger.value ?: return@mapNotNull null)
        }

        val travelTime = event.getProperty<net.fortuna.ical4j.model.property.XProperty>("X-APPLE-TRAVEL-DURATION")
            ?.value?.let { Regex("PT(\\d+)M").find(it)?.groupValues?.get(1)?.toIntOrNull() }

        val attendees = event.getProperties<net.fortuna.ical4j.model.property.Attendee>(Property.ATTENDEE)
            .mapNotNull { it.calAddress?.toString()?.removePrefix("mailto:")?.trim() }

        return EditableFields(
            title = event.summary?.value ?: "",
            location = event.location?.value,
            notes = event.description?.value,
            url = event.getProperty<net.fortuna.ical4j.model.property.Url>(Property.URL)?.value,
            startMillis = startMillis,
            endMillis = endMillis,
            isAllDay = isAllDay,
            rruleString = rrule,
            alarms = alarms,
            attendees = attendees,
            travelTimeMins = travelTime,
        )
    }

    data class EditableFields(
        val title: String = "",
        val location: String? = null,
        val notes: String? = null,
        val url: String? = null,
        val startMillis: Long = System.currentTimeMillis(),
        val endMillis: Long = System.currentTimeMillis() + 3_600_000,
        val isAllDay: Boolean = false,
        val rruleString: String? = null,
        val alarms: List<Int> = emptyList(),
        val attendees: List<String> = emptyList(),
        val travelTimeMins: Int? = null,
    )

    /**
     * Extracts attendee email addresses from an ICS string.
     */
    fun parseAttendees(icsString: String): List<String> {
        val builder = CalendarBuilder()
        val calendar = builder.build(StringReader(icsString))
        val event = calendar.getComponents<VEvent>(Component.VEVENT).firstOrNull()
            ?: return emptyList()

        return event.getProperties<net.fortuna.ical4j.model.property.Attendee>(Property.ATTENDEE)
            .mapNotNull { attendee ->
                attendee.calAddress?.toString()
                    ?.removePrefix("mailto:")
                    ?.trim()
            }
    }

    /**
     * Extracts VALARM components and converts their TRIGGER durations
     * to minutes-before-event offset.
     */
    fun parseAlarms(icsString: String): List<AlarmTrigger> {
        val builder = CalendarBuilder()
        val calendar = builder.build(StringReader(icsString))
        val event = calendar.getComponents<VEvent>(Component.VEVENT).firstOrNull()
            ?: return emptyList()

        return event.getComponents<VAlarm>(Component.VALARM).mapNotNull { alarm ->
            val trigger = alarm.trigger ?: return@mapNotNull null

            // Parse trigger value string like "-PT15M", "-P1D", "-PT1H30M"
            val triggerValue = trigger.value ?: return@mapNotNull null
            val totalMins = parseDurationToMinutes(triggerValue)

            val desc = alarm.description?.value ?: "Reminder"
            AlarmTrigger(offsetMins = totalMins, description = desc)
        }
    }

    /**
     * Parses an iCalendar duration string like "-PT15M", "-P1D", "-PT1H30M"
     * into total minutes (always positive).
     */
    private fun parseDurationToMinutes(duration: String): Int {
        val clean = duration.removePrefix("-").removePrefix("+")
        var totalMins = 0
        // Days
        Regex("(\\d+)D").find(clean)?.let { totalMins += it.groupValues[1].toInt() * 24 * 60 }
        // Weeks
        Regex("(\\d+)W").find(clean)?.let { totalMins += it.groupValues[1].toInt() * 7 * 24 * 60 }
        // Hours
        Regex("(\\d+)H").find(clean)?.let { totalMins += it.groupValues[1].toInt() * 60 }
        // Minutes
        Regex("(\\d+)M").find(clean)?.let { totalMins += it.groupValues[1].toInt() }
        return totalMins
    }
}
