package dev.emusic.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.emusic.data.db.entity.PlaylistEntity
import dev.emusic.data.db.entity.PlaylistTrackCrossRef
import dev.emusic.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Upsert
    suspend fun upsert(playlist: PlaylistEntity)

    @Upsert
    suspend fun upsertAll(playlists: List<PlaylistEntity>)

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT id FROM playlists")
    suspend fun getAllIds(): List<String>

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    suspend fun getAllSorted(): List<PlaylistEntity>

    @Query("SELECT playlistId FROM playlist_tracks WHERE trackId = :trackId")
    suspend fun getPlaylistIdsContainingTrack(trackId: String): List<String>

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM playlists WHERE id NOT IN (:keepIds)")
    suspend fun deleteNotIn(keepIds: List<String>)

    @Query("DELETE FROM playlists")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackRefs(refs: List<PlaylistTrackCrossRef>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deleteTrackRefs(playlistId: String)

    @Transaction
    suspend fun replacePlaylistTracks(playlistId: String, refs: List<PlaylistTrackCrossRef>) {
        deleteTrackRefs(playlistId)
        insertTrackRefs(refs)
    }

    @Query(
        """
        SELECT tracks.* FROM tracks
        INNER JOIN playlist_tracks ON tracks.id = playlist_tracks.trackId
        WHERE playlist_tracks.playlistId = :playlistId
        ORDER BY playlist_tracks.sortOrder
        """,
    )
    fun observePlaylistTracks(playlistId: String): Flow<List<TrackEntity>>

    @Query("UPDATE playlists SET trackCount = :count WHERE id = :id")
    suspend fun updateTrackCount(id: String, count: Int)

    @Query("UPDATE playlists SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("SELECT * FROM playlists WHERE pinned = 1 ORDER BY name COLLATE NOCASE")
    fun observePinned(): Flow<List<PlaylistEntity>>

    @Query("""
        SELECT tracks.* FROM tracks
        INNER JOIN playlist_tracks ON tracks.id = playlist_tracks.trackId
        WHERE playlist_tracks.playlistId = :playlistId AND tracks.localPath IS NULL
    """)
    suspend fun getUndownloadedPlaylistTracks(playlistId: String): List<TrackEntity>
}
