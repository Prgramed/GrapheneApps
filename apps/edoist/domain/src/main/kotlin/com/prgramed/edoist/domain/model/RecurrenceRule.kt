package com.prgramed.edoist.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

data class RecurrenceRule(
    val frequency: Frequency,
    val interval: Int = 1,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val dayOfMonth: Int? = null,
    val endDate: LocalDate? = null,
    val count: Int? = null,
) {
    enum class Frequency {
        DAILY,
        WEEKLY,
        MONTHLY,
        YEARLY,
    }

    fun toRRuleString(): String = buildString {
        append("RRULE:FREQ=")
        append(frequency.name)

        if (interval > 1) {
            append(";INTERVAL=$interval")
        }

        if (daysOfWeek.isNotEmpty()) {
            append(";BYDAY=")
            append(daysOfWeek.joinToString(",") { it.toRRuleDay() })
        }

        if (dayOfMonth != null) {
            append(";BYMONTHDAY=$dayOfMonth")
        }

        if (endDate != null) {
            append(";UNTIL=${endDate.toString().replace("-", "")}")
        }

        if (count != null) {
            append(";COUNT=$count")
        }
    }

    companion object {
        fun fromRRuleString(rrule: String): RecurrenceRule? {
            val rule = rrule.removePrefix("RRULE:")
            val parts = rule.split(";").associate { part ->
                val (key, value) = part.split("=", limit = 2)
                key to value
            }

            val frequency = parts["FREQ"]?.let {
                runCatching { Frequency.valueOf(it) }.getOrNull()
            } ?: return null

            val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1

            val daysOfWeek = parts["BYDAY"]
                ?.split(",")
                ?.mapNotNull { it.toDayOfWeek() }
                ?.toSet()
                ?: emptySet()

            val dayOfMonth = parts["BYMONTHDAY"]?.toIntOrNull()

            val endDate = parts["UNTIL"]?.let { parseUntilDate(it) }

            val count = parts["COUNT"]?.toIntOrNull()

            return RecurrenceRule(
                frequency = frequency,
                interval = interval,
                daysOfWeek = daysOfWeek,
                dayOfMonth = dayOfMonth,
                endDate = endDate,
                count = count,
            )
        }

        private fun parseUntilDate(until: String): LocalDate? {
            if (until.length < 8) return null
            return runCatching {
                LocalDate(
                    year = until.substring(0, 4).toInt(),
                    monthNumber = until.substring(4, 6).toInt(),
                    dayOfMonth = until.substring(6, 8).toInt(),
                )
            }.getOrNull()
        }

        private fun String.toDayOfWeek(): DayOfWeek? = when (this) {
            "MO" -> DayOfWeek.MONDAY
            "TU" -> DayOfWeek.TUESDAY
            "WE" -> DayOfWeek.WEDNESDAY
            "TH" -> DayOfWeek.THURSDAY
            "FR" -> DayOfWeek.FRIDAY
            "SA" -> DayOfWeek.SATURDAY
            "SU" -> DayOfWeek.SUNDAY
            else -> null
        }

        private fun DayOfWeek.toRRuleDay(): String = when (this) {
            DayOfWeek.MONDAY -> "MO"
            DayOfWeek.TUESDAY -> "TU"
            DayOfWeek.WEDNESDAY -> "WE"
            DayOfWeek.THURSDAY -> "TH"
            DayOfWeek.FRIDAY -> "FR"
            DayOfWeek.SATURDAY -> "SA"
            DayOfWeek.SUNDAY -> "SU"
            else -> "MO"
        }
    }
}
