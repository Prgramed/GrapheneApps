package dev.egallery.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.egallery.data.db.entity.MediaTagEntity
import dev.egallery.data.db.entity.TagEntity

@Dao
interface MediaTagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: MediaTagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<MediaTagEntity>)

    @Query("DELETE FROM media_tag WHERE nasId = :nasId")
    suspend fun deleteByNasId(nasId: String)

    @Query(
        """
        SELECT t.* FROM tags t
        INNER JOIN media_tag mt ON t.id = mt.tagId
        WHERE mt.nasId = :nasId
        ORDER BY t.name COLLATE NOCASE ASC
        """,
    )
    suspend fun getTagsForItem(nasId: String): List<TagEntity>
}
