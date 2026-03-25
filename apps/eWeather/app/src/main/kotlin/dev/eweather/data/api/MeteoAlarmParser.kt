package dev.eweather.data.api

import android.util.Xml
import dev.eweather.domain.model.AlertSeverity
import dev.eweather.domain.model.WeatherAlert
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object MeteoAlarmParser {

    fun parse(xml: String): List<WeatherAlert> {
        val alerts = mutableListOf<WeatherAlert>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        var inEntry = false
        var id: String? = null
        var event: String? = null
        var severity: String? = null
        var area: String? = null
        var effective: Long? = null
        var expires: Long? = null
        var currentTag: String? = null

        val now = System.currentTimeMillis()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name
                    if (tag == "entry") {
                        inEntry = true
                        id = null; event = null; severity = null
                        area = null; effective = null; expires = null
                    }
                    if (inEntry) currentTag = tag
                }
                XmlPullParser.TEXT -> {
                    if (inEntry && currentTag != null) {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "cap:identifier" -> id = text
                                "cap:event" -> event = text
                                "cap:severity" -> severity = text
                                "cap:areaDesc" -> area = text
                                "cap:effective" -> effective = parseIsoDateTime(text)
                                "cap:expires" -> expires = parseIsoDateTime(text)
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "entry" && inEntry) {
                        inEntry = false
                        if (id != null && event != null && expires != null && expires > now) {
                            val alertSeverity = mapSeverity(severity)
                            if (alertSeverity != null) {
                                alerts.add(
                                    WeatherAlert(
                                        id = id,
                                        event = event,
                                        severity = alertSeverity,
                                        headline = "$event — ${area ?: ""}",
                                        area = area ?: "",
                                        effective = effective ?: now,
                                        expires = expires,
                                    ),
                                )
                            }
                        }
                    }
                    currentTag = null
                }
            }
        }

        return alerts
    }

    private fun mapSeverity(text: String?): AlertSeverity? = when (text?.lowercase()) {
        "minor" -> AlertSeverity.MINOR
        "moderate" -> AlertSeverity.MODERATE
        "severe" -> AlertSeverity.SEVERE
        "extreme" -> AlertSeverity.EXTREME
        else -> null // Skip "unknown" or green/no-warning entries
    }

    private fun parseIsoDateTime(text: String): Long? = try {
        ZonedDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            .toInstant().toEpochMilli()
    } catch (_: Exception) {
        null
    }
}
