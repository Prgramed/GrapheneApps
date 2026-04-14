package dev.egallery.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.egallery.data.db.entity.MediaEntity
import kotlinx.coroutines.flow.Flow

data class LocalHashCacheEntry(
    val localPath: String,
    val nasHash: String,
    val localFileModifiedAt: Long?,
)

data class ViewerEntry(
    val nasId: String,
    val localPath: String?,
    val mediaType: String,
)

data class ServerFilenameRef(
    val filename: String,
    val nasId: String,
)

@Dao
interface MediaDao {

    @Query(
        """
        SELECT * FROM media
        WHERE (:fromDate IS NULL OR captureDate >= :fromDate)
          AND (:toDate IS NULL OR captureDate <= :toDate)
          AND storageStatus != 'TRASHED'
        ORDER BY captureDate DESC
        """,
    )
    fun timelinePagingSource(fromDate: Long? = null, toDate: Long? = null): PagingSource<Int, MediaEntity>

    @Query("SELECT * FROM media WHERE folderId = :folderId AND storageStatus != 'TRASHED' ORDER BY captureDate DESC")
    fun getByFolder(folderId: Int): Flow<List<MediaEntity>>

    @Query(
        """
        SELECT m.* FROM media m
        INNER JOIN album_media am ON m.nasId = am.nasId
        WHERE am.albumId = :albumId
          AND m.storageStatus != 'TRASHED'
        ORDER BY m.captureDate DESC
        """,
    )
    fun getByAlbum(albumId: String): Flow<List<MediaEntity>>

