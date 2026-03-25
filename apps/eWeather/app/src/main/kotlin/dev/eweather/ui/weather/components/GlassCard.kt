package dev.eweather.ui.weather.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val GlassShape = RoundedCornerShape(20.dp)

/**
 * Frosted-glass card modifier — the design system's signature look.
 * Semi-transparent white background with thin white border.
 * Used by all data cards (hourly strip, daily forecast, detail cards, astronomy).
 */
fun Modifier.glassCard(): Modifier =
    this
        .clip(GlassShape)
        .background(Color.White.copy(alpha = 0.15f), GlassShape)
        .border(0.5.dp, Color.White.copy(alpha = 0.25f), GlassShape)
        .padding(12.dp)
