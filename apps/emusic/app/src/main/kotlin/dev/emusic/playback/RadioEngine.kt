package dev.emusic.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.emusic.domain.model.Track
import dev.emusic.domain.repository.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioEngine @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val queueManager: QueueManager,
    private val dataStore: DataStore<Preferences>,
) {
    private val scope = CoroutineScope(SupervisorJob())

    var isActive: Boolean = false
        private set

    private val playedIds = mutableSetOf<String>()
    private var lastSeedTrackId: String? = null

    init {
        // Set the queue-low callback
        queueManager.onQueueLow = { refillIfNeeded() }
    }

    fun startFromTrack(track: Track) {
        scope.launch {
            isActive = true
            playedIds.clear()
            playedIds.add(track.id)
            lastSeedTrackId = track.id

            val similar = fetchSimilar(track.id, track.genre)
            val queue = listOf(track) + similar
            queueManager.play(queue, 0)
        }
    }

    fun startFromArtist(artistId: String, artistName: String) {
        scope.launch {
            isActive = true
            playedIds.clear()

            val topSongs = libraryRepository.getTopSongs(artistName)
            val seed = topSongs.firstOrNull() ?: return@launch
            playedIds.add(seed.id)
            lastSeedTrackId = seed.id

            val similar = fetchSimilar(seed.id, seed.genre)
            val queue = listOf(seed) + similar
            queueManager.play(queue, 0)
        }
    }

    fun stop() {
        isActive = false
        playedIds.clear()
        lastSeedTrackId = null
    }

    fun banTrack(trackId: String) {
        scope.launch {
            dataStore.edit { prefs ->
                val current = prefs[BANNED_IDS_KEY] ?: emptySet()
                prefs[BANNED_IDS_KEY] = current + trackId
            }
        }
    }

    private fun refillIfNeeded() {
        if (!isActive) return
        val remaining = queueManager.queue.value.size - queueManager.currentIndex.value
        if (remaining > 5) return

        scope.launch {
            val seedId = lastSeedTrackId ?: return@launch
            val currentTrack = queueManager.currentTrack.value
            val genre = currentTrack?.genre

            val newTracks = fetchSimilar(seedId, genre, count = 20)
            if (newTracks.isNotEmpty()) {
                // Use the last fetched track as next seed
                lastSeedTrackId = newTracks.last().id
                newTracks.forEach { queueManager.addToQueue(it) }
            }
        }
    }

    private suspend fun fetchSimilar(trackId: String, genre: String?, count: Int = 25): List<Track> {
        val bannedIds = dataStore.data.first()[BANNED_IDS_KEY] ?: emptySet()

        var results = libraryRepository.getSimilarSongs(trackId, count)
            .filter { it.id !in playedIds && it.id !in bannedIds }

        // Fallback: supplement with random songs if too few
        if (results.size < 5) {
            val random = libraryRepository.getRandomSongs(20, genre)
                .filter { it.id !in playedIds && it.id !in bannedIds }
            results = results + random
        }

        val unique = results.distinctBy { it.id }.take(count)
        playedIds.addAll(unique.map { it.id })
        return unique
    }

    companion object {
        private val BANNED_IDS_KEY = stringSetPreferencesKey("radio_banned_track_ids")
    }
}
