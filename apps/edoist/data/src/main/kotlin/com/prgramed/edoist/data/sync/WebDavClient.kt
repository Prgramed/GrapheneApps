package com.prgramed.edoist.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun upload(
        url: String,
        username: String,
        password: String,
        path: String,
        data: ByteArray,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Ensure parent directory exists via MKCOL
            val dirPath = path.substringBeforeLast('/', "")
            if (dirPath.isNotEmpty()) {
                ensureDirectory(url, username, password, dirPath)
            }

            val fullUrl = buildUrl(url, path)
            val request = Request.Builder()
                .url(fullUrl)
                .put(data.toRequestBody("application/json".toMediaType()))
                .header("Authorization", Credentials.basic(username, password))
                .build()
            val response = okHttpClient.newCall(request).execute()
            response.use { it.isSuccessful || it.code == 201 || it.code == 204 }
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureDirectory(url: String, username: String, password: String, dirPath: String) {
        try {
            val dirUrl = buildUrl(url, dirPath)
            val mkcolRequest = Request.Builder()
                .url(dirUrl)
                .method("MKCOL", null)
                .header("Authorization", Credentials.basic(username, password))
                .build()
            val response = okHttpClient.newCall(mkcolRequest).execute()
            response.close() // 201 = created, 405 = already exists, both fine
        } catch (_: Exception) {
        }
    }

    suspend fun download(
        url: String,
        username: String,
        password: String,
        path: String,
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val fullUrl = buildUrl(url, path)
            val request = Request.Builder()
                .url(fullUrl)
                .get()
                .header("Authorization", Credentials.basic(username, password))
                .build()
            val response = okHttpClient.newCall(request).execute()
            response.use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun exists(
        url: String,
        username: String,
        password: String,
        path: String,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val fullUrl = buildUrl(url, path)
            val request = Request.Builder()
                .url(fullUrl)
                .head()
                .header("Authorization", Credentials.basic(username, password))
                .build()
            val response = okHttpClient.newCall(request).execute()
            response.use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun testConnection(
        url: String,
        username: String,
        password: String,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = url.trimEnd('/')
            val request = Request.Builder()
                .url(normalizedUrl)
                .method(
                    "PROPFIND",
                    PROPFIND_BODY.toRequestBody("application/xml".toMediaType()),
                )
                .header("Authorization", Credentials.basic(username, password))
                .header("Depth", "0")
                .build()
            val response = okHttpClient.newCall(request).execute()
            response.use { it.isSuccessful || it.code == 207 }
        } catch (_: Exception) {
            false
        }
    }

    private fun buildUrl(baseUrl: String, path: String): String {
        val base = baseUrl.trimEnd('/')
        val cleanPath = path.trimStart('/')
        return "$base/$cleanPath"
    }

    private companion object {
        const val PROPFIND_BODY =
            """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
    }
}
