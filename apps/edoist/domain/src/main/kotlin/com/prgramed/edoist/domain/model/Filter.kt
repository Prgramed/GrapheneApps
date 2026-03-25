package com.prgramed.edoist.domain.model

data class Filter(
    val id: String,
    val name: String,
    val iconName: String? = null,
    val color: Long,
    val query: FilterQuery,
    val sortOrder: Int = 0,
)
