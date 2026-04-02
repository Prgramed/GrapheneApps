package dev.egallery.ui.memories

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.egallery.api.dto.ImmichMemory
import kotlinx.coroutines.delay

@Composable
fun MemoryViewerScreen(
    memory: ImmichMemory,
    serverUrl: String,
    onBack: () -> Unit,
) {
    val assets = memory.assets
    if (assets.isEmpty()) {
        onBack()
        return
    }

    val pagerState = rememberPagerState(pageCount = { assets.size })
    var isPlaying by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf(0f) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Background music — shuffle ambient tracks from res/raw (supports 1-9)
    val mediaPlayer = remember {
        val trackIds = (1..9).mapNotNull { i ->
            val resId = context.resources.getIdentifier("memory_ambient_$i", "raw", context.packageName)
            if (resId != 0) resId else null
        }.shuffled()

        if (trackIds.isNotEmpty()) {
            android.media.MediaPlayer.create(context, trackIds.first())?.apply {
                isLooping = true
                setVolume(0f, 0f) // Start silent, fade in
            }
        } else null
    }

    // Fade in music on start, fade out on exit
    DisposableEffect(mediaPlayer) {
        mediaPlayer?.let { mp ->
            mp.start()
            // Fade in over 2 seconds
            val fadeThread = Thread {
                try {
                    for (i in 0..20) {
                        val vol = i / 20f * 0.4f // Max 40% volume
                        mp.setVolume(vol, vol)
                        Thread.sleep(100)
                    }
                } catch (_: Exception) {}
            }
            fadeThread.start()
        }
        onDispose {
            mediaPlayer?.let { mp ->
                // Quick fade out
                try {
                    for (i in 20 downTo 0) {
                        val vol = i / 20f * 0.4f
                        mp.setVolume(vol, vol)
                        Thread.sleep(30)
                    }
                } catch (_: Exception) {}
                mp.stop()
                mp.release()
            }
        }
    }

    // Pause/resume music with slideshow
    LaunchedEffect(isPlaying) {
        mediaPlayer?.let { mp ->
            if (isPlaying && !mp.isPlaying) mp.start()
            else if (!isPlaying && mp.isPlaying) mp.pause()
        }
    }

    val yearsAgo = java.time.Year.now().value - memory.data.year
    val title = when {
        yearsAgo == 1 -> "1 Year Ago"
        yearsAgo > 1 -> "$yearsAgo Years Ago"
        else -> "This Year"
    }

    // Auto-advance slideshow — instant page jump (no slide animation)
    LaunchedEffect(pagerState.currentPage, isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        progress = 0f
        val steps = 50 // 5 seconds / 100ms
        for (i in 1..steps) {
            delay(100)
            progress = i.toFloat() / steps
        }
        if (pagerState.currentPage < assets.size - 1) {
            pagerState.scrollToPage(pagerState.currentPage + 1)
        } else {
            isPlaying = false // Stop at last photo
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Photo pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val assetId = assets[page].id
            AsyncImage(
                model = "${serverUrl.trimEnd('/')}/api/assets/$assetId/thumbnail?size=preview",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Top gradient + title
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
                    ),
                )
                .align(Alignment.TopCenter),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 40.dp, start = 8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${pagerState.currentPage + 1} of ${assets.size}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { isPlaying = !isPlaying }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        // Progress bar
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 36.dp),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.2f),
        )

        // Bottom gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)),
                    ),
                )
                .align(Alignment.BottomCenter),
        )
    }
}
