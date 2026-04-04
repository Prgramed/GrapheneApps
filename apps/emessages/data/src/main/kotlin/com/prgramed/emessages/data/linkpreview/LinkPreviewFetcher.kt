package com.prgramed.emessages.data.linkpreview

import android.content.Context
import android.util.Log
import com.prgramed.emessages.domain.model.LinkPreview
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LinkPreviewFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val memCache = ConcurrentHashMap<String, LinkPreview>()
    private val failedUrls = ConcurrentHashMap<String, Long>()
    private val failRetryMs = 60L * 1000

    private val diskPrefs by lazy { context.getSharedPreferences("link_previews", Context.MODE_PRIVATE) }

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetch(url: String): LinkPreview? {
        // 1. Memory cache
        memCache[url]?.let { return it }

        // 2. Disk cache
        val diskJson = diskPrefs.getString(url, null)
        if (diskJson != null) {
            val preview = deserialize(diskJson)
            if (preview != null) {
                memCache[url] = preview
                return preview
            }
        }

        // 3. Failed URL cooldown
        val failedAt = failedUrls[url]
        if (failedAt != null && System.currentTimeMillis() - failedAt < failRetryMs) return null

        return withContext(Dispatchers.IO) {
            try {
                val isMeta = url.contains("instagram.com") || url.contains("facebook.com") || url.contains("threads.net")
                val ua = if (isMeta) "facebookexternalhit/1.1"
                    else "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", ua)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.let { rb ->
                    val sb = StringBuilder(50_000)
                    val buf = CharArray(8192)
                    val reader = rb.charStream().buffered()
                    var total = 0
                    while (total < 100_000) {
                        val read = reader.read(buf)
                        if (read == -1) break
                        sb.append(buf, 0, read)
                        total += read
                        if (total > 10_000 && (sb.contains("og:image") || sb.contains("</head>"))) break
                    }
                    rb.close()
                    if (sb.isNotEmpty()) sb.toString() else null
                }
                response.close()

                if (body == null) {
                    failedUrls[url] = System.currentTimeMillis()
                    return@withContext null
                }

                val title = extractMeta(body, "og:title")
                    ?: extractMeta(body, "twitter:title")
                    ?: extractHtmlTitle(body)
                val description = extractMeta(body, "og:description")
                    ?: extractMeta(body, "twitter:description")
                val rawImageUrl = extractMeta(body, "og:image")
                    ?: extractMeta(body, "twitter:image")

                val imageUrl = rawImageUrl?.let { img ->
                    when {
                        img.startsWith("http://") || img.startsWith("https://") -> img
                        img.startsWith("//") -> "https:$img"
                        img.startsWith("/") -> {
                            try {
                                val uri = URI(url)
                                "${uri.scheme}://${uri.host}$img"
                            } catch (_: Exception) { null }
                        }
                        else -> null
                    }
                }

                val domain = try {
                    URI(url).host?.removePrefix("www.") ?: url
                } catch (_: Exception) { url }

                if (title == null && description == null && imageUrl == null) {
                    failedUrls[url] = System.currentTimeMillis()
                    return@withContext null
                }

                val preview = LinkPreview(url = url, title = title, description = description, imageUrl = imageUrl, domain = domain)
                memCache[url] = preview
                // Persist to disk
                diskPrefs.edit().putString(url, serialize(preview)).apply()
                preview
            } catch (_: Exception) {
                failedUrls[url] = System.currentTimeMillis()
                null
            }
        }
    }

    private fun serialize(p: LinkPreview): String = JSONObject().apply {
        put("url", p.url)
        put("title", p.title ?: "")
        put("description", p.description ?: "")
        put("imageUrl", p.imageUrl ?: "")
        put("domain", p.domain)
    }.toString()

    private fun deserialize(json: String): LinkPreview? = try {
        val j = JSONObject(json)
        LinkPreview(
            url = j.getString("url"),
            title = j.optString("title").ifBlank { null },
            description = j.optString("description").ifBlank { null },
            imageUrl = j.optString("imageUrl").ifBlank { null },
            domain = j.optString("domain", ""),
        )
    } catch (_: Exception) { null }

    private fun extractMeta(html: String, property: String): String? {
        val patterns = listOf(
            Regex("""<meta\s+(?:[^>]*?)property\s*=\s*"$property"\s+(?:[^>]*?)content\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE),
            Regex("""<meta\s+(?:[^>]*?)content\s*=\s*"([^"]*)"\s+(?:[^>]*?)property\s*=\s*"$property"""", RegexOption.IGNORE_CASE),
            Regex("""<meta\s+(?:[^>]*?)name\s*=\s*"$property"\s+(?:[^>]*?)content\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE),
            Regex("""<meta\s+(?:[^>]*?)content\s*=\s*"([^"]*)"\s+(?:[^>]*?)name\s*=\s*"$property"""", RegexOption.IGNORE_CASE),
        )
        for (pattern in patterns) {
            val match = pattern.find(html)
            if (match != null) {
                val value = match.groupValues[1].trim()
                if (value.isNotEmpty()) return decodeHtmlEntities(value)
            }
        }
        return null
    }

    private fun extractHtmlTitle(html: String): String? {
        val match = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE).find(html)
        return match?.groupValues?.get(1)?.trim()?.let { decodeHtmlEntities(it) }
    }

    private fun decodeHtmlEntities(text: String): String {
        var result = text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
        result = Regex("&#(\\d+);").replace(result) {
            val code = it.groupValues[1].toIntOrNull()
            if (code != null) String(Character.toChars(code)) else it.value
        }
        result = Regex("&#x([0-9a-fA-F]+);").replace(result) {
            val code = it.groupValues[1].toIntOrNull(16)
            if (code != null) String(Character.toChars(code)) else it.value
        }
        return result
    }
}
