package dev.equran.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.equran.data.db.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE surah = :surah ORDER BY ayah ASC")
    fun observeForSurah(surah: Int): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE surah = :surah AND ayah = :ayah")
    suspend fun delete(surah: Int, ayah: Int)

    @Query("SELECT COUNT(*) > 0 FROM bookmarks WHERE surah = :surah AND ayah = :ayah")
    suspend fun exists(surah: Int, ayah: Int): Boolean

    @Query("UPDATE bookmarks SET note = :note WHERE surah = :surah AND ayah = :ayah")
    suspend fun updateNote(surah: Int, ayah: Int, note: String?)
}

@Dao
interface MemorizationDao {
    @Query("SELECT * FROM memorized_verses ORDER BY memorizedAt DESC")
    fun observeAll(): Flow<List<MemorizedVerseEntity>>

    @Query("SELECT * FROM memorized_verses WHERE surah = :surah ORDER BY ayah ASC")
    fun observeForSurah(surah: Int): Flow<List<MemorizedVerseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(verse: MemorizedVerseEntity)

    @Query("DELETE FROM memorized_verses WHERE surah = :surah AND ayah = :ayah")
    suspend fun delete(surah: Int, ayah: Int)

    @Query("""
        SELECT * FROM memorized_verses
        WHERE confidence < 3
        ORDER BY
            CASE WHEN lastReviewed IS NULL THEN 0 ELSE lastReviewed END ASC,
            confidence ASC
        LIMIT :limit
    """)
    suspend fun getReviewQueue(limit: Int): List<MemorizedVerseEntity>

    @Query("UPDATE memorized_verses SET confidence = :confidence, lastReviewed = :now WHERE surah = :surah AND ayah = :ayah")
    suspend fun updateReview(surah: Int, ayah: Int, confidence: Int, now: Long = System.currentTimeMillis())
}

@Dao
interface ReadingPlanDao {
    @Query("SELECT * FROM reading_plans WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<ReadingPlanEntity?>

    @Insert
    suspend fun insert(plan: ReadingPlanEntity): Long

    @Query("UPDATE reading_plans SET isActive = 0 WHERE id = :planId")
    suspend fun archive(planId: Long)

    @Query("SELECT * FROM reading_progress WHERE planId = :planId ORDER BY readDate ASC")
    suspend fun getProgress(planId: Long): List<ReadingProgressEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProgress(progress: List<ReadingProgressEntity>)

    @Query("SELECT COUNT(DISTINCT surah || ':' || ayah) FROM reading_progress WHERE planId = :planId")
    suspend fun getVersesReadCount(planId: Long): Int

    @Query("SELECT COUNT(DISTINCT readDate) FROM reading_progress WHERE planId = :planId")
    suspend fun getDaysWithProgressCount(planId: Long): Int

    @Query("SELECT DISTINCT readDate FROM reading_progress WHERE planId = :planId ORDER BY readDate ASC")
    suspend fun getReadDates(planId: Long): List<String>
}

@Dao
interface WordByWordDao {
    @Query("SELECT * FROM word_by_word_cache WHERE surah = :surah AND ayah = :ayah")
    suspend fun get(surah: Int, ayah: Int): WordByWordCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cache: WordByWordCacheEntity)
}

@Dao
interface TafsirDao {
    @Query("SELECT * FROM tafsir_cache WHERE surah = :surah AND ayah = :ayah AND edition = :edition")
    suspend fun get(surah: Int, ayah: Int, edition: String): TafsirCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cache: TafsirCacheEntity)
}

@Dao
interface TopicDao {
    @Query("SELECT t.*, COUNT(tv.id) as verseCount FROM topics t LEFT JOIN topic_verses tv ON t.id = tv.topicId GROUP BY t.id ORDER BY t.displayOrder ASC")
    suspend fun getAllWithCount(): List<TopicWithCount>

    @Query("SELECT surah, ayah FROM topic_verses WHERE topicId = :topicId ORDER BY surah ASC, ayah ASC")
    suspend fun getVerses(topicId: Long): List<TopicVerseRef>

    @Insert
    suspend fun insertTopic(topic: TopicEntity): Long

    @Insert
    suspend fun insertVerses(verses: List<TopicVerseEntity>)

    @Query("SELECT COUNT(*) FROM topics")
    suspend fun count(): Int
}

data class TopicWithCount(
    val id: Long,
    val nameEn: String,
    val nameAr: String?,
    val description: String?,
    val category: String,
    val displayOrder: Int,
    val verseCount: Int,
)

data class TopicVerseRef(
    val surah: Int,
    val ayah: Int,
)
