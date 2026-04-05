package dev.egallery.util

object ThumbnailUrlBuilder {
    fun thumbnail(serverUrl: String, assetId: String): String {
        if (serverUrl.isBlank() || assetId.isBlank()) return ""
        return "${serverUrl.trimEnd('/')}/api/assets/$assetId/thumbnail?size=thumbnail"
    }

    fun preview(serverUrl: String, assetId: String): String {
        if (serverUrl.isBlank() || assetId.isBlank()) return ""
        return "${serverUrl.trimEnd('/')}/api/assets/$assetId/thumbnail?size=preview"
    }

    fun original(serverUrl: String, assetId: String): String {
        if (serverUrl.isBlank() || assetId.isBlank()) return ""
        return "${serverUrl.trimEnd('/')}/api/assets/$assetId/original"
    }

    fun cachedThumbFile(cacheDir: java.io.File, assetId: String): java.io.File? {
        val file = java.io.File(cacheDir, "thumbs/$assetId.jpg")
        return if (file.exists()) file else null
    }
}
