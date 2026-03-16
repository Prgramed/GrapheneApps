package com.prgramed.eprayer.feature.qibla

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grapheneapps.core.designsystem.theme.DarkGreen
import com.grapheneapps.core.designsystem.theme.Seed
import com.prgramed.eprayer.feature.qibla.components.CompassView
import com.prgramed.eprayer.feature.qibla.components.QiblaDirectionIndicator

@Composable
fun QiblaScreen(
    modifier: Modifier = Modifier,
    viewModel: QiblaViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Seed, DarkGreen, MaterialTheme.colorScheme.background),
                    startY = 0f,
                    endY = 600f,
                ),
            ),
    ) {
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            uiState.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = uiState.error ?: "An error occurred",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            else -> {
                val direction = uiState.qiblaDirection ?: return

                val animatedHeading by animateFloatAsState(
                    targetValue = direction.deviceHeading,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                    label = "heading",
                )
                val animatedRelative by animateFloatAsState(
                    targetValue = direction.relativeAngle.toFloat(),
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                    label = "relative",
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Qibla",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        text = "%.1f\u00B0".format(direction.qiblaBearing),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Box(contentAlignment = Alignment.Center) {
                        CompassView(deviceHeading = animatedHeading)
                        QiblaDirectionIndicator(relativeAngle = animatedRelative.toDouble())
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Point the arrow toward the Kaaba",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
