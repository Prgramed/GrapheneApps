package dev.ecalendar.util

import androidx.compose.ui.graphics.Color

data class CalendarColor(
    val name: String,
    val lightHex: String,
    val darkHex: String,
)

object ColorPalette {

    val Tomato = CalendarColor("Tomato", "#D50000", "#EF5350")
    val Flamingo = CalendarColor("Flamingo", "#E67C73", "#F4978E")
    val Tangerine = CalendarColor("Tangerine", "#F4511E", "#FF7043")
    val Banana = CalendarColor("Banana", "#F6BF26", "#FFD54F")
    val Sage = CalendarColor("Sage", "#33B679", "#66BB6A")
    val Basil = CalendarColor("Basil", "#0B8043", "#43A047")
    val Peacock = CalendarColor("Peacock", "#039BE5", "#42A5F5")
    val Blueberry = CalendarColor("Blueberry", "#3F51B5", "#5C6BC0")

    fun defaultColors(): List<CalendarColor> = listOf(
        Tomato, Flamingo, Tangerine, Banana, Sage, Basil, Peacock, Blueberry,
    )

    fun forTheme(hex: String, isDark: Boolean): Color {
        val match = defaultColors().find { it.lightHex.equals(hex, ignoreCase = true) || it.darkHex.equals(hex, ignoreCase = true) }
        val targetHex = if (match != null) {
            if (isDark) match.darkHex else match.lightHex
        } else {
            hex
        }
        return parseHex(targetHex)
    }

    fun parseHex(hex: String): Color {
        val clean = hex.removePrefix("#")
        return try {
            Color(android.graphics.Color.parseColor("#$clean"))
        } catch (_: Exception) {
            Color(0xFF4285F4) // Default blue
        }
    }
}
