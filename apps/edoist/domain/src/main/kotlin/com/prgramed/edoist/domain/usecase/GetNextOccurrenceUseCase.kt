package com.prgramed.edoist.domain.usecase

import com.prgramed.edoist.domain.model.RecurrenceRule
import com.prgramed.edoist.domain.model.RecurrenceRule.Frequency
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import javax.inject.Inject

class GetNextOccurrenceUseCase @Inject constructor() {

    operator fun invoke(currentDate: LocalDate, rule: RecurrenceRule): LocalDate? {
        val endDate = rule.endDate
        if (endDate != null && currentDate >= endDate) return null

        val nextDate = when (rule.frequency) {
            Frequency.DAILY -> computeNextDaily(currentDate, rule.interval)
            Frequency.WEEKLY -> computeNextWeekly(currentDate, rule.interval, rule.daysOfWeek)
            Frequency.MONTHLY -> computeNextMonthly(currentDate, rule.interval, rule.dayOfMonth)
            Frequency.YEARLY -> computeNextYearly(currentDate, rule.interval)
        }

        if (endDate != null && nextDate > endDate) return null

        return nextDate
    }

    private fun computeNextDaily(currentDate: LocalDate, interval: Int): LocalDate =
        currentDate.plus(interval, DateTimeUnit.DAY)

    private fun computeNextWeekly(
        currentDate: LocalDate,
        interval: Int,
        daysOfWeek: Set<DayOfWeek>,
    ): LocalDate {
        if (daysOfWeek.isEmpty()) {
            return currentDate.plus(interval * 7, DateTimeUnit.DAY)
        }

        // Look for the next matching day within the current week first
        var candidate = currentDate.plus(1, DateTimeUnit.DAY)
        val weekEnd = currentDate.plus(7, DateTimeUnit.DAY)

        while (candidate < weekEnd) {
            if (candidate.dayOfWeek in daysOfWeek) {
                return candidate
            }
            candidate = candidate.plus(1, DateTimeUnit.DAY)
        }

        // Jump ahead by (interval - 1) weeks and find the first matching day
        val nextWeekStart = if (interval > 1) {
            currentDate.plus((interval - 1) * 7, DateTimeUnit.DAY)
                .plus(1, DateTimeUnit.DAY)
        } else {
            weekEnd
        }

        candidate = nextWeekStart
        // Search up to 7 days to find a matching day of week
        repeat(7) {
            if (candidate.dayOfWeek in daysOfWeek) {
                return candidate
            }
            candidate = candidate.plus(1, DateTimeUnit.DAY)
        }

        // Fallback: just advance by interval weeks
        return currentDate.plus(interval * 7, DateTimeUnit.DAY)
    }

    private fun computeNextMonthly(
        currentDate: LocalDate,
        interval: Int,
        dayOfMonth: Int?,
    ): LocalDate {
        val targetDay = dayOfMonth ?: currentDate.dayOfMonth

        // Advance by interval months using the 1st to avoid day-of-month overflow
        val firstOfCurrentMonth = LocalDate(currentDate.year, currentDate.monthNumber, 1)
        val firstOfTargetMonth = firstOfCurrentMonth.plus(interval, DateTimeUnit.MONTH)

        val nextYear = firstOfTargetMonth.year
        val monthNumber = firstOfTargetMonth.monthNumber
        val daysInMonth = daysInMonth(nextYear, monthNumber)
        val clampedDay = targetDay.coerceAtMost(daysInMonth)

        return LocalDate(nextYear, monthNumber, clampedDay)
    }

    private fun computeNextYearly(currentDate: LocalDate, interval: Int): LocalDate {
        val nextYear = currentDate.year + interval
        val monthNumber = currentDate.monthNumber
        val daysInMonth = daysInMonth(nextYear, monthNumber)
        val clampedDay = currentDate.dayOfMonth.coerceAtMost(daysInMonth)

        return LocalDate(nextYear, monthNumber, clampedDay)
    }

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        2 -> if (isLeapYear(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}
