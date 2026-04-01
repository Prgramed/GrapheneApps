package dev.egallery.util

import android.util.Base64
import java.io.File
import java.security.MessageDigest

object HashUtil {

    /** SHA-1 hash encoded as Base64 (no-wrap) — matches Immich's checksum format. */
    fun sha1Base64(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
