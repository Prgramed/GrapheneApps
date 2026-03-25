package dev.ecalendar.caldav

import android.util.Xml
import dev.ecalendar.data.db.dao.CalendarDao
import dev.ecalendar.data.db.dao.EventDao
import dev.ecalendar.data.db.entity.toEntity
import dev.ecalendar.domain.model.CalendarSource
import dev.ecalendar.ical.ICalParser
import dev.ecalendar.ical.RecurrenceExpander
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.io.StringReader
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private data class SyncItem(
    val href: String,
    val etag: String,
    val icsData: String,
)

object CalDavSyncEngine {

    private val UTC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    /**
     * Full sync: REPORT all VEVENTs in ±1 year window, parse, expand, upsert Room.
     * Deletes events that exist in Room but no longer on server.
     */
    suspend fun fullSync(
        client: CalDavClient,
        source: CalendarSource,
        eventDao: EventDao,
        calendarDao: CalendarDao,
    ) {
        val now = LocalDate.now()
        val rangeStart = now.minusYears(1)
        val rangeEnd = now.plusYears(1)

        val startStr = rangeStart.atStartOfDay().atOffset(ZoneOffset.UTC).format(UTC_FORMAT)
        val endStr = rangeEnd.atStartOfDay().atOffset(ZoneOffset.UTC).format(UTC_FORMAT)

        val reportBody = buildReportBody(startStr, endStr)
        val response = client.report(source.calDavUrl, reportBody)

        if (!response.isSuccessful && response.code != 207) {
            Timber.w("fullSync REPORT failed: ${response.code} for ${source.calDavUrl}")
            response.close()
            return
        }

        val body = response.body?.string() ?: return
        response.close()

        if (!isXmlResponse(response, body)) {
            Timber.w("fullSync: non-XML response from ${source.displayName}: ${body.take(200)}")
            logZohoError(body)
            return
        }

        val items = parseMultiStatusResponse(body, source.calDavUrl)
        Timber.d("fullSync: ${items.size} events from ${source.displayName}")

        val serverUids = mutableSetOf<String>()

        for (item in items) {
            try {
                val series = ICalParser.parseEventSeries(
                    icsString = item.icsData,
                    calendarSourceId = source.id,
                    etag = item.etag,
                    serverUrl = item.href,
                )
                serverUids.add(series.uid)

                // Expand recurrence
                val events = RecurrenceExpander.expand(series, rangeStart, rangeEnd)

                // Upsert to Room
                eventDao.upsertSeries(series.toEntity())
                // Delete old instances for this uid before inserting fresh ones
                eventDao.deleteEventsByUid(series.uid)
                for (event in events) {
                    eventDao.upsertEvent(event.toEntity())
                }
            } catch (e: Exception) {
                Timber.w(e, "fullSync: failed to parse event at ${item.href}")
            }
        }

        // Delete events in Room that are no longer on server
        val existingEtags = eventDao.getEtags(source.id)
        for (entry in existingEtags) {
            if (entry.uid !in serverUids) {
                eventDao.deleteEventsByUid(entry.uid)
                eventDao.deleteSeriesByUid(entry.uid)
                Timber.d("fullSync: deleted removed event ${entry.uid}")
            }
        }

        // Update ctag (if server provides one, we'd parse it from PROPFIND — for now use timestamp)
        calendarDao.upsert(
            dev.ecalendar.data.db.entity.CalendarSourceEntity(
                id = source.id,
                accountId = source.accountId,
                calDavUrl = source.calDavUrl,
                displayName = source.displayName,
                colorHex = source.colorHex,
                ctag = System.currentTimeMillis().toString(),
                isReadOnly = source.isReadOnly,
                isVisible = source.isVisible,
                isMirror = source.isMirror,
            ),
        )
    }

    /**
     * Smart sync: uses quickSync if ctag exists and < 7 days old, otherwise fullSync.
     */
    suspend fun sync(
        client: CalDavClient,
        source: CalendarSource,
        eventDao: EventDao,
        calendarDao: CalendarDao,
    ) {
        val hasCtag = source.ctag != null
        val ctagAge = source.ctag?.toLongOrNull()?.let { System.currentTimeMillis() - it } ?: Long.MAX_VALUE
        val sevenDays = 7L * 24 * 60 * 60 * 1000

        if (hasCtag && ctagAge < sevenDays) {
            quickSync(client, source, eventDao, calendarDao)
        } else {
            fullSync(client, source, eventDao, calendarDao)
        }
    }

