package com.prgramed.emessages.data.backup

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
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

    fun upload(url: String, username: String, password: String, data: ByteArray, contentType: String = "application/json") {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(username, password))
            .put(data.toRequestBody(contentType.toMediaType()))
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful && response.code != 201 && response.code != 204) {
            throw Exception("WebDAV upload failed: ${response.code} ${response.message}")
        }
        response.close()
    }

    fun download(url: String, username: String, password: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(username, password))
            .get()
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("WebDAV download failed: ${response.code} ${response.message}")
        }
        return response.body?.bytes() ?: throw Exception("Empty response")
    }

    fun createFolder(url: String, username: String, password: String) {
        val request = Request.Builder()
            .url(url.trimEnd('/') + "/")
            .header("Authorization", Credentials.basic(username, password))
            .method("MKCOL", null)
            .build()
        val response = client.newCall(request).execute()
        // 405 = already exists, which is fine
        if (!response.isSuccessful && response.code != 405 && response.code != 301) {
            throw Exception("WebDAV MKCOL failed: ${response.code}")
        }
        response.close()
    }

    fun exists(url: String, username: String, password: String): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(username, password))
            .head()
            .build()
        return try {
            val response = client.newCall(request).execute()
            val exists = response.isSuccessful
            response.close()
            exists
        } catch (_: Exception) { false }
    }
}
