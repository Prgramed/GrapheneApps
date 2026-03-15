package com.prgramed.eprayer.feature.qibla.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CompassView(
    deviceHeading: Float,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.size(280.dp)) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.minDimension / 2 - 20.dp.toPx()

        rotate(-deviceHeading, Offset(centerX, centerY)) {
            // Outer circle
            drawCircle(
                color = surfaceVariantColor,
                radius = radius,
                center = Offset(centerX, centerY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
            )

            // Degree ticks
            for (i in 0 until 360 step 10) {
                val tickLength = if (i % 30 == 0) 16.dp.toPx() else 8.dp.toPx()
                val angle = Math.toRadians(i.toDouble())
                val startRadius = radius - tickLength
                drawLine(
                    color = onSurfaceColor,
                    start = Offset(
                        centerX + (startRadius * kotlin.math.sin(angle)).toFloat(),
                        centerY - (startRadius * kotlin.math.cos(angle)).toFloat(),
                    ),
                    end = Offset(
                        centerX + (radius * kotlin.math.sin(angle)).toFloat(),
                        centerY - (radius * kotlin.math.cos(angle)).toFloat(),
                    ),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            // Cardinal directions
            val directions = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
            for ((label, angle) in directions) {
                val radians = Math.toRadians(angle.toDouble())
                val textRadius = radius - 32.dp.toPx()
                val style = TextStyle(
                    fontSize = 16.sp,
                    color = if (label == "N") primaryColor else onSurfaceColor,
                )
                val measured = textMeasurer.measure(label, style)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        centerX + (textRadius * kotlin.math.sin(radians)).toFloat()
                            - measured.size.width / 2,
                        centerY - (textRadius * kotlin.math.cos(radians)).toFloat()
                            - measured.size.height / 2,
                    ),
                )
            }
        }
    }
}
