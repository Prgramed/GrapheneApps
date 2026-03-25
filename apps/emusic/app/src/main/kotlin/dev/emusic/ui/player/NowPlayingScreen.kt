package dev.emusic.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import dev.emusic.playback.RadioStreamError
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap

@Composable
fun NowPlayingScreen(
    onBack: () -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onEqualizerClick: () -> Unit = {},
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showArtistSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showCastSheet by remember { mutableStateOf(false) }
    var showRatingSheet by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }

    if (showCastSheet) {
        dev.emusic.ui.cast.CastSheet(
            castManager = viewModel.castManager,
            onDeviceSelected = { device ->
                val track = uiState.track
                if (track != null) {
                    viewModel.castManager.castTrack(device, track)
                }
                showCastSheet = false
            },
            onDismiss = { showCastSheet = false },
        )
    }

    if (showSleepTimerSheet) {
        SleepTimerSheet(
            state = uiState.sleepTimerState,
            isLiveStream = uiState.isLiveStream,
            onSetTimer = viewModel::setSleepTimer,
            onExtendTimer = viewModel::extendSleepTimer,
            onCancelTimer = viewModel::cancelSleepTimer,
            onToggleStopAfterTrack = { viewModel.toggleStopAfterTrack() },
            onDismiss = { showSleepTimerSheet = false },
        )
    }

    if (showArtistSheet && !uiState.isLiveStream && uiState.track != null) {
        MoreFromArtistSheet(
            artistId = uiState.track!!.artistId,
            artistName = uiState.track!!.artist,
            libraryRepository = viewModel.libraryRepository,
            onTrackClick = { track ->
                viewModel.playPause()
                showArtistSheet = false
            },
            onAlbumClick = { id ->
                showArtistSheet = false
                onAlbumClick(id)
            },
            onGoToArtist = { id ->
                showArtistSheet = false
                onArtistClick(id)
            },
            onDismiss = { showArtistSheet = false },
        )
    }

    if (showRatingSheet && uiState.track != null) {
        dev.emusic.ui.components.RatingBottomSheet(
            trackTitle = uiState.track?.title ?: "",
            currentRating = uiState.track?.userRating,
            onRate = { rating ->
                viewModel.rateTrack(rating)
                showRatingSheet = false
            },
            onDismiss = { showRatingSheet = false },
        )
    }

    if (showPlaylistSheet && uiState.track != null) {
        dev.emusic.ui.components.AddToPlaylistSheet(
            trackId = uiState.track!!.id,
            onDismiss = { showPlaylistSheet = false },
        )
    }

    NowPlayingContent(
        state = uiState,
        onToggleLyrics = viewModel::toggleLyrics,
        onArtistNameClick = { showArtistSheet = true },
        onShowSleepTimer = { showSleepTimerSheet = true },
        onEqualizerClick = onEqualizerClick,
        onCastClick = { showCastSheet = true },
        onRetryRadio = viewModel::retryRadio,
        onPlayPause = viewModel::playPause,
        onSkipNext = viewModel::skipNext,
        onSkipPrevious = viewModel::skipPrevious,
        onSeek = viewModel::seekTo,
        onToggleShuffle = viewModel::toggleShuffle,
        onCycleRepeat = viewModel::cycleRepeatMode,
        onToggleStar = viewModel::toggleStar,
        onShowRating = { showRatingSheet = true },
        onShowAddToPlaylist = { showPlaylistSheet = true },
        onCastVolumeChange = viewModel::setCastVolume,
        onBack = onBack,
    )
}

