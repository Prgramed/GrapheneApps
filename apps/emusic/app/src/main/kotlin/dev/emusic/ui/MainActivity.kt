package dev.emusic.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.grapheneapps.core.designsystem.theme.GrapheneAppsTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.emusic.data.preferences.AppPreferencesRepository
import dev.emusic.data.preferences.NetworkMonitor
import dev.emusic.domain.repository.LibraryRepository
import dev.emusic.domain.repository.PlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import dev.emusic.playback.IcyMetadataHandler
import dev.emusic.playback.QueueManager
import dev.emusic.playback.RadioNowPlayingBridge
import dev.emusic.ui.navigation.EMusicNavHost
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var queueManager: QueueManager
    @Inject lateinit var controllerFuture: ListenableFuture<MediaController>
    @Inject lateinit var libraryRepository: LibraryRepository
    @Inject lateinit var preferencesRepository: AppPreferencesRepository
    @Inject lateinit var networkMonitor: NetworkMonitor
    @Inject lateinit var playlistRepository: PlaylistRepository
    @Inject lateinit var radioNowPlayingBridge: RadioNowPlayingBridge
    @Inject lateinit var icyMetadataHandler: IcyMetadataHandler

    private val activityScope = CoroutineScope(SupervisorJob())

    private var lastPlaylistSyncMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                val now = System.currentTimeMillis()
                if (now - lastPlaylistSyncMs < 5 * 60 * 1000) return // Skip if synced < 5 min ago
                lastPlaylistSyncMs = now
                activityScope.launch {
                    // Delay playlist sync so it doesn't compete with home screen loading
                    kotlinx.coroutines.delay(5000)
                    try {
                        val prefs = preferencesRepository.preferencesFlow.first()
                        if (prefs.serverUrl.isNotBlank()) {
                            playlistRepository.syncPlaylists()
                        }
                    } catch (_: Exception) { }
                }
            }
        })
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by preferencesRepository.preferencesFlow.collectAsState(
                initial = dev.emusic.data.preferences.AppPreferences()
            )
            val darkTheme = when (prefs.themeMode) {
                1 -> false
                2 -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            GrapheneAppsTheme(darkTheme = darkTheme) {
                val currentTrack by queueManager.currentTrack.collectAsState()

                var isPlaying by remember { mutableStateOf(false) }
                var playbackProgress by remember { mutableStateOf(0f) }
                var controller by remember { mutableStateOf<MediaController?>(null) }

                DisposableEffect(controllerFuture) {
                    val listener = Runnable {
                        val mc = controllerFuture.get()
                        controller = mc
                        isPlaying = mc.isPlaying
                        mc.addListener(object : Player.Listener {
                            override fun onIsPlayingChanged(playing: Boolean) {
                                isPlaying = playing
                            }
                        })
                    }
                    controllerFuture.addListener(listener, MoreExecutors.directExecutor())
                    onDispose {
                        controller?.removeListener(object : Player.Listener {})
                    }
                }

                // Poll playback progress — only when playing
                androidx.compose.runtime.LaunchedEffect(controller, isPlaying) {
                    while (isPlaying) {
                        controller?.let { mc ->
                            val duration = mc.duration
                            if (duration > 0) {
                                playbackProgress = mc.currentPosition.toFloat() / duration
                            }
                        }
                        kotlinx.coroutines.delay(1000)
                    }
                }

                val coverArtUrl = remember(currentTrack?.albumId) {
                    currentTrack?.albumId?.let {
                        libraryRepository.getCoverArtUrl(it)
                    }
                }

                val isServerConfigured = prefs.serverUrl.isNotBlank()
                val isOnline by networkMonitor.isOnline.collectAsState()

                val isLiveStream by queueManager.isLiveStream.collectAsState()
                val radioStation by radioNowPlayingBridge.currentStation.collectAsState()
                val icyNowPlaying by icyMetadataHandler.nowPlaying.collectAsState()
                val radioStreamError by radioNowPlayingBridge.streamError.collectAsState()

                EMusicNavHost(
                    currentTrack = currentTrack,
                    coverArtUrl = coverArtUrl,
                    isPlaying = isPlaying,
                    isServerConfigured = isServerConfigured,
                    isOffline = !isOnline,
                    playbackProgress = playbackProgress,
                    isLiveStream = isLiveStream,
                    radioStationName = radioStation?.name,
                    radioFavicon = radioStation?.favicon,
                    radioNowPlayingText = when (radioStreamError) {
                        is dev.emusic.playback.RadioStreamError.Unavailable -> "Station unavailable"
                        is dev.emusic.playback.RadioStreamError.Reconnecting -> "Reconnecting\u2026"
                        null -> icyNowPlaying?.let { icy ->
                            listOfNotNull(icy.artist, icy.title).joinToString(" \u2014 ").ifEmpty { null }
                        }
                    },
                    onPlayPause = {
                        controller?.let { mc ->
                            if (mc.isPlaying) mc.pause() else mc.play()
                        }
                    },
                    onSkipNext = { controller?.seekToNextMediaItem() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    override fun onDestroy() {
        activityScope.cancel()
        MediaController.releaseFuture(controllerFuture)
        super.onDestroy()
    }
}
