package dev.egallery.ui.memories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.egallery.api.dto.ImmichMemory

/**
 * Shows one card per memory (one per year for "on_this_day"), matching Immich's layout.
 * Sorted newest year first.
 */
@Composable
fun MemoriesCarousel(
    memories: List<ImmichMemory>,
    serverUrl: String,
    onMemoryClick: (ImmichMemory) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (memories.isEmpty()) return

    // Immich already curates each memory as a distinct card (web UI matches this).
    // Don't merge by year — it produces bloated cards with hundreds of photos.
    val grouped = remember(memories) {
        memories
            .filter { it.assets.isNotEmpty() }
            .sortedByDescending { it.data.year }
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(grouped, key = { it.id }) { memory ->
            MemoryCard(
                memory = memory,
                serverUrl = serverUrl,
                onClick = { onMemoryClick(memory) },
            )
        }
    }
}

@Composable
private fun MemoryCard(
    memory: ImmichMemory,
    serverUrl: String,
    onClick: () -> Unit,
) {
    val coverAssetId = memory.assets.firstOrNull()?.id ?: return
    val currentYear = java.time.Year.now().value
    val yearsAgo = if (memory.data.year > 0) currentYear - memory.data.year else 0
    val title = if (memory.data.year > 0) memory.data.year.toString() else "On This Day"
    val subtitle = when {
        yearsAgo == 1 -> "1 year ago"
        yearsAgo > 1 -> "$yearsAgo years ago"
        else -> ""
    }

    Box(
        modifier = Modifier
            .width(160.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = "${serverUrl.trimEnd('/')}/api/assets/$coverAssetId/thumbnail?size=preview",
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                        startY = 100f,
                    ),
                ),
        )

        // Title
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
            Text(
                text = "${memory.assets.size} photos",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
            )
        }
    }
}