@Composable
private fun NowPlayingContent(
    state: NowPlayingUiState,
    onToggleLyrics: () -> Unit = {},
    onArtistNameClick: () -> Unit = {},
    onShowSleepTimer: () -> Unit = {},
    onEqualizerClick: () -> Unit = {},
    onCastClick: () -> Unit = {},
    onRetryRadio: () -> Unit = {},
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleStar: () -> Unit = {},
    onShowRating: () -> Unit = {},
    onShowAddToPlaylist: () -> Unit = {},
    onCastVolumeChange: (Int) -> Unit = {},
    onBack: () -> Unit,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    var dominantColor by remember { mutableStateOf(surfaceColor) }
    val animatedColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 300),
        label = "bg_color",
    )
    val gradient = Brush.verticalGradient(
        colors = listOf(animatedColor, surfaceColor),
    )

    // Album art pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )
    val artScale by animateFloatAsState(
        targetValue = if (state.isPlaying) pulseScale else 1f,
        animationSpec = spring(),
        label = "art_scale",
    )

    // Swipe down to dismiss
    var dragOffset by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffset > 200f) {
                            onBack()
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f },
                ) { _, dragAmount ->
                    if (dragAmount > 0) { // Only swipe down
                        dragOffset += dragAmount
                    }
                }
            },
    ) {
        if (state.isLiveStream) {
            RadioNowPlayingLayout(
                state = state,
                artScale = artScale,
                onShowSleepTimer = onShowSleepTimer,
                onEqualizerClick = onEqualizerClick,
                onCastClick = onCastClick,
                onRetryRadio = onRetryRadio,
                onPlayPause = onPlayPause,
                onBack = onBack,
            )
        } else {
            MusicNowPlayingLayout(
                state = state,
                artScale = artScale,
                dominantColor = dominantColor,
                onDominantColorChange = { dominantColor = it },
                onToggleLyrics = onToggleLyrics,
                onArtistNameClick = onArtistNameClick,
                onShowSleepTimer = onShowSleepTimer,
                onEqualizerClick = onEqualizerClick,
                onCastClick = onCastClick,
                onPlayPause = onPlayPause,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                onSeek = onSeek,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
                onToggleStar = onToggleStar,
                onShowRating = onShowRating,
                onShowAddToPlaylist = onShowAddToPlaylist,
                onCastVolumeChange = onCastVolumeChange,
            )
        }
    }
}