    /**
     * Delta sync: PROPFIND depth=1 for ETags only, compare with Room, fetch only changed.
     */
    suspend fun quickSync(
        client: CalDavClient,
        source: CalendarSource,
        eventDao: EventDao,
        calendarDao: CalendarDao,
    ) {
        val propfindBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop><d:getetag/></d:prop>
            </d:propfind>
        """.trimIndent()

        val response = client.propfind(source.calDavUrl, 1, propfindBody)
        if (!response.isSuccessful && response.code != 207) {
            Timber.w("quickSync PROPFIND failed: ${response.code}, falling back to fullSync")
            response.close()
            fullSync(client, source, eventDao, calendarDao)
            return
        }

        val body = response.body?.string() ?: return
        response.close()

        if (!isXmlResponse(response, body)) {
            Timber.w("quickSync: non-XML response from ${source.displayName}: ${body.take(200)}")
            logZohoError(body)
            fullSync(client, source, eventDao, calendarDao)
            return
        }

        val serverEtags = parseEtagPropfind(body, source.calDavUrl) // href → etag
        val localEtags = eventDao.getServerUrlEtags(source.id)
            .associate { it.serverUrl to it.etag } // serverUrl → etag

        val now = LocalDate.now()
        val rangeStart = now.minusYears(1)
        val rangeEnd = now.plusYears(1)

        // Find added or changed
        var fetchCount = 0
        for ((href, serverEtag) in serverEtags) {
            val localEtag = localEtags[href]
            if (localEtag == null || localEtag != serverEtag) {
                // New or changed — fetch full ICS
                try {
                    val getResponse = client.get(href)
                    if (getResponse.isSuccessful) {
                        val icsData = getResponse.body?.string() ?: continue
                        getResponse.close()

                        val series = ICalParser.parseEventSeries(icsData, source.id, serverEtag, href)
                        val events = RecurrenceExpander.expand(series, rangeStart, rangeEnd)

                        eventDao.deleteEventsByUid(series.uid)
                        eventDao.upsertSeries(series.toEntity())
                        for (event in events) {
                            eventDao.upsertEvent(event.toEntity())
                        }
                        fetchCount++
                    } else {
                        getResponse.close()
                    }
                } catch (e: Exception) {
                    Timber.w(e, "quickSync: failed to fetch $href")
                }
            }
        }

        // Find deleted (in Room but not on server)
        val serverHrefs = serverEtags.keys
        for ((localHref, _) in localEtags) {
            if (localHref !in serverHrefs) {
                val series = eventDao.getSeriesByServerUrl(localHref)
                if (series != null) {
                    eventDao.deleteEventsByUid(series.uid)
                    eventDao.deleteSeriesByUid(series.uid)
                    Timber.d("quickSync: deleted removed event ${series.uid}")
                }
            }
        }

        Timber.d("quickSync: fetched $fetchCount changed events for ${source.displayName}")

        // Update ctag
        calendarDao.upsert(
            dev.ecalendar.data.db.entity.CalendarSourceEntity(
                id = source.id, accountId = source.accountId,
                calDavUrl = source.calDavUrl, displayName = source.displayName,
                colorHex = source.colorHex, ctag = System.currentTimeMillis().toString(),
                isReadOnly = source.isReadOnly, isVisible = source.isVisible, isMirror = source.isMirror,
            ),
        )
    }

    /**
     * Parses a PROPFIND depth=1 response to extract href → etag map.
     */
    private fun parseEtagPropfind(xml: String, baseUrl: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        var inResponse = false
        var href: String? = null
        var etag: String? = null
        var currentTag = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name ?: ""
                    if (currentTag == "response") {
                        inResponse = true; href = null; etag = null
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    if (text.isNotBlank() && inResponse) {
                        when (currentTag) {
                            "href" -> href = resolveUrl(baseUrl, text)
                            "getetag" -> etag = text.trim('"')
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "response" && inResponse) {
                        if (href != null && etag != null && href != baseUrl.trimEnd('/') && href != baseUrl) {
                            result[href] = etag
                        }
                        inResponse = false
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }
        return result
    }

    private fun buildReportBody(start: String, end: String): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
          <d:prop>
            <d:getetag/>
            <c:calendar-data/>
          </d:prop>
          <c:filter>
            <c:comp-filter name="VCALENDAR">
              <c:comp-filter name="VEVENT">
                <c:time-range start="$start" end="$end"/>
              </c:comp-filter>
            </c:comp-filter>
          </c:filter>
        </c:calendar-query>
    """.trimIndent()

    /**
     * Parses a WebDAV multi-status XML response into SyncItem tuples.
     * Extracts href, getetag, and calendar-data from each response element.
     */
    private fun parseMultiStatusResponse(xml: String, baseUrl: String): List<SyncItem> {
        val items = mutableListOf<SyncItem>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        var inResponse = false
        var href: String? = null
        var etag: String? = null
        var icsData: String? = null
        var currentTag = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name ?: ""
                    if (currentTag == "response") {
                        inResponse = true
                        href = null; etag = null; icsData = null
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    if (text.isNotBlank() && inResponse) {
                        when (currentTag) {
                            "href" -> href = resolveUrl(baseUrl, text)
                            "getetag" -> etag = text.trim('"')
                            "calendar-data" -> icsData = text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "response" && inResponse) {
                        if (href != null && etag != null && icsData != null && icsData.contains("VEVENT")) {
                            items.add(SyncItem(href, etag, icsData))
                        }
                        inResponse = false
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }
        return items
    }

    private fun resolveUrl(base: String, path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val baseUri = java.net.URI(base)
        return baseUri.resolve(path).toString()
    }

    private fun isXmlResponse(response: okhttp3.Response, body: String): Boolean {
        val ct = response.header("Content-Type") ?: return body.trimStart().startsWith("<?xml") || body.trimStart().startsWith("<")
        if (ct.contains("xml", ignoreCase = true)) return true
        if (ct.contains("text/calendar", ignoreCase = true)) return true
        // Zoho sometimes returns text/plain with valid XML — check body
        return body.trimStart().startsWith("<?xml") || body.trimStart().startsWith("<d:") || body.trimStart().startsWith("<D:")
    }

    private fun logZohoError(body: String) {
        val lower = body.lowercase()
        when {
            "no calendar found" in lower -> Timber.e("Zoho: calendar not found — check URL")
            "invalid credentials" in lower || "authentication" in lower -> Timber.e("Zoho: authentication failed — check app-specific password")
            "rate limit" in lower || "too many" in lower -> Timber.e("Zoho: rate limit reached — will retry later")
            else -> Timber.w("Zoho: unexpected response — ${body.take(300)}")
        }
    }
}
