package dev.emusic.data.download

import android.content.Context
import java.io.File

object StoragePaths {

    private fun downloadsDir(context: Context): File =
        File(context.filesDir, "downloads")

    private fun artworkDir(context: Context): File =
        File(context.filesDir, "artwork")

    fun trackDir(context: Context, artistId: String, albumId: String): File =
        File(downloadsDir(context), "$artistId/$albumId")

    fun trackFile(context: Context, artistId: String, albumId: String, trackId: String, ext: String): File =
        File(trackDir(context, artistId, albumId), "$trackId.$ext")

    fun artworkFile(context: Context, albumId: String): File =
        File(artworkDir(context), "$albumId.jpg")
}
