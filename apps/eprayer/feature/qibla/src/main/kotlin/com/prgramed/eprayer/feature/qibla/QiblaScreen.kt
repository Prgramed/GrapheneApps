package com.prgramed.eprayer.feature.qibla

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prgramed.eprayer.feature.qibla.components.CompassView
import com.prgramed.eprayer.feature.qibla.components.QiblaDirectionIndicator

private val Navy = Color(0xFF0F1B2D)
private val Peach = Color(0xFFE8B98A)
private val TextMuted = Color(0xFF8899AA)

@Composable
fun QiblaScreen(
    modifier: Modifier = Modifier,
    viewModel: QiblaViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Navy),
    ) {
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Peach)
                }
            }
            uiState.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = uiState.error ?: "Error", color = Peach)
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
                ) {
                    // Location header
                    Text(
                        text = "LOCATION",
                        fontSize = 12.sp,
                        color = TextMuted,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = uiState.cityName ?: "Locating...",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Compass
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CompassView(deviceHeading = animatedHeading)
                            QiblaDirectionIndicator(
                                relativeAngle = animatedRelative.toDouble(),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Direction hint
                    val angle = direction.relativeAngle
                    val hintText = when {
                        angle < 5 || angle > 355 -> buildAnnotatedString {
                            withStyle(SpanStyle(color = Peach)) { append("You are facing ") }
                            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                                append("Qibla")
                            }
                        }
                        angle in 1.0..180.0 -> buildAnnotatedString {
                            withStyle(SpanStyle(color = Peach)) { append("Turn to your ") }
                            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                                append("right")
                            }
                        }
                        else -> buildAnnotatedString {
                            withStyle(SpanStyle(color = Peach)) { append("Turn to your ") }
                            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                                append("left")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(text = hintText, fontSize = 20.sp)
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
