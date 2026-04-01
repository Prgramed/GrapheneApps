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
                val index = exoPlayer.currentMediaItemIndex
                queueManager.setCurrentIndex(index)

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

        // Load queue into player
        serviceScope.launch {
            loadQueueIntoPlayer()

            // React to queue changes (full reload)
            queueManager.queue.collect { _ ->
                loadQueueIntoPlayer()
            }
        }
        // React to index changes (seek only — no rebuild)
        serviceScope.launch {
            var prevIndex = queueManager.currentIndex.value
            queueManager.currentIndex.collect { newIndex ->
                if (newIndex != prevIndex) {
                    prevIndex = newIndex
                    val exoPlayer = player ?: return@collect
                    if (queueManager.isLiveStream.value) return@collect
                    if (newIndex in 0 until exoPlayer.mediaItemCount && exoPlayer.currentMediaItemIndex != newIndex) {
                        exoPlayer.seekTo(newIndex, 0)
                        exoPlayer.play()
                    }
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
            android.util.Log.d("BrowseTree", "onGetChildren: parentId=$parentId page=$page pageSize=$pageSize")
            return try {
                val children = if (parentId == BrowseTree.ROOT) {
                    browseTree.getRootItems()
                } else {
                    kotlinx.coroutines.runBlocking {
                        browseTree.getChildren(parentId, page, pageSize)
                    }
                }
                Futures.immediateFuture(
                    LibraryResult.ofItemList(ImmutableList.copyOf(children), params),
                )
            } catch (e: Exception) {
                Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            return try {
                val resolved = kotlinx.coroutines.runBlocking {
                    mediaItems.mapNotNull { item ->
                        resolveTrackToPlayable(item.mediaId)
                    }.toMutableList()
                }
                if (resolved.isEmpty()) {
                    // If we can't resolve, pass through the original items
                    Futures.immediateFuture(mediaItems)
                } else {
                    Futures.immediateFuture(resolved)
                }
            } catch (e: Exception) {
                android.util.Log.e("BrowseTree", "onAddMediaItems failed", e)
                Futures.immediateFuture(mediaItems)
            }
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
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Persist final position
        player?.let { queueManager.persistPositionMs(it.currentPosition) }
        equalizerManager.release()
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

    private suspend fun loadQueueIntoPlayer() {
        if (queueManager.isLiveStream.value) return
        val exoPlayer = player ?: return
        val tracks = queueManager.queue.value.map { it.track }
        val currentIndex = queueManager.currentIndex.value
        val maxBitRate = batteryAwareQualityManager.maxBitRate.value

        val mediaItems = tracks.map { track -> track.toMediaItem(maxBitRate) }

        val currentPosition = exoPlayer.currentPosition
        val wasPlaying = exoPlayer.playWhenReady

        if (mediaItems.isEmpty()) {
            exoPlayer.clearMediaItems()
            return
        }

        if (!mediaItemsMatch(exoPlayer, mediaItems)) {
            val startPosition = if (!hasRestoredPosition) {
                hasRestoredPosition = true
                queueManager.restoredPositionMs
            } else {
                0L
            }
            exoPlayer.setMediaItems(mediaItems, currentIndex.coerceAtLeast(0), startPosition)
            exoPlayer.shuffleModeEnabled = queueManager.restoredShuffle
            exoPlayer.repeatMode = queueManager.restoredRepeat
            exoPlayer.prepare()
            if (isInitialQueueLoad) {
                isInitialQueueLoad = false
            } else {
                exoPlayer.play()
            }
        }
    }

    private fun mediaItemsMatch(player: ExoPlayer, items: List<MediaItem>): Boolean {
        if (player.mediaItemCount != items.size) return false
        for (i in items.indices) {
            if (player.getMediaItemAt(i).mediaId != items[i].mediaId) return false
        }
        return true
    }

    private fun Track.toMediaItem(maxBitRate: Int): MediaItem {
        val uri = streamResolver.resolveUri(this, maxBitRate)
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
                    .build(),
            )
            .build()
    }
}
