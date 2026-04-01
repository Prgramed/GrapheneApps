package dev.ecalendar.caldav

import dev.ecalendar.data.db.dao.CalendarDao
import dev.ecalendar.data.db.entity.CalendarSourceEntity
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.component.VEvent
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.StringReader
import java.util.UUID

/**
 * Fetches an iCal subscription URL and mirrors its events into a Synology CalDAV calendar.
 * The mirror calendar is created on Synology if it doesn't exist (via MKCALENDAR).
 * All existing events in the mirror calendar are deleted first, then subscription events are PUT.
 */
object ICalSubscriptionSyncer {

    /**
     * Syncs an iCal subscription to a mirror calendar on Synology.
     *
     * @param subscriptionUrl The public .ics URL
     * @param displayName Calendar display name for the mirror
     * @param synoClient CalDavClient authenticated to the Synology CalDAV server
     * @param synoCalHomeUrl The Synology calendar home URL (e.g., /caldav/user/calendars/)
     * @param httpClient Plain OkHttpClient for fetching the subscription (no auth needed)
     */
    suspend fun sync(
        subscriptionUrl: String,
        displayName: String,
        synoAccountId: Long,
        synoClient: CalDavClient,
        synoCalHomeUrl: String,
        httpClient: OkHttpClient,
        calendarDao: CalendarDao,
    ) {
        // 1. Fetch the full .ics file
        val icsBody = fetchSubscription(subscriptionUrl, httpClient) ?: return

        // 2. Parse all VEVENTs
        val vevents = parseVEvents(icsBody)
        if (vevents.isEmpty()) {
            Timber.d("iCal subscription: no events found at $subscriptionUrl")
            return
        }
        Timber.d("iCal subscription: parsed ${vevents.size} events from $subscriptionUrl")

        // 3. Ensure mirror calendar exists on Synology
        val slug = displayName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "ical-mirror" }
        val mirrorCalUrl = "${synoCalHomeUrl.trimEnd('/')}/$slug/"

        ensureMirrorCalendarExists(synoClient, mirrorCalUrl, displayName)

        // 3b. Ensure CalendarSource exists in Room with isMirror = true
        val existing = calendarDao.getByUrl(mirrorCalUrl)
        if (existing == null) {
            calendarDao.upsert(
                CalendarSourceEntity(
                    accountId = synoAccountId,
                    calDavUrl = mirrorCalUrl,
                    displayName = displayName,
                    colorHex = "#7986CB",
                    isReadOnly = true,
                    isVisible = true,
                    isMirror = true,
                ),
            )
        }

        // 4. Build map of new event URLs
        val newEventUrls = mutableSetOf<String>()
        var putCount = 0
        for (vevent in vevents) {
            try {
                val uid = vevent.uid?.value ?: UUID.randomUUID().toString()
                val icsPayload = wrapInVCalendar(vevent)
                val eventUrl = "${mirrorCalUrl.trimEnd('/')}/${uid}.ics"
                newEventUrls.add(eventUrl)
                synoClient.put(eventUrl, icsPayload, etag = null).use { response ->
                    if (response.isSuccessful || response.code == 201 || response.code == 204) {
                        putCount++
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "iCal subscription: failed to PUT event")
            }
        }

        // 5. Delete events that are no longer in the subscription (diff-based)
        val existingHrefs = fetchExistingEventHrefs(synoClient, mirrorCalUrl)
        for (href in existingHrefs) {
            if (href !in newEventUrls) {
                try {
                    synoClient.delete(href, etag = "*")
                } catch (e: Exception) {
                    Timber.w(e, "iCal subscription: failed to delete $href")
                }
            }
        }
        Timber.d("iCal subscription: mirrored $putCount/${vevents.size} events to $mirrorCalUrl")
    }

    private suspend fun fetchSubscription(url: String, client: OkHttpClient): String? {
        return try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("iCal subscription: GET failed with ${response.code} for $url")
                        return@withContext null
                    }
                    response.body?.string()
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "iCal subscription: fetch failed for $url")
            null
        }
    }

    private fun parseVEvents(icsBody: String): List<VEvent> {
        return try {
            val builder = CalendarBuilder()
            val calendar = builder.build(StringReader(icsBody))
            calendar.getComponents<VEvent>("VEVENT")
        } catch (e: Exception) {
            Timber.w(e, "iCal subscription: failed to parse ICS")
            emptyList()
        }
    }

    private suspend fun ensureMirrorCalendarExists(
        client: CalDavClient,
        calUrl: String,
        displayName: String,
    ) {
        // Try PROPFIND first to check if it exists
        try {
            client.propfind(calUrl, 0, """
                <?xml version="1.0" encoding="utf-8"?>
                <d:propfind xmlns:d="DAV:">
                  <d:prop><d:displayname/></d:prop>
                </d:propfind>
            """.trimIndent()).use { response ->
                if (response.isSuccessful || response.code == 207) return
            }
        } catch (_: Exception) {}

        // Create via MKCALENDAR
        try {
            client.mkcalendar(calUrl, displayName, "#7986CB").use { response ->
                Timber.d("iCal subscription: MKCALENDAR ${response.code} for $calUrl")
            }
        } catch (e: Exception) {
            Timber.w(e, "iCal subscription: MKCALENDAR failed for $calUrl")
        }
    }

    private suspend fun fetchExistingEventHrefs(client: CalDavClient, calUrl: String): List<String> {
        val reportBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
              <d:prop><d:getetag/></d:prop>
              <c:filter>
                <c:comp-filter name="VCALENDAR">
                  <c:comp-filter name="VEVENT"/>
                </c:comp-filter>
              </c:filter>
            </c:calendar-query>
        """.trimIndent()

        return try {
            val xml = client.report(calUrl, reportBody).use { response ->
                if (!response.isSuccessful && response.code != 207) return emptyList()
                response.body?.string() ?: return emptyList()
            }
            parseHrefs(xml, calUrl)
        } catch (e: Exception) {
            Timber.w(e, "iCal subscription: failed to fetch existing hrefs")
            emptyList()
        }
    }

    private fun parseHrefs(xml: String, baseUrl: String): List<String> {
        val hrefs = mutableListOf<String>()
        val parser = android.util.Xml.newPullParser()
        parser.setInput(StringReader(xml))

        var currentTag = ""
        var eventType = parser.eventType
        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> currentTag = parser.name ?: ""
                org.xmlpull.v1.XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    if (currentTag == "href" && text.isNotBlank()) {
                        val resolved = if (text.startsWith("http")) text
                        else java.net.URI(baseUrl).resolve(text).toString()
                        if (resolved != baseUrl.trimEnd('/') && resolved != baseUrl) {
                            hrefs.add(resolved)
                        }
                    }
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> currentTag = ""
            }
            eventType = parser.next()
        }
        return hrefs
    }

    private fun wrapInVCalendar(vevent: VEvent): String {
        return buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//eCalendar//iCal Mirror//EN")
            append(vevent.toString())
            appendLine("END:VCALENDAR")
        }
    }
}
