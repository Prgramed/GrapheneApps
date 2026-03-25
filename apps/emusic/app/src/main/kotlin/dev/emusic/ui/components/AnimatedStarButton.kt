package dev.emusic.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun AnimatedStarButton(
    starred: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var animating by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (animating) 1.4f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "star_scale",
    )

    LaunchedEffect(animating) {
        if (animating) {
            delay(150)
            animating = false
        }
    }

    IconButton(
        onClick = {
            animating = true
            onToggle()
        },
        modifier = modifier.size(36.dp),
    ) {
        Icon(
            imageVector = if (starred) Icons.Default.Star else Icons.Default.StarBorder,
            contentDescription = if (starred) "Unstar" else "Star",
            tint = if (starred) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.scale(scale),
        )
    }
}
