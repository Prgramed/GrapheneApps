package dev.emusic.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.emusic.data.db.entity.LyricsEntity

@Dao
interface LyricsDao {

    @Upsert
    suspend fun upsert(entity: LyricsEntity)

    @Query("SELECT * FROM lyrics WHERE trackId = :trackId")
    suspend fun getByTrackId(trackId: String): LyricsEntity?
}
