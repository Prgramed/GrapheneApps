package com.prgramed.eprayer.feature.qibla.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

private val Peach = Color(0xFFE8B98A)
private val KaabaBody = Color(0xFF3A3A3A)
private val KaabaBand = Color(0xFFD4A64A)

@Composable
fun QiblaDirectionIndicator(
    relativeAngle: Double,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(300.dp)) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.minDimension / 2 - 8.dp.toPx()

        // Fixed peach teardrop needle pointing UP (device forward direction)
        val needlePath = Path().apply {
            moveTo(centerX, centerY - 60.dp.toPx())
            cubicTo(
                centerX - 16.dp.toPx(), centerY - 20.dp.toPx(),
                centerX - 10.dp.toPx(), centerY + 10.dp.toPx(),
                centerX, centerY + 16.dp.toPx(),
            )
            cubicTo(
                centerX + 10.dp.toPx(), centerY + 10.dp.toPx(),
                centerX + 16.dp.toPx(), centerY - 20.dp.toPx(),
                centerX, centerY - 60.dp.toPx(),
            )
            close()
        }
        drawPath(needlePath, color = Peach)

        // Kaaba on the compass rim — relativeAngle is already relative to device forward
        rotate(relativeAngle.toFloat(), Offset(centerX, centerY)) {
            val kaabaY = centerY - radius + 6.dp.toPx()
            val kSize = 22.dp.toPx()

            // Kaaba body
            drawRoundRect(
                color = KaabaBody,
                topLeft = Offset(centerX - kSize / 2, kaabaY - kSize / 2),
                size = Size(kSize, kSize),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
            // Gold band
            drawRect(
                color = KaabaBand,
                topLeft = Offset(centerX - kSize / 2, kaabaY - 2.dp.toPx()),
                size = Size(kSize, 4.dp.toPx()),
            )
        }
    }
}
