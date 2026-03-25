package dev.emusic.domain.usecase

import dev.emusic.data.db.dao.ScrobbleDao
import dev.emusic.data.db.dao.TrackDao
import dev.emusic.data.db.entity.toDomain
import dev.emusic.domain.model.Album
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetSuggestionsUseCase @Inject constructor(
    private val trackDao: TrackDao,
    private val scrobbleDao: ScrobbleDao,
) {
    suspend operator fun invoke(): List<Album> {
        val topTracks = trackDao.getTopByPlayCount(50)
        if (topTracks.isEmpty()) return emptyList()

        val recentTrackIds = scrobbleDao.getRecentDistinctTrackIds(100).first().toSet()
        val now = System.currentTimeMillis()

        val scored = topTracks.map { entity ->
            val playCountScore = entity.playCount.coerceAtMost(100) / 100.0
            val starredBonus = if (entity.starred) 1.0 else 0.0
            val isRecent = entity.id in recentTrackIds
            val recencyScore = if (isRecent) 0.8 else 0.2

            val score = playCountScore * 0.4 + starredBonus * 0.3 + recencyScore * 0.3
            entity to score
        }

        return scored
            .sortedByDescending { it.second }
            .map { it.first.toDomain() }
            .distinctBy { it.albumId }
            .take(20)
            .map { track ->
                Album(
                    id = track.albumId,
                    name = track.album,
                    artist = track.artist,
                    artistId = track.artistId,
                    coverArtId = track.albumId,
                )
            }
    }
}
