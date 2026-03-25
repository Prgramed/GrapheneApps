package dev.eweather.ui.weather.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import dev.eweather.ui.util.isReduceMotionEnabled
import dev.eweather.util.SkyCategory
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private data class Particle(
    var x: Float,
    var y: Float,
    val speed: Float,
    val size: Float,
    val alpha: Float,
    val angle: Float = 0f,
    val phase: Float = 0f,
)

/**
 * Canvas overlay for weather condition animations.
 * Renders rain, snow, thunder, sun rays, stars, fog, or wind streaks
 * on top of the SkyBackground.
 */
@Composable
fun WeatherAnimationLayer(
    skyCategory: SkyCategory,
    intensity: Float = 0.5f,
    modifier: Modifier = Modifier,
) {
    if (isReduceMotionEnabled()) return

    val density = LocalDensity.current.density
    var frameTime by remember { mutableLongStateOf(0L) }
    var lastFrame by remember { mutableLongStateOf(0L) }

    // Thunder flash state
    var flashAlpha by remember { mutableFloatStateOf(0f) }
    var nextFlashTime by remember { mutableLongStateOf(0L) }

    // Particle arrays — remembered to avoid reallocation
    val particles = remember(skyCategory, intensity) {
        createParticles(skyCategory, intensity)
    }

    // Animation loop — keyed on Unit so it doesn't restart on recomposition
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { millis ->
                val delta = if (lastFrame == 0L) 16L else min(millis - lastFrame, 32L)
                lastFrame = millis
                frameTime = millis

                val dt = delta / 1000f // seconds

                when (skyCategory) {
                    SkyCategory.RAIN -> updateRainParticles(particles, dt, density)
                    SkyCategory.SNOW -> updateSnowParticles(particles, dt, density, millis)
                    SkyCategory.STORM -> {
                        updateRainParticles(particles, dt, density)
                        // Thunder flash logic
                        if (millis > nextFlashTime) {
                            flashAlpha = 0.7f
                            nextFlashTime = millis + (4000L + Random.nextLong(11000L))
                        }
                        if (flashAlpha > 0f) {
                            flashAlpha = (flashAlpha - dt * 8f).coerceAtLeast(0f)
                        }
                    }
                    SkyCategory.OVERCAST, SkyCategory.PARTLY_CLOUDY_DAY,
                    SkyCategory.PARTLY_CLOUDY_NIGHT -> updateWindParticles(particles, dt, density)
                    else -> {} // Stars, fog, sun rays handled in draw
                }
            }
        }
    }

    // Continuous invalidation via InfiniteTransition — drives Canvas redraws
    val infiniteTransition = rememberInfiniteTransition(label = "weather_anim")
    val animTick by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "tick",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        // Read animTick to ensure continuous Canvas invalidation
        @Suppress("UNUSED_EXPRESSION")
        animTick

        when (skyCategory) {
            SkyCategory.RAIN -> drawRain(particles)
            SkyCategory.SNOW -> drawSnow(particles)
            SkyCategory.STORM -> {
                drawRain(particles)
                drawFlash(flashAlpha)
            }
            SkyCategory.CLEAR_DAY, SkyCategory.SUNRISE -> drawSunRays(frameTime, density)
            SkyCategory.CLEAR_NIGHT, SkyCategory.PRE_DAWN -> drawStars(particles, frameTime)
            SkyCategory.FOG -> drawFog(frameTime, density)
            SkyCategory.OVERCAST, SkyCategory.PARTLY_CLOUDY_DAY,
            SkyCategory.PARTLY_CLOUDY_NIGHT -> drawWind(particles)
            SkyCategory.SUNSET -> drawSunRays(frameTime, density)
        }
    }
}

// --- Particle creation ---

private fun createParticles(category: SkyCategory, intensity: Float): MutableList<Particle> {
    val list = mutableListOf<Particle>()
    // Ensure minimum intensity for weather categories so animations always show
    val effectiveIntensity = when (category) {
        SkyCategory.RAIN -> intensity.coerceAtLeast(0.4f)
        SkyCategory.STORM -> intensity.coerceAtLeast(0.7f)
        SkyCategory.SNOW -> intensity.coerceAtLeast(0.3f)
        SkyCategory.FOG -> intensity.coerceAtLeast(0.5f)
        else -> intensity
    }
    when (category) {
        SkyCategory.RAIN, SkyCategory.STORM -> {
            val count = (effectiveIntensity * 80).toInt().coerceIn(10, 80)
            repeat(count) {
                list.add(Particle(
                    x = Random.nextFloat(),
                    y = Random.nextFloat(),
                    speed = 400f + Random.nextFloat() * 400f,
                    size = 8f + Random.nextFloat() * 12f,
                    alpha = 0.3f + Random.nextFloat() * 0.3f,
                    angle = -15f + Random.nextFloat() * 30f,
                ))
            }
        }
        SkyCategory.SNOW -> {
            val count = (effectiveIntensity * 40).toInt().coerceIn(5, 40)
            repeat(count) {
                list.add(Particle(
                    x = Random.nextFloat(),
                    y = Random.nextFloat(),
                    speed = 30f + Random.nextFloat() * 50f,
                    size = 2f + Random.nextFloat() * 4f,
                    alpha = 0.5f + Random.nextFloat() * 0.3f,
                    phase = Random.nextFloat() * 2f * PI.toFloat(),
                ))
            }
        }
        SkyCategory.CLEAR_NIGHT, SkyCategory.PRE_DAWN -> {
            repeat(50) {
                list.add(Particle(
                    x = Random.nextFloat(),
                    y = Random.nextFloat() * 0.7f, // Stars in upper 70%
                    speed = 0f,
                    size = 1f + Random.nextFloat() * 2f,
                    alpha = 0.4f + Random.nextFloat() * 0.6f,
                    phase = Random.nextFloat() * 2f * PI.toFloat(),
                ))
            }
        }
        SkyCategory.OVERCAST, SkyCategory.PARTLY_CLOUDY_DAY, SkyCategory.PARTLY_CLOUDY_NIGHT -> {
            repeat(20) {
                list.add(Particle(
                    x = Random.nextFloat(),
                    y = Random.nextFloat(),
                    speed = 200f + Random.nextFloat() * 300f,
                    size = 20f + Random.nextFloat() * 40f,
                    alpha = 0.1f + Random.nextFloat() * 0.2f,
                ))
            }
        }
        else -> {}
    }
    return list
}

