package dev.emusic.data.api

import dev.emusic.data.api.dto.SubsonicResponseWrapper
import retrofit2.http.GET
import retrofit2.http.Query

interface SubsonicApiService {

    // --- Auth ---

    @GET("rest/ping")
    suspend fun ping(): SubsonicResponseWrapper

    // --- Library ---

    @GET("rest/getArtists")
    suspend fun getArtists(): SubsonicResponseWrapper

    @GET("rest/getArtist")
    suspend fun getArtist(@Query("id") id: String): SubsonicResponseWrapper

    @GET("rest/getAlbum")
    suspend fun getAlbum(@Query("id") id: String): SubsonicResponseWrapper

    @GET("rest/getAlbumList2")
    suspend fun getAlbumList2(
        @Query("type") type: String,
        @Query("size") size: Int = 500,
        @Query("offset") offset: Int = 0,
    ): SubsonicResponseWrapper

    // --- Search ---

    @GET("rest/search3")
    suspend fun search3(
        @Query("query") query: String,
        @Query("artistCount") artistCount: Int = 20,
        @Query("albumCount") albumCount: Int = 20,
        @Query("songCount") songCount: Int = 20,
        @Query("artistOffset") artistOffset: Int = 0,
        @Query("albumOffset") albumOffset: Int = 0,
        @Query("songOffset") songOffset: Int = 0,
    ): SubsonicResponseWrapper

    // --- Playlists ---

    @GET("rest/getPlaylists")
    suspend fun getPlaylists(): SubsonicResponseWrapper

    @GET("rest/getPlaylist")
    suspend fun getPlaylist(@Query("id") id: String): SubsonicResponseWrapper

    @GET("rest/createPlaylist")
    suspend fun createPlaylist(
        @Query("name") name: String,
        @Query("songId") songIds: List<String>? = null,
    ): SubsonicResponseWrapper

    @GET("rest/updatePlaylist")
    suspend fun updatePlaylist(
        @Query("playlistId") playlistId: String,
        @Query("name") name: String? = null,
        @Query("comment") comment: String? = null,
        @Query("public") public: Boolean? = null,
        @Query("songIdToAdd") songIdsToAdd: List<String>? = null,
        @Query("songIndexToRemove") songIndexesToRemove: List<Int>? = null,
    ): SubsonicResponseWrapper

    @GET("rest/deletePlaylist")
    suspend fun deletePlaylist(@Query("id") id: String): SubsonicResponseWrapper

    // --- Similar / Top / Random ---

    @GET("rest/getSimilarSongs2")
    suspend fun getSimilarSongs2(
        @Query("id") id: String,
        @Query("count") count: Int = 25,
    ): SubsonicResponseWrapper

    @GET("rest/getTopSongs")
    suspend fun getTopSongs(
        @Query("artist") artist: String,
        @Query("count") count: Int = 50,
    ): SubsonicResponseWrapper

    @GET("rest/getRandomSongs")
    suspend fun getRandomSongs(
        @Query("size") size: Int = 20,
        @Query("genre") genre: String? = null,
    ): SubsonicResponseWrapper

    // --- Starring ---

    @GET("rest/star")
    suspend fun star(
        @Query("id") id: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("artistId") artistId: String? = null,
    ): SubsonicResponseWrapper

    @GET("rest/unstar")
    suspend fun unstar(
        @Query("id") id: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("artistId") artistId: String? = null,
    ): SubsonicResponseWrapper

    @GET("rest/getStarred2")
    suspend fun getStarred2(): SubsonicResponseWrapper

    // --- Scrobble ---

    @GET("rest/scrobble")
    suspend fun scrobble(
        @Query("id") id: String,
        @Query("submission") submission: Boolean = true,
    ): SubsonicResponseWrapper

    // --- Lyrics ---

    @GET("rest/getLyrics")
    suspend fun getLyrics(
        @Query("artist") artist: String? = null,
        @Query("title") title: String? = null,
    ): SubsonicResponseWrapper

    // --- Rating ---

    @GET("rest/setRating")
    suspend fun setRating(
        @Query("id") id: String,
        @Query("rating") rating: Int,
    ): SubsonicResponseWrapper

    // --- Play Queue ---

    @GET("rest/savePlayQueue")
    suspend fun savePlayQueue(
        @Query("id") ids: List<String>,
        @Query("current") current: String,
        @Query("position") position: Long,
    ): SubsonicResponseWrapper

    @GET("rest/getPlayQueue")
    suspend fun getPlayQueue(): SubsonicResponseWrapper

    // --- Info ---

    @GET("rest/getArtistInfo2")
    suspend fun getArtistInfo2(
        @Query("id") id: String,
        @Query("count") count: Int = 20,
    ): SubsonicResponseWrapper

    @GET("rest/getAlbumInfo2")
    suspend fun getAlbumInfo2(@Query("id") id: String): SubsonicResponseWrapper
}
