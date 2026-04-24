package dev.ecalendar.caldav

import dev.ecalendar.data.db.dao.CalendarDao
import dev.ecalendar.data.db.entity.CalendarSourceEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.component.VEvent
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.StringReader
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fetches an iCal subscription URL and mirrors its events into a Synology CalDAV calendar.
 * The mirror calendar is created on Synology if it doesn't exist (via MKCALENDAR).
 * All existing events in the mirror calendar are deleted first, then subscription events are PUT.
 */
object ICalSubscriptionSyncer {

    // Cache of ics-body SHA-256 per subscription URL. If the feed hasn't
    // changed since last sync, skip the entire mirror (no PROPFIND, no PUTs).
    // The Synology PROPFIND/REPORT for existing events takes 20+ seconds per
    // calendar — this eliminates it on steady-state syncs.
    private val feedHashCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun sha256(data: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }

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

        // 1b. Check if feed content changed since last sync. If identical,
        //     skip the entire mirror (PROPFIND + PUTs + deletes) — that path
        //     takes 20+ seconds per calendar on Synology even when nothing
        //     needs to be written.
        val feedHash = sha256(icsBody)
        val lastHash = feedHashCache[subscriptionUrl]
        if (feedHash == lastHash) {
            Timber.d("iCal subscription: feed unchanged, skipping mirror for $subscriptionUrl")
            return
        }

        // 2. Parse all VEVENTs
        val vevents = parseVEvents(icsBody)
        if (vevents.isEmpty()) {
            Timber.d("iCal subscription: no events found at $subscriptionUrl")
            feedHashCache[subscriptionUrl] = feedHash // cache even if empty
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

        // 4. Fetch existing event hrefs from the mirror BEFORE pushing — so we
        //    only PUT genuinely new events instead of re-PUTting all 884 events
        //    on every sync (each returning 412 already-exists). This drops sync
        //    from ~60s to ~2s when nothing changed.
        val existingHrefs = fetchExistingEventHrefs(synoClient, mirrorCalUrl).toSet()

        val newEventUrls = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val putCount = AtomicInteger(0)
        val skipped = AtomicInteger(0)
        val putFailures = AtomicInteger(0)

        // Build the full list of expected URLs for diff-based deletion later.
        val allExpectedUrls = vevents.map { vevent ->
            val uid = vevent.uid?.value ?: UUID.randomUUID().toString()
            "${mirrorCalUrl.trimEnd('/')}/${uid}.ics"
        }.toSet()
        newEventUrls.addAll(allExpectedUrls)

        // Only PUT events whose URL is NOT already on the server.
        val toPush = vevents.filter { vevent ->
            val uid = vevent.uid?.value ?: return@filter true
            val eventUrl = "${mirrorCalUrl.trimEnd('/')}/${uid}.ics"
            eventUrl !in existingHrefs
        }

        if (toPush.isNotEmpty()) {
            coroutineScope {
                val putSemaphore = Semaphore(8)
                toPush.map { vevent ->
                    async {
                        putSemaphore.withPermit {
                            try {
                                val uid = vevent.uid?.value ?: UUID.randomUUID().toString()
                                val icsPayload = wrapInVCalendar(vevent)
                                val eventUrl = "${mirrorCalUrl.trimEnd('/')}/${uid}.ics"
                                synoClient.put(eventUrl, icsPayload, etag = null).use { response ->
                                    when {
                                        response.isSuccessful -> putCount.incrementAndGet()
                                        response.code == 412 -> skipped.incrementAndGet()
                                        else -> {
                                            putFailures.incrementAndGet()
                                            if (putFailures.get() <= 3) {
                                                Timber.w("iCal mirror PUT ${response.code} for $eventUrl")
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                putFailures.incrementAndGet()
                                Timber.w(e, "iCal subscription: failed to PUT event")
                            }
                        }
                    }
                }.awaitAll()
            }
        }
        skipped.addAndGet(vevents.size - toPush.size) // count pre-filtered as skipped

        // 5. Delete events that are no longer in the subscription (diff-based)
        for (href in existingHrefs) {
            if (href !in newEventUrls) {
                try {
                    synoClient.delete(href, etag = "*").close()
                } catch (e: Exception) {
                    Timber.w(e, "iCal subscription: failed to delete $href")
                }
            }
        }
        Timber.d(
            "iCal mirror: $mirrorCalUrl → new=${putCount.get()}, " +
                "skipped=${skipped.get()}, failed=${putFailures.get()} " +
                "(of ${vevents.size})",
        )
        // Cache the feed hash so next sync skips the mirror if unchanged.
        feedHashCache[subscriptionUrl] = feedHash
    }

    private suspend fun fetchSubscription(url: String, client: OkHttpClient): String? {
        return try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // Google's iCal endpoint returns 404 for .../basic.ics/ (trailing
                // slash). If the URL was mis-normalized on save, transparently
                // retry without the trailing slash before giving up.
                val attempts = buildList {
                    add(url)
                    if (url.endsWith("/")) add(url.trimEnd('/'))
                }
                for (attempt in attempts) {
                    val request = Request.Builder().url(attempt).get().build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            return@withContext response.body?.string()
                        }
                        Timber.w("iCal subscription: GET failed with ${response.code} for $attempt")
                    }
                }
                null
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
