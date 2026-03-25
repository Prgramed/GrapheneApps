package dev.eweather.data.api

import dev.eweather.data.api.dto.AirQualityResponseDto
import dev.eweather.data.api.dto.ForecastResponseDto
import dev.eweather.data.api.dto.GeocodingResultDto
import dev.eweather.data.api.dto.RainViewerResponseDto
import dev.eweather.domain.model.AirQuality
import dev.eweather.domain.model.CurrentWeather
import dev.eweather.domain.model.DailyPoint
import dev.eweather.domain.model.HourlyPoint
import dev.eweather.domain.model.RadarFrame
import dev.eweather.domain.model.SavedLocation
import dev.eweather.domain.model.WeatherData

object OpenMeteoMapper {

    fun mapForecast(dto: ForecastResponseDto): WeatherData {
        val current = dto.current?.let { c ->
            CurrentWeather(
                temp = c.temperature ?: 0f,
                feelsLike = c.apparentTemperature ?: 0f,
                weatherCode = c.weatherCode ?: 0,
                windSpeed = c.windSpeed ?: 0f,
                windDirection = c.windDirection ?: 0,
                humidity = c.humidity ?: 0,
                pressure = c.surfacePressure ?: 0f,
                cloudCover = c.cloudCover ?: 0,
                visibility = c.visibility ?: 0f,
                precipitation = c.precipitation ?: 0f,
                isDay = (c.isDay ?: 1) == 1,
                timestamp = System.currentTimeMillis(),
            )
        } ?: CurrentWeather(
            temp = 0f, feelsLike = 0f, weatherCode = 0, windSpeed = 0f,
            windDirection = 0, humidity = 0, pressure = 0f, cloudCover = 0,
            visibility = 0f, precipitation = 0f, isDay = true,
            timestamp = System.currentTimeMillis(),
        )

        val hourly = dto.hourly?.let { h ->
            h.time.indices.map { i ->
                HourlyPoint(
                    timestamp = parseIsoToMillis(h.time.getOrNull(i)),
                    temp = h.temperature.getOrNull(i) ?: 0f,
                    feelsLike = h.apparentTemperature.getOrNull(i) ?: 0f,
                    weatherCode = h.weatherCode.getOrNull(i) ?: 0,
                    precipProbability = h.precipProbability.getOrNull(i) ?: 0,
                    precipAmount = h.precipitation.getOrNull(i) ?: 0f,
                    windSpeed = h.windSpeed.getOrNull(i) ?: 0f,
                    windDirection = h.windDirection.getOrNull(i) ?: 0,
                    humidity = h.humidity.getOrNull(i) ?: 0,
                    uvIndex = h.uvIndex.getOrNull(i) ?: 0f,
                    isDay = (h.isDay.getOrNull(i) ?: 1) == 1,
                )
            }
        } ?: emptyList()

        val daily = dto.daily?.let { d ->
            d.time.indices.map { i ->
                DailyPoint(
                    date = d.time.getOrNull(i) ?: "",
                    tempMax = d.tempMax.getOrNull(i) ?: 0f,
                    tempMin = d.tempMin.getOrNull(i) ?: 0f,
                    weatherCode = d.weatherCode.getOrNull(i) ?: 0,
                    sunrise = d.sunrise.getOrNull(i) ?: "",
                    sunset = d.sunset.getOrNull(i) ?: "",
                    uvIndexMax = d.uvIndexMax.getOrNull(i) ?: 0f,
                    precipSum = d.precipSum.getOrNull(i) ?: 0f,
                    windSpeedMax = d.windSpeedMax.getOrNull(i) ?: 0f,
                    windDirDominant = d.windDirDominant.getOrNull(i) ?: 0,
                )
            }
        } ?: emptyList()

        return WeatherData(
            current = current,
            hourly = hourly,
            daily = daily,
        )
    }

    fun mapAirQuality(dto: AirQualityResponseDto): AirQuality? {
        val h = dto.hourly ?: return null
        // Return the most recent non-null values
        val now = System.currentTimeMillis()
        val currentHour = h.time.indexOfLast { parseIsoToMillis(it) <= now }.coerceAtLeast(0)
        return AirQuality(
            pm25 = h.pm25.getOrNull(currentHour),
            pm10 = h.pm10.getOrNull(currentHour),
            no2 = h.no2.getOrNull(currentHour),
            ozone = h.ozone.getOrNull(currentHour),
            europeanAqi = h.europeanAqi.getOrNull(currentHour),
            usAqi = h.usAqi.getOrNull(currentHour),
        )
    }

    fun mapGeocodingResult(dto: GeocodingResultDto): SavedLocation = SavedLocation(
        id = dto.id ?: 0,
        name = dto.name ?: "",
        region = dto.admin1 ?: "",
        country = dto.country ?: "",
        lat = dto.latitude ?: 0.0,
        lon = dto.longitude ?: 0.0,
    )

    fun mapRadarFrames(dto: RainViewerResponseDto): List<RadarFrame> {
        val host = dto.host ?: "https://tilecache.rainviewer.com"
        val frames = mutableListOf<RadarFrame>()
        dto.radar?.past?.forEach { frame ->
            if (frame.time != null && frame.path != null) {
                frames.add(
                    RadarFrame(
                        timestamp = frame.time * 1000, // Convert to millis
                        tileUrlTemplate = "$host${frame.path}/512/{z}/{x}/{y}/6/1_1.png",
                    ),
                )
            }
        }
        dto.radar?.nowcast?.forEach { frame ->
            if (frame.time != null && frame.path != null) {
                frames.add(
                    RadarFrame(
                        timestamp = frame.time * 1000,
                        tileUrlTemplate = "$host${frame.path}/512/{z}/{x}/{y}/6/1_1.png",
                    ),
                )
            }
        }
        return frames.sortedBy { it.timestamp }
    }

    private fun parseIsoToMillis(iso: String?): Long {
        if (iso == null) return 0L
        return try {
            java.time.LocalDateTime.parse(iso).atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.LocalDate.parse(iso).atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        }
    }
}
