package dev.emusic.domain.repository

import androidx.paging.PagingData
import dev.emusic.domain.model.Album
import dev.emusic.domain.model.AlbumInfo
import dev.emusic.domain.model.Artist
import dev.emusic.domain.model.ArtistInfo
import dev.emusic.domain.model.Lyrics
import dev.emusic.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {

    // Sync (API → Room)
    suspend fun syncArtists()
    suspend fun syncAlbums()
    suspend fun syncAlbumsIncremental(): List<String>
    suspend fun syncAlbumTracks(albumId: String)
    suspend fun syncAllTracks(onProgress: (current: Int, total: Int) -> Unit = { _, _ -> })

    // Observe (Room → Domain)
    fun observeArtists(): Flow<PagingData<Artist>>
    fun observeAlbums(): Flow<PagingData<Album>>
    fun observeAlbumsSorted(sortSql: String, filterWhere: String): Flow<PagingData<Album>>
    fun observeAllTracks(): Flow<PagingData<Track>>
    fun observeAlbumsByArtist(artistId: String): Flow<List<Album>>
    fun observeAlbumsByGenre(genre: String): Flow<List<Album>>
    fun observeTracksByAlbum(albumId: String): Flow<List<Track>>
    fun searchTracks(query: String): Flow<List<Track>>

    // Single item
    suspend fun getTrack(id: String): Track?
    suspend fun getAlbum(id: String): Album?
    suspend fun getArtist(id: String): Artist?

    // Star (optimistic Room update + API)
    suspend fun starTrack(id: String)
    suspend fun unstarTrack(id: String)
    suspend fun starAlbum(id: String)
    suspend fun unstarAlbum(id: String)
    suspend fun starArtist(id: String)
    suspend fun unstarArtist(id: String)
    suspend fun syncStarred()

    // Rating
    suspend fun setRating(id: String, rating: Int)

    // URL helpers
    fun getCoverArtUrl(id: String, size: Int = 300): String
    fun getStreamUrl(id: String, maxBitRate: Int = 0): String

    // Info (direct API, not cached)
    suspend fun getArtistInfo(id: String): ArtistInfo?
    suspend fun getAlbumInfo(id: String): AlbumInfo?
    suspend fun getTopSongs(artistName: String): List<Track>
    suspend fun getSimilarSongs(id: String, count: Int = 25): List<Track>
    suspend fun getRandomSongs(size: Int = 20, genre: String? = null): List<Track>
    suspend fun getLyrics(artist: String, title: String): Lyrics?
}
