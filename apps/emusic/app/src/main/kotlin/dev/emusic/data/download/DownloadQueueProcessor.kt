package dev.emusic.data.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.emusic.data.db.dao.AlbumDao
import dev.emusic.data.db.dao.PlaylistDao
import dev.emusic.data.db.dao.TrackDao
import dev.emusic.data.db.entity.toDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadQueueProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistDao: PlaylistDao,
    private val albumDao: AlbumDao,
    private val trackDao: TrackDao,
    private val downloadManager: DownloadManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        // Reconcile on startup — fix any tracks with files on disk but no localPath in DB
        scope.launch { reconcileDownloads() }

        // React to pin changes immediately
        scope.launch {
            combine(playlistDao.observePinned(), albumDao.observePinned()) { playlists, albums ->
                playlists.map { it.id } to albums.map { it.id }
            }.collect { (playlistIds, albumIds) ->
                processQueue(playlistIds, albumIds)
            }
        }

        // Periodic re-check every 30s
        scope.launch {
            while (true) {
                delay(30_000)
                try {
                    val pinnedPlaylists = playlistDao.observePinned().first().map { it.id }
                    val pinnedAlbums = albumDao.observePinned().first().map { it.id }
                    if (pinnedPlaylists.isNotEmpty() || pinnedAlbums.isNotEmpty()) {
                        downloadManager.pruneWork()
                        processQueue(pinnedPlaylists, pinnedAlbums)
                    }
                } catch (_: Exception) { }
            }
        }
    }

    private suspend fun reconcileDownloads() {
        val downloadsDir = File(context.filesDir, "downloads")
        if (!downloadsDir.exists()) return

        // Forward: files on disk with no DB localPath
        var reconciled = 0
        downloadsDir.walkBottomUp().filter { it.isFile }.forEach { file ->
            val trackId = file.nameWithoutExtension
            val entity = trackDao.getById(trackId) ?: return@forEach
            if (entity.localPath == null && file.length() > 8192) {
                trackDao.updateLocalPath(trackId, file.absolutePath)
                reconciled++
            }
        }

        // Reverse: DB says downloaded but file missing or corrupted
        var cleared = 0
        val allDownloaded = trackDao.getAllDownloaded()
        for (entity in allDownloaded) {
            val path = entity.localPath ?: continue
            val file = File(path)
            if (!file.exists() || file.length() < 8192) {
                trackDao.updateLocalPath(entity.id, null)
                cleared++
            }
        }

        if (reconciled > 0 || cleared > 0) {
            Timber.d("Reconciled downloads: $reconciled restored, $cleared cleared (missing/corrupted)")
        }
    }

    private suspend fun processQueue(playlistIds: List<String>, albumIds: List<String>) {
        for (playlistId in playlistIds) {
            val undownloaded = playlistDao.getUndownloadedPlaylistTracks(playlistId)
            if (undownloaded.isEmpty()) continue
            Timber.d("Pinned playlist $playlistId: ${undownloaded.size} tracks to download")
            for (track in undownloaded) {
                downloadManager.enqueue(track.toDomain())
            }
        }

        for (albumId in albumIds) {
            val undownloaded = trackDao.getUndownloadedByAlbum(albumId)
            if (undownloaded.isEmpty()) continue
            Timber.d("Pinned album $albumId: ${undownloaded.size} tracks to download")
            for (track in undownloaded) {
                downloadManager.enqueue(track.toDomain())
            }
        }
    }
}
