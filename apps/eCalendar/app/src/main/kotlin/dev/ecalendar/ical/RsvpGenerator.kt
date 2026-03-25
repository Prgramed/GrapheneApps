package dev.ecalendar.ical

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.DtStamp
import net.fortuna.ical4j.model.property.Method
import java.io.StringReader
import java.net.URI

enum class PartStat {
    ACCEPTED,
    DECLINED,
    TENTATIVE,
}

object RsvpGenerator {

    /**
     * Generates a METHOD:REPLY ICS string for responding to a calendar invitation.
     * The reply contains the original VEVENT with the attendee's PARTSTAT updated.
     */
    fun generateRsvpReply(
        originalIcs: String,
        attendeeEmail: String,
        status: PartStat,
    ): String {
        val builder = CalendarBuilder()
        val calendar = builder.build(StringReader(originalIcs))
        val vevent = calendar.getComponents<VEvent>(Component.VEVENT).firstOrNull()
            ?: throw IllegalArgumentException("No VEVENT in original ICS")

        // Build reply calendar
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//eCalendar//eCalendar 1.0//EN")
        sb.appendLine("METHOD:REPLY")
        sb.appendLine("BEGIN:VEVENT")

        // Copy UID, DTSTART, DTEND, SUMMARY, ORGANIZER from original
        vevent.getProperty<net.fortuna.ical4j.model.property.Uid>(Property.UID)?.let {
            sb.appendLine("UID:${it.value}")
        }
        vevent.getProperty<net.fortuna.ical4j.model.property.DtStart>(Property.DTSTART)?.let {
            sb.appendLine("DTSTART:${it.value}")
        }
        vevent.getProperty<net.fortuna.ical4j.model.property.DtEnd>(Property.DTEND)?.let {
            sb.appendLine("DTEND:${it.value}")
        }
        vevent.getProperty<net.fortuna.ical4j.model.property.Summary>(Property.SUMMARY)?.let {
            sb.appendLine("SUMMARY:${it.value}")
        }
        vevent.getProperty<net.fortuna.ical4j.model.property.Organizer>(Property.ORGANIZER)?.let {
            sb.appendLine("ORGANIZER:${it.value}")
        }

        // DTSTAMP = now
        val now = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())
        sb.appendLine("DTSTAMP:$now")

        // ATTENDEE with PARTSTAT
        sb.appendLine("ATTENDEE;PARTSTAT=${status.name}:mailto:$attendeeEmail")

        sb.appendLine("END:VEVENT")
        sb.appendLine("END:VCALENDAR")
        return sb.toString()
    }
}
