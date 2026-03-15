package com.prgramed.eprayer.feature.qibla.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

@Composable
fun QiblaDirectionIndicator(
    relativeAngle: Double,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.size(280.dp)) {
        val centerX = size.width / 2
        val centerY = size.height / 2

        rotate(relativeAngle.toFloat(), Offset(centerX, centerY)) {
            val arrowPath = Path().apply {
                moveTo(centerX, centerY - 100.dp.toPx())
                lineTo(centerX - 12.dp.toPx(), centerY - 70.dp.toPx())
                lineTo(centerX + 12.dp.toPx(), centerY - 70.dp.toPx())
                close()
            }
            drawPath(arrowPath, color = primaryColor)

            drawCircle(
                color = primaryColor,
                radius = 6.dp.toPx(),
                center = Offset(centerX, centerY - 104.dp.toPx()),
            )
        }
    }
}
