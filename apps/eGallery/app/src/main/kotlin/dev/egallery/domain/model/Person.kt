package dev.egallery.domain.model

data class Person(
    val id: String,
    val name: String,
    val coverPhotoId: String? = null,
    val photoCount: Int = 0,
)
