package dev.emusic.data.api

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubsonicUrlBuilder @Inject constructor(
    private val credentialProvider: CredentialProvider,
) {
    private fun baseUrl(): String = credentialProvider.serverUrl.trimEnd('/')

    private fun authParams(): String {
        val salt = SubsonicAuthInterceptor.generateSalt()
        val token = SubsonicAuthInterceptor.md5("${credentialProvider.password}$salt")
        return "u=${credentialProvider.username}&t=$token&s=$salt" +
            "&v=${SubsonicAuthInterceptor.API_VERSION}&c=${SubsonicAuthInterceptor.CLIENT_NAME}&f=json"
    }

    /**
     * Cover art URL WITHOUT auth — used by Coil (auth added by SubsonicAuthInterceptor).
     * Stable URL = Coil cache hit on every call for the same album.
     */
    fun getCoverArtUrl(id: String, size: Int = 300): String =
        "${baseUrl()}/rest/getCoverArt?id=$id&size=$size"

    /**
     * Cover art URL WITH auth baked in — for Media3 notification/BrowseTree artwork
     * which loads via its own HTTP client (not our OkHttp interceptor).
     */
    fun getCoverArtUrlWithAuth(id: String, size: Int = 300): String =
        "${baseUrl()}/rest/getCoverArt?id=$id&size=$size&${authParams()}"

    fun getStreamUrl(id: String, maxBitRate: Int = 0, format: String? = null): String {
        val base = "${baseUrl()}/rest/stream?id=$id&${authParams()}"
        val withBitRate = if (maxBitRate > 0) "$base&maxBitRate=$maxBitRate" else base
        return if (format != null) "$withBitRate&format=$format" else withBitRate
    }
}
