package com.grapheneapps.enotes.data.sync

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Singleton
class WebDavClient @Inject constructor() {

    private val client: OkHttpClient = run {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    fun propfind(url: String, username: String, password: String, depth: Int = 1): List<WebDavEntry> {
        val body = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:resourcetype/>
    <d:getlastmodified/>
    <d:getetag/>
    <d:getcontentlength/>
  </d:prop>
</d:propfind>"""

        val request = Request.Builder()
            .url(url.trimEnd('/') + "/")
            .header("Authorization", Credentials.basic(username, password))
            .header("Depth", depth.toString())
            .method("PROPFIND", body.toRequestBody("application/xml".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val xml = response.body?.string() ?: ""
        response.close()

        if (!response.isSuccessful && response.code != 207) {
            Timber.w("PROPFIND failed: ${response.code}")
            return emptyList()
        }

        return parsePropfindResponse(xml, url)
    }

    fun propfindRaw(url: String, username: String, password: String): String? {
        val body = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
        val request = Request.Builder()
            .url(url.trimEnd('/') + "/")
            .header("Authorization", Credentials.basic(username, password))
            .header("Depth", "1")
            .method("PROPFIND", body.toRequestBody("application/xml".toMediaType()))
            .build()
        val response = client.newCall(request).execute()
        val xml = response.body?.string()
        response.close()
        return xml
    }

    fun get(url: String, username: String, password: String): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(username, password))
            .get()
            .build()
        val response = client.newCall(request).execute()
        return response.use {
            if (it.isSuccessful) {
                it.body?.bytes()
            } else {
                Timber.w("GET failed: ${it.code} $url")
                null
            }
        }
    }

    fun put(url: String, username: String, password: String, data: ByteArray, contentType: String = "application/json"): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(username, password))
            .put(data.toRequestBody(contentType.toMediaType()))
            .build()
        val response = client.newCall(request).execute()
        val success = response.isSuccessful || response.code == 201 || response.code == 204
        response.close()
        if (!success) Timber.w("PUT failed: ${response.code} $url")
        return success
    }

    fun delete(url: String, username: String, password: String): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(username, password))
            .delete()
            .build()
        val response = client.newCall(request).execute()
        val success = response.isSuccessful || response.code == 204 || response.code == 404
        response.close()
        return success
    }

    fun mkcol(url: String, username: String, password: String): Boolean {
        val request = Request.Builder()
            .url(url.trimEnd('/') + "/")
            .header("Authorization", Credentials.basic(username, password))
            .method("MKCOL", null)
            .build()
        val response = client.newCall(request).execute()
        val success = response.isSuccessful || response.code == 405 || response.code == 301
        response.close()
        return success
    }

    fun testConnection(url: String, username: String, password: String): Result<String> {
        return try {
            // Use PROPFIND instead of HEAD — more reliable on Synology WebDAV
            val propfindBody = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""

            val request = Request.Builder()
                .url(url.trimEnd('/') + "/")
                .header("Authorization", Credentials.basic(username, password))
                .header("Depth", "0")
                .method("PROPFIND", propfindBody.toRequestBody("application/xml".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            val code = response.code
            response.close()
            when {
                code == 207 || code == 200 -> Result.success("Connected")
                code == 401 -> Result.failure(Exception("Authentication failed — check username/password"))
                code == 403 -> Result.failure(Exception("Access denied — check folder permissions in DSM"))
                code == 404 -> Result.failure(Exception("Folder not found — create it on the NAS first"))
                else -> Result.failure(Exception("HTTP $code"))
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            Timber.e("Test connection failed: $msg")
            Result.failure(Exception(msg))
        }
    }

    private fun parsePropfindResponse(xml: String, baseUrl: String): List<WebDavEntry> {
        Timber.d("parsePropfindResponse: xml length=${xml.length}, first 500 chars: ${xml.take(500)}")
        val entries = mutableListOf<WebDavEntry>()
        // Match any namespace prefix: <d:response>, <D:response>, <response>, <ns0:response>, etc.
        val responsePattern = Pattern.compile("<(?:[a-zA-Z0-9]+:)?response[^>]*>(.*?)</(?:[a-zA-Z0-9]+:)?response>", Pattern.DOTALL)
        val matcher = responsePattern.matcher(xml)

        while (matcher.find()) {
            val block = matcher.group(1) ?: continue
            val href = extractTag(block, "href") ?: continue
            val etag = extractTag(block, "getetag")?.replace("\"", "")
            val lastModified = extractTag(block, "getlastmodified")
            val isCollection = block.contains("<collection", ignoreCase = true) ||
                block.contains(":collection", ignoreCase = true)
            val contentLength = extractTag(block, "getcontentlength")?.toLongOrNull() ?: 0

            val name = href.trimEnd('/').substringAfterLast('/')
            if (name.isBlank()) continue

            entries.add(
                WebDavEntry(
                    href = href,
                    name = java.net.URLDecoder.decode(name, "UTF-8"),
                    etag = etag,
                    lastModified = lastModified,
                    isCollection = isCollection,
                    contentLength = contentLength,
                ),
            )
        }

        Timber.d("parsePropfindResponse: parsed ${entries.size} entries")
        // Remove the first entry (the directory itself)
        return if (entries.isNotEmpty()) entries.drop(1) else entries
    }

    private fun extractTag(xml: String, tagLocalName: String): String? {
        // Match any namespace prefix: <d:href>, <D:href>, <href>, <ns0:href>, etc.
        val pattern = Pattern.compile("<(?:[a-zA-Z0-9]+:)?$tagLocalName>([^<]*)</(?:[a-zA-Z0-9]+:)?$tagLocalName>")
        val m = pattern.matcher(xml)
        return if (m.find()) m.group(1)?.trim() else null
    }
}
