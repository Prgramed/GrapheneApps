package com.prgramed.eprayer.feature.qibla

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prgramed.eprayer.feature.qibla.components.CompassView
import com.prgramed.eprayer.feature.qibla.components.QiblaDirectionIndicator

@Composable
fun QiblaScreen(
    modifier: Modifier = Modifier,
    viewModel: QiblaViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        uiState.error != null -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
                label = "heading",
            )
            val animatedRelative by animateFloatAsState(
                targetValue = direction.relativeAngle.toFloat(),
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
                label = "relative",
            )

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Qibla Direction",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 24.dp),
                )

                Box(contentAlignment = Alignment.Center) {
                    CompassView(deviceHeading = animatedHeading)
                    QiblaDirectionIndicator(relativeAngle = animatedRelative.toDouble())
                }

                Text(
                    text = "%.1f\u00B0".format(direction.qiblaBearing),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}
