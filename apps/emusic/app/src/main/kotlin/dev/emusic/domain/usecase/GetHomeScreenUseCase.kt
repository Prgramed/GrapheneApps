package dev.emusic.domain.usecase

import dev.emusic.data.api.SubsonicApiService
import dev.emusic.data.api.toDomain
import dev.emusic.data.db.dao.AlbumDao
import dev.emusic.data.db.dao.ScrobbleDao
import dev.emusic.data.db.entity.toDomain
import dev.emusic.domain.model.Album
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class HomeData(
    val jumpBackIn: List<Album>? = null,
    val recentlyAdded: List<Album>? = null,
    val frequentlyPlayed: List<Album>? = null,
    val starredAlbums: List<Album>? = null,
    val topRated: List<Album>? = null,
)

@Singleton
class GetHomeScreenUseCase @Inject constructor(
    private val api: SubsonicApiService,
    private val scrobbleDao: ScrobbleDao,
    private val albumDao: AlbumDao,
) {
    @Volatile private var lastFetchMs: Long = 0L
    private val cacheTtlMs = 30L * 60 * 1000 // 30 minutes

    fun isStale(): Boolean = System.currentTimeMillis() - lastFetchMs > cacheTtlMs

    suspend operator fun invoke(forceRefresh: Boolean = false): HomeData = coroutineScope {
        val now = System.currentTimeMillis()
        val isStale = forceRefresh || now - lastFetchMs > cacheTtlMs
        // Room reads — always fast
        val jumpBackIn = async {
            runCatching {
                val albumIds = scrobbleDao.getRecentAlbumIds(6)
                albumIds.mapNotNull { albumDao.getById(it)?.toDomain() }
            }.getOrNull()?.ifEmpty { null }
        }
        val cachedNewest = async { albumDao.getNewest(10).map { it.toDomain() }.ifEmpty { null } }
        val cachedFrequent = async { albumDao.getMostPlayed(10).map { it.toDomain() }.ifEmpty { null } }
        val cachedStarred = async { albumDao.getStarred(10).map { it.toDomain() }.ifEmpty { null } }

        if (!isStale) {
            return@coroutineScope HomeData(
                jumpBackIn = jumpBackIn.await(),
                recentlyAdded = cachedNewest.await(),
                frequentlyPlayed = cachedFrequent.await(),
                starredAlbums = cachedStarred.await(),
            )
        }

        // API refresh — skip "newest" (Room has it, API is very slow for this query)
        val apiFrequent = async {
            runCatching { api.getAlbumList2("frequent", size = 10).subsonicResponse.albumList2?.album?.map { it.toDomain() } }.getOrNull()?.ifEmpty { null }
        }
        val apiStarred = async {
            runCatching { api.getAlbumList2("starred", size = 10).subsonicResponse.albumList2?.album?.map { it.toDomain() } }.getOrNull()?.ifEmpty { null }
        }
        val apiTopRated = async {
            runCatching { api.getAlbumList2("highest", size = 10).subsonicResponse.albumList2?.album?.map { it.toDomain() } }.getOrNull()?.ifEmpty { null }
        }

        lastFetchMs = now

        HomeData(
            jumpBackIn = jumpBackIn.await(),
            recentlyAdded = cachedNewest.await(), // Always from Room (fast, API is 15s+ for this)
            frequentlyPlayed = apiFrequent.await() ?: cachedFrequent.await(),
            starredAlbums = apiStarred.await() ?: cachedStarred.await(),
            topRated = apiTopRated.await(),
        )
    }
}
