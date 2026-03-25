package dev.emusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CardShape = RoundedCornerShape(16.dp)

@Composable
fun Modifier.musicCard(): Modifier {
    val bg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    return this
        .clip(CardShape)
        .background(bg, CardShape)
        .border(0.5.dp, Color.White.copy(alpha = 0.12f), CardShape)
        .padding(12.dp)
}
