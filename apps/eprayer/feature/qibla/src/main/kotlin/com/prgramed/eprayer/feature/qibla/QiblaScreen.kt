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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = uiState.error ?: "Error", color = Peach)
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.TextButton(onClick = { viewModel.retry() }) {
                        Text("Retry", color = Peach)
                    }
                }
            }
            else -> {
                val direction = uiState.qiblaDirection ?: return

                // Track cumulative angles to avoid 360→0 wraparound jumps
                var headingTarget by remember { mutableFloatStateOf(direction.deviceHeading) }
                var relativeTarget by remember { mutableFloatStateOf(direction.relativeAngle.toFloat()) }

                headingTarget = shortestRotation(headingTarget, direction.deviceHeading)
                relativeTarget = shortestRotation(relativeTarget, direction.relativeAngle.toFloat())

                val animatedHeading by animateFloatAsState(
                    targetValue = headingTarget,
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 200f),
                    label = "heading",
                )
                val animatedRelative by animateFloatAsState(
                    targetValue = relativeTarget,
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 200f),
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

                    if (uiState.needsCalibration) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2A1A0A), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Compass needs calibration",
                                color = Peach,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Move phone in a figure-8",
                                color = TextMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }

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

private fun shortestRotation(current: Float, target: Float): Float {
    var delta = (target - current) % 360f
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return current + delta
}
