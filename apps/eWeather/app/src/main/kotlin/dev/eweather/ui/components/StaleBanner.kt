package dev.eweather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BannerShape = RoundedCornerShape(16.dp)

@Composable
fun StaleBanner(
    fetchedAt: Long,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val agoText = formatTimeAgo(fetchedAt)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(BannerShape)
            .background(Color.White.copy(alpha = 0.15f), BannerShape)
            .border(0.5.dp, Color.White.copy(alpha = 0.25f), BannerShape)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Updated $agoText",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text("Retry", color = Color.White, fontSize = 13.sp)
        }
    }
}

private fun formatTimeAgo(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    val minutes = (diff / 60000).toInt()
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        else -> "${minutes / 60}h ${minutes % 60}m ago"
    }
}
