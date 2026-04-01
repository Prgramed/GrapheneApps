package com.prgramed.emessages.data.message

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ThumbnailCache {

    fun cache(context: Context, contentUri: Uri, messageId: Long) {
        try {
            val thumbDir = File(context.cacheDir, "thumbnails").apply { mkdirs() }
            val thumbFile = File(thumbDir, "$messageId.jpg")
            if (thumbFile.exists()) return

            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bitmap = context.contentResolver.openInputStream(contentUri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return

            FileOutputStream(thumbFile).use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it)
            }
            bitmap.recycle()
        } catch (_: Exception) {
        }
    }

    fun getThumbnail(context: Context, messageId: Long): File? {
        val f = File(context.cacheDir, "thumbnails/$messageId.jpg")
        return if (f.exists()) f else null
    }
}
