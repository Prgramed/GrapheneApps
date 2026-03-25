package dev.eweather.domain.model

data class SavedLocation(
    val id: Long = 0,
    val name: String,
    val region: String = "",
    val country: String = "",
    val lat: Double,
    val lon: Double,
    val isGps: Boolean = false,
    val sortOrder: Int = 0,
)
