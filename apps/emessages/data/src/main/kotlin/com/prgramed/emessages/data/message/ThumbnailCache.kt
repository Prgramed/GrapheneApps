package com.prgramed.emessages.data.message

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ThumbnailCache {

    /** Cache an image attachment's downsampled thumbnail. */
    fun cache(context: Context, contentUri: Uri, messageId: Long) {
        try {
            val thumbFile = thumbFile(context, messageId)
            if (thumbFile.exists()) return
            thumbFile.parentFile?.mkdirs()

            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bitmap = context.contentResolver.openInputStream(contentUri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return

            FileOutputStream(thumbFile).use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it)
            }
            bitmap.recycle()
        } catch (_: Exception) {
        }
    }

    /** Cache a video attachment's first-frame thumbnail via MediaMetadataRetriever. */
    fun cacheVideo(context: Context, contentUri: Uri, messageId: Long) {
        val thumbFile = thumbFile(context, messageId)
        if (thumbFile.exists()) return
        thumbFile.parentFile?.mkdirs()

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, contentUri)
            // Grab frame at 0us; if that fails, fall back to getFrameAtTime() (any key frame)
            val frame: Bitmap = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime()
                ?: return
            FileOutputStream(thumbFile).use {
                frame.compress(Bitmap.CompressFormat.JPEG, 80, it)
            }
            frame.recycle()
        } catch (_: Exception) {
            // Best effort — leave the ChatBubble fallback to show the play icon
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    fun getThumbnail(context: Context, messageId: Long): File? {
        val f = thumbFile(context, messageId)
        return if (f.exists()) f else null
    }

    private fun thumbFile(context: Context, messageId: Long): File =
        File(context.cacheDir, "thumbnails/$messageId.jpg")
}
