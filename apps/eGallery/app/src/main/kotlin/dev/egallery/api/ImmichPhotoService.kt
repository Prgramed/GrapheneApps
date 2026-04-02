package dev.egallery.api

import dev.egallery.api.dto.BulkUploadCheckResponse
import dev.egallery.api.dto.DeltaSyncRequest
import dev.egallery.api.dto.DeltaSyncResponse
import dev.egallery.api.dto.ImmichAlbum
import dev.egallery.api.dto.ImmichAsset
import dev.egallery.api.dto.ImmichMapMarker
import dev.egallery.api.dto.ImmichPeopleResponse
import dev.egallery.api.dto.ImmichServerInfo
import dev.egallery.api.dto.ImmichTimeBucket
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface ImmichPhotoService {

    // --- Server ---

    @GET("/api/server/about")
    suspend fun getServerInfo(): ImmichServerInfo

    // --- Timeline ---

    @GET("/api/timeline/buckets")
    suspend fun getTimeBuckets(
        @Query("size") size: String = "MONTH",
    ): List<ImmichTimeBucket>

    @GET("/api/timeline/bucket")
    suspend fun getTimeBucket(
        @Query("timeBucket") timeBucket: String,
        @Query("size") size: String = "MONTH",
    ): dev.egallery.api.dto.ImmichTimeBucketData

    // --- Assets ---

    @GET("/api/assets/{id}")
    suspend fun getAsset(@Path("id") id: String): ImmichAsset

    @Streaming
    @GET("/api/assets/{id}/thumbnail")
    suspend fun getThumbnail(
        @Path("id") id: String,
        @Query("size") size: String = "thumbnail",
    ): ResponseBody

    @Streaming
    @GET("/api/assets/{id}/original")
    suspend fun downloadOriginal(@Path("id") id: String): ResponseBody

    @Streaming
    @GET("/api/assets/{id}/video/playback")
    suspend fun streamVideo(@Path("id") id: String): ResponseBody

    @DELETE("/api/assets")
    suspend fun deleteAssets(@Body body: kotlinx.serialization.json.JsonObject): Unit

    // --- Albums ---

    @GET("/api/albums")
    suspend fun getAlbums(): List<ImmichAlbum>

    @GET("/api/albums/{id}")
    suspend fun getAlbum(@Path("id") id: String): ImmichAlbum

    @POST("/api/albums")
    suspend fun createAlbum(@Body body: Map<String, String>): ImmichAlbum

    @PATCH("/api/albums/{id}")
    suspend fun updateAlbum(@Path("id") id: String, @Body body: Map<String, String>): ImmichAlbum

    @DELETE("/api/albums/{id}")
    suspend fun deleteAlbum(@Path("id") id: String)

    @PUT("/api/albums/{id}/assets")
    suspend fun addAssetsToAlbum(@Path("id") id: String, @Body body: Map<String, List<String>>)

    // --- People ---

    @GET("/api/people")
    suspend fun getPeople(): ImmichPeopleResponse

    @Streaming
    @GET("/api/people/{id}/thumbnail")
    suspend fun getPersonThumbnail(@Path("id") id: String): ResponseBody

    // --- Map ---

    @GET("/api/map/markers")
    suspend fun getMapMarkers(): List<ImmichMapMarker>

    // --- Search ---

    @POST("/api/search/smart")
    suspend fun searchSmart(@Body body: kotlinx.serialization.json.JsonObject): dev.egallery.api.dto.ImmichSearchResponse

    @POST("/api/search/metadata")
    suspend fun searchMetadata(@Body body: kotlinx.serialization.json.JsonObject): dev.egallery.api.dto.ImmichSearchResponse

    @GET("/api/search/suggestions")
    suspend fun getSearchSuggestions(
        @Query("type") type: String,
        @Query("query") query: String? = null,
    ): List<String>

    // --- Memories ---

    @GET("/api/memories")
    suspend fun getMemories(): List<dev.egallery.api.dto.ImmichMemory>

    // --- Sync ---

    @POST("/api/sync/delta-sync")
    suspend fun deltaSync(@Body request: DeltaSyncRequest): DeltaSyncResponse

    @POST("/api/assets/bulk-upload-check")
    suspend fun bulkUploadCheck(@Body body: kotlinx.serialization.json.JsonObject): BulkUploadCheckResponse

    // --- Upload ---

    @Multipart
    @POST("/api/assets")
    suspend fun uploadAsset(
        @Part file: MultipartBody.Part,
        @PartMap params: Map<String, @JvmSuppressWildcards RequestBody>,
    ): ImmichAsset
}
