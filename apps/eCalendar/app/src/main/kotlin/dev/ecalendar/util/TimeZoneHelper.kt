package dev.ecalendar.util

import net.fortuna.ical4j.model.DateTime
import java.util.TimeZone

object TimeZoneHelper {

    /**
     * Converts an ical4j Date to epoch millis in the device's local timezone.
     * Handles both all-day Date (no time) and DateTime (with time + optional TZID).
     */
    fun toLocalMillis(icalDate: net.fortuna.ical4j.model.Date, tzId: String? = null): Long {
        return if (icalDate is DateTime) {
            // DateTime — has time component
            if (tzId != null) {
                val tz = TimeZone.getTimeZone(tzId)
                val utcMillis = icalDate.time
                // ical4j DateTime stores in UTC if TZID is set on the property
                utcMillis
            } else {
                icalDate.time
            }
        } else {
            // Date only (all-day) — treat as midnight in device timezone
            val cal = java.util.Calendar.getInstance()
            cal.time = icalDate
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
    }
}
