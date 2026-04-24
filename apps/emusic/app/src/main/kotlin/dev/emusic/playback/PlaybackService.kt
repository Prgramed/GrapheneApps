package dev.emusic.playback

import android.content.Intent
import timber.log.Timber
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import dev.emusic.data.preferences.AppPreferencesRepository
import dev.emusic.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    companion object

    @Inject lateinit var queueManager: QueueManager
    @Inject lateinit var streamResolver: OfflineStreamResolver
    @Inject lateinit var preferencesRepository: AppPreferencesRepository
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var scrobbleManager: ScrobbleManager
    @Inject lateinit var batteryAwareQualityManager: BatteryAwareQualityManager
    @Inject lateinit var replayGainManager: ReplayGainManager
    @Inject lateinit var icyMetadataHandler: IcyMetadataHandler
    @Inject lateinit var sleepTimerManager: SleepTimerManager
    @Inject lateinit var internetRadioRepository: dev.emusic.domain.repository.InternetRadioRepository
    @Inject lateinit var radioNowPlayingBridge: RadioNowPlayingBridge
    @Inject lateinit var equalizerManager: EqualizerManager
    @Inject lateinit var castManager: dev.emusic.playback.cast.CastManager
    @Inject lateinit var browseTree: BrowseTree
    @Inject lateinit var urlBuilder: dev.emusic.data.api.SubsonicUrlBuilder
    @Inject lateinit var trackDaoForBrowse: dev.emusic.data.db.dao.TrackDao
    @Inject lateinit var subsonicApi: dev.emusic.data.api.SubsonicApiService

    private val _radioStreamState = kotlinx.coroutines.flow.MutableStateFlow<RadioStreamState>(RadioStreamState.Idle)
    val radioStreamState: kotlinx.coroutines.flow.StateFlow<RadioStreamState> = _radioStreamState

    private var mediaSession: MediaLibrarySession? = null
    private var player: ExoPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private var hasPlayedFirstTrack = false
    private var hasRestoredPosition = false
    private var isInitialQueueLoad = true
    private var radioRetryCount = 0
    private var radioRetryJob: Job? = null
    private var widgetPollJob: Job? = null
    private var autoStopJob: Job? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player = exoPlayer
        scrobbleManager.startObserving(exoPlayer)
        sleepTimerManager.setPlayer(exoPlayer)

        mediaSession = MediaLibrarySession.Builder(this, exoPlayer, librarySessionCallback)
            .build()

        // Auto-disconnect cast on network loss
        val connectivityManager = getSystemService(android.net.ConnectivityManager::class.java)
        connectivityManager?.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onLost(network: android.net.Network) {
                if (castManager.isCasting) {
                    timber.log.Timber.d("Network lost — auto-disconnecting cast")
                    castManager.disconnect()
                }
            }
        })

        // Mute local player when casting starts, restore when disconnected
        var preCastVolume = 1f
        serviceScope.launch {
            castManager.activeDevice.collect { device ->
                if (device != null) {
                    // Casting started — save current volume, mute, and ensure playing
                    preCastVolume = exoPlayer.volume
                    exoPlayer.volume = 0f
                    if (!exoPlayer.isPlaying) {
                        exoPlayer.play() // Keep local player running (muted) for position tracking
                    }
                } else {
                    // Casting stopped — restore saved volume
                    exoPlayer.volume = preCastVolume
                }
            }
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                equalizerManager.release()
                equalizerManager.initialize(audioSessionId)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                queueManager.persistShuffleRepeat(shuffleModeEnabled, exoPlayer.repeatMode)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                queueManager.persistShuffleRepeat(exoPlayer.shuffleModeEnabled, repeatMode)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Look up the transitioning media item BY ITS ID in the queue,
                // not by ExoPlayer's currentMediaItemIndex. During rapid skip
                // sequences, the index counter may have advanced past the callback's
                // media item, causing a mediaId mismatch that silently dropped the
                // index update — which is the root cause of "loses current track".
                val mid = mediaItem?.mediaId
                val currentQueue = queueManager.queue.value
                val matchIndex = if (mid != null) {
                    currentQueue.indexOfFirst { it.track.id == mid }
                } else {
                    -1
                }
                if (matchIndex >= 0) {
                    queueManager.setCurrentIndex(matchIndex)
                }

                // Fire heads-up on skip or auto-advance, not on first play
                if (hasPlayedFirstTrack && mediaItem != null) {
                    val shouldNotify = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                    if (shouldNotify) {
                        val track = queueManager.currentTrack.value
                        if (track != null) {
                            notificationHelper.fireHeadsUpNotification(this@PlaybackService, track)
                        }
                    }
                }
                hasPlayedFirstTrack = true

                // Update widget
                broadcastWidgetUpdate()

                // Forward to cast device if casting
                val castTrack = queueManager.currentTrack.value
                if (castTrack != null && castManager.isCasting) {
                    castManager.onTrackChanged(castTrack)
                }

                // Save play queue to server (debounced)
                if (!queueManager.isLiveStream.value) {
                    queueManager.saveToServer(subsonicApi, exoPlayer.currentPosition)
                }

                // Check sleep timer "stop after track"
                if (sleepTimerManager.checkStopAfterTrack()) return

                // Apply ReplayGain volume (skip if casting — local player is muted)
                if (!castManager.isCasting) {
                    val currentTrack = queueManager.currentTrack.value
                    if (currentTrack != null) {
                        exoPlayer.volume = replayGainManager.computeVolume(currentTrack)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Save queue to server on pause
                if (!isPlaying && !queueManager.isLiveStream.value) {
                    queueManager.saveToServer(subsonicApi, exoPlayer.currentPosition)
                }
                if (isPlaying && queueManager.isLiveStream.value) {
                    radioRetryCount = 0
                    radioNowPlayingBridge.onStreamRecovered()
                }
                broadcastWidgetUpdate()

                // Event-driven widget polling + auto-stop
                widgetPollJob?.cancel()
                autoStopJob?.cancel()
                if (isPlaying) {
                    // Poll widget every 30s while playing
                    widgetPollJob = serviceScope.launch {
                        while (true) {
                            delay(30_000)
                            broadcastWidgetUpdate()
                        }
                    }
                } else {
                    // Auto-stop after 30 minutes of pause
                    autoStopJob = serviceScope.launch {
                        delay(30 * 60_000L)
                        stopSelf()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val errorCode = error.errorCode
                Timber.e( "Playback error: code=$errorCode, message=${error.message}")

                // Radio stream retry logic
                if (queueManager.isLiveStream.value) {
                    radioRetryCount++
                    val station = radioNowPlayingBridge.currentStation.value
                    if (radioRetryCount > 3 || station == null) {
                        Timber.w( "Radio stream failed after $radioRetryCount attempts")
                        _radioStreamState.value = RadioStreamState.Offline(
                            station ?: return,
                        )
                        radioNowPlayingBridge.onStreamFailed()
                        exoPlayer.pause()
                        return
                    }

                    Timber.w( "Radio stream error, retry attempt $radioRetryCount/3")
                    _radioStreamState.value = RadioStreamState.Reconnecting(station, radioRetryCount)
                    radioNowPlayingBridge.onStreamReconnecting(radioRetryCount)

                    radioRetryJob?.cancel()
                    radioRetryJob = serviceScope.launch {
                        val delayMs = 2000L * (1 shl (radioRetryCount - 1)) // 2s, 4s, 8s
                        delay(delayMs)
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                    return
                }

                when {
                    // 404 — track missing on server, skip to next
                    errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> {
                        Timber.w( "Track not found on server, skipping")
                        if (exoPlayer.hasNextMediaItem()) {
                            exoPlayer.seekToNextMediaItem()
                            exoPlayer.prepare()
                            exoPlayer.play()
                        } else {
                            exoPlayer.pause()
                        }
                    }
                    // Network error — pause gracefully
                    errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                        Timber.w( "Network lost during playback")
                        exoPlayer.pause()
                    }
                    // Other IO errors
                    else -> {
                        Timber.e( "Unhandled playback error", error)
                        exoPlayer.pause()
                    }
                }
            }
        })

        // Fire notification when sleep timer expires
        serviceScope.launch {
            sleepTimerManager.state.collect { timerState ->
                if (timerState.fired) {
                    notificationHelper.fireSleepTimerExpiredNotification(this@PlaybackService)
                }
            }
        }

        // Widget + auto-stop: event-driven via player listener (added below)
        // widgetJob and autoStopJob managed in onIsPlayingChanged listener

        // Try to restore queue from server if local queue is empty
        serviceScope.launch {
            queueManager.restoreFromServer(subsonicApi)
        }

        // Load queue into player — version-based to avoid race conditions
        serviceScope.launch {
            loadQueueIntoPlayer()

            var lastVersion = queueManager.queueVersion.value
            queueManager.queueVersion.collect { version ->
                if (version != lastVersion) {
                    lastVersion = version
                    backfillJob?.cancel()
                    loadQueueIntoPlayer()
                }
            }
        }
        // Index-only seek for same-queue track switches (e.g. click different track in same album).
        // Guard: only seek if ExoPlayer's current index disagrees AND the change wasn't
        // driven by ExoPlayer itself (onMediaItemTransition already moved there). Without
        // this guard, rapid skip-next creates a feedback loop: transition callback →
        // setCurrentIndex → observer fires → redundant seekTo → another transition callback.
        serviceScope.launch {
            var prevIndex = queueManager.currentIndex.value
            var prevVersion = queueManager.queueVersion.value
            queueManager.currentIndex.collect { newIndex ->
                val currentVersion = queueManager.queueVersion.value
                if (currentVersion == prevVersion && newIndex != prevIndex) {
                    prevIndex = newIndex
                    val exoPlayer = player ?: return@collect
                    if (!queueManager.isLiveStream.value &&
                        newIndex in 0 until exoPlayer.mediaItemCount &&
                        exoPlayer.currentMediaItemIndex != newIndex) {
                        // ExoPlayer is at a DIFFERENT index — this is a user-initiated
                        // jump (e.g. tapping a specific track in the queue list).
                        exoPlayer.seekTo(newIndex, 0)
                        exoPlayer.play()
                    }
                    // else: ExoPlayer already at newIndex (transition-driven update) — no-op
                } else {
                    prevIndex = newIndex
                    prevVersion = currentVersion
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    private val librarySessionCallback = object : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            android.util.Log.d("BrowseTree", "onGetLibraryRoot called from ${browser.packageName}")
            val root = MediaItem.Builder()
                .setMediaId(BrowseTree.ROOT)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .setTitle("eMusic")
                        .build(),
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (parentId == BrowseTree.ROOT) {
                return Futures.immediateFuture(
                    LibraryResult.ofItemList(ImmutableList.copyOf(browseTree.getRootItems()), params),
                )
            }
            val future = com.google.common.util.concurrent.SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                try {
                    val children = browseTree.getChildren(parentId, page, pageSize)
                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(children), params))
                } catch (e: Exception) {
                    future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
                }
            }
            return future
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val future = com.google.common.util.concurrent.SettableFuture.create<MutableList<MediaItem>>()
            serviceScope.launch {
                try {
                    val resolved = mediaItems.mapNotNull { item ->
                        resolveTrackToPlayable(item.mediaId)
                    }.toMutableList()
                    future.set(if (resolved.isEmpty()) mediaItems else resolved)
                } catch (e: Exception) {
                    future.set(mediaItems)
                }
            }
            return future
        }
    }

    private suspend fun resolveTrackToPlayable(trackId: String): MediaItem? {
        val track = trackDaoForBrowse.getById(trackId) ?: return null
        val domainTrack = dev.emusic.domain.model.Track(
            id = track.id, title = track.title, artist = track.artist,
            artistId = track.artistId, album = track.album, albumId = track.albumId,
            duration = track.duration, trackNumber = track.trackNumber,
            localPath = track.localPath,
        )
        val streamUri = streamResolver.resolveUri(domainTrack)
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(streamUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(android.net.Uri.parse(urlBuilder.getCoverArtUrlWithAuth(track.coverArtId ?: track.albumId)))
                    .build(),
            )
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Always stop when app is swiped away — persist position first
        player?.let { queueManager.persistPositionMs(it.currentPosition) }
        stopSelf()
    }

    override fun onDestroy() {
        // onTaskRemoved already persisted position if app was swiped away.
        // Only persist here if player is still alive (direct stopSelf path).
        if (player?.isPlaying == true || player?.playWhenReady == true) {
            player?.let { queueManager.persistPositionMs(it.currentPosition) }
        }
        equalizerManager.release()
        batteryAwareQualityManager.release()
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    private fun broadcastWidgetUpdate() {
        sendBroadcast(
            android.content.Intent(dev.emusic.ui.widget.MusicWidgetProvider.ACTION_WIDGET_UPDATE)
                .setPackage(packageName),
        )
    }

    fun playLiveStream(station: dev.emusic.domain.model.RadioStation) {
        val exoPlayer = player ?: return

        // Clear music queue
        queueManager.clear()
        queueManager.setLiveStream(true)
        icyMetadataHandler.clear()
        radioRetryCount = 0
        radioRetryJob?.cancel()
        radioNowPlayingBridge.onStationStarted(station)
        _radioStreamState.value = RadioStreamState.Loading(station)

        // Create media item for live stream
        val mediaItem = MediaItem.Builder()
            .setMediaId("radio_${station.stationUuid}")
            .setUri(station.urlResolved ?: station.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setArtist(station.country ?: "")
                    .build(),
            )
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()

        // Report click to Radio Browser API
        serviceScope.launch {
            internetRadioRepository.reportClick(station.stationUuid)
            internetRadioRepository.updateLastPlayed(station.stationUuid)
        }

        // Monitor ICY metadata
        serviceScope.launch {
            icyMetadataHandler.nowPlaying.collect { icy ->
                val current = _radioStreamState.value
                if (current is RadioStreamState.Loading || current is RadioStreamState.Playing) {
                    val s = (current as? RadioStreamState.Playing)?.station
                        ?: (current as? RadioStreamState.Loading)?.station
                        ?: return@collect
                    _radioStreamState.value = RadioStreamState.Playing(s, icy)

                    // Update notification with current song info
                    val icyText = listOfNotNull(icy?.artist, icy?.title).joinToString(" \u2014 ")
                    if (icyText.isNotBlank()) {
                        val currentItem = exoPlayer.currentMediaItem ?: return@collect
                        val updated = currentItem.buildUpon()
                            .setMediaMetadata(
                                currentItem.mediaMetadata.buildUpon()
                                    .setTitle(s.name)
                                    .setArtist(icyText)
                                    .build(),
                            )
                            .build()
                        exoPlayer.replaceMediaItem(0, updated)
                    }
                }
            }
        }
    }

    private var backfillJob: Job? = null

    private suspend fun loadQueueIntoPlayer() {
        if (queueManager.isLiveStream.value) return
        val exoPlayer = player ?: return
        val tracks = queueManager.queue.value.map { it.track }
        val currentIndex = queueManager.currentIndex.value
        val maxBitRate = batteryAwareQualityManager.maxBitRate.value

        if (tracks.isEmpty()) {
            exoPlayer.clearMediaItems()
            return
        }

        // Build media items off main thread
        val mediaItems = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            tracks.map { track -> track.toMediaItem(maxBitRate) }
        }
        val startPosition = if (!hasRestoredPosition) {
            hasRestoredPosition = true
            queueManager.restoredPositionMs
        } else {
            0L
        }

        backfillJob?.cancel()

        val userInitiated = queueManager.isUserInitiated
        queueManager.isUserInitiated = false

        if (isInitialQueueLoad && !userInitiated && tracks.size > 50) {
            // Cold restore: load small window, don't auto-play
            isInitialQueueLoad = false
            val windowStart = (currentIndex - 3).coerceAtLeast(0)
            val windowEnd = (currentIndex + 10).coerceAtMost(tracks.size)
            val windowItems = mediaItems.subList(windowStart, windowEnd)
            val adjustedIndex = currentIndex - windowStart

            exoPlayer.setMediaItems(windowItems, adjustedIndex.coerceAtLeast(0), startPosition)
            exoPlayer.shuffleModeEnabled = queueManager.restoredShuffle
            exoPlayer.repeatMode = queueManager.restoredRepeat
            exoPlayer.prepare()

            backfillJob = serviceScope.launch {
                kotlinx.coroutines.delay(800)
                val p = player ?: return@launch
                val nowIndex = p.currentMediaItemIndex
                val nowPos = p.currentPosition
                // Only backfill if items actually changed (avoid re-trigger loop)
                if (p.mediaItemCount != mediaItems.size) {
                    val wasPlaying = p.isPlaying
                    p.setMediaItems(mediaItems, nowIndex.coerceAtLeast(0), nowPos)
                    p.prepare()
                    if (wasPlaying) p.play()
                }
            }
        } else {
            isInitialQueueLoad = false
            exoPlayer.setMediaItems(mediaItems, currentIndex.coerceAtLeast(0), startPosition)
            exoPlayer.shuffleModeEnabled = queueManager.restoredShuffle
            exoPlayer.repeatMode = queueManager.restoredRepeat
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    private fun Track.toMediaItem(maxBitRate: Int): MediaItem {
        val uri = streamResolver.resolveUri(this, maxBitRate)
        val artworkUri = albumId?.let {
            android.net.Uri.parse(urlBuilder.getCoverArtUrlWithAuth(it))
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setTrackNumber(trackNumber)
                    .setDiscNumber(discNumber)
                    .setArtworkUri(artworkUri)
                    .build(),
            )
            .build()
    }
}