@Composable
private fun RadioNowPlayingLayout(
    state: NowPlayingUiState,
    artScale: Float,
    onShowSleepTimer: () -> Unit = {},
    onEqualizerClick: () -> Unit = {},
    onCastClick: () -> Unit = {},
    onRetryRadio: () -> Unit = {},
    onPlayPause: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .padding(top = 48.dp, bottom = 16.dp),
    ) {
        // Cast + Timer + EQ icons
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = onCastClick) {
                    Icon(
                        Icons.Default.Cast,
                        contentDescription = if (state.isCasting) "Casting to ${state.castDeviceName}" else "Cast",
                        tint = if (state.isCasting) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onEqualizerClick) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Equalizer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onShowSleepTimer) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = "Sleep Timer",
                        tint = if (state.sleepTimerState.isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Station art
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .scale(artScale)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (state.radioFavicon.isNullOrBlank()) {
                Icon(
                    Icons.Default.Radio,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(state.radioFavicon)
                        .crossfade(300)
                        .build(),
                    contentDescription = state.radioStationName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Status badge
        when (state.radioStreamError) {
            is RadioStreamError.Reconnecting -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Reconnecting\u2026 (${state.radioStreamError.attempt}/3)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is RadioStreamError.Unavailable -> {
                Text(
                    text = "Station unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.Red),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Station name
        Text(
            text = state.radioStationName ?: "Radio",
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        // ICY metadata (animated transitions)
        AnimatedContent(
            targetState = Pair(state.icyTitle, state.icyArtist),
            transitionSpec = {
                (slideInVertically { it / 4 } + fadeIn()) togetherWith
                    (slideOutVertically { -it / 4 } + fadeOut())
            },
            label = "icy_info",
        ) { (title, artist) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                if (artist != null) {
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                if (title == null && artist == null) {
                    Text(
                        text = "Listening live",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Play/Pause or Retry
        if (state.radioStreamError is RadioStreamError.Unavailable) {
            Button(onClick = onRetryRadio) {
                Text("Retry")
            }
        } else {
            FloatingActionButton(
                onClick = onPlayPause,
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun MusicNowPlayingLayout(
    state: NowPlayingUiState,
    artScale: Float,
    dominantColor: Color,
    onDominantColorChange: (Color) -> Unit,
    onToggleLyrics: () -> Unit,
    onArtistNameClick: () -> Unit,
    onShowSleepTimer: () -> Unit = {},
    onEqualizerClick: () -> Unit = {},
    onCastClick: () -> Unit = {},
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleStar: () -> Unit = {},
    onShowRating: () -> Unit = {},
    onShowAddToPlaylist: () -> Unit = {},
    onCastVolumeChange: (Int) -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .padding(top = 48.dp, bottom = 16.dp),
    ) {
        // Cast + EQ + Sleep timer + Lyrics
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = onCastClick) {
                    Icon(
                        Icons.Default.Cast,
                        contentDescription = if (state.isCasting) "Casting to ${state.castDeviceName}" else "Cast",
                        tint = if (state.isCasting) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onEqualizerClick) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Equalizer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onShowSleepTimer) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = "Sleep Timer",
                        tint = if (state.sleepTimerState.isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggleLyrics) {
                    Icon(
                        Icons.Default.Lyrics,
                        contentDescription = "Lyrics",
                        tint = if (state.showLyrics) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Casting banner + volume
        if (state.isCasting && state.castDeviceName != null) {
            Text(
                text = "Casting to ${state.castDeviceName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            // Cast volume slider
            var castVolume by remember { mutableStateOf(20f) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                Icon(
                    Icons.Default.VolumeMute,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = castVolume,
                    onValueChange = { castVolume = it },
                    onValueChangeFinished = { onCastVolumeChange(castVolume.toInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f).height(32.dp),
                )
                Icon(
                    Icons.Default.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.showLyrics) {
            // Lyrics panel
            LyricsPanel(
                lyrics = state.lyrics,
                positionMs = state.positionMs,
                onSeek = onSeek,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        } else {
            // Album art with pulse
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(state.coverArtUrl)
                    .crossfade(300)
                    .allowHardware(false)
                    .build(),
                contentDescription = state.track?.album,
                contentScale = ContentScale.Crop,
                onState = { imageState ->
                    if (imageState is AsyncImagePainter.State.Success) {
                        val bitmap = imageState.result.image.toBitmap()
                        Palette.from(bitmap).generate { palette ->
                            palette?.dominantSwatch?.rgb?.let { rgb ->
                                onDominantColorChange(Color(rgb).copy(alpha = 0.6f))
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .scale(artScale)
                    .clip(RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        var swipeDelta = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (swipeDelta > 150f) onSkipNext()
                                else if (swipeDelta < -150f) onSkipPrevious()
                                swipeDelta = 0f
                            },
                            onDragCancel = { swipeDelta = 0f },
                        ) { _, dragAmount -> swipeDelta += dragAmount }
                    },
            )
        } // end else (album art vs lyrics)

        Spacer(Modifier.height(24.dp))

        // Animated track info
        AnimatedContent(
            targetState = state.track?.id,
            transitionSpec = {
                (slideInVertically { it / 4 } + fadeIn()) togetherWith
                    (slideOutVertically { -it / 4 } + fadeOut())
            },
            label = "track_info",
        ) { trackId ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // trackId used as key for recomposition
                val title = if (trackId == state.track?.id) state.track?.title ?: "" else ""
                val artist = if (trackId == state.track?.id) state.track?.artist ?: "" else ""
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onArtistNameClick() },
                )
            }
        }

        // Action row: Star + More menu
        if (!state.isLiveStream && state.track != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Star button
                IconButton(onClick = onToggleStar) {
                    Icon(
                        imageVector = if (state.track?.starred == true) Icons.Filled.Star
                            else Icons.Outlined.StarOutline,
                        contentDescription = "Star",
                        tint = if (state.track?.starred == true) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row {
                    IconButton(onClick = onShowAddToPlaylist) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Add to playlist",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onShowRating) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Rate",
                            tint = if ((state.track?.userRating ?: 0) > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.height(24.dp))
        }

        // Scrub bar
        val duration = state.durationMs.toFloat().coerceAtLeast(1f)
        var isSeeking by remember { mutableStateOf(false) }
        var seekPosition by remember { mutableStateOf(0f) }

        Slider(
            value = if (isSeeking) seekPosition else state.positionMs.toFloat(),
            onValueChange = {
                isSeeking = true
                seekPosition = it
            },
            onValueChangeFinished = {
                onSeek(seekPosition.toLong())
                isSeeking = false
            },
            valueRange = 0f..duration,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        )

        // Time labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(if (isSeeking) seekPosition.toLong() else state.positionMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTime(state.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (state.shuffleEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            IconButton(onClick = onSkipPrevious) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(36.dp),
                )
            }

            FloatingActionButton(
                onClick = onPlayPause,
                shape = CircleShape,
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(36.dp),
                )
            }

            IconButton(onClick = onSkipNext) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(36.dp),
                )
            }

            IconButton(onClick = onCycleRepeat) {
                Icon(
                    imageVector = when (state.repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    },
                    contentDescription = "Repeat",
                    tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
