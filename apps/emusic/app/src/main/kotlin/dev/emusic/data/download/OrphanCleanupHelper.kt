package dev.emusic.data.download

import dev.emusic.data.db.dao.AlbumDao
import dev.emusic.data.db.dao.PlaylistDao
import dev.emusic.data.db.dao.TrackDao
import dev.emusic.data.db.entity.TrackEntity
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cleans up locally downloaded track files when a track is no longer referenced
 * by any pinned playlist or pinned album.
 *
 * For each track:
 *  - If still in any pinned playlist → keep.
 *  - Else if its album is pinned → keep.
 *  - Else → cancel any in-flight download work, delete the local file, clear localPath.
 */
@Singleton
class OrphanCleanupHelper @Inject constructor(
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val albumDao: AlbumDao,
    private val downloadManager: DownloadManager,
) {
    suspend fun cleanupIfOrphaned(trackIds: Collection<String>) {
        if (trackIds.isEmpty()) return
        for (trackId in trackIds) {
            val entity = trackDao.getById(trackId) ?: continue
            if (isStillNeeded(entity)) continue

            // Cancel in-flight download so it can't re-populate localPath after we clean up.
            downloadManager.cancel(trackId)

            val path = entity.localPath ?: continue
            try {
                File(path).delete()
            } catch (e: Exception) {
                Timber.w(e, "Failed to delete orphaned download: $path")
            }
            trackDao.updateLocalPath(trackId, null)
        }
    }

    private suspend fun isStillNeeded(entity: TrackEntity): Boolean {
        if (playlistDao.isTrackInAnyPinnedPlaylist(entity.id)) return true
        val album = albumDao.getById(entity.albumId) ?: return false
        return album.pinned
    }
}
