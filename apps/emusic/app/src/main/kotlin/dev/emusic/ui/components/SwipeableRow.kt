package dev.emusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

data class SwipeAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
)

@Composable
fun SwipeableRow(
    leftAction: SwipeAction? = null,
    rightAction: SwipeAction? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val threshold = 80.dp

    Box(modifier = modifier) {
        // Background actions
        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left action (revealed on right swipe)
            AnimatedVisibility(
                visible = offsetX > 50f && rightAction != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                rightAction?.let { action ->
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(80.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(start = 16.dp),
                    ) {
                        IconButton(onClick = action.onClick) {
                            Icon(
                                action.icon,
                                contentDescription = action.contentDescription,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            Box(Modifier.weight(1f))

            // Right action (revealed on left swipe)
            AnimatedVisibility(
                visible = offsetX < -50f && leftAction != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                leftAction?.let { action ->
                    Box(
                        contentAlignment = Alignment.CenterEnd,
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(80.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(end = 16.dp),
                    ) {
                        IconButton(onClick = action.onClick) {
                            Icon(
                                action.icon,
                                contentDescription = action.contentDescription,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
        }

        // Content
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX > 150f) rightAction?.onClick?.invoke()
                            if (offsetX < -150f) leftAction?.onClick?.invoke()
                            offsetX = 0f
                        },
                        onDragCancel = { offsetX = 0f },
                    ) { _, dragAmount ->
                        val newOffset = offsetX + dragAmount
                        // Only allow swipe in directions that have actions
                        if (newOffset > 0 && rightAction != null) {
                            offsetX = newOffset.coerceAtMost(200f)
                        } else if (newOffset < 0 && leftAction != null) {
                            offsetX = newOffset.coerceAtLeast(-200f)
                        }
                    }
                },
        ) {
            content()
        }
    }
}
