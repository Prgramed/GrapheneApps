package dev.egallery.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun localFilePath(nasId: String, filename: String): File {
        val dir = File(context.filesDir, "media")
        dir.mkdirs()
        // Prefix with nasId to avoid filename collisions
        return File(dir, "${nasId}_$filename")
    }

    fun deleteLocalFile(localPath: String): Boolean {
        val file = File(localPath)
        if (!file.exists()) return true

        val deleted = file.delete()
        if (deleted) {
            // Clean up empty parent directory
            val parent = file.parentFile
            if (parent != null && parent.isDirectory && parent.listFiles()?.isEmpty() == true) {
                parent.delete()
            }
            Timber.d("Deleted local file: $localPath")
        } else {
            Timber.w("Failed to delete local file: $localPath")
        }
        return deleted
    }
}
