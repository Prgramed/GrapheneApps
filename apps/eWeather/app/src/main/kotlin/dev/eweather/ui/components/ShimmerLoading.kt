package dev.eweather.ui.components

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.eweather.ui.weather.components.SkyBackground
import dev.eweather.util.SkyCategory
import java.time.LocalTime

private val ShimmerLight = Color.White.copy(alpha = 0.15f)
private val ShimmerBright = Color.White.copy(alpha = 0.35f)

@Composable
fun ShimmerLoadingScreen() {
    val hour = LocalTime.now().hour
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
        ),
        label = "shimmer",
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(ShimmerLight, ShimmerBright, ShimmerLight),
        start = Offset(translateAnim, 0f),
        end = Offset(translateAnim + 400f, 0f),
    )

    Box(Modifier.fillMaxSize()) {
        SkyBackground(SkyCategory.CLEAR_DAY, hour)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // City name placeholder
            ShimmerBox(
                brush = shimmerBrush,
                modifier = Modifier.width(100.dp).height(16.dp),
            )

            Spacer(Modifier.height(12.dp))

            // Temperature placeholder
            ShimmerBox(
                brush = shimmerBrush,
                modifier = Modifier.width(120.dp).height(60.dp),
            )

            Spacer(Modifier.height(8.dp))

            // Description placeholder
            ShimmerBox(
                brush = shimmerBrush,
                modifier = Modifier.width(140.dp).height(14.dp),
            )

            Spacer(Modifier.height(32.dp))

            // Card placeholders
            repeat(3) {
                ShimmerBox(
                    brush = shimmerBrush,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(80.dp),
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ShimmerBox(
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(brush),
    )
}
