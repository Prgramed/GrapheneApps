package dev.egallery.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.egallery.data.db.entity.MediaEntity
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT * FROM media WHERE localPath = :localPath LIMIT 1")
    suspend fun getByLocalPath(localPath: String): MediaEntity?

    @Query("SELECT COUNT(*) FROM media WHERE folderId = :folderId")
    suspend fun getCountByFolder(folderId: Int): Int

    @Query("SELECT * FROM media WHERE folderId = :folderId ORDER BY captureDate DESC LIMIT 1")
    suspend fun getFirstByFolder(folderId: Int): MediaEntity?

    @Query("SELECT nasId FROM media")
    suspend fun getAllNasIds(): List<String>

    @Query("SELECT nasId FROM media WHERE storageStatus != 'TRASHED' ORDER BY captureDate DESC")
    suspend fun getAllNasIdsOrdered(): List<String>

    @Query("SELECT COUNT(*) FROM media WHERE nasId IS NOT NULL AND nasId != ''")
    suspend fun getNasItemCount(): Int

    @Query("SELECT MAX(captureDate) FROM media WHERE nasId IS NOT NULL AND nasId != ''")
    suspend fun getLatestNasCaptureDate(): Long?

    @Query("DELETE FROM media WHERE nasId = :nasId")
    suspend fun deleteByNasId(nasId: String)

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

    @Query("SELECT * FROM media WHERE storageStatus = :status")
    suspend fun getByStorageStatus(status: String): List<MediaEntity>

    @Transaction
    suspend fun replaceEntity(oldNasId: String, newEntity: MediaEntity) {
        deleteByNasId(oldNasId)
        insert(newEntity)
    }
}
