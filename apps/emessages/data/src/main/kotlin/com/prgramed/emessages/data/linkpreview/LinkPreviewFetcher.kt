package com.prgramed.emessages.data.linkpreview

import android.util.Log
import com.prgramed.emessages.domain.model.LinkPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LinkPreviewFetcher @Inject constructor() {

    private val cache = ConcurrentHashMap<String, LinkPreview?>()
    private val maxCacheSize = 200

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetch(url: String): LinkPreview? {
        cache[url]?.let { return it }
        if (cache.containsKey(url)) return null // Already tried, failed
        // Evict if cache too large
        if (cache.size > maxCacheSize) {
            cache.keys.take(cache.size - maxCacheSize + 20).forEach { cache.remove(it) }
        }

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (compatible; LinkPreview/1.0)")
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string()?.take(200_000) // Limit to 200KB
                response.close()

                if (body == null) {
                    cache[url] = null
                    return@withContext null
                }

                val title = extractMeta(body, "og:title")
                    ?: extractMeta(body, "twitter:title")
                    ?: extractHtmlTitle(body)
                val description = extractMeta(body, "og:description")
                    ?: extractMeta(body, "twitter:description")
                val rawImageUrl = extractMeta(body, "og:image")
                    ?: extractMeta(body, "twitter:image")

                // Resolve relative image URLs to absolute
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

                Log.d("LinkPreview", "url=$url title=$title image=$imageUrl")

                if (title == null && description == null && imageUrl == null) {
                    cache[url] = null
                    return@withContext null
                }

                val preview = LinkPreview(
                    url = url,
                    title = title,
                    description = description,
                    imageUrl = imageUrl,
                    domain = domain,
                )
                cache[url] = preview
                preview
            } catch (_: Exception) {
                cache[url] = null
                null
            }
        }
    }

    private fun extractMeta(html: String, property: String): String? {
        // Match <meta property="og:title" content="..."> or <meta name="twitter:title" content="...">
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
        // Decode numeric entities: &#039; &#x1f34a;
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
