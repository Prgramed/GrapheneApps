package com.prgramed.econtacts.data.carddav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import com.prgramed.econtacts.data.di.CardDavHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardDavClient @Inject constructor(
    @CardDavHttpClient private val httpClient: OkHttpClient,
) {

    suspend fun propfind(url: String, body: String, username: String, password: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .method("PROPFIND", body.toRequestBody(XML_MEDIA_TYPE))
                .header("Authorization", Credentials.basic(username, password))
                .header("Depth", "1")
                .header("Content-Type", "application/xml; charset=utf-8")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: throw IOException("Empty PROPFIND response")
                if (!response.isSuccessful && response.code != 207) {
                    throw IOException("PROPFIND failed (${response.code}): ${responseBody.take(200)}")
                }
                ensureXml(responseBody, "PROPFIND")
            }
        }

    suspend fun report(url: String, body: String, username: String, password: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .method("REPORT", body.toRequestBody(XML_MEDIA_TYPE))
                .header("Authorization", Credentials.basic(username, password))
                .header("Depth", "1")
                .header("Content-Type", "application/xml; charset=utf-8")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: throw IOException("Empty REPORT response")
                if (!response.isSuccessful && response.code != 207) {
                    throw IOException("REPORT failed (${response.code}): ${responseBody.take(200)}")
                }
                ensureXml(responseBody, "REPORT")
            }
        }

    private fun ensureXml(body: String, method: String): String {
        val trimmed = body.trimStart()
        if (!trimmed.startsWith("<?xml") && !trimmed.startsWith("<")) {
            throw IOException("$method returned non-XML response: ${body.take(200)}")
        }
        return body
    }

    suspend fun put(url: String, vcardData: String, username: String, password: String, etag: String? = null): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(url)
                .put(vcardData.toRequestBody(VCARD_MEDIA_TYPE))
                .header("Authorization", Credentials.basic(username, password))
                .header("Content-Type", "text/vcard; charset=utf-8")

            if (etag != null) {
                builder.header("If-Match", "\"$etag\"")
            } else {
                builder.header("If-None-Match", "*")
            }

            httpClient.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("PUT failed: ${response.code} ${response.message}")
                }
                response.header("ETag")?.removeSurrounding("\"") ?: ""
            }
        }

    suspend fun delete(url: String, username: String, password: String, etag: String? = null) =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(url)
                .delete()
                .header("Authorization", Credentials.basic(username, password))

            if (etag != null) {
                builder.header("If-Match", "\"$etag\"")
            }

            httpClient.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("DELETE failed: ${response.code} ${response.message}")
                }
            }
        }

    suspend fun testConnection(url: String, username: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .method("PROPFIND", CardDavXmlBuilder.propfindAddressBook().toRequestBody(XML_MEDIA_TYPE))
                    .header("Authorization", Credentials.basic(username, password))
                    .header("Depth", "0")
                    .header("Content-Type", "application/xml; charset=utf-8")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    response.isSuccessful || response.code == 207
                }
            } catch (_: Exception) {
                false
            }
        }

    suspend fun discover(serverUrl: String, username: String, password: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val baseUrl = serverUrl.trimEnd('/')

                // Strategy 1: Try Synology's known CardDAV path structure
                // Synology DSM 7 uses: /carddav/USERNAME/ as the principal
                val synologyPath = discoverFromSynology(baseUrl, username, password)
                if (synologyPath != null) return@withContext synologyPath

                // Strategy 2: Try .well-known/carddav (standard RFC 6764)
                val wellKnownPath = discoverFromWellKnown(baseUrl, username, password)
                if (wellKnownPath != null) return@withContext wellKnownPath

                // Strategy 3: Try PROPFIND on root for current-user-principal
                val rootPath = discoverFromRoot(baseUrl, username, password)
                if (rootPath != null) return@withContext rootPath

                null
            } catch (_: Exception) {
                null
            }
        }

    private suspend fun discoverFromSynology(
        baseUrl: String,
        username: String,
        password: String,
    ): String? = try {
        // Synology principal is at /carddav/USERNAME/
        val principalUrl = "$baseUrl/carddav/$username/"
        val listXml = propfind(principalUrl, CardDavXmlBuilder.propfindAddressBook(), username, password)
        val resources = CardDavXmlParser.parseMultistatus(listXml)
        // Find the first address book — skip the principal itself (compare normalized paths)
        val principalSuffix = "carddav/$username/"
        val addressBook = resources.firstOrNull { resource ->
            val normalized = resource.href.trimStart('/')
            normalized != principalSuffix && normalized.startsWith(principalSuffix)
        }?.href
        // Return the address book href or fall back to principal
        if (addressBook != null) {
            addressBook.trimStart('/')
        } else if (resources.isNotEmpty()) {
            // Synology returned resources but none matched — return principal
            "carddav/$username/"
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    private suspend fun discoverFromWellKnown(
        baseUrl: String,
        username: String,
        password: String,
    ): String? = try {
        val request = Request.Builder()
            .url("$baseUrl/.well-known/carddav")
            .method("PROPFIND", CardDavXmlBuilder.propfindPrincipal().toRequestBody(XML_MEDIA_TYPE))
            .header("Authorization", Credentials.basic(username, password))
            .header("Depth", "0")
            .header("Content-Type", "application/xml; charset=utf-8")
            .build()
        val (body, isSuccess) = httpClient.newCall(request).execute().use { response ->
            val b = response.body?.string()
            b to (response.isSuccessful || response.code == 207)
        }
        if (body == null || !isSuccess) return null
        if (!body.trimStart().startsWith("<")) return null

        val principal = CardDavXmlParser.parseHref(body, "current-user-principal") ?: return null
        discoverAddressBookFromPrincipal(baseUrl, principal, username, password)
    } catch (_: Exception) {
        null
    }

    private suspend fun discoverFromRoot(
        baseUrl: String,
        username: String,
        password: String,
    ): String? = try {
        val xml = propfindDepthZero(baseUrl, CardDavXmlBuilder.propfindPrincipal(), username, password)
        if (!xml.trimStart().startsWith("<")) return null
        val principal = CardDavXmlParser.parseHref(xml, "current-user-principal") ?: return null
        discoverAddressBookFromPrincipal(baseUrl, principal, username, password)
    } catch (_: Exception) {
        null
    }

    private suspend fun discoverAddressBookFromPrincipal(
        baseUrl: String,
        principalPath: String,
        username: String,
        password: String,
    ): String? = try {
        val principalUrl = resolveUrl(baseUrl, principalPath)

        val homeXml = propfindDepthZero(principalUrl, CardDavXmlBuilder.propfindAddressBookHome(), username, password)
        val homePath = CardDavXmlParser.parseHref(homeXml, "addressbook-home-set") ?: principalPath
        val homeUrl = resolveUrl(baseUrl, homePath)

        val listXml = propfind(homeUrl, CardDavXmlBuilder.propfindAddressBook(), username, password)
        val resources = CardDavXmlParser.parseMultistatus(listXml)
        resources.firstOrNull()?.href ?: homePath
    } catch (_: Exception) {
        null
    }

    private suspend fun propfindDepthZero(
        url: String,
        body: String,
        username: String,
        password: String,
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", body.toRequestBody(XML_MEDIA_TYPE))
            .header("Authorization", Credentials.basic(username, password))
            .header("Depth", "0")
            .header("Content-Type", "application/xml; charset=utf-8")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 207) {
                throw IOException("PROPFIND failed: ${response.code} ${response.message}")
            }
            response.body?.string() ?: throw IOException("Empty PROPFIND response")
        }
    }

    private fun resolveUrl(baseUrl: String, path: String): String {
        return if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            val base = baseUrl.trimEnd('/')
            val p = if (path.startsWith("/")) path else "/$path"
            // Extract scheme + host from baseUrl
            val uri = java.net.URI(base)
            "${uri.scheme}://${uri.authority}$p"
        }
    }

    companion object {
        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        private val VCARD_MEDIA_TYPE = "text/vcard; charset=utf-8".toMediaType()
    }
}
