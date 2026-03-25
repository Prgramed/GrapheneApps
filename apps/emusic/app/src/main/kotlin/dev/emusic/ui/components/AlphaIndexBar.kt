package dev.emusic.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LETTERS = ('A'..'Z').toList() + '#'

@Composable
fun AlphaIndexBar(
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val letterHeight = with(density) { 16.sp.toDp() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
        modifier = modifier
            .padding(end = 4.dp, top = 4.dp, bottom = 4.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    val index = (change.position.y / (size.height.toFloat() / LETTERS.size))
                        .toInt()
                        .coerceIn(0, LETTERS.size - 1)
                    onLetterSelected(LETTERS[index])
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val index = (offset.y / (size.height.toFloat() / LETTERS.size))
                        .toInt()
                        .coerceIn(0, LETTERS.size - 1)
                    onLetterSelected(LETTERS[index])
                }
            },
    ) {
        LETTERS.forEach { letter ->
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
