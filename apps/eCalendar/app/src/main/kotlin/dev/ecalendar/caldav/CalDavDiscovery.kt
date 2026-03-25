package dev.ecalendar.caldav

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.io.StringReader

data class DiscoveredCalendar(
    val url: String,
    val displayName: String,
    val colorHex: String? = null,
    val isWritable: Boolean = true,
)

sealed class DiscoveryResult {
    data class Success(val calendars: List<DiscoveredCalendar>) : DiscoveryResult()
    data object AuthFailed : DiscoveryResult()
    data object NotCalDav : DiscoveryResult()
    data class NetworkError(val message: String) : DiscoveryResult()
}

object CalDavDiscovery {

    /**
     * Full CalDAV account discovery:
     * 1. PROPFIND baseUrl → current-user-principal
     * 2. PROPFIND principal → calendar-home-set
     * 3. PROPFIND home depth=1 → list of calendars
     */
    suspend fun discoverAccount(client: CalDavClient, baseUrl: String): DiscoveryResult {
        return try {
            // Step 1: Get principal URL
            val principalUrl = discoverPrincipal(client, baseUrl)
                ?: return tryDirectCalendarListing(client, baseUrl)

            // Step 2: Get calendar home URL
            val homeUrl = discoverCalendarHome(client, principalUrl)
                ?: return tryDirectCalendarListing(client, baseUrl)

            // Step 3: List calendars
            val calendars = listCalendars(client, homeUrl)
            if (calendars.isEmpty()) {
                // Zoho quirk: home URL might be the base URL directly
                val fallback = listCalendars(client, baseUrl)
                if (fallback.isNotEmpty()) return DiscoveryResult.Success(fallback)
                return DiscoveryResult.NotCalDav
            }
            DiscoveryResult.Success(calendars)
        } catch (e: Exception) {
            Timber.w(e, "CalDAV discovery failed")
            when {
                e.message?.contains("401") == true -> DiscoveryResult.AuthFailed
                else -> DiscoveryResult.NetworkError(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Discovers just the calendar home URL (steps 1+2 of full discovery).
     * Returns null if discovery fails.
     */
    suspend fun discoverCalendarHomeUrl(client: CalDavClient, baseUrl: String): String? {
        return try {
            val principalUrl = discoverPrincipal(client, baseUrl) ?: return null
            discoverCalendarHome(client, principalUrl)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun discoverPrincipal(client: CalDavClient, baseUrl: String): String? {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:current-user-principal/>
              </d:prop>
            </d:propfind>
        """.trimIndent()

        val response = client.propfind(baseUrl, 0, body)
        if (response.code == 401) throw Exception("401")
        if (!response.isSuccessful && response.code != 207) return null

        val xml = response.body?.string() ?: return null
        return extractHref(xml, "current-user-principal")?.let { resolveUrl(baseUrl, it) }
    }

    private suspend fun discoverCalendarHome(client: CalDavClient, principalUrl: String): String? {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
              <d:prop>
                <c:calendar-home-set/>
              </d:prop>
            </d:propfind>
        """.trimIndent()

        val response = client.propfind(principalUrl, 0, body)
        if (!response.isSuccessful && response.code != 207) return null

        val xml = response.body?.string() ?: return null
        return extractHref(xml, "calendar-home-set")?.let { resolveUrl(principalUrl, it) }
    }

    private suspend fun listCalendars(client: CalDavClient, homeUrl: String): List<DiscoveredCalendar> {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:cs="http://calendarserver.org/ns/" xmlns:ical="http://apple.com/ns/ical/">
              <d:prop>
                <d:resourcetype/>
                <d:displayname/>
                <ical:calendar-color/>
                <d:current-user-privilege-set/>
              </d:prop>
            </d:propfind>
        """.trimIndent()

        val response = client.propfind(homeUrl, 1, body)
        if (!response.isSuccessful && response.code != 207) return emptyList()

        val xml = response.body?.string() ?: return emptyList()
        return parseCalendarList(xml, homeUrl)
    }

    private suspend fun tryDirectCalendarListing(client: CalDavClient, baseUrl: String): DiscoveryResult {
        val calendars = listCalendars(client, baseUrl)
        return if (calendars.isNotEmpty()) {
            DiscoveryResult.Success(calendars)
        } else {
            DiscoveryResult.NotCalDav
        }
    }

    // --- XML Parsing ---

    private fun extractHref(xml: String, parentElement: String): String? {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var inTarget = false
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == parentElement) inTarget = true
                    if (inTarget && parser.name == "href") {
                        parser.next()
                        if (parser.eventType == XmlPullParser.TEXT) {
                            return parser.text.trim()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == parentElement) inTarget = false
                }
            }
            eventType = parser.next()
        }
        return null
    }

    private fun parseCalendarList(xml: String, baseUrl: String): List<DiscoveredCalendar> {
        val calendars = mutableListOf<DiscoveredCalendar>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        var inResponse = false
        var href: String? = null
        var displayName: String? = null
        var color: String? = null
        var isCalendar = false
        var isWritable = false
        var currentTag = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name ?: ""
                    when (currentTag) {
                        "response" -> {
                            inResponse = true
                            href = null; displayName = null; color = null
                            isCalendar = false; isWritable = false
                        }
                        "calendar" -> isCalendar = true
                        "write" -> isWritable = true
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    if (text.isNotBlank() && inResponse) {
                        when (currentTag) {
                            "href" -> if (href == null) href = text
                            "displayname" -> displayName = text
                            "calendar-color" -> color = normalizeColor(text)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "response" && inResponse) {
                        if (isCalendar && href != null && displayName != null) {
                            calendars.add(
                                DiscoveredCalendar(
                                    url = resolveUrl(baseUrl, href),
                                    displayName = displayName,
                                    colorHex = color,
                                    isWritable = isWritable,
                                ),
                            )
                        }
                        inResponse = false
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }
        return calendars
    }

    private fun normalizeColor(color: String): String? {
        if (color.isBlank()) return null
        // Apple format: #RRGGBBAA → #RRGGBB
        val hex = color.trim()
        return if (hex.length == 9 && hex.startsWith("#")) {
            hex.substring(0, 7)
        } else {
            hex
        }
    }

    private fun resolveUrl(base: String, path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val baseUri = java.net.URI(base)
        return baseUri.resolve(path).toString()
    }
}