// --- Update functions ---

private fun updateRainParticles(particles: MutableList<Particle>, dt: Float, density: Float) {
    for (p in particles) {
        p.y += p.speed * dt / (density * 800f)
        p.x += sin(p.angle * PI.toFloat() / 180f) * p.speed * dt / (density * 800f)
        if (p.y > 1f) { p.y = -0.05f; p.x = Random.nextFloat() }
        if (p.x < 0f) p.x = 1f
        if (p.x > 1f) p.x = 0f
    }
}

private fun updateSnowParticles(particles: MutableList<Particle>, dt: Float, density: Float, time: Long) {
    for (p in particles) {
        p.y += p.speed * dt / (density * 800f)
        p.x += sin(time / 2000f + p.phase) * 0.0003f
        if (p.y > 1f) { p.y = -0.05f; p.x = Random.nextFloat() }
    }
}

private fun updateWindParticles(particles: MutableList<Particle>, dt: Float, density: Float) {
    for (p in particles) {
        p.x += p.speed * dt / (density * 800f)
        if (p.x > 1.1f) { p.x = -0.1f; p.y = Random.nextFloat() }
    }
}

// --- Draw functions ---

private fun DrawScope.drawRain(particles: List<Particle>) {
    for (p in particles) {
        val x = p.x * size.width
        val y = p.y * size.height
        val len = p.size * density
        val angleRad = p.angle * PI.toFloat() / 180f
        drawLine(
            color = Color.White.copy(alpha = p.alpha),
            start = Offset(x, y),
            end = Offset(x + sin(angleRad) * len, y + cos(angleRad) * len),
            strokeWidth = 1.5f * density,
        )
    }
}

private fun DrawScope.drawSnow(particles: List<Particle>) {
    for (p in particles) {
        drawCircle(
            color = Color.White.copy(alpha = p.alpha),
            radius = p.size * density,
            center = Offset(p.x * size.width, p.y * size.height),
        )
    }
}

private fun DrawScope.drawFlash(alpha: Float) {
    if (alpha > 0.01f) {
        drawRect(
            color = Color.White.copy(alpha = alpha),
            size = size,
        )
    }
}

private fun DrawScope.drawSunRays(time: Long, density: Float) {
    val cx = size.width * 0.85f
    val cy = size.height * 0.08f
    val rotation = (time % 60000L) / 60000f * 360f
    val rayLength = size.width * 0.6f

    for (i in 0 until 8) {
        val angle = (rotation + i * 45f) * PI.toFloat() / 180f
        val endX = cx + cos(angle) * rayLength
        val endY = cy + sin(angle) * rayLength
        drawLine(
            color = Color.White.copy(alpha = 0.04f),
            start = Offset(cx, cy),
            end = Offset(endX, endY),
            strokeWidth = 30f * density,
        )
    }
}

private fun DrawScope.drawStars(particles: List<Particle>, time: Long) {
    for (p in particles) {
        val pulse = (sin(time / 1000f * (0.3f + p.phase * 0.2f) + p.phase) + 1f) / 2f
        val alpha = 0.4f + pulse * 0.6f
        drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = p.size * density,
            center = Offset(p.x * size.width, p.y * size.height),
        )
    }
}

private fun DrawScope.drawFog(time: Long, density: Float) {
    val drift = sin(time / 8000f * 2f * PI.toFloat()) * 20f * density
    for (i in 0 until 3) {
        val yOffset = size.height * (0.3f + i * 0.2f)
        drawRect(
            color = Color.White.copy(alpha = 0.08f - i * 0.02f),
            topLeft = Offset(drift + i * 10f * density, yOffset),
            size = Size(size.width + 40f * density, 60f * density),
        )
    }
}

private fun DrawScope.drawWind(particles: List<Particle>) {
    for (p in particles) {
        val x = p.x * size.width
        val y = p.y * size.height
        drawLine(
            color = Color.White.copy(alpha = p.alpha),
            start = Offset(x, y),
            end = Offset(x + p.size * density, y),
            strokeWidth = 1f * density,
        )
    }
}
