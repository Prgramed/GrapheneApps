package dev.emusic.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [
        Index("albumId"),
        Index("artistId"),
        Index("starred"),
        Index("genre"),
        Index("playCount"),
        Index("localPath"),
    ],
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val artistId: String,
    val album: String,
    val albumId: String,
    val coverArtId: String? = null,
    val duration: Int = 0,
    val trackNumber: Int = 0,
    val discNumber: Int = 1,
    val year: Int? = null,
    val genre: String? = null,
    val size: Long = 0,
    val contentType: String? = null,
    val suffix: String? = null,
    val bitRate: Int? = null,
    val starred: Boolean = false,
    val playCount: Int = 0,
    val userRating: Int? = null,
    val localPath: String? = null,
    val trackGain: Float? = null,
    val albumGain: Float? = null,
    val trackPeak: Float? = null,
    val albumPeak: Float? = null,
)
