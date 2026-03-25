package com.grapheneapps.enotes.data.sync

data class WebDavEntry(
    val href: String,
    val name: String,
    val etag: String? = null,
    val lastModified: String? = null,
    val isCollection: Boolean = false,
    val contentLength: Long = 0,
)
