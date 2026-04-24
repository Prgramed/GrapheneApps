package dev.emusic.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.emusic.data.api.toDomain as dtoToDomain
import dev.emusic.data.db.dao.TrackDao
import dev.emusic.data.db.entity.toDomain
import dev.emusic.domain.model.QueueItem
import dev.emusic.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val trackDao: TrackDao,
    private val radioNowPlayingBridge: RadioNowPlayingBridge,
) {
    private val scope = CoroutineScope(SupervisorJob())

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    private val _currentIndex = MutableStateFlow(-1)
    private val _isLiveStream = MutableStateFlow(false)
    val isLiveStream: StateFlow<Boolean> = _isLiveStream.asStateFlow()

    private val _queueVersion = MutableStateFlow(0L)
    val queueVersion: StateFlow<Long> = _queueVersion.asStateFlow()

    val queue: StateFlow<List<QueueItem>> = _queue
        .map { tracks ->
            tracks.mapIndexed { index, track -> QueueItem(track = track, queueIndex = index) }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    val currentTrack: StateFlow<Track?> = combine(_queue, _currentIndex) { tracks, index ->
        tracks.getOrNull(index)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch { restoreQueue() }
    }

    fun setLiveStream(live: Boolean) {
        _isLiveStream.value = live
    }

    fun play(tracks: List<Track>, startIndex: Int = 0) {
        isUserInitiated = true
        _isLiveStream.value = false
        radioNowPlayingBridge.onStationStopped()
        _queue.value = tracks
        _currentIndex.value = startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        _queueVersion.value++
        persistQueue(immediate = true)
    }

    fun addToQueue(track: Track) {
        _queue.update { it + track }
        if (_currentIndex.value < 0) _currentIndex.value = 0
        persistQueue()
    }

    fun addNext(track: Track) {
        val insertAt = (_currentIndex.value + 1).coerceAtMost(_queue.value.size)
        _queue.update { it.toMutableList().apply { add(insertAt, track) } }
        if (_currentIndex.value < 0) _currentIndex.value = 0
        persistQueue()
    }

    fun remove(index: Int) {
        val current = _currentIndex.value
        _queue.update { it.toMutableList().apply { removeAt(index) } }
        when {
            index < current -> _currentIndex.value = current - 1
            index == current && current >= _queue.value.size -> {
                _currentIndex.value = (_queue.value.size - 1).coerceAtLeast(-1)
            }
        }
        persistQueue()
    }

    fun moveItem(from: Int, to: Int) {
        _queue.update {
            it.toMutableList().apply {
                val item = removeAt(from)
                add(to, item)
            }
        }
        val current = _currentIndex.value
        _currentIndex.value = when (current) {
            from -> to
            in (from + 1)..to -> current - 1
            in to until from -> current + 1
            else -> current
        }
        persistQueue()
    }

    fun shuffle() {
        val current = _queue.value.getOrNull(_currentIndex.value) ?: return
        _queue.update { list ->
            val remaining = list.toMutableList().apply { removeAt(_currentIndex.value) }
            remaining.shuffle()
            listOf(current) + remaining
        }
        _currentIndex.value = 0
        persistQueue()
    }

    fun clear() {
        _queue.value = emptyList()
        _currentIndex.value = -1
        persistQueue()
    }

    fun setCurrentIndex(index: Int) {
        if (index in _queue.value.indices) {
            _currentIndex.value = index
            persistQueue()
            checkQueueLow()
        }
    }

    private fun checkQueueLow() {
        val remaining = _queue.value.size - _currentIndex.value
        if (remaining <= 5) {
            onQueueLow?.invoke()
        }
    }

    var onQueueLow: (() -> Unit)? = null

    fun hasNext(): Boolean = _currentIndex.value < _queue.value.size - 1

    fun hasPrevious(): Boolean = _currentIndex.value > 0

    fun persistPositionMs(positionMs: Long) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[QUEUE_POSITION_KEY] = positionMs
            }
        }
    }

    private var persistJob: Job? = null

    private fun persistQueue(immediate: Boolean = false) {
        // Debounce — batch rapid index changes (e.g. skip-next) into single write.
        // When `immediate` is true (new play session), write without delay so a
        // crash right after starting a new queue doesn't lose the entire session.
        persistJob?.cancel()
        persistJob = scope.launch {
            if (!immediate) delay(200)
            dataStore.edit { prefs ->
                val ids = _queue.value.joinToString(",") { it.id }
                prefs[QUEUE_IDS_KEY] = ids
                prefs[QUEUE_INDEX_KEY] = _currentIndex.value
            }
        }
    }

    var restoredPositionMs: Long = 0L
        private set

    private suspend fun restoreQueue() {
        val prefs = dataStore.data.first()
        val idsString = prefs[QUEUE_IDS_KEY] ?: return
        val ids = idsString.split(",").filter { it.isNotEmpty() }
        if (ids.isEmpty()) return

        val tracks = ids.mapNotNull { id -> trackDao.getById(id)?.toDomain() }
        val index = prefs[QUEUE_INDEX_KEY] ?: 0
        restoredPositionMs = prefs[QUEUE_POSITION_KEY] ?: 0L
        restoredShuffle = prefs[SHUFFLE_KEY] ?: false
        restoredRepeat = prefs[REPEAT_KEY] ?: 0
        _queue.value = tracks
        _currentIndex.value = index.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        // Bump version so PlaybackService's observer fires loadQueueIntoPlayer
        // with the restored queue. Without this, the service's initial
        // loadQueueIntoPlayer() races with this coroutine and may see an
        // empty queue — leaving ExoPlayer with 0 media items, so play/next
        // buttons do nothing after a cold restart.
        _queueVersion.value++
    }

    /** True when queue was set by user action (play/shuffle), false when restored from DataStore. */
    @Volatile
    var isUserInitiated: Boolean = false

    /** Save current queue to Navidrome server for cross-device resume. */
    fun saveToServer(api: dev.emusic.data.api.SubsonicApiService, positionMs: Long) {
        val tracks = _queue.value
        val index = _currentIndex.value
        if (tracks.isEmpty() || index < 0) return
        val currentTrackId = tracks.getOrNull(index)?.id ?: return
        val ids = tracks.map { it.id }
        saveServerJob?.cancel()
        saveServerJob = scope.launch {
            delay(5000) // debounce — don't spam server
            try {
                api.savePlayQueue(ids, currentTrackId, positionMs)
            } catch (_: Exception) { }
        }
    }

    private var saveServerJob: kotlinx.coroutines.Job? = null

    /** Restore queue from Navidrome server (cold start, empty local queue). */
    suspend fun restoreFromServer(api: dev.emusic.data.api.SubsonicApiService) {
        if (_queue.value.isNotEmpty()) return // local queue exists, don't overwrite
        try {
            val response = api.getPlayQueue()
            val pq = response.subsonicResponse.playQueue ?: return
            val tracks = pq.entry.map { it.dtoToDomain() }
            if (tracks.isEmpty()) return
            val currentId = pq.current
            val index = if (currentId != null) tracks.indexOfFirst { it.id == currentId }.coerceAtLeast(0) else 0
            restoredPositionMs = pq.position ?: 0L
            _queue.value = tracks
            _currentIndex.value = index
        } catch (_: Exception) { }
    }

    var restoredShuffle: Boolean = false
        private set
    var restoredRepeat: Int = 0
        private set

    fun persistShuffleRepeat(shuffle: Boolean, repeat: Int) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[SHUFFLE_KEY] = shuffle
                prefs[REPEAT_KEY] = repeat
            }
        }
    }

    companion object {
        private val QUEUE_IDS_KEY = stringPreferencesKey("queue_track_ids")
        private val QUEUE_INDEX_KEY = intPreferencesKey("queue_current_index")
        private val QUEUE_POSITION_KEY = androidx.datastore.preferences.core.longPreferencesKey("queue_position_ms")
        private val SHUFFLE_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("queue_shuffle")
        private val REPEAT_KEY = intPreferencesKey("queue_repeat")
    }
}
