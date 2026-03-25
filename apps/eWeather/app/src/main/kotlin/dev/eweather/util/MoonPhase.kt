package dev.eweather.util

import dev.eweather.domain.model.MoonPhaseInfo
import kotlin.math.abs
import kotlin.math.cos

/**
 * Computes moon phase from a Gregorian date using the synodic period algorithm.
 * No API needed — pure math.
 */
object MoonPhaseCalculator {

    private const val SYNODIC_PERIOD = 29.53058770576
    // Known New Moon: 2000-01-06 18:14 UTC
    private const val REFERENCE_NEW_MOON_JD = 2451550.1

    fun calculate(year: Int, month: Int, day: Int): MoonPhaseInfo {
        val jd = gregorianToJulianDay(year, month, day)
        val daysSinceNewMoon = jd - REFERENCE_NEW_MOON_JD
        val cycles = daysSinceNewMoon / SYNODIC_PERIOD
        val fraction = ((cycles % 1.0) + 1.0) % 1.0 // 0.0 to 1.0

        // Illumination: 0 at new moon (fraction=0), 1 at full moon (fraction=0.5)
        val illumination = (1.0 - cos(fraction * 2.0 * Math.PI)) / 2.0

        val (phaseName, emoji) = when {
            fraction < 0.0625 -> "New Moon" to "\uD83C\uDF11"
            fraction < 0.1875 -> "Waxing Crescent" to "\uD83C\uDF12"
            fraction < 0.3125 -> "First Quarter" to "\uD83C\uDF13"
            fraction < 0.4375 -> "Waxing Gibbous" to "\uD83C\uDF14"
            fraction < 0.5625 -> "Full Moon" to "\uD83C\uDF15"
            fraction < 0.6875 -> "Waning Gibbous" to "\uD83C\uDF16"
            fraction < 0.8125 -> "Last Quarter" to "\uD83C\uDF17"
            fraction < 0.9375 -> "Waning Crescent" to "\uD83C\uDF18"
            else -> "New Moon" to "\uD83C\uDF11"
        }

        return MoonPhaseInfo(
            fraction = fraction.toFloat(),
            illumination = illumination.toFloat(),
            phaseName = phaseName,
            emoji = emoji,
        )
    }

    private fun gregorianToJulianDay(year: Int, month: Int, day: Int): Double {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        return (day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045).toDouble()
    }
}
