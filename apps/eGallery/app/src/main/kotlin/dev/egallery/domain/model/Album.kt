package dev.egallery.domain.model

data class Album(
    val id: String,
    val name: String,
    val coverPhotoId: String? = null,
    val photoCount: Int = 0,
    val type: AlbumType = AlbumType.MANUAL,
)

enum class AlbumType { MANUAL, PEOPLE }
