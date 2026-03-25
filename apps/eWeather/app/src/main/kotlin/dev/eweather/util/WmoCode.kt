package dev.eweather.util

enum class SkyCategory {
    CLEAR_DAY,
    CLEAR_NIGHT,
    PARTLY_CLOUDY_DAY,
    PARTLY_CLOUDY_NIGHT,
    OVERCAST,
    RAIN,
    SNOW,
    STORM,
    FOG,
    // Time-derived (not from WMO code — set by ViewModel based on time-of-day)
    PRE_DAWN,
    SUNRISE,
    SUNSET,
}

enum class IconCategory {
    CLEAR,
    PARTLY_CLOUDY,
    OVERCAST,
    FOG,
    DRIZZLE,
    RAIN,
    SNOW,
    STORM,
    HAIL,
}

data class WmoDescription(
    val label: String,
    val iconCategory: IconCategory,
    val skyCategory: SkyCategory,
    val hasRain: Boolean = false,
    val hasSnow: Boolean = false,
    val hasThunder: Boolean = false,
    val hasFog: Boolean = false,
)

object WmoCode {

    fun describe(code: Int, isDay: Boolean): WmoDescription = when (code) {
        0 -> WmoDescription(
            label = "Clear sky",
            iconCategory = IconCategory.CLEAR,
            skyCategory = if (isDay) SkyCategory.CLEAR_DAY else SkyCategory.CLEAR_NIGHT,
        )
        1 -> WmoDescription(
            label = "Mainly clear",
            iconCategory = IconCategory.PARTLY_CLOUDY,
            skyCategory = if (isDay) SkyCategory.PARTLY_CLOUDY_DAY else SkyCategory.PARTLY_CLOUDY_NIGHT,
        )
        2 -> WmoDescription(
            label = "Partly cloudy",
            iconCategory = IconCategory.PARTLY_CLOUDY,
            skyCategory = if (isDay) SkyCategory.PARTLY_CLOUDY_DAY else SkyCategory.PARTLY_CLOUDY_NIGHT,
        )
        3 -> WmoDescription(
            label = "Overcast",
            iconCategory = IconCategory.OVERCAST,
            skyCategory = SkyCategory.OVERCAST,
        )
        45 -> WmoDescription(
            label = "Fog",
            iconCategory = IconCategory.FOG,
            skyCategory = SkyCategory.FOG,
            hasFog = true,
        )
        48 -> WmoDescription(
            label = "Depositing rime fog",
            iconCategory = IconCategory.FOG,
            skyCategory = SkyCategory.FOG,
            hasFog = true,
        )
        51 -> WmoDescription(
            label = "Light drizzle",
            iconCategory = IconCategory.DRIZZLE,
            skyCategory = SkyCategory.RAIN,
            hasRain = true,
        )
        53 -> WmoDescription(
            label = "Moderate drizzle",
            iconCategory = IconCategory.DRIZZLE,
            skyCategory = SkyCategory.RAIN,
            hasRain = true,
        )
        55 -> WmoDescription(
            label = "Dense drizzle",
            iconCategory = IconCategory.DRIZZLE,
            skyCategory = SkyCategory.RAIN,
            hasRain = true,
        )
        61 -> WmoDescription(
            label = "Slight rain",
            iconCategory = IconCategory.RAIN,
            skyCategory = SkyCategory.RAIN,
            hasRain = true,
        )
        63 -> WmoDescription(
            label = "Moderate rain",
            iconCategory = IconCategory.RAIN,
            skyCategory = SkyCategory.RAIN,
            hasRain = true,
        )
        65 -> WmoDescription(
            label = "Heavy rain",
            iconCategory = IconCategory.RAIN,
            skyCategory = SkyCategory.RAIN,
            hasRain = true,
        )
        71 -> WmoDescription(
            label = "Slight snow",
            iconCategory = IconCategory.SNOW,
            skyCategory = SkyCategory.SNOW,
            hasSnow = true,
        )
        73 -> WmoDescription(
            label = "Moderate snow",
            iconCategory = IconCategory.SNOW,
            skyCategory = SkyCategory.SNOW,
            hasSnow = true,
        )
        75 -> WmoDescription(
            label = "Heavy snow",
            iconCategory = IconCategory.SNOW,
            skyCategory = SkyCategory.SNOW,
            hasSnow = true,
        )
        77 -> WmoDescription(
            label = "Snow grains",
            iconCategory = IconCategory.SNOW,
            skyCategory = SkyCategory.SNOW,
            hasSnow = true,
        )
        80 -> WmoDescription(
            label = "Slight rain showers",
            iconCategory = IconCategory.RAIN,
            skyCategory = SkyCategory.RAIN,
            hasRain = true,
        )
        81 -> WmoDescription(
            label = "Moderate rain showers",
            iconCategory = IconCategory.RAIN,
            skyCategory = SkyCategory.RAIN,
            hasRain = true,
        )
        82 -> WmoDescription(
            label = "Violent rain showers",
            iconCategory = IconCategory.RAIN,
            skyCategory = SkyCategory.RAIN,
            hasRain = true,
        )
        85 -> WmoDescription(
            label = "Slight snow showers",
            iconCategory = IconCategory.SNOW,
            skyCategory = SkyCategory.SNOW,
            hasSnow = true,
        )
        86 -> WmoDescription(
            label = "Heavy snow showers",
            iconCategory = IconCategory.SNOW,
            skyCategory = SkyCategory.SNOW,
            hasSnow = true,
        )
        95 -> WmoDescription(
            label = "Thunderstorm",
            iconCategory = IconCategory.STORM,
            skyCategory = SkyCategory.STORM,
            hasRain = true,
            hasThunder = true,
        )
        96 -> WmoDescription(
            label = "Thunderstorm with slight hail",
            iconCategory = IconCategory.HAIL,
            skyCategory = SkyCategory.STORM,
            hasRain = true,
            hasThunder = true,
        )
        99 -> WmoDescription(
            label = "Thunderstorm with heavy hail",
            iconCategory = IconCategory.HAIL,
            skyCategory = SkyCategory.STORM,
            hasRain = true,
            hasThunder = true,
        )
        else -> WmoDescription(
            label = "Unknown",
            iconCategory = IconCategory.OVERCAST,
            skyCategory = if (isDay) SkyCategory.CLEAR_DAY else SkyCategory.CLEAR_NIGHT,
        )
    }
}
