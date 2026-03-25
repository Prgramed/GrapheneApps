package dev.emusic.domain.usecase

import dev.emusic.data.db.dao.ScrobbleDao
import dev.emusic.data.db.dao.TrackDao
import dev.emusic.data.db.entity.toDomain
import dev.emusic.domain.model.Track
import dev.emusic.domain.repository.LibraryRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds a personalized 30-track queue:
 * - 40% top played (weighted by play count)
 * - 30% starred/favorited
 * - 20% similar to recent listens (API)
 * - 10% pure random for discovery
 *
 * Falls back to API random if not enough local data.
 */
@Singleton
class SmartQuickMixUseCase @Inject constructor(
    private val trackDao: TrackDao,
    private val scrobbleDao: ScrobbleDao,
    private val libraryRepository: LibraryRepository,
) {
    suspend operator fun invoke(totalSize: Int = 30): List<Track> = coroutineScope {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<Track>()

        val topCount = (totalSize * 0.4).toInt()    // 12
        val starCount = (totalSize * 0.3).toInt()    // 9
        val similarCount = (totalSize * 0.2).toInt() // 6
        val randomCount = totalSize - topCount - starCount - similarCount // 3

        // Fetch all buckets in parallel
        val topPlayed = async {
            trackDao.getTopByPlayCount(topCount * 2) // Fetch extra to allow dedup
                .map { it.toDomain() }
        }
        val starred = async {
            trackDao.getRandomStarred(starCount * 2)
                .map { it.toDomain() }
        }
        val similar = async {
            try {
                // Seed from recent scrobbles
                val recentIds = scrobbleDao.getRecentDistinctTrackIds(5).first()
                val seedId = recentIds.firstOrNull()
                if (seedId != null) {
                    libraryRepository.getSimilarSongs(seedId, similarCount * 2)
                } else emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
        val random = async {
            trackDao.getLocalRandom(randomCount * 2)
                .map { it.toDomain() }
        }

        // Build mix with deduplication
        fun addTracks(tracks: List<Track>, maxCount: Int) {
            for (track in tracks) {
                if (result.size >= totalSize) break
                if (track.id !in seen) {
                    seen.add(track.id)
                    result.add(track)
                    if (result.size - (totalSize - maxCount) >= maxCount) break
                }
            }
        }

        // Fill each bucket (order matters for priority)
        val topList = topPlayed.await()
        val starList = starred.await()
        val similarList = similar.await()
        val randomList = random.await()

        // Take from each bucket up to its quota
        val topTake = topList.filter { it.id !in seen }.take(topCount)
        topTake.forEach { seen.add(it.id); result.add(it) }

        val starTake = starList.filter { it.id !in seen }.take(starCount)
        starTake.forEach { seen.add(it.id); result.add(it) }

        val similarTake = similarList.filter { it.id !in seen }.take(similarCount)
        similarTake.forEach { seen.add(it.id); result.add(it) }

        val randomTake = randomList.filter { it.id !in seen }.take(randomCount)
        randomTake.forEach { seen.add(it.id); result.add(it) }

        // If we don't have enough tracks, fill remaining from any source
        if (result.size < totalSize) {
            val remaining = totalSize - result.size
            val filler = (topList + starList + randomList)
                .filter { it.id !in seen }
                .take(remaining)
            filler.forEach { seen.add(it.id); result.add(it) }
        }

        // Last resort: API random (for first launch with empty Room)
        if (result.size < 10) {
            try {
                val apiRandom = libraryRepository.getRandomSongs(totalSize)
                    .filter { it.id !in seen }
                apiRandom.forEach { seen.add(it.id); result.add(it) }
            } catch (_: Exception) { }
        }

        // Shuffle so it's not grouped by category
        result.shuffled()
    }
}
