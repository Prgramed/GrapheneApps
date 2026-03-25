package dev.eweather.ui.weather.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.eweather.ui.util.isReduceMotionEnabled
import dev.eweather.util.SkyCategory
import dev.eweather.util.WmoCode

/**
 * Full-bleed animated sky gradient that fills the entire screen.
 * Sits at z-index 0 — everything else is layered on top.
 *
 * Transitions smoothly (2s tween) between sky states.
 */
@Composable
fun SkyBackground(
    skyCategory: SkyCategory,
    currentHour: Int = java.time.LocalTime.now().hour,
    modifier: Modifier = Modifier,
) {
    val colors = skyGradientColors(skyCategory, currentHour)
    val reduceMotion = isReduceMotionEnabled()

    // Animate each color stop independently for smooth blending
    val animatedColors = colors.map { color ->
        val animated by animateColorAsState(
            targetValue = color,
            animationSpec = if (reduceMotion) snap() else tween(durationMillis = 2000),
            label = "sky_color",
        )
        animated
    }

    val brush = Brush.verticalGradient(animatedColors)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush),
    )
}

/**
 * Derives the effective SkyCategory from weather code + time of day.
 * Called from ViewModel to combine WMO code with time-of-day overrides.
 */
fun deriveSkyCategory(weatherCode: Int, isDay: Boolean, hour: Int): SkyCategory {
    val wmoCategory = WmoCode.describe(weatherCode, isDay).skyCategory

    // Time-of-day overrides only apply when weather is clear or partly cloudy
    val isClearish = wmoCategory in listOf(
        SkyCategory.CLEAR_DAY,
        SkyCategory.CLEAR_NIGHT,
        SkyCategory.PARTLY_CLOUDY_DAY,
        SkyCategory.PARTLY_CLOUDY_NIGHT,
    )

    return when {
        isClearish && hour in 3..4 -> SkyCategory.PRE_DAWN
        isClearish && hour in 5..6 -> SkyCategory.SUNRISE
        isClearish && hour in 17..19 -> SkyCategory.SUNSET
        else -> wmoCategory
    }
}

private fun skyGradientColors(category: SkyCategory, hour: Int): List<Color> = when (category) {
    SkyCategory.PRE_DAWN -> listOf(
        Color(0xFF0D0D2B),
        Color(0xFF1A1040),
    )
    SkyCategory.SUNRISE -> listOf(
        Color(0xFFFF6B35),
        Color(0xFFFFB347),
        Color(0xFF87CEEB),
    )
    SkyCategory.CLEAR_DAY -> if (hour < 12) {
        // Morning clear
        listOf(
            Color(0xFF87CEEB),
            Color(0xFFB8D4E8),
        )
    } else {
        // Afternoon clear
        listOf(
            Color(0xFF4FC3F7),
            Color(0xFF81D4FA),
        )
    }
    SkyCategory.SUNSET -> listOf(
        Color(0xFFFF6B35),
        Color(0xFFE91E63),
        Color(0xFF673AB7),
    )
    SkyCategory.CLEAR_NIGHT -> listOf(
        Color(0xFF0A0E27),
        Color(0xFF1C2951),
    )
    SkyCategory.PARTLY_CLOUDY_DAY -> listOf(
        Color(0xFF87CEEB),
        Color(0xFFB8D4E8),
    )
    SkyCategory.PARTLY_CLOUDY_NIGHT -> listOf(
        Color(0xFF1A1A2E),
        Color(0xFF2D3561),
    )
    SkyCategory.OVERCAST -> listOf(
        Color(0xFF546E7A),
        Color(0xFF78909C),
    )
    SkyCategory.RAIN -> listOf(
        Color(0xFF37474F),
        Color(0xFF546E7A),
    )
    SkyCategory.STORM -> listOf(
        Color(0xFF212121),
        Color(0xFF37474F),
    )
    SkyCategory.SNOW -> listOf(
        Color(0xFF90A4AE),
        Color(0xFFCFD8DC),
    )
    SkyCategory.FOG -> listOf(
        Color(0xFF78909C),
        Color(0xFFB0BEC5),
    )
}
