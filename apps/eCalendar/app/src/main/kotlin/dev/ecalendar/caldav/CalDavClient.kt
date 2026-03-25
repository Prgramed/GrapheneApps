package dev.ecalendar.caldav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

class CalDavClient(
    private val baseUrl: String,
    username: String,
    password: String,
    baseClient: OkHttpClient = OkHttpClient(),
) {
    private val client: OkHttpClient = baseClient.newBuilder()
        .addInterceptor(BasicAuthInterceptor(username, password))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .followRedirects(true)
        .build()

    private val xmlMediaType = "application/xml; charset=utf-8".toMediaType()
    private val icsMediaType = "text/calendar; charset=utf-8".toMediaType()

    suspend fun propfind(url: String, depth: Int, body: String): Response =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .method("PROPFIND", body.toRequestBody(xmlMediaType))
                .header("Depth", depth.toString())
                .header("Content-Type", "application/xml; charset=utf-8")
                .build()
            client.newCall(request).execute()
        }

    suspend fun report(url: String, body: String): Response =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .method("REPORT", body.toRequestBody(xmlMediaType))
                .header("Depth", "1")
                .header("Content-Type", "application/xml; charset=utf-8")
                .build()
            client.newCall(request).execute()
        }

    suspend fun put(url: String, icsContent: String, etag: String? = null): Response =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(url)
                .put(icsContent.toRequestBody(icsMediaType))
                .header("Content-Type", "text/calendar; charset=utf-8")

            if (etag != null) {
                // Update existing — optimistic lock
                builder.header("If-Match", "\"$etag\"")
            } else {
                // Create new — fail if already exists
                builder.header("If-None-Match", "*")
            }

            client.newCall(builder.build()).execute()
        }

    suspend fun delete(url: String, etag: String): Response =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .delete()
                .header("If-Match", "\"$etag\"")
                .build()
            client.newCall(request).execute()
        }

    suspend fun get(url: String): Response =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            client.newCall(request).execute()
        }
}
