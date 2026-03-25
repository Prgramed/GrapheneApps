package com.grapheneapps.enotes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─── Classic Paper / Moleskine palette ───

// Light — ivory parchment
private val ParchmentLightColors = lightColorScheme(
    // Accent: deep sienna ink
    primary = Color(0xFF8B5E3C),
    onPrimary = Color(0xFFFFF8F0),
    primaryContainer = Color(0xFFE8D0B8),
    onPrimaryContainer = Color(0xFF3B2210),

    // Secondary: muted olive
    secondary = Color(0xFF7A7560),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAE4D4),
    onSecondaryContainer = Color(0xFF33301E),

    tertiary = Color(0xFF6B7F6A),

    // Surfaces: ivory / parchment
    background = Color(0xFFFAF6F0),        // warm ivory
    onBackground = Color(0xFF2C2418),      // dark sepia
    surface = Color(0xFFF7F2EA),           // lighter parchment
    onSurface = Color(0xFF2C2418),         // dark sepia ink
    surfaceVariant = Color(0xFFEDE6D8),    // aged paper
    onSurfaceVariant = Color(0xFF6B5D4F),  // faded ink

    // Borders
    outline = Color(0xFFCEC4B4),           // paper edge
    outlineVariant = Color(0xFFDDD6C8),    // subtle crease

    // Error: faded red ink
    error = Color(0xFFAD4430),
    onError = Color.White,
    errorContainer = Color(0xFFF5D5CC),
    onErrorContainer = Color(0xFF4A1510),

    inverseSurface = Color(0xFF35302A),
    inverseOnSurface = Color(0xFFF5EDE0),
    inversePrimary = Color(0xFFD4A878),
    surfaceTint = Color(0xFF8B5E3C),
)

// Dark — aged leather journal
private val ParchmentDarkColors = darkColorScheme(
    primary = Color(0xFFD4A878),
    onPrimary = Color(0xFF2C1A08),
    primaryContainer = Color(0xFF5C3D24),
    onPrimaryContainer = Color(0xFFF0D8C0),

    secondary = Color(0xFFC0B8A4),
    onSecondary = Color(0xFF2A2618),
    secondaryContainer = Color(0xFF454030),
    onSecondaryContainer = Color(0xFFE0D8C8),

    tertiary = Color(0xFFA4B8A3),

    // Surfaces: deep walnut / espresso
    background = Color(0xFF1A1510),        // deep espresso
    onBackground = Color(0xFFE0D4C4),      // aged paper text
    surface = Color(0xFF252018),           // worn leather
    onSurface = Color(0xFFE0D4C4),         // cream text
    surfaceVariant = Color(0xFF352E24),    // dark parchment
    onSurfaceVariant = Color(0xFFADA298),  // faded text

    outline = Color(0xFF5C5448),
    outlineVariant = Color(0xFF454038),

    error = Color(0xFFE8927A),
    onError = Color(0xFF3A1008),
    errorContainer = Color(0xFF5C2A1A),
    onErrorContainer = Color(0xFFF5D5CC),

    inverseSurface = Color(0xFFE0D4C4),
    inverseOnSurface = Color(0xFF252018),
    inversePrimary = Color(0xFF8B5E3C),
    surfaceTint = Color(0xFFD4A878),
)

@Composable
fun ENotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ParchmentDarkColors else ParchmentLightColors,
        content = content,
    )
}
