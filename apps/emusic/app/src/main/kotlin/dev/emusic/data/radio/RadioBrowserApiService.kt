package dev.emusic.data.radio

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface RadioBrowserApiService {

    @GET("json/countries")
    suspend fun getCountries(): List<CountryDto>

    @GET("json/stations/bycountrycodeexact/{code}")
    suspend fun getStationsByCountry(
        @Path("code") code: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 40,
        @Query("order") order: String = "votes",
        @Query("reverse") reverse: Boolean = true,
    ): List<RadioStationDto>

    @GET("json/stations/byname/{name}")
    suspend fun searchStations(
        @Path("name") name: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 40,
    ): List<RadioStationDto>

    @GET("json/stations/bytag/{tag}")
    suspend fun getStationsByTag(
        @Path("tag") tag: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 40,
    ): List<RadioStationDto>

    @GET("json/stations/topvote/{count}")
    suspend fun getTopVoted(
        @Path("count") count: Int = 20,
    ): List<RadioStationDto>

    @GET("json/stations/topclick/{count}")
    suspend fun getTopClicked(
        @Path("count") count: Int = 20,
    ): List<RadioStationDto>

    @POST("json/url/{uuid}")
    suspend fun reportStationClick(
        @Path("uuid") uuid: String,
    )
}
