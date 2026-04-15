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
        serverCtag: String? = null,
    ) {
        val now = LocalDate.now()
        val rangeStart = now.minusYears(1)
        val rangeEnd = now.plusYears(1)

        val startStr = rangeStart.atStartOfDay().atOffset(ZoneOffset.UTC).format(UTC_FORMAT)
        val endStr = rangeEnd.atStartOfDay().atOffset(ZoneOffset.UTC).format(UTC_FORMAT)

        val reportBody = buildReportBody(startStr, endStr)
        val (body, contentType) = client.report(source.calDavUrl, reportBody).use { response ->
            if (!response.isSuccessful && response.code != 207) {
                Timber.w("fullSync REPORT failed: ${response.code} for ${source.calDavUrl}")
                return
            }
            val b = response.body?.string() ?: return
            b to response.header("Content-Type")
        }

        if (!isXmlBody(contentType, body)) {
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

        // Store real server CTAG if available, otherwise fallback to timestamp
        calendarDao.upsert(
            dev.ecalendar.data.db.entity.CalendarSourceEntity(
                id = source.id,
                accountId = source.accountId,
                calDavUrl = source.calDavUrl,
                displayName = source.displayName,
                colorHex = source.colorHex,
                ctag = serverCtag ?: System.currentTimeMillis().toString(),
                isReadOnly = source.isReadOnly,
                isVisible = source.isVisible,
                isMirror = source.isMirror,
            ),
        )
    }

    /**
     * Smart sync: fetches server CTAG via PROPFIND depth=0.
     * If CTAG matches stored value, skip sync entirely.
     * If CTAG differs or unavailable, use quickSync (if we have a stored ctag) or fullSync.
     */
    suspend fun sync(
        client: CalDavClient,
        source: CalendarSource,
        eventDao: EventDao,
        calendarDao: CalendarDao,
    ) {
        // Fetch server CTAG to decide if sync is needed.
        val serverCtag = fetchCtag(client, source.calDavUrl)

        // If we think we're up to date but have zero events for this source
        // locally, don't trust the ctag match — a previous sync may have
        // written the ctag without actually pulling events (e.g. REPORT
        // returned non-XML and was silently dropped). Force a full sync to
        // recover instead of being stuck empty forever.
        val localCount = try { eventDao.countSeriesForSource(source.id) } catch (_: Exception) { -1 }
        val trustCtag = serverCtag != null && serverCtag == source.ctag && localCount > 0

        if (trustCtag) {
            Timber.d("sync: CTAG unchanged for ${source.displayName}, skipping")
            return
        }

        if (source.ctag != null && localCount > 0) {
            quickSync(client, source, eventDao, calendarDao, serverCtag)
        } else {
            fullSync(client, source, eventDao, calendarDao, serverCtag)
        }
    }

    /**
     * Fetches the CTAG (getctag) for a calendar via PROPFIND depth=0.
     * Returns null if the server doesn't support CTAG.
     */
    private suspend fun fetchCtag(client: CalDavClient, calUrl: String): String? {
        return try {
            val body = """
                <?xml version="1.0" encoding="utf-8"?>
                <d:propfind xmlns:d="DAV:" xmlns:cs="http://calendarserver.org/ns/">
                  <d:prop><cs:getctag/></d:prop>
                </d:propfind>
            """.trimIndent()
            val response = client.propfind(calUrl, 0, body)
            val xml = response.body?.string()
            response.close()
            if (xml == null) return null
            // Parse getctag from response
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            var currentTag = ""
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> currentTag = parser.name ?: ""
                    XmlPullParser.TEXT -> {
                        if (currentTag == "getctag") {
                            val ctag = parser.text?.trim()
                            if (!ctag.isNullOrBlank()) return ctag
                        }
                    }
                    XmlPullParser.END_TAG -> currentTag = ""
                }
                eventType = parser.next()
            }
            null
        } catch (e: Exception) {
            Timber.d("fetchCtag: failed for $calUrl: ${e.message}")
            null
        }
    }

    /**
     * Delta sync: PROPFIND depth=1 for ETags only, compare with Room, fetch only changed.
     */
    private suspend fun quickSync(
        client: CalDavClient,
        source: CalendarSource,
        eventDao: EventDao,
        calendarDao: CalendarDao,
        serverCtag: String? = null,
    ) {
        val propfindBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop><d:getetag/></d:prop>
            </d:propfind>
        """.trimIndent()

        val (body, contentType) = client.propfind(source.calDavUrl, 1, propfindBody).use { response ->
            if (!response.isSuccessful && response.code != 207) {
                Timber.w("quickSync PROPFIND failed: ${response.code}, falling back to fullSync")
                fullSync(client, source, eventDao, calendarDao)
                return
            }
            val b = response.body?.string() ?: return
            b to response.header("Content-Type")
        }

        if (!isXmlBody(contentType, body)) {
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
                    val icsData = client.get(href).use { getResponse ->
                        if (!getResponse.isSuccessful) return@use null
                        getResponse.body?.string()
                    } ?: continue

                    val series = ICalParser.parseEventSeries(icsData, source.id, serverEtag, href)
                    val events = RecurrenceExpander.expand(series, rangeStart, rangeEnd)

                    eventDao.deleteEventsByUid(series.uid)
                    eventDao.upsertSeries(series.toEntity())
                    for (event in events) {
                        eventDao.upsertEvent(event.toEntity())
                    }
                    fetchCount++
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

        // Store real server CTAG if available, otherwise fallback to timestamp
        calendarDao.upsert(
            dev.ecalendar.data.db.entity.CalendarSourceEntity(
                id = source.id, accountId = source.accountId,
                calDavUrl = source.calDavUrl, displayName = source.displayName,
                colorHex = source.colorHex, ctag = serverCtag ?: System.currentTimeMillis().toString(),
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

    private fun isXmlBody(contentType: String?, body: String): Boolean {
        if (contentType != null) {
            if (contentType.contains("xml", ignoreCase = true)) return true
            if (contentType.contains("text/calendar", ignoreCase = true)) return true
        }
        // Zoho sometimes returns text/plain with valid XML — check body
        val trimmed = body.trimStart()
        return trimmed.startsWith("<?xml") || trimmed.startsWith("<d:") || trimmed.startsWith("<D:") || trimmed.startsWith("<")
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
