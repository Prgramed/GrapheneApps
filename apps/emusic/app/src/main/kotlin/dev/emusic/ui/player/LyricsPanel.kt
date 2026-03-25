package dev.emusic.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.emusic.domain.model.Lyrics
import dev.emusic.domain.model.SyncedLine

@Composable
fun LyricsPanel(
    lyrics: Lyrics?,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        lyrics == null -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No lyrics available",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        lyrics.isSynced -> {
            SyncedLyricsView(
                lines = lyrics.syncedLines,
                positionMs = positionMs,
                onSeek = onSeek,
                modifier = modifier,
            )
        }
        else -> {
            Text(
                text = lyrics.text ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun SyncedLyricsView(
    lines: List<SyncedLine>,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Find current line index
    val currentIndex = lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)

    // Auto-scroll to current line
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && lines.isNotEmpty()) {
            listState.animateScrollToItem(
                index = currentIndex,
                scrollOffset = -200,
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
    ) {
        items(lines.size, key = { lines[it].timeMs }) { index ->
            val line = lines[index]
            val isCurrent = index == currentIndex
            val isPast = index < currentIndex

            Text(
                text = line.text,
                style = if (isCurrent) {
                    MaterialTheme.typography.bodyLarge
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = when {
                    isCurrent -> MaterialTheme.colorScheme.primary
                    isPast -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeek(line.timeMs) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}
