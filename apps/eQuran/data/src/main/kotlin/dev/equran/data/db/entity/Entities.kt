package dev.equran.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks", indices = [Index("surah", "ayah", unique = true)])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surah: Int,
    val ayah: Int,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "memorized_verses", indices = [Index("surah", "ayah", unique = true)])
data class MemorizedVerseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surah: Int,
    val ayah: Int,
    val confidence: Int = 1,
    val memorizedAt: Long = System.currentTimeMillis(),
    val lastReviewed: Long? = null,
)

@Entity(tableName = "reading_plans")
data class ReadingPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "Khatma",
    val totalDays: Int,
    val startDate: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [ForeignKey(entity = ReadingPlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("planId"), Index("planId", "surah", "ayah", unique = true)],
)
data class ReadingProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val surah: Int,
    val ayah: Int,
    val readDate: String,
)

@Entity(tableName = "word_by_word_cache", indices = [Index("surah", "ayah", unique = true)])
data class WordByWordCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surah: Int,
    val ayah: Int,
    val wordsJson: String,
    val fetchedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "tafsir_cache", indices = [Index("surah", "ayah", "edition", unique = true)])
data class TafsirCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surah: Int,
    val ayah: Int,
    val edition: String,
    val text: String,
    val fetchedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "topics")
data class TopicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nameEn: String,
    val nameAr: String? = null,
    val description: String? = null,
    val category: String,
    val displayOrder: Int = 0,
)

@Entity(
    tableName = "topic_verses",
    foreignKeys = [ForeignKey(entity = TopicEntity::class, parentColumns = ["id"], childColumns = ["topicId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("topicId")],
)
data class TopicVerseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topicId: Long,
    val surah: Int,
    val ayah: Int,
    val relevanceScore: Float = 1.0f,
)
