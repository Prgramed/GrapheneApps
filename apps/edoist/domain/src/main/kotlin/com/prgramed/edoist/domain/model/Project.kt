package com.prgramed.edoist.domain.model

data class Project(
    val id: String,
    val name: String,
    val color: Long,
    val iconName: String? = null,
    val isInbox: Boolean = false,
    val isArchived: Boolean = false,
    val defaultView: ViewType = ViewType.LIST,
    val sortOrder: Int = 0,
    val sections: List<Section> = emptyList(),
    val activeTaskCount: Int = 0,
)
