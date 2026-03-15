package com.prgramed.eprayer.domain.model

data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val cityName: String? = null,
)
