package com.prgramed.edoist.domain.model

data class Label(
    val id: String,
    val name: String,
    val color: Long,
    val sortOrder: Int = 0,
)
