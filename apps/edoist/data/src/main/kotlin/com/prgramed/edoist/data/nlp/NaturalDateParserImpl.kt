package com.prgramed.edoist.data.nlp

import com.prgramed.edoist.domain.model.RecurrenceRule
import com.prgramed.edoist.domain.model.RecurrenceRule.Frequency
import com.prgramed.edoist.domain.usecase.NaturalDateParser
import com.prgramed.edoist.domain.usecase.ParsedDate
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NaturalDateParserImpl @Inject constructor() : NaturalDateParser {

    override fun parse(input: String): ParsedDate {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ParsedDate(remainingText = "")

        var remaining = trimmed
        var date: LocalDate? = null
        var time: LocalTime? = null
        var recurrenceRule: RecurrenceRule? = null

        // Parse recurring patterns first — they may contain date/day tokens
        val recurResult = parseRecurrence(remaining)
        if (recurResult != null) {
            recurrenceRule = recurResult.rule
            date = recurResult.date
            remaining = recurResult.remaining
        }

        // Parse date if not already set by recurrence
        if (date == null) {
            val dateResult = parseDate(remaining)
            if (dateResult != null) {
                date = dateResult.date
                remaining = dateResult.remaining
            }
        }

        // Parse time
        val timeResult = parseTime(remaining)
        if (timeResult != null) {
            time = timeResult.time
            remaining = timeResult.remaining
        }

        return ParsedDate(
            date = date,
            time = time,
            recurrenceRule = recurrenceRule,
            remainingText = remaining.trim().replace(Regex("\\s+"), " "),
        )
    }

    // ── Date parsing ───────────────────────────────────────────────────────

    private data class DateResult(val date: LocalDate, val remaining: String)

    private fun parseDate(input: String): DateResult? {
        val lower = input.lowercase()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        // "today"
        if (lower.contains(RE_TODAY)) {
            return DateResult(today, input.replace(RE_TODAY_REPLACE, ""))
        }

        // "tomorrow" / "tmr" / "tmrw"
        if (lower.contains(RE_TOMORROW)) {
            return DateResult(
                today.plus(1, DateTimeUnit.DAY),
                input.replace(RE_TOMORROW_REPLACE, ""),
            )
        }

        // "yesterday"
        if (lower.contains(RE_YESTERDAY)) {
            return DateResult(
                today.plus(-1, DateTimeUnit.DAY),
                input.replace(RE_YESTERDAY_REPLACE, ""),
            )
        }

        // "next week" — next Monday
        if (lower.contains(RE_NEXT_WEEK)) {
            val daysUntilMonday = (DayOfWeek.MONDAY.ordinal - today.dayOfWeek.ordinal + 7) % 7
            val nextMonday = today.plus(
                if (daysUntilMonday == 0) 7 else daysUntilMonday,
                DateTimeUnit.DAY,
            )
            return DateResult(nextMonday, input.replace(RE_NEXT_WEEK_REPLACE, ""))
        }

        // "next month" — 1st of next month
        if (lower.contains(RE_NEXT_MONTH)) {
            val firstOfMonth = LocalDate(today.year, today.monthNumber, 1)
            val nextMonth = firstOfMonth.plus(1, DateTimeUnit.MONTH)
            return DateResult(nextMonth, input.replace(RE_NEXT_MONTH_REPLACE, ""))
        }

        // "next <day>" — e.g., "next monday"
        val nextDayMatch = RE_NEXT_DAY.find(lower)
        if (nextDayMatch != null) {
            val dayOfWeek = parseDayOfWeek(nextDayMatch.groupValues[1])
            if (dayOfWeek != null) {
                val daysUntil = (dayOfWeek.ordinal - today.dayOfWeek.ordinal + 7) % 7
                val target = today.plus(if (daysUntil == 0) 7 else daysUntil, DateTimeUnit.DAY)
                // "next" means the week after if the day hasn't passed yet
                val nextWeekTarget = if (daysUntil == 0) target else target.plus(7, DateTimeUnit.DAY)
                return DateResult(
                    nextWeekTarget,
                    input.replace("(?i)\\bnext\\s+${Regex.escape(nextDayMatch.groupValues[1])}\\b".toRegex(), ""),
                )
            }
        }

        // Day name alone — "monday", "tuesday", etc. → next occurrence
        val dayMatch = RE_DAY_ALONE.find(lower)
        if (dayMatch != null) {
            val dayOfWeek = parseDayOfWeek(dayMatch.groupValues[1])
            if (dayOfWeek != null) {
                val daysUntil = (dayOfWeek.ordinal - today.dayOfWeek.ordinal + 7) % 7
                val target = today.plus(if (daysUntil == 0) 7 else daysUntil, DateTimeUnit.DAY)
                return DateResult(
                    target,
                    input.replace("(?i)\\b${Regex.escape(dayMatch.groupValues[1])}\\b".toRegex(), ""),
                )
            }
        }

        // Month + day: "jan 15", "january 15", "march 3rd"
        val monthDayMatch = RE_MONTH_DAY.find(lower)
        if (monthDayMatch != null) {
            val month = parseMonth(monthDayMatch.groupValues[1])
            val day = monthDayMatch.groupValues[2].toIntOrNull()
            if (month != null && day != null && day in 1..31) {
                var candidate = runCatching { LocalDate(today.year, month, day) }.getOrNull()
                if (candidate != null && candidate < today) {
                    candidate = runCatching { LocalDate(today.year + 1, month, day) }.getOrNull()
                }
                if (candidate != null) {
                    return DateResult(
                        candidate,
                        input.replace(monthDayMatch.value.toRegex(RegexOption.IGNORE_CASE), ""),
                    )
                }
            }
        }

        // Numeric: "3/15", "3/15/2026", "2026-03-15"
        val numericSlash = RE_NUMERIC_SLASH.find(input)
        if (numericSlash != null) {
            val m = numericSlash.groupValues[1].toIntOrNull()
            val d = numericSlash.groupValues[2].toIntOrNull()
            var y = numericSlash.groupValues[3].toIntOrNull()
            if (m != null && d != null && m in 1..12 && d in 1..31) {
                if (y != null && y < 100) y += 2000
                val year = y ?: today.year
                val candidate = runCatching { LocalDate(year, m, d) }.getOrNull()
                if (candidate != null) {
                    return DateResult(
                        candidate,
                        input.replace(numericSlash.value, ""),
                    )
                }
            }
        }

        // ISO format: "2026-03-15"
        val isoMatch = RE_ISO_DATE.find(input)
        if (isoMatch != null) {
            val candidate = runCatching {
                LocalDate(
                    isoMatch.groupValues[1].toInt(),
                    isoMatch.groupValues[2].toInt(),
                    isoMatch.groupValues[3].toInt(),
                )
            }.getOrNull()
            if (candidate != null) {
                return DateResult(candidate, input.replace(isoMatch.value, ""))
            }
        }

        return null
    }

    // ── Time parsing ───────────────────────────────────────────────────────

    private data class TimeResult(val time: LocalTime, val remaining: String)

    private fun parseTime(input: String): TimeResult? {
        // "at 3pm", "at 3:30pm", "at 15:00", "3pm", "3:30 pm"
        val timeRegex = "(?i)\\b(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b".toRegex()
        val match12 = timeRegex.find(input)
        if (match12 != null) {
            var hour = match12.groupValues[1].toIntOrNull() ?: return null
            val minute = match12.groupValues[2].toIntOrNull() ?: 0
            val ampm = match12.groupValues[3].lowercase()
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            if (hour in 0..23 && minute in 0..59) {
                return TimeResult(
                    LocalTime(hour, minute),
                    input.replace(match12.value, "").replace("(?i)\\bat\\b".toRegex(), ""),
                )
            }
        }

        // 24-hour: "at 15:00", "15:00"
        val time24Regex = "(?i)\\b(?:at\\s+)?(\\d{1,2}):(\\d{2})\\b".toRegex()
        val match24 = time24Regex.find(input)
        if (match24 != null) {
            val hour = match24.groupValues[1].toIntOrNull() ?: return null
            val minute = match24.groupValues[2].toIntOrNull() ?: return null
            if (hour in 0..23 && minute in 0..59) {
                return TimeResult(
                    LocalTime(hour, minute),
                    input.replace(match24.value, "").replace("(?i)\\bat\\b".toRegex(), ""),
                )
            }
        }

        return null
    }

    // ── Recurrence parsing ─────────────────────────────────────────────────

    private data class RecurrenceResult(
        val rule: RecurrenceRule,
        val date: LocalDate?,
        val remaining: String,
    )

    private fun parseRecurrence(input: String): RecurrenceResult? {
        val lower = input.lowercase()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        // "every day" / "daily"
        if (lower.contains("\\bevery\\s+day\\b".toRegex()) || lower.contains("\\bdaily\\b".toRegex())) {
            val cleaned = input
                .replace("(?i)\\bevery\\s+day\\b".toRegex(), "")
                .replace("(?i)\\bdaily\\b".toRegex(), "")
            return RecurrenceResult(
                RecurrenceRule(frequency = Frequency.DAILY),
                today,
                cleaned,
            )
        }

        // "every <n> days"
        val everyNDays = "(?i)\\bevery\\s+(\\d+)\\s+days?\\b".toRegex().find(lower)
        if (everyNDays != null) {
            val interval = everyNDays.groupValues[1].toIntOrNull() ?: 1
            return RecurrenceResult(
                RecurrenceRule(frequency = Frequency.DAILY, interval = interval),
                today,
                input.replace(everyNDays.value.toRegex(RegexOption.IGNORE_CASE), ""),
            )
        }

        // "every <day>" — e.g., "every monday", "every monday and friday"
        val everyDay = "(?i)\\bevery\\s+($DAY_NAMES_PATTERN(?:\\s*(?:and|,)\\s*$DAY_NAMES_PATTERN)*)\\b".toRegex().find(lower)
        if (everyDay != null) {
            val daysPart = everyDay.groupValues[1]
            val days = "(?i)($DAY_NAMES_PATTERN)".toRegex()
                .findAll(daysPart)
                .mapNotNull { parseDayOfWeek(it.groupValues[1]) }
                .toSet()
            if (days.isNotEmpty()) {
                // Next occurrence of the earliest day
                val nextDate = days.minOfOrNull { dow ->
                    val daysUntil = (dow.ordinal - today.dayOfWeek.ordinal + 7) % 7
                    if (daysUntil == 0) 7 else daysUntil
                }?.let { today.plus(it, DateTimeUnit.DAY) } ?: today
                return RecurrenceResult(
                    RecurrenceRule(frequency = Frequency.WEEKLY, daysOfWeek = days),
                    nextDate,
                    input.replace(everyDay.value.toRegex(RegexOption.IGNORE_CASE), ""),
                )
            }
        }

        // "every week" / "weekly"
        if (lower.contains("\\bevery\\s+week\\b".toRegex()) || lower.contains("\\bweekly\\b".toRegex())) {
            val cleaned = input
                .replace("(?i)\\bevery\\s+week\\b".toRegex(), "")
                .replace("(?i)\\bweekly\\b".toRegex(), "")
            return RecurrenceResult(
                RecurrenceRule(frequency = Frequency.WEEKLY),
                today,
                cleaned,
            )
        }

        // "every <n> weeks"
        val everyNWeeks = "(?i)\\bevery\\s+(\\d+)\\s+weeks?\\b".toRegex().find(lower)
        if (everyNWeeks != null) {
            val interval = everyNWeeks.groupValues[1].toIntOrNull() ?: 1
            return RecurrenceResult(
                RecurrenceRule(frequency = Frequency.WEEKLY, interval = interval),
                today,
                input.replace(everyNWeeks.value.toRegex(RegexOption.IGNORE_CASE), ""),
            )
        }

        // "every month" / "monthly"
        if (lower.contains("\\bevery\\s+month\\b".toRegex()) || lower.contains("\\bmonthly\\b".toRegex())) {
            val cleaned = input
                .replace("(?i)\\bevery\\s+month\\b".toRegex(), "")
                .replace("(?i)\\bmonthly\\b".toRegex(), "")
            return RecurrenceResult(
                RecurrenceRule(frequency = Frequency.MONTHLY, dayOfMonth = today.dayOfMonth),
                today,
                cleaned,
            )
        }

        // "every <n> months"
        val everyNMonths = "(?i)\\bevery\\s+(\\d+)\\s+months?\\b".toRegex().find(lower)
        if (everyNMonths != null) {
            val interval = everyNMonths.groupValues[1].toIntOrNull() ?: 1
            return RecurrenceResult(
                RecurrenceRule(
                    frequency = Frequency.MONTHLY,
                    interval = interval,
                    dayOfMonth = today.dayOfMonth,
                ),
                today,
                input.replace(everyNMonths.value.toRegex(RegexOption.IGNORE_CASE), ""),
            )
        }

        // "every year" / "yearly" / "annually"
        if (lower.contains("\\bevery\\s+year\\b".toRegex()) ||
            lower.contains("\\byearly\\b".toRegex()) ||
            lower.contains("\\bannually\\b".toRegex())
        ) {
            val cleaned = input
                .replace("(?i)\\bevery\\s+year\\b".toRegex(), "")
                .replace("(?i)\\byearly\\b".toRegex(), "")
                .replace("(?i)\\bannually\\b".toRegex(), "")
            return RecurrenceResult(
                RecurrenceRule(frequency = Frequency.YEARLY),
                today,
                cleaned,
            )
        }

        return null
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun parseDayOfWeek(name: String): DayOfWeek? = when (name.lowercase()) {
        "monday", "mon" -> DayOfWeek.MONDAY
        "tuesday", "tue", "tues" -> DayOfWeek.TUESDAY
        "wednesday", "wed" -> DayOfWeek.WEDNESDAY
        "thursday", "thu", "thur", "thurs" -> DayOfWeek.THURSDAY
        "friday", "fri" -> DayOfWeek.FRIDAY
        "saturday", "sat" -> DayOfWeek.SATURDAY
        "sunday", "sun" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun parseMonth(name: String): Int? = when (name.lowercase()) {
        "jan", "january" -> 1
        "feb", "february" -> 2
        "mar", "march" -> 3
        "apr", "april" -> 4
        "may" -> 5
        "jun", "june" -> 6
        "jul", "july" -> 7
        "aug", "august" -> 8
        "sep", "september" -> 9
        "oct", "october" -> 10
        "nov", "november" -> 11
        "dec", "december" -> 12
        else -> null
    }

    companion object {
        private const val DAY_NAMES_PATTERN =
            "monday|tuesday|wednesday|thursday|friday|saturday|sunday|" +
                "mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun"

        private const val MONTH_NAMES_PATTERN =
            "january|february|march|april|may|june|july|august|september|october|november|december|" +
                "jan|feb|mar|apr|jun|jul|aug|sep|oct|nov|dec"

        // Pre-compiled regex patterns (avoid recompilation on every parse call)
        val RE_TODAY = "\\btoday\\b".toRegex()
        val RE_TODAY_REPLACE = "(?i)\\btoday\\b".toRegex()
        val RE_TOMORROW = "\\b(tomorrow|tmr|tmrw)\\b".toRegex()
        val RE_TOMORROW_REPLACE = "(?i)\\b(tomorrow|tmr|tmrw)\\b".toRegex()
        val RE_YESTERDAY = "\\byesterday\\b".toRegex()
        val RE_YESTERDAY_REPLACE = "(?i)\\byesterday\\b".toRegex()
        val RE_NEXT_WEEK = "\\bnext\\s+week\\b".toRegex()
        val RE_NEXT_WEEK_REPLACE = "(?i)\\bnext\\s+week\\b".toRegex()
        val RE_NEXT_MONTH = "\\bnext\\s+month\\b".toRegex()
        val RE_NEXT_MONTH_REPLACE = "(?i)\\bnext\\s+month\\b".toRegex()
        val RE_NEXT_DAY = "(?i)\\bnext\\s+($DAY_NAMES_PATTERN)\\b".toRegex()
        val RE_DAY_ALONE = "(?i)\\b($DAY_NAMES_PATTERN)\\b".toRegex()
        val RE_MONTH_DAY = "(?i)\\b($MONTH_NAMES_PATTERN)\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b".toRegex()
        val RE_NUMERIC_SLASH = "\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b".toRegex()
        val RE_ISO_DATE = "\\b(\\d{4})-(\\d{2})-(\\d{2})\\b".toRegex()
    }
}
