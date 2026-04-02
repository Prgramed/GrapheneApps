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
 * Merges multiple "on_this_day" memories (one per year) into a single combined memory
 * with all assets from all years, like Immich's web UI.
 */
@Composable
fun MemoriesCarousel(
    memories: List<ImmichMemory>,
    serverUrl: String,
    onMemoryClick: (ImmichMemory) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (memories.isEmpty()) return

    // Group by type and merge assets — "on_this_day" memories become one card
    val merged = remember(memories) {
        memories.groupBy { it.type }.map { (type, group) ->
            val allAssets = group.flatMap { it.assets }
            val years = group.mapNotNull { if (it.data.year > 0) it.data.year else null }.sorted()
            ImmichMemory(
                id = "merged_$type",
                type = type,
                data = group.first().data, // Use first year for title
                assets = allAssets,
                createdAt = group.first().createdAt,
            ) to years
        }
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(merged, key = { it.first.id }) { (memory, years) ->
            MemoryCard(
                memory = memory,
                years = years,
                serverUrl = serverUrl,
                onClick = { onMemoryClick(memory) },
            )
        }
    }
}

@Composable
private fun MemoryCard(
    memory: ImmichMemory,
    years: List<Int>,
    serverUrl: String,
    onClick: () -> Unit,
) {
    val coverAssetId = memory.assets.firstOrNull()?.id ?: return
    val currentYear = java.time.Year.now().value
    val title = "On This Day"
    val subtitle = when {
        years.size == 1 -> "${currentYear - years.first()} year${if (currentYear - years.first() > 1) "s" else ""} ago"
        years.size > 1 -> "${years.size} years of memories"
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
