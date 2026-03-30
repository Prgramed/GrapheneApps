package dev.egallery.util

import android.media.ExifInterface
import dev.egallery.domain.model.MediaType
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

object MediaFileUtil {

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "heic", "heif", "webp")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "mkv", "webm")
    private val ALL_EXTENSIONS = IMAGE_EXTENSIONS + VIDEO_EXTENSIONS

    fun isMediaFile(filename: String): Boolean {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return ext in ALL_EXTENSIONS
    }

    fun mediaTypeFromFile(filename: String): MediaType {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return if (ext in VIDEO_EXTENSIONS) MediaType.VIDEO else MediaType.PHOTO
    }

    fun extractCaptureDate(file: File): Long {
        return try {
            val exif = ExifInterface(file)
            val dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            if (dateStr != null) {
                val sdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                sdf.parse(dateStr)?.time ?: file.lastModified()
            } else {
                file.lastModified()
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to read EXIF from ${file.name}")
            file.lastModified()
        }
    }
}