    @Query(
        """
        SELECT m.* FROM media m
        INNER JOIN media_tag mt ON m.nasId = mt.nasId
        WHERE mt.tagId = :tagId
          AND m.storageStatus != 'TRASHED'
        ORDER BY m.captureDate DESC
        """,
    )
    fun getByTag(tagId: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE lat IS NOT NULL AND lng IS NOT NULL AND storageStatus != 'TRASHED'")
    suspend fun getWithLocation(): List<MediaEntity>

    @Query(
        """
        SELECT * FROM media
        WHERE localPath IS NOT NULL
          AND (
            (localExpiryType = 'ROLLING' AND lastSyncedAt < :rollingCutoff)
            OR (localExpiryType = 'FIXED' AND localExpiryAt IS NOT NULL AND localExpiryAt < :now)
          )
        """,
    )
    suspend fun getExpiredLocalFiles(rollingCutoff: Long, now: Long): List<MediaEntity>

    @Query("SELECT * FROM media WHERE filename LIKE '%' || :query || '%' AND storageStatus != 'TRASHED' ORDER BY captureDate DESC")
    suspend fun searchByFilename(query: String): List<MediaEntity>

    @Query(
        """
        SELECT DISTINCT m.* FROM media m
        LEFT JOIN media_tag mt ON m.nasId = mt.nasId
        WHERE filename LIKE '%' || :query || '%'
          AND (:mediaType IS NULL OR m.mediaType = :mediaType)
          AND (:fromDate IS NULL OR m.captureDate >= :fromDate)
          AND (:toDate IS NULL OR m.captureDate <= :toDate)
          AND (:tagId IS NULL OR mt.tagId = :tagId)
          AND m.storageStatus != 'TRASHED'
        ORDER BY m.captureDate DESC
        """,
    )
    suspend fun searchFiltered(
        query: String,
        mediaType: String? = null,
        fromDate: Long? = null,
        toDate: Long? = null,
        tagId: String? = null,
    ): List<MediaEntity>

    @Upsert
    suspend fun upsert(item: MediaEntity)

    @Upsert
    suspend fun upsertAll(items: List<MediaEntity>)

    @Query("UPDATE media SET storageStatus = :status, localPath = :localPath WHERE nasId = :nasId")
    suspend fun updateStorageStatus(nasId: String, status: String, localPath: String? = null)

    @Query("SELECT * FROM media WHERE nasId = :nasId")
    suspend fun getByNasId(nasId: String): MediaEntity?

    @Query("SELECT * FROM media WHERE nasHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): MediaEntity?

    @Query("SELECT * FROM media WHERE storageStatus = :status")
    suspend fun getByStatus(status: String): List<MediaEntity>

    @Query("SELECT nasHash FROM media WHERE nasHash IS NOT NULL AND nasHash != '' GROUP BY nasHash HAVING COUNT(*) > 1")
    suspend fun findHashDuplicates(): List<String>

    @Query("SELECT * FROM media WHERE nasHash = :hash")
    suspend fun getAllByHash(hash: String): List<MediaEntity>

    @Query("SELECT filename FROM media WHERE storageStatus != 'TRASHED' GROUP BY filename HAVING COUNT(*) > 1")
    suspend fun findFilenameDuplicates(): List<String>

    @Query("SELECT * FROM media WHERE filename = :filename AND storageStatus != 'TRASHED'")
    suspend fun getAllByFilename(filename: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE localPath = :localPath LIMIT 1")
    suspend fun getByLocalPath(localPath: String): MediaEntity?

    @Query("SELECT COUNT(*) FROM media WHERE folderId = :folderId")
    suspend fun getCountByFolder(folderId: Int): Int

    @Query("SELECT * FROM media WHERE folderId = :folderId ORDER BY captureDate DESC LIMIT 1")
    suspend fun getFirstByFolder(folderId: Int): MediaEntity?

    @Query("SELECT nasId FROM media")
    suspend fun getAllNasIds(): List<String>

    @Query("SELECT nasId FROM media WHERE storageStatus != 'TRASHED' ORDER BY captureDate DESC LIMIT :limit OFFSET :offset")
    suspend fun getNasIdsOrderedChunk(limit: Int, offset: Int): List<String>

    @Query("SELECT nasId, localPath, mediaType FROM media WHERE storageStatus != 'TRASHED' ORDER BY captureDate DESC")
    suspend fun getViewerEntries(): List<ViewerEntry>

    @Query("SELECT COUNT(*) FROM media WHERE nasId IS NOT NULL AND nasId != ''")
    suspend fun getNasItemCount(): Int

    @Query("SELECT MAX(captureDate) FROM media WHERE nasId IS NOT NULL AND nasId != ''")
    suspend fun getLatestNasCaptureDate(): Long?

    @Query("DELETE FROM media WHERE nasId = :nasId")
    suspend fun deleteByNasId(nasId: String)

    @Query("DELETE FROM media WHERE nasId IN (:nasIds) AND storageStatus = 'NAS'")
    suspend fun deleteNasOnlyByIds(nasIds: List<String>): Int

    /**
     * Mark entries as TRASHED (local trash) when they've been trashed/deleted server-side.
     * Only touches rows that aren't already TRASHED and have a real server nasId; preserves
     * the user's ability to restore within the 30-day retention window.
     */
    @Query("UPDATE media SET storageStatus = 'TRASHED', trashedAt = :trashedAt WHERE nasId IN (:nasIds) AND storageStatus != 'TRASHED'")
    suspend fun trashByIds(nasIds: List<String>, trashedAt: Long): Int

    /** Restore TRASHED entries whose trashedAt is within a recent window. SYNCED if localPath exists, NAS otherwise. */
    @Query("UPDATE media SET storageStatus = CASE WHEN localPath IS NOT NULL THEN 'SYNCED' ELSE 'NAS' END, trashedAt = NULL WHERE storageStatus = 'TRASHED' AND trashedAt IS NOT NULL AND trashedAt >= :since")
    suspend fun restoreRecentlyTrashed(since: Long): Int

    @Query("DELETE FROM media WHERE nasId IN (:nasIds)")
    suspend fun deleteByNasIds(nasIds: List<String>): Int

    @Query("UPDATE media SET thumbnailPath = NULL WHERE thumbnailPath = 'cached' OR thumbnailPath = 'none'")
    suspend fun resetStaleThumbnailPaths(): Int

    @Query("DELETE FROM media WHERE (filename LIKE '%.MOV' OR filename LIKE '%.mov' OR filename LIKE '%.mp4') AND mediaType = 'PHOTO'")
    suspend fun deleteLivePhotoMovDuplicates(): Int

    @Query("DELETE FROM media")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM media WHERE storageStatus != 'TRASHED'")
    suspend fun getCount(): Int

    @Query("SELECT COALESCE(SUM(fileSize), 0) FROM media WHERE localPath IS NOT NULL")
    suspend fun getLocalStorageBytes(): Long

    @Query("SELECT DISTINCT localPath FROM media WHERE localPath IS NOT NULL")
    suspend fun getAllLocalPaths(): List<String>

    /** Server-side (NAS) entries keyed by filename — for fast in-memory device-scan dedup. */
    @Query("SELECT filename, nasId FROM media WHERE length(nasId) > 10 AND nasId NOT LIKE '-%' AND storageStatus != 'TRASHED'")
    suspend fun getServerFilenames(): List<ServerFilenameRef>

    @Query("SELECT * FROM media WHERE localPath LIKE :dirPrefix || '%' AND storageStatus != 'TRASHED' ORDER BY captureDate DESC")
    fun getByLocalDir(dirPrefix: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE thumbnailPath IS NULL ORDER BY captureDate DESC LIMIT :limit")
    suspend fun getUnprefetched(limit: Int): List<MediaEntity>

    @Query("UPDATE media SET thumbnailPath = :path WHERE nasId = :nasId")
    suspend fun updateThumbnailPath(nasId: String, path: String)

    @Insert
    suspend fun insert(item: MediaEntity)

    @Query("SELECT * FROM media WHERE storageStatus = 'TRASHED' ORDER BY trashedAt DESC")
    fun getTrash(): Flow<List<MediaEntity>>

    @Query("UPDATE media SET storageStatus = 'TRASHED', trashedAt = :trashedAt WHERE nasId = :nasId")
    suspend fun trash(nasId: String, trashedAt: Long)

    @Query("UPDATE media SET storageStatus = :status, trashedAt = null WHERE nasId = :nasId")
    suspend fun restore(nasId: String, status: String)

    @Query("SELECT * FROM media WHERE storageStatus = 'TRASHED' AND trashedAt IS NOT NULL AND trashedAt < :cutoff")
    suspend fun getExpiredTrash(cutoff: Long): List<MediaEntity>

    @Query("DELETE FROM media WHERE nasId = :nasId")
    suspend fun permanentDelete(nasId: String)

    @Query("DELETE FROM media WHERE storageStatus = 'TRASHED'")
    suspend fun emptyTrash()

    @Query("UPDATE media SET storageStatus = 'NAS_ONLY', trashedAt = null WHERE storageStatus = 'TRASHED'")
    suspend fun restoreAllTrash(): Int

    @Query("UPDATE media SET isFavorite = :favorite WHERE nasId = :nasId")
    suspend fun setFavorite(nasId: String, favorite: Boolean)

    @Query("SELECT * FROM media WHERE isFavorite = 1 AND storageStatus != 'TRASHED' ORDER BY captureDate DESC")
    fun getFavorites(): Flow<List<MediaEntity>>

    @Query("SELECT COUNT(*) FROM media WHERE storageStatus = 'TRASHED'")
    suspend fun getTrashCount(): Int

    @Query("UPDATE media SET nasId = :newNasId, storageStatus = :status WHERE nasId = :oldNasId")
    suspend fun updateNasIdAndStatus(oldNasId: String, newNasId: String, status: String)

    @Query("UPDATE media SET nasHash = :hash WHERE nasId = :nasId")
    suspend fun updateHash(nasId: String, hash: String)

    @Query("UPDATE media SET filename = :filename, nasHash = :hash WHERE nasId = :nasId")
    suspend fun updateFilenameAndHash(nasId: String, filename: String, hash: String?)

    /** NAS-only entries missing a checksum — candidates for bucket ghost reconciliation. */
    @Query("SELECT * FROM media WHERE storageStatus = 'NAS' AND (nasHash IS NULL OR nasHash = '')")
    suspend fun getNasWithoutHash(): List<MediaEntity>

    /** NAS-only entries whose capture date is within the rolling cache window — candidates for auto-download. */
    @Query("SELECT * FROM media WHERE storageStatus = 'NAS' AND captureDate >= :captureCutoff ORDER BY captureDate DESC")
    suspend fun getNasOnlyWithin(captureCutoff: Long): List<MediaEntity>

    /** SYNCED entries whose capture date is outside the rolling cache window — candidates for eviction. */
    @Query("SELECT * FROM media WHERE storageStatus = 'SYNCED' AND localPath IS NOT NULL AND captureDate < :captureCutoff")
    suspend fun getSyncedOlderThan(captureCutoff: Long): List<MediaEntity>

    @Query("UPDATE media SET lat = :lat, lng = :lng WHERE nasId = :nasId")
    suspend fun updateLatLng(nasId: String, lat: Double, lng: Double)

    @Query("SELECT localPath, nasHash, localFileModifiedAt FROM media WHERE localPath IS NOT NULL AND nasHash IS NOT NULL")
    suspend fun getLocalHashCache(): List<LocalHashCacheEntry>

    @Query("UPDATE media SET nasHash = :hash, localFileModifiedAt = :modifiedAt WHERE nasId = :nasId")
    suspend fun updateHashAndModifiedAt(nasId: String, hash: String, modifiedAt: Long)

    @Query("SELECT * FROM media WHERE storageStatus = :status")
    suspend fun getByStorageStatus(status: String): List<MediaEntity>

    @Transaction
    suspend fun replaceEntity(oldNasId: String, newEntity: MediaEntity) {
        deleteByNasId(oldNasId)
        insert(newEntity)
    }
}
