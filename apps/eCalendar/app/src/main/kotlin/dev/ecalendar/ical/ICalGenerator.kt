package dev.ecalendar.ical

import dev.ecalendar.domain.model.EditableEvent
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStamp
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Location
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Trigger
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.property.Url
import net.fortuna.ical4j.model.property.Version
import net.fortuna.ical4j.model.property.XProperty
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Dur
import java.io.StringReader
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

object ICalGenerator {

    private const val PRODID = "-//eCalendar//eCalendar 1.0//EN"

    /**
     * Generates a complete VCALENDAR ICS string from an EditableEvent.
     * If originalIcs is set (editing), preserves unknown properties for round-trip fidelity.
     */
    fun generateEventIcs(event: EditableEvent): String {
        return if (event.originalIcs != null) {
            generateFromExisting(event)
        } else {
            generateNew(event)
        }
    }

    private fun generateNew(event: EditableEvent): String {
        val uid = event.uid ?: UUID.randomUUID().toString()
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:$PRODID")
        sb.appendLine("BEGIN:VEVENT")
        sb.appendLine("UID:$uid")
        sb.appendLine("DTSTAMP:${formatDateTime(System.currentTimeMillis())}")

        if (event.isAllDay) {
            val (startDate, endDateExclusive) = allDayRange(event.startMillis, event.endMillis)
            sb.appendLine("DTSTART;VALUE=DATE:${formatLocalDate(startDate)}")
            sb.appendLine("DTEND;VALUE=DATE:${formatLocalDate(endDateExclusive)}")
        } else {
            sb.appendLine("DTSTART:${formatDateTime(event.startMillis)}")
            sb.appendLine("DTEND:${formatDateTime(event.endMillis)}")
        }

        sb.appendLine("SUMMARY:${escapeIcal(event.title)}")

        event.location?.let { sb.appendLine("LOCATION:${escapeIcal(it)}") }
        event.notes?.let { sb.appendLine("DESCRIPTION:${escapeIcal(it)}") }
        event.url?.let { sb.appendLine("URL:$it") }
        event.rruleString?.let { sb.appendLine("RRULE:$it") }
        event.colorHex?.let { sb.appendLine("COLOR:$it") }
        event.travelTimeMins?.let { sb.appendLine("X-APPLE-TRAVEL-DURATION;VALUE=DURATION:PT${it}M") }

        for (email in event.attendees) {
            sb.appendLine("ATTENDEE;RSVP=TRUE:mailto:$email")
        }

        for (mins in event.alarms) {
            sb.appendLine("BEGIN:VALARM")
            sb.appendLine("ACTION:DISPLAY")
            sb.appendLine("TRIGGER:-PT${mins}M")
            sb.appendLine("DESCRIPTION:Reminder")
            sb.appendLine("END:VALARM")
        }

        sb.appendLine("END:VEVENT")
        sb.appendLine("END:VCALENDAR")
        return sb.toString()
    }

    private fun generateFromExisting(event: EditableEvent): String {
        // Round-trip: parse original, modify changed fields, re-serialize
        val builder = CalendarBuilder()
        val calendar = builder.build(StringReader(event.originalIcs!!))
        val vevent = calendar.getComponents<VEvent>(Component.VEVENT).firstOrNull()
            ?: return generateNew(event)

        // Update standard properties
        replaceProperty(vevent, Summary(escapeIcal(event.title)))
        replaceProperty(vevent, DtStamp(DateTime(System.currentTimeMillis())))

        if (event.isAllDay) {
            val (startDate, endDateExclusive) = allDayRange(event.startMillis, event.endMillis)
            // net.fortuna.ical4j.model.Date(yyyymmdd string) is the safest
            // way to construct a DATE-only value (no time component) per RFC 5545.
            replaceProperty(vevent, DtStart(net.fortuna.ical4j.model.Date(formatLocalDate(startDate))))
            replaceProperty(vevent, DtEnd(net.fortuna.ical4j.model.Date(formatLocalDate(endDateExclusive))))
        } else {
            replaceProperty(vevent, DtStart(DateTime(event.startMillis)))
            replaceProperty(vevent, DtEnd(DateTime(event.endMillis)))
        }

        // Optional fields
        updateOptionalProperty(vevent, Property.LOCATION, event.location?.let { Location(it) })
        updateOptionalProperty(vevent, Property.DESCRIPTION, event.notes?.let { Description(it) })
        updateOptionalProperty(vevent, Property.URL, event.url?.let { Url(URI(it)) })

        // RRULE
        vevent.properties.removeAll { it.name == Property.RRULE }
        event.rruleString?.let { vevent.properties.add(RRule(it)) }

        // Attendees — replace all
        vevent.properties.removeAll { it.name == Property.ATTENDEE }
        for (email in event.attendees) {
            vevent.properties.add(Attendee(URI("mailto:$email")))
        }

        // VALARMs — replace all
        vevent.components.removeAll { it is VAlarm }
        for (mins in event.alarms) {
            val alarm = VAlarm()
            alarm.properties.add(Trigger(Dur(0, 0, -mins, 0)))
            alarm.properties.add(Action.DISPLAY)
            alarm.properties.add(Description("Reminder"))
            vevent.components.add(alarm)
        }

        // Travel time
        vevent.properties.removeAll { it.name == "X-APPLE-TRAVEL-DURATION" }
        event.travelTimeMins?.let {
            vevent.properties.add(XProperty("X-APPLE-TRAVEL-DURATION", "PT${it}M"))
        }

        return calendar.toString()
    }

    private fun replaceProperty(vevent: VEvent, property: net.fortuna.ical4j.model.Property) {
        vevent.properties.removeAll { it.name == property.name }
        vevent.properties.add(property)
    }

    private fun updateOptionalProperty(
        vevent: VEvent,
        name: String,
        property: net.fortuna.ical4j.model.Property?,
    ) {
        vevent.properties.removeAll { it.name == name }
        if (property != null) vevent.properties.add(property)
    }

    private fun formatDateTime(millis: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date(millis))
    }

    private fun formatDate(millis: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
        return sdf.format(java.util.Date(millis))
    }

    private fun formatLocalDate(date: LocalDate): String =
        "%04d%02d%02d".format(date.year, date.monthValue, date.dayOfMonth)

    /**
     * Normalizes an all-day event's raw start/end millis (which may share the
     * same date, or have drifted) into an RFC 5545-compliant DATE range:
     *   - DTSTART = start date (inclusive)
     *   - DTEND   = day AFTER the last day of the event (exclusive)
     *
     * Without this, single-day all-day events emit DTEND == DTSTART, which
     * strict CalDAV servers reject on PUT and which makes the recurrence
     * expander produce zero-duration instances that never appear in the
     * timeline's `instanceEnd > :start` query.
     */
    private fun allDayRange(startMillis: Long, endMillis: Long): Pair<LocalDate, LocalDate> {
        val zone = ZoneId.systemDefault()
        val startDate = LocalDate.ofInstant(Instant.ofEpochMilli(startMillis), zone)
        val endDateRaw = LocalDate.ofInstant(Instant.ofEpochMilli(endMillis), zone)
        val lastDay = if (endDateRaw.isAfter(startDate)) endDateRaw else startDate
        return startDate to lastDay.plusDays(1)
    }

    private fun escapeIcal(text: String): String =
        text.replace("\\", "\\\\")
            .replace(",", "\\,")
            .replace(";", "\\;")
            .replace("\n", "\\n")
}
