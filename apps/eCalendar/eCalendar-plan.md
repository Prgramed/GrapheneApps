# eCalendar — Master Build Plan
> GrapheneOS · Synology CalDAV (primary) · Zoho CalDAV · iCloud Read-Only · Thunderbird RSVP · No GMS
> Last updated: 2026-03-23

---

## Confirmed Decisions

| Concern | Decision |
|---|---|
| **Platform** | GrapheneOS, sideloaded APK, single user, no GMS |
| **Primary store** | Synology CalDAV via Tailscale — all new events live here |
| **Zoho (4 accounts)** | Full CalDAV read/write; app-specific passwords (2FA); Zoho authoritative for its own events |
| **iCloud shared** | iCal URL subscription → mirrored to Synology; GET full `.ics`, full-replace a dedicated "iCloud – Shared" Synology calendar each sync; read-only in app |
| **Gmail / Hotmail** | ICS import only via Thunderbird intent; no direct sync |
| **RSVP flow** | App generates `METHOD:REPLY` `.ics` → `ACTION_SEND` intent → user approves send in Thunderbird |
| **Offline** | Local queue (Room); sync to Synology when Tailscale reconnects |
| **Conflict resolution** | Last write wins — silent; server ETag check prevents accidental overwrites |
| **Recurrence** | ical4j — full RFC 5545 compliance including exceptions and single-occurrence edits |
| **Notifications** | VALARM-based; custom time(s) per event; exact alarms via `AlarmManager` |
| **Views** | Month grid, Week, Day, Agenda/list |
| **Event fields** | Title, date/time, all-day, location, notes, URL, attendees, colour, recurrence, reminders, travel time buffer |
| **Design goal** | iOS Calendar quality — clean, fast, beautiful typography, smooth transitions |
| **Location** | Android native `LocationManager` for "use current location" — no GMS |

---

## How to Use This Plan with Claude Code

Each **Session** is one Claude Code working block. At the start of every session paste:

```
This is session X.Y of the eCalendar Android app build.
Reference file: eCalendar-plan.md (attached or pasted).
Previous sessions are complete. Build only what is listed under session X.Y.
Do not refactor earlier sessions unless explicitly listed as a deliverable.
```

---

## Tech Stack

| Layer | Library | Notes |
|---|---|---|
| Language | Kotlin 2.x | |
| UI | Jetpack Compose + Material 3 | |
| Architecture | MVVM + Clean Architecture + UDF | |
| CalDAV HTTP | OkHttp 4 with custom methods | PROPFIND / REPORT / PUT / DELETE — no CalDAV lib needed |
| iCalendar | ical4j 4.x (Android-compatible branch) | RFC 5545 parsing, generation, recurrence expansion, VALARM |
| Local DB | Room | Event cache + offline queue + ETag store |
| DI | Hilt | |
| Async | Coroutines + Flow | |
| Preferences | DataStore (Proto) | |
| Credentials | EncryptedSharedPreferences | App-specific passwords for Zoho + iCloud |
| Background sync | WorkManager | |
| Notifications | `AlarmManager` (exact alarms) + `NotificationManager` | VALARM triggers |
| Animations | Compose Animation + spring physics | |

> **No GMS dependencies anywhere.** No `FusedLocationProviderClient`. Validate all transitive deps.

---

## CalDAV Protocol Reference

CalDAV is WebDAV + iCalendar over HTTPS. All operations are standard HTTP — implemented directly with OkHttp using custom HTTP methods.

### HTTP Methods Used

| Method | Purpose | OkHttp |
|---|---|---|
| `PROPFIND` | Discover calendars; get ETags + display names | `Request.Builder().method("PROPFIND", body)` |
| `REPORT` | Fetch events in a date range; get changed events since last sync | Same pattern |
| `PUT` | Create or update a single event `.ics` | Standard PUT |
| `DELETE` | Delete an event | Standard DELETE |

### Key Request Headers
```
Depth: 0 / 1       — PROPFIND scope
Content-Type: application/xml     — for PROPFIND/REPORT bodies
Content-Type: text/calendar; charset=utf-8   — for PUT
Authorization: Basic base64(username:apppassword)
If-Match: "etag-value"    — optimistic concurrency on PUT/DELETE
```

### Discovery Flow (run once per account)
1. `PROPFIND {base_url}` with `DAV:current-user-principal` → get user principal URL
2. `PROPFIND {principal_url}` with `caldav:calendar-home-set` → get calendar home URL
3. `PROPFIND {calendar_home_url}` depth=1 with `DAV:resourcetype`, `DAV:displayname`, `apple:calendar-color` → list of calendar URLs + metadata

### Sync Flow (run on schedule)
1. `REPORT {calendar_url}` with `caldav:calendar-query` filtering by date range → get all event URLs + ETags
2. Compare to stored ETags in Room → identify new/changed/deleted
3. For new/changed: `GET {event_url}` → parse ical4j → upsert Room
4. For deleted: remove from Room
5. Store new ETags; store `lastSyncedAt`

### Account-Specific CalDAV URLs

| Account | Base URL | Notes |
|---|---|---|
| Synology | `http://[tailscale-ip]:5000/caldav2/[username]/` | HTTP ok on LAN/Tailscale |
| Zoho | `https://calendar.zoho.com/caldav/[email]/` | HTTPS required |
| iCloud | Subscription URL from Mac (paste as `https://`) | No auth — URL is the credential; full `.ics` fetch only |

> **Synology note:** Use HTTP (not HTTPS) on the Tailscale connection — Synology's self-signed cert causes OkHttp to reject HTTPS on LAN. HTTP is fine on a private Tailscale tunnel.

---

## iCalendar Data Model (ical4j)

Key ical4j types used throughout the app:

```kotlin
// Parse
val calendar: Calendar = CalendarBuilder().build(inputStream)
val events: List<VEvent> = calendar.getComponents(Component.VEVENT)

// Access properties
val uid: String = event.getProperty(Property.UID).value
val summary: String = event.getProperty(Property.SUMMARY).value
val dtStart: DateTime = event.startDate.date as DateTime
val rrule: RRule? = event.getProperty(Property.RRULE)
val alarms: List<VAlarm> = event.getComponents(Component.VALARM)

// Expand recurrence
val recur: Recur = rrule.recur
val dates: DateList = recur.getDates(dtStart, rangeStart, rangeEnd, Value.DATE_TIME)

// Generate
val event = VEvent(dtStart, dtEnd, "Meeting title").apply {
    properties.add(Uid(UUID.randomUUID().toString()))
    properties.add(RRule(Recur("FREQ=WEEKLY;BYDAY=MO,WE,FR")))
    components.add(VAlarm(Dur(-1, 0, 0, 0)).apply { // 1 hour before
        properties.add(Action(Action.DISPLAY))
        properties.add(Description("Reminder"))
    })
}
```

---

## Design System

### Colour Sources
Each calendar (Synology calendars, each Zoho account, iCloud) has an assigned colour. Events inherit their calendar's colour. Colour stored as `COLOR` (RFC 7986) on Synology, `X-APPLE-CALENDAR-COLOR` on iCloud, and read from Zoho's CalDAV metadata.

### View Design Principles (iOS Calendar quality)
- **Month grid**: 6-week grid; event dots/pills below date numbers; today circle highlight; clean SF-like typography using `fontFamily = FontFamily.Default` (system font)
- **Week view**: time-grid with events as coloured rounded rectangles; current time red line; multi-day events in a separate header strip
- **Day view**: single-column time grid; same event blocks as week view but wider; hour labels left-aligned in small grey text
- **Agenda**: date headers with full event cards below; card shows colour bar left edge, title, time, calendar name; infinite scroll both directions
- **Transitions**: shared element on event title between views; swipe left/right to navigate forward/back in time; spring physics on all gestures

### Typography
- Month day numbers: `FontWeight.Light`, 16sp
- Today: same but inside a filled circle (accent colour)
- Event pills in month: 10sp, single line, truncated
- Time labels: `FontWeight.Normal`, 12sp, 40% alpha
- Event block title: `FontWeight.Medium`, 13sp
- Agenda date header: `FontWeight.SemiBold`, 18sp

### Colour System
- Background: `MaterialTheme.colorScheme.background` (follows system dark/light)
- Event colours: 8 predefined palette colours assignable per calendar; each has a light and dark variant for the two themes
- Today highlight: `MaterialTheme.colorScheme.primary`
- Weekend day numbers: slightly muted alpha vs weekday

---

## Project Structure

```
eCalendar/
├── app/src/main/
│   ├── caldav/
│   │   ├── CalDavClient.kt          ← OkHttp wrapper: PROPFIND/REPORT/PUT/DELETE
│   │   ├── CalDavDiscovery.kt       ← Account discovery flow (Synology + Zoho only)
│   │   ├── CalDavSyncEngine.kt      ← Full + delta sync logic per account
│   │   ├── ICalSubscriptionSyncer.kt← GET .ics → PUT all events to Synology mirror calendar
│   │   └── ZohoRateLimiter.kt       ← Per-account token bucket; 490 req/hour cap
│   ├── ical/
│   │   ├── ICalParser.kt            ← ical4j parsing → domain models
│   │   ├── ICalGenerator.kt         ← domain models → ical4j → .ics string
│   │   ├── RecurrenceExpander.kt    ← Expand RRULE into concrete event instances
│   │   └── RsvpGenerator.kt         ← Generate METHOD:REPLY .ics for Thunderbird
│   ├── data/
│   │   ├── db/
│   │   │   ├── AppDatabase.kt
│   │   │   ├── dao/                 ← EventDao, CalendarDao, AccountDao, SyncQueueDao
│   │   │   └── entity/              ← EventEntity, CalendarEntity, AccountEntity, SyncQueueEntity
│   │   └── repository/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── CalendarEvent.kt     ← Fully expanded event (one row per recurrence instance)
│   │   │   ├── EventSeries.kt       ← Master event with RRULE (before expansion)
│   │   │   ├── CalendarAccount.kt   ← Synology / Zoho-1 / Zoho-2 / iCloud
│   │   │   ├── CalendarSource.kt    ← One calendar within an account
│   │   │   └── SyncQueueItem.kt     ← Pending local change awaiting sync
│   │   └── repository/              ← Interfaces
│   ├── sync/
│   │   ├── SyncCoordinator.kt       ← Orchestrates all account sync in parallel
│   │   ├── SyncWorker.kt            ← WorkManager worker
│   │   └── OfflineQueueProcessor.kt ← Drain queue when connectivity returns
│   ├── alarm/
│   │   └── AlarmScheduler.kt        ← AlarmManager exact alarms from VALARM data
│   ├── ui/
│   │   ├── MainActivity.kt
│   │   ├── navigation/NavGraph.kt
│   │   ├── month/
│   │   │   ├── MonthScreen.kt
│   │   │   └── MonthViewModel.kt
│   │   ├── week/
│   │   │   ├── WeekScreen.kt
│   │   │   └── WeekViewModel.kt
│   │   ├── day/
│   │   │   ├── DayScreen.kt
│   │   │   └── DayViewModel.kt
│   │   ├── agenda/
│   │   │   ├── AgendaScreen.kt
│   │   │   └── AgendaViewModel.kt
│   │   ├── event/
│   │   │   ├── EventDetailScreen.kt
│   │   │   ├── EventEditScreen.kt
│   │   │   └── EventViewModel.kt
│   │   ├── accounts/
│   │   │   ├── AccountsScreen.kt
│   │   │   └── AccountSetupScreen.kt
│   │   └── settings/
│   │       └── SettingsScreen.kt
│   └── util/
│       ├── TimeZoneHelper.kt
│       └── ColorPalette.kt
```

---

## Phase 0 — Synology CalDAV Setup (Prerequisites, Not Code)

**Do this before writing a single line of app code.**

### Step 1 — Install Synology Calendar Package
1. Open DSM → **Package Center**
2. Search "Calendar" → Install **Synology Calendar**
3. Wait for install to complete

### Step 2 — Enable CalDAV
1. Open **Synology Calendar** app in DSM
2. Go to Settings (top-right gear icon) → **CalDAV**
3. Enable CalDAV server
4. Note the CalDAV URL shown — it will look like:
   `http://[NAS-IP]:5000/caldav2/[your-username]/`
   On Tailscale, replace `[NAS-IP]` with your Tailscale IP (e.g. `100.x.x.x`)

### Step 3 — Create Your Calendars
Create at least these calendars in Synology Calendar:
- **Personal** — your primary calendar for new events
- **Work** — optional, if you want work events separated
- **Imported** — suggested bucket for ICS imports from Gmail/Hotmail

Each calendar will have its own sub-path under your CalDAV URL.

### Step 4 — Verify Access
Test CalDAV access from your phone before building:
```
curl -u "username:password" \
  -X PROPFIND \
  -H "Depth: 1" \
  http://[tailscale-ip]:5000/caldav2/[username]/
```
Should return XML listing your calendars. If it does, CalDAV is working.

### Step 5 — Zoho App-Specific Passwords
For each of your 4 Zoho accounts:
1. Go to `accounts.zoho.com` → Security → **App Passwords**
2. Create a new app password named "eCalendar"
3. Copy it immediately — it won't be shown again
4. Repeat for all 4 accounts

Zoho CalDAV URL pattern: `https://calendar.zoho.com/caldav/[email-address]/`

### Step 6 — Get Your iCloud Subscription URL
1. Open **Calendar** on your Mac
2. Right-click the shared calendar in the sidebar → **Get Info**
3. Copy the URL shown — it starts with `webcal://`
4. Change `webcal://` to `https://` — that's the URL you'll paste into eCalendar
5. No password needed — the URL itself is the credential; keep it private

---

## Phase 1 — Foundation & CalDAV Layer

**Goal:** Talk to Synology CalDAV, parse iCalendar data, store everything in Room.

---

- [x] **Session 1.1 — Project Scaffolding** ✅ 2026-03-24
  - `applicationId = "dev.ecalendar"`, minSdk 29, targetSdk 35
  - `libs.versions.toml`: Kotlin 2.x, Compose BOM, OkHttp 4, ical4j 4.x (`net.sf.biweekly:biweekly` — actually use `org.mnode.ical4j:ical4j:4.x` with the Android-compatible configuration), Room, Hilt, DataStore Proto, WorkManager, Kotlin serialization, EncryptedSharedPreferences
  - ical4j Android config: add `ical4j.properties` to `src/main/resources/` with `net.fortuna.ical4j.timezone.cache.impl=net.fortuna.ical4j.util.MapTimeZoneCache` — disables ical4j's default file-based timezone cache which crashes on Android
  - Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`, `USE_EXACT_ALARM`, `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`
  - Use `USE_EXACT_ALARM` (API 33+) not `SCHEDULE_EXACT_ALARM` — calendar apps are explicitly listed as an eligible category; `USE_EXACT_ALARM` requires no user approval and cannot be revoked in Settings, unlike `SCHEDULE_EXACT_ALARM`; no permission request dialog needed
  - For API 29–32 fallback: `SCHEDULE_EXACT_ALARM` still needed; add `if (Build.VERSION.SDK_INT < 33)` branch in `AlarmScheduler` that checks `AlarmManager.canScheduleExactAlarms()` and uses `setExactAndAllowWhileIdle` only if granted
  - `ECalendarApp.kt`: `@HiltAndroidApp`; initialise `StrictMode` in debug only
  - ProGuard rules for ical4j — all four lines required; missing any causes release-only crashes that don't appear in debug:
    ```
    -keep class net.fortuna.ical4j.**
    -keep interface net.fortuna.ical4j.**
    -keepattributes *Annotation*
    -keep class * implements net.fortuna.ical4j.** { *; }
    ```
  - ical4j uses service-loader and reflection internally — R8 strips these silently in release builds; always test a release APK before declaring any ical4j session complete
  - **Key files:** `libs.versions.toml`, `app/build.gradle.kts`, `AndroidManifest.xml`, `ECalendarApp.kt`, `src/main/resources/ical4j.properties`
  - **Exit test:** `./gradlew assembleDebug` — clean build; ical4j timezone cache config verified by unit test that creates a `VEvent` without crashing

---

- [x] **Session 1.2 — Domain Models** ✅ 2026-03-24
  - `CalendarAccount`: `id: Long`, `type: AccountType (SYNOLOGY/ZOHO/ICAL_SUBSCRIPTION)`, `displayName: String`, `baseUrl: String`, `username: String`, `colorHex: String`, `lastSyncedAt: Long?`, `isEnabled: Boolean`
  - `AccountType` enum: `SYNOLOGY` (CalDAV read/write, primary), `ZOHO` (CalDAV read/write, Zoho-authoritative), `ICAL_SUBSCRIPTION` (periodic GET of full `.ics` → mirror to Synology; no CalDAV, no credentials)
  - Note: `ICLOUD` as a separate account type is not needed — iCloud is handled as `ICAL_SUBSCRIPTION`; the type name makes its behaviour self-documenting
  - `CalendarSource`: `id: Long`, `accountId: Long`, `calDavUrl: String`, `displayName: String`, `colorHex: String`, `ctag: String?` (server change token for delta sync), `isReadOnly: Boolean`, `isVisible: Boolean`
  - `EventSeries` (master record, stored once per unique UID): `uid: String`, `calendarSourceId: Long`, `rawIcs: String` (full ics blob from server), `etag: String`, `serverUrl: String`, `isLocal: Boolean` (created offline, not yet pushed)
  - `CalendarEvent` (one row per visible instance, expanded from recurrence): `id: Long`, `uid: String` (FK to EventSeries), `instanceStart: Long`, `instanceEnd: Long`, `title: String`, `location: String?`, `notes: String?`, `url: String?`, `colorHex: String?`, `isAllDay: Boolean`, `calendarSourceId: Long`, `recurrenceId: Long?` (non-null = this instance was individually modified), `isCancelled: Boolean`, `travelTimeMins: Int?`
  - `SyncQueueItem`: `id: Long`, `accountId: Long`, `calendarUrl: String`, `eventUid: String`, `operation: SyncOp (CREATE/UPDATE/DELETE)`, `icsPayload: String?`, `createdAt: Long`, `retryCount: Int`
  - `AccountType` enum, `SyncOp` enum
  - **Key files:** `domain/model/*.kt` (6 files)
  - **Exit test:** `./gradlew testDebug` — data class unit tests pass

---

- [x] **Session 1.3 — iCalendar Parser (ical4j → Domain)** ✅ 2026-03-24
  - `ICalParser.kt`:
    - **`CalendarBuilder` is not thread-safe** — it maintains internal parser state; `SyncCoordinator` runs multiple account syncs in parallel via `async {}`; always create a `new CalendarBuilder()` instance per parse call, never share one across coroutines; add a `// WARNING: do not share — not thread-safe` comment at the call site
    - `parseEventSeries(icsString: String, calendarSourceId: Long, etag: String, serverUrl: String): EventSeries` — parses raw ICS into an `EventSeries`; stores raw ICS blob verbatim for round-trip fidelity
    - `parseAttendees(icsString): List<String>` — extract `ATTENDEE` properties as email strings
    - `parseAlarms(icsString): List<AlarmTrigger(offsetMins: Int, description: String)>` — extract all `VALARM` components; convert `TRIGGER` duration to minutes-before offset
  - `RecurrenceExpander.kt`:
    - `expand(series: EventSeries, rangeStart: LocalDate, rangeEnd: LocalDate): List<CalendarEvent>` — uses ical4j `Recur.getDates()` to expand `RRULE` into concrete instances within range; handles `EXDATE` (excluded dates); handles `RECURRENCE-ID` (individual instance overrides)
    - **Hard cap: never return more than 500 instances per series** — if expansion would exceed 500, truncate and set a `isTruncated = true` flag on the last returned instance; `EventDetailScreen` checks this flag and shows "Showing first 500 occurrences" note
    - Non-recurring events: returns a single `CalendarEvent`
    - All-day events: `DTSTART` has `VALUE=DATE` (no time component) — detect and set `isAllDay=true`
    - Timezone handling: ical4j handles `TZID` parameters; convert to device timezone for display using `TimeZoneHelper.toLocalDateTime()`
  - `TimeZoneHelper.kt`: `fun toLocalMillis(icalDate: Date, tzId: String?): Long` — converts ical4j date to epoch millis in correct timezone
  - **Key files:** `ical/ICalParser.kt`, `ical/RecurrenceExpander.kt`, `util/TimeZoneHelper.kt`
  - **Exit test:** Unit tests:
    - Parse a real-world ICS with `FREQ=WEEKLY;BYDAY=MO,WE,FR;UNTIL=20261231` → expand for November 2026 → correct instance count
    - Parse ICS with `EXDATE` → excluded date not in expanded list
    - Parse all-day event → `isAllDay=true`, `instanceStart` is midnight

---

- [x] **Session 1.4 — iCalendar Generator (Domain → ical4j)** ✅ 2026-03-24
  - `ICalGenerator.kt`:
    - `generateEventIcs(event: EditableEvent): String` — builds a full `VCALENDAR` + `VEVENT` string ready for `PUT`; `EditableEvent` is a UI-layer model containing all editable fields
    - Preserves unknown properties from original ICS if editing an existing event (round-trip fidelity) — parse original raw ICS, modify only changed properties, re-serialize
    - `PRODID: -//eCalendar//eCalendar 1.0//EN`
    - Generates `UID` as `UUID.randomUUID()` for new events
    - Converts `travelTimeMins` to `X-APPLE-TRAVEL-DURATION;VALUE=DURATION:PT${mins}M` — standard Apple extension, understood by most clients
    - Encodes `ATTENDEE` as `ATTENDEE;CN="Name";RSVP=TRUE:mailto:email@example.com`
    - Encodes `VALARM`: `ACTION:DISPLAY`, `TRIGGER:-PT${mins}M`, `DESCRIPTION:Reminder`
    - `ORGANIZER` set to primary Synology account email for new events
  - `RsvpGenerator.kt`:
    - `generateRsvpReply(originalIcs: String, attendeeEmail: String, status: PartStat): String`
    - `PartStat` enum: `ACCEPTED`, `DECLINED`, `TENTATIVE`
    - Builds `VCALENDAR` with `METHOD:REPLY`; copies original `VEVENT`; sets `ATTENDEE;PARTSTAT=ACCEPTED:mailto:${attendeeEmail}`; sets `DTSTAMP` to now
    - Returns complete ICS string ready to attach to email
  - **Key files:** `ical/ICalGenerator.kt`, `ical/RsvpGenerator.kt`
  - **Exit test:** Unit test — generate an event ICS, parse it back with `ICalParser`, verify all fields round-trip correctly (title, location, RRULE, VALARM offset, attendees). Generate RSVP reply, verify `METHOD:REPLY` and `PARTSTAT:ACCEPTED` are present.

---

- [x] **Session 1.5 — CalDAV HTTP Client** ✅ 2026-03-24
  - `CalDavClient.kt`: thin OkHttp wrapper; all CalDAV HTTP operations
  - Constructor takes `baseUrl`, `username`, `password`; builds `OkHttpClient` with `BasicAuthInterceptor` (adds `Authorization: Basic ...` header); 30s timeout; `ConnectionPool(5, 5, TimeUnit.MINUTES)`
  - ```kotlin
    suspend fun propfind(url: String, depth: Int, body: String): Response
    suspend fun report(url: String, body: String): Response
    suspend fun put(url: String, icsContent: String, etag: String?): Response
        // etag non-null → adds `If-Match: "etag"` header (optimistic lock)
        // etag null → adds `If-None-Match: *` (create only, fail if exists)
    suspend fun delete(url: String, etag: String): Response
    suspend fun get(url: String): Response
    ```
  - All methods: return raw `Response`; callers check `response.code`; 412 (Precondition Failed) = ETag mismatch = conflict → last-write-wins means: re-fetch, discard local, retry PUT with new ETag
  - `BasicAuthInterceptor.kt`: encodes `username:password` as Base64, adds to every request
  - Hilt module providing `CalDavClient` as `@Singleton` per account (keyed by `accountId`)
  - **Key files:** `caldav/CalDavClient.kt`, `caldav/BasicAuthInterceptor.kt`, `di/CalDavModule.kt`
  - **Exit test:** Integration test against a real Synology instance — `propfind` returns 207 Multi-Status; response XML is non-empty. `put` of a test event returns 201 Created; subsequent `delete` returns 204.

---

- [x] **Session 1.6 — CalDAV Discovery** ✅ 2026-03-24
  - `CalDavDiscovery.kt`:
    - `discoverAccount(client: CalDavClient): DiscoveryResult` — full discovery flow:
      1. `PROPFIND {baseUrl}` for `DAV:current-user-principal` → principal URL
      2. `PROPFIND {principalUrl}` for `caldav:calendar-home-set` → home URL
      3. `PROPFIND {homeUrl}` depth=1 for `DAV:resourcetype`, `DAV:displayname`, `ical:calendar-color`, `DAV:current-user-privilege-set` → list of `DiscoveredCalendar(url, name, color, isWritable)`
    - `DiscoveryResult`: `sealed class` — `Success(calendars)` or `AuthFailed` or `NotCalDav` or `NetworkError(message)`
    - XML parsing via Android's built-in `XmlPullParser` — no extra library
    - iCloud quirk: Apple redirects from `caldav.icloud.com` to a sharded URL (`p01-caldav.icloud.com` etc.); `CalDavClient` must follow redirects; `OkHttpClient.followRedirects = true`
    - Zoho quirk: calendar home is directly at `https://calendar.zoho.com/caldav/[email]/` — discovery may return the principal URL directly; handle both patterns
  - `AccountSetupViewModel.kt` uses `CalDavDiscovery` to validate credentials before saving
  - **Key files:** `caldav/CalDavDiscovery.kt`
  - **Exit test:** Integration test — `discoverAccount` against real Synology returns at least one `DiscoveredCalendar` with correct `displayName`. Wrong password returns `AuthFailed`.

---

- [x] **Session 1.7 — Room Database** ✅ 2026-03-24
  - Entities: `AccountEntity`, `CalendarSourceEntity`, `EventSeriesEntity`, `CalendarEventEntity`, `SyncQueueEntity`
  - All indexes: `CalendarEventEntity` — `@Index(instanceStart)`, `@Index(instanceEnd)`, `@Index(calendarSourceId)`, `@Index(uid)`; `SyncQueueEntity` — `@Index(accountId)`, `@Index(createdAt)`
  - `EventDao`:
    - `getEventsInRange(start: Long, end: Long): Flow<List<CalendarEvent>>` — for calendar views
    - `getEventsForDay(dayStart: Long, dayEnd: Long): Flow<List<CalendarEvent>>`
    - `upsertSeries(series: EventSeriesEntity)` — insert or replace
    - `upsertEvent(event: CalendarEventEntity)` — insert or replace
    - `deleteSeriesByUid(uid: String)` — cascades to instances
    - `getSeriesByUid(uid: String): EventSeriesEntity?`
    - `getEtags(calendarSourceId: Long): Map<String, String>` — uid → etag, used for delta sync
  - `CalendarDao`, `AccountDao`, `SyncQueueDao` — standard CRUD + `Flow` observables
  - `AppDatabase` v1; `TypeConverters` for `AccountType (SYNOLOGY/ZOHO/ICAL_SUBSCRIPTION)` and `SyncOp` enums; `ICAL_SUBSCRIPTION` accounts have no entries in `SyncQueueEntity` — the Room converter must handle all three values
  - **Key files:** `db/entity/*.kt`, `db/dao/*.kt`, `db/AppDatabase.kt`, `di/DatabaseModule.kt`
  - **Exit test:** Room schema compile-time validation. DAO unit test: insert `EventSeries` + 3 `CalendarEvent` instances; `getEventsInRange` returns correct events; delete series cascades.

---

- [x] **Session 1.8 — DataStore + Credentials** ✅ 2026-03-24
  - `app_preferences.proto`: `active_view: enum (MONTH/WEEK/DAY/AGENDA)`, `active_date: int64` (epoch millis of currently displayed date), `default_calendar_source_id: int64`, `time_format_24h: bool`, `first_day_of_week: enum (MON/SUN)`, `default_reminder_mins: int32`, `notifications_enabled: bool`, `sync_interval_hours: int32`
  - `AppPreferencesRepository.kt`: typed `Flow<AppPreferences>` + `suspend fun update*()` for each field
  - `CredentialStore.kt`: wraps `EncryptedSharedPreferences`; stores `password` per `accountId` using key `"password_$accountId"`; `fun getPassword(accountId: Long): String?`; `fun setPassword(accountId: Long, password: String)`; `fun deletePassword(accountId: Long)`
  - Hilt module providing both
  - **Key files:** `app_preferences.proto`, `AppPreferencesRepository.kt`, `data/CredentialStore.kt`, `di/PreferencesModule.kt`
  - **Exit test:** Unit test — `CredentialStore` stores and retrieves password for account ID 1; deletes correctly; retrieving deleted key returns null.

---

- [x] **Session 1.9 — Repository Layer** ✅ 2026-03-24
  - `CalendarRepository` interface + `CalendarRepositoryImpl`:
    - `observeEventsInRange(start, end): Flow<List<CalendarEvent>>` — Room, reactive
    - `observeCalendars(): Flow<List<CalendarSource>>` — Room, reactive
    - `getEventSeries(uid): EventSeries?`
    - `createEvent(editable: EditableEvent): SyncQueueItem` — generates ICS, writes `EventSeries` + expanded `CalendarEvent` instances to Room, enqueues `CREATE` in `SyncQueueEntity`
    - `updateEvent(uid, editable, scope: EditScope): SyncQueueItem` — `EditScope` = `THIS_ONLY / THIS_AND_FOLLOWING / ALL`; for `THIS_ONLY` writes `RECURRENCE-ID` exception; updates Room; enqueues `UPDATE`
    - `deleteEvent(uid, scope: EditScope)` — marks cancelled in Room; enqueues `DELETE`
    - `starEvent / unstarEvent` — stored as `X-ECALENDAR-STARRED` custom property
  - `AccountRepository` interface + `AccountRepositoryImpl`: CRUD for accounts; calls `CalDavDiscovery` on add
  - **Key files:** `data/repository/CalendarRepositoryImpl.kt`, `data/repository/AccountRepositoryImpl.kt`, `di/RepositoryModule.kt`
  - **Exit test:** Integration test — `createEvent` writes to Room correctly and enqueues a `SyncQueueItem`; `observeEventsInRange` emits the new event. `updateEvent(scope=THIS_ONLY)` creates a `RECURRENCE-ID` exception event in Room.

---

**Phase 1 Exit Criteria:** `./gradlew testDebug` passes all unit + integration tests. ical4j parses and generates ICS correctly. CalDAV client talks to Synology. Discovery finds calendars. Room stores and queries events. Credentials stored securely.

---

### Phase 2 — Synology Sync Engine

**Goal:** Full background sync with Synology as the primary store. Offline queue drains on reconnect.

---

- [x] **Session 2.1 — Full Sync (PROPFIND → REPORT → Room)** ✅ 2026-03-24
  - `CalDavSyncEngine.kt`: one instance per `CalendarSource`; injected with `CalDavClient`, `ICalParser`, `RecurrenceExpander`, `EventDao`
  - `fullSync(source: CalendarSource)`:
    1. `REPORT {source.calDavUrl}` with `calendar-query` REPORT body requesting all VEVENTs in a ±1 year window + their ETags and `calendar-data`
    2. Parse response XML → list of `(href, etag, icsData)` tuples
    3. For each: `ICalParser.parseEventSeries` → `RecurrenceExpander.expand` → `EventDao.upsertSeries` + `EventDao.upsertEvent` for each instance
    4. Events in Room but not in server response → `EventDao.deleteSeriesByUid` (deleted on server)
    5. Update `CalendarSourceEntity.ctag` with new server ctag
  - REPORT request body (XML):
    ```xml
    <c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
      <d:prop><d:getetag/><c:calendar-data/></d:prop>
      <c:filter><c:comp-filter name="VCALENDAR">
        <c:comp-filter name="VEVENT">
          <c:time-range start="20250101T000000Z" end="20271231T235959Z"/>
        </c:comp-filter>
      </c:comp-filter></c:filter>
    </c:calendar-query>
    ```
  - Recurrence expansion window: ±1 year from today; re-expand when window shifts (WorkManager job daily)
  - **Key files:** `caldav/CalDavSyncEngine.kt`
  - **Exit test:** Integration test against real Synology — `fullSync` populates Room with all events in the Synology calendar; `EventDao.getEventsInRange` returns them correctly

---

- [x] **Session 2.2 — Delta Sync (ETag-based)** ✅ 2026-03-24
  - Add `quickSync(source: CalendarSource)` to `CalDavSyncEngine`:
    1. `PROPFIND {source.calDavUrl}` depth=1 requesting only `DAV:getetag` for all items → map of `href → etag`
    2. Compare to `EventDao.getEtags(source.id)` — identify: added (href not in Room), changed (etag differs), deleted (in Room but not in server response)
    3. For added/changed: `GET {href}` → parse → upsert Room
    4. For deleted: `EventDao.deleteSeriesByUid(uid)` — uid extracted from Room record for that href
    5. Update ctag
  - First sync always does `fullSync`; subsequent syncs use `quickSync`; fall back to `fullSync` if ctag-based detection is unavailable
  - Store `lastFullSyncAt` in DataStore; force `fullSync` every 7 days regardless
  - **Key files:** `caldav/CalDavSyncEngine.kt` (update)
  - **Exit test:** Add event to Synology via web UI; trigger `quickSync` → event appears in Room. Delete that event via web UI; trigger `quickSync` → event removed from Room.

---

- [x] **Session 2.3 — Write Operations (Push Local Changes)** ✅ 2026-03-24
  - `OfflineQueueProcessor.kt`: drains `SyncQueueEntity` in order of `createdAt`; processes one item at a time:
    - `CREATE`: `CalDavClient.put(calendarUrl + uid + ".ics", icsPayload, etag=null)` with `If-None-Match: *` → 201 Created → update `EventSeriesEntity.isLocal=false`, store returned ETag; 412 = UID conflict → regenerate UID and retry once
    - `UPDATE`: fetch current ETag from server (`HEAD {url}`) → `CalDavClient.put(url, icsPayload, etag=serverEtag)` → 204 Updated; 412 = conflict → last-write-wins: re-fetch server version, overwrite local Room record, discard local change, remove from queue silently
    - `DELETE`: `CalDavClient.delete(url, etag)` → 204; 404 = already deleted on server → remove from queue silently
  - `NetworkMonitor.kt`: `ConnectivityManager.NetworkCallback` → `StateFlow<Boolean>`; `OfflineQueueProcessor` observes this — starts draining when `isOnline=true`, pauses when `isOnline=false`
  - **Recurrence exception consistency:** after the queue processor successfully pushes a `SyncQueueItem` whose ICS contains a `RECURRENCE-ID` (a single-occurrence edit), immediately trigger `CalDavSyncEngine.quickSync` on that calendar before processing the next queue item — this re-fetches the recurrence master and all its instances so Room stays consistent; skip this re-sync for non-recurrence items to avoid unnecessary round-trips
  - **Key files:** `sync/OfflineQueueProcessor.kt`, `sync/NetworkMonitor.kt`
  - **Exit test:** Disconnect Tailscale; create an event in app → `SyncQueueEntity` has one `CREATE` item; reconnect → queue drains; event appears in Synology web UI

---

- [x] **Session 2.4 — SyncCoordinator + WorkManager** ✅ 2026-03-24
  - `SyncCoordinator.kt`: orchestrates sync across all accounts; `suspend fun syncAll()`:
    - Load all enabled `CalendarAccount`s + their `CalendarSource`s
    - For each account: create `CalDavClient(baseUrl, username, credentialStore.getPassword(id))`
    - For each source: `quickSync(source)` in parallel via `async { }` inside `coroutineScope { }`
    - After sync: `OfflineQueueProcessor.drainQueue()` for Synology and Zoho accounts
    - Catches exceptions per-source; one failing source doesn't block others; logs to Timber
  - `SyncWorker.kt`: `CoroutineWorker`; calls `SyncCoordinator.syncAll()`; constraints: `NetworkType.CONNECTED`; `setRequiresBatteryNotLow(true)` for periodic sync
  - Periodic sync: `PeriodicWorkRequest` with interval from `AppPreferencesRepository.syncIntervalHours` (default 1h); scheduled on first launch; rescheduled on `BOOT_COMPLETED` and on interval preference change
  - **Recurrence window maintenance** (add to `SyncCoordinator.syncAll()`):
    - Expand window: ±1 year from today; on each sync pass, check if any recurring `EventSeries` has instances approaching the edge of the window (< 14 days of instances remaining in the future direction) → call `RecurrenceExpander.expand` for the next 14-day batch only, not a full re-expand
    - Cleanup: after each sync pass, `EventDao.deleteOldInstances(before = today - 90 days)` — removes stale past instances from `CalendarEventEntity`; keeps the table lean without touching `EventSeriesEntity` (the master records are kept indefinitely for recurrence context)
    - Daily WorkManager job (`RecurrenceMaintenanceWorker`) runs the window expansion + cleanup independently of full sync; lightweight, no network needed
  - `SyncState`: `StateFlow<SyncState (Idle / Syncing / Error(message) / LastSyncedAt(ts))>` in `SyncViewModel`; shown as a subtle indicator in the app toolbar
  - **Key files:** `sync/SyncCoordinator.kt`, `sync/SyncWorker.kt`, `sync/SyncViewModel.kt`
  - **Exit test:** `SyncWorker` scheduled correctly in WorkManager (`adb shell dumpsys jobscheduler`); trigger via `adb shell am broadcast`; all Synology calendars sync; `SyncState` transitions Idle → Syncing → LastSyncedAt

---

**Phase 2 Exit Criteria:** Full sync populates all events from Synology into Room. Create event in app → appears in Synology web after sync. Edit event in app → updated in Synology. Delete → removed. Create event offline → queues → syncs when Tailscale reconnects.

---

### Phase 3 — Calendar Views

**Goal:** Four beautiful, fluid views. iOS Calendar quality.

---

- [x] **Session 3.1 — Shared Calendar Infrastructure** ✅ 2026-03-24
  - `CalendarViewModel.kt` (shared across all views): `observeEventsInRange(start, end)` from `CalendarRepository`; `activeDate: StateFlow<LocalDate>` (navigated date); `fun navigate(date: LocalDate)` updates `activeDate` + writes to `AppPreferencesRepository`; `visibleCalendars: StateFlow<Set<Long>>` (which calendar source IDs are shown); restores `activeDate` from DataStore on init
  - `EventChip.kt`: shared composable for event representation; takes `event: CalendarEvent`, `isCompact: Boolean`; compact = small coloured dot/pill; full = coloured rounded rectangle with title; colour from `event.colorHex ?: CalendarSource.colorHex`
  - `ColorPalette.kt`: 8 predefined event colours: Tomato, Flamingo, Tangerine, Banana, Sage, Basil, Peacock, Blueberry (matching Google Calendar palette); each has `lightVariant` and `darkVariant` hex strings; `fun forTheme(hex: String, isDark: Boolean): Color`
  - `CalendarHeader.kt`: shared top bar for all views; shows current month + year; left/right arrows; "Today" button (returns to today's date); view switcher (M/W/D/A icon toggle); sync status indicator dot (green = synced, amber = syncing, red = error)
  - Navigation between views via `NavGraph`: all views share `CalendarViewModel` via `viewModel(owner = activity)`
  - **Key files:** `ui/CalendarViewModel.kt`, `ui/components/EventChip.kt`, `ui/components/CalendarHeader.kt`, `util/ColorPalette.kt`
  - **Exit test:** `CalendarViewModel` emits correct events for a date range containing known Room data. `ColorPalette.forTheme` returns the correct variant in dark vs light mode.

---

- [x] **Session 3.2 — Month Grid View** ✅ 2026-03-24
  - `MonthScreen.kt` + `MonthViewModel.kt`
  - 6-row × 7-column grid; each cell = one day; built with `LazyVerticalGrid` or a custom `Layout` composable (custom layout preferred — gives precise control over cell sizing)
  - Each cell: day number (top-left); up to 3 event pills below (truncated title, coloured background); "+N more" label if >3 events
  - Today: day number inside a filled circle using `MaterialTheme.colorScheme.primary`
  - Days from previous/next month: 40% alpha day numbers, dimmer event pills
  - Selected day: subtle rounded square background
  - Horizontal swipe between months: `HorizontalPager` with `pageCount = Int.MAX_VALUE`, `initialPage = Int.MAX_VALUE / 2`; maps page index to month offset from today; spring physics `flingBehavior`
  - Tapping a day: sets `CalendarViewModel.activeDate` + navigates to Day view if already selected, or just selects if not
  - Month grid loads events for ±2 months from current (prefetch); each month fetches from Room via `observeEventsInRange`
  - **Key files:** `ui/month/MonthScreen.kt`, `ui/month/MonthViewModel.kt`
  - **Exit test:** Month grid renders correctly; swipe to next month animates; events appear on correct days; today is highlighted; tapping a day with events navigates to Day view

---

- [x] **Session 3.3 — Week View** ✅ 2026-03-24
  - `WeekScreen.kt` + `WeekViewModel.kt`
  - Time grid: 7 columns (one per day), 24 rows (hours); scrollable vertically; current time = horizontal red line with a dot
  - Hour labels: left column, small grey text, positioned at hour boundaries
  - Multi-day / all-day events: separate header strip above the time grid; each event spans its day columns; vertically stacked if multiple
  - Event blocks: `CalendarEvent` mapped to a coloured `Box` with rounded corners; positioned absolutely using `Layout` based on `instanceStart` / `instanceEnd` minutes from midnight; overlapping events split the column width proportionally
  - Swipe left/right: navigate one week forward/back; `HorizontalPager` same pattern as month view
  - Day column headers: "Mon 23", "Tue 24" etc.; today column header bold + accent colour
  - Tapping an event block → `EventDetailScreen`
  - Long-press on an empty time slot → `EventEditScreen` with `instanceStart` pre-filled
  - Vertical scroll restores position via `LazyListState` saved in ViewModel
  - **Key files:** `ui/week/WeekScreen.kt`, `ui/week/WeekViewModel.kt`
  - **Exit test:** Week view shows all 7 days; events positioned at correct hours; overlapping events split column; multi-day events in header strip; swipe advances week; current time line visible

---

- [x] **Session 3.4 — Day View** ✅ 2026-03-24
  - `DayScreen.kt` + `DayViewModel.kt`
  - Single-column time grid; same structure as week view but one column fills the full width
  - Event blocks wider → room for 2–3 lines of text (title + location)
  - Swipe left/right: one day forward/back
  - Day header: full date "Wednesday, March 23" — tapping → navigates to Month view with that day selected
  - All-day events: header strip same as week view
  - Empty day: subtle "No events" centred in the working hours area (8am–8pm); full day still scrollable
  - Long-press → create event at that time
  - **Key files:** `ui/day/DayScreen.kt`, `ui/day/DayViewModel.kt`
  - **Exit test:** Day view shows correct events at correct times; swipe navigates day-by-day; empty day shows "No events" only in working hours area

---

- [x] **Session 3.5 — Agenda / List View** ✅ Done 2026-03-25
  - `AgendaScreen.kt` + `AgendaViewModel.kt`
  - `LazyColumn` with sticky date headers; infinite scroll in both directions (past and future)
  - Each event: left colour bar (calendar colour), title (bold), time range or "All day", location (if set), calendar name (small, bottom-right)
  - Date headers: full date "Wednesday, 23 March 2026"; today's header has accent colour; past dates have muted text
  - Grouping: consecutive days with no events collapsed into a single "— No events —" row (not an empty header per day)
  - Paging: loads 30 days forward + 30 days backward from initial date; triggers `AgendaViewModel.loadMore(direction)` when near end of list
  - Pull-to-refresh: triggers `SyncViewModel.syncNow()` + `Snackbar` "Syncing…"
  - No events at all: empty state "Your calendar is empty — tap + to add an event"
  - **Key files:** `ui/agenda/AgendaScreen.kt`, `ui/agenda/AgendaViewModel.kt`
  - **Exit test:** Agenda loads; date headers sticky on scroll; pull-to-refresh triggers sync; loading more past events works; empty groups collapsed correctly

---

- [x] **Session 3.6 — View Transitions + Navigation** ✅ Done 2026-03-25
  - `NavGraph.kt`: `MainActivity` hosts a `NavHost`; routes: `month`, `week`, `day/{date}`, `agenda`, `event/detail/{uid}/{instanceStart}`, `event/edit/{uid?}`, `accounts`, `settings`
  - View switcher (M/W/D/A) in `CalendarHeader`: taps navigate to the corresponding route; active tab highlighted
  - Shared element transition: event pill in month/week/day → event card in `EventDetailScreen` — `SharedTransitionLayout` on event title + colour indicator
  - Back from `EventDetailScreen`: reverse shared element transition back to originating view
  - FAB (floating action button): visible in all calendar views; `+` icon; taps → `EventEditScreen` with current `activeDate` pre-filled as start date
  - Bottom bar (optional): if screen width < 400dp, show bottom navigation tabs for M/W/D/A; else use top `CalendarHeader` switcher
  - **Key files:** `ui/navigation/NavGraph.kt`, `ui/MainActivity.kt`
  - **Exit test:** Navigate all four views; shared element transition on event tap → detail → back; FAB creates event; back navigation is correct throughout

---

**Phase 3 Exit Criteria:** All four views render correctly with real Synology data. Navigation between views is smooth with correct shared element transitions. Swiping forward/back works in all views. Events display with correct colours and times.

---

### Phase 4 — Event Creation & Editing

**Goal:** A beautiful, complete event editor. All fields. Recurrence. Reminders.

---

- [x] **Session 4.1 — Event Edit Screen (Core Fields)** ✅ Done 2026-03-25
  - `EventEditScreen.kt` + `EventViewModel.kt`
  - Full-screen bottom sheet or dedicated screen (decide based on visual testing — full screen likely better given the number of fields)
  - Fields in order:
    - **Title**: large `TextField`, `fontWeight = FontWeight.Light`, 24sp, no label (placeholder "Event title")
    - **All-day toggle**: `Switch` inline with date/time row; toggling hides time pickers and shows only date
    - **Start date/time**: tapping → `DatePickerDialog` + `TimePickerDialog` (Material 3 components)
    - **End date/time**: same; auto-advances to 1h after start when start changes; shows duration "(1 hour)" in small text beside end time
    - **Calendar picker**: dropdown showing all writable `CalendarSource`s with their colour dots; defaults to `AppPreferences.defaultCalendarSourceId`; iCloud read-only sources hidden
    - **Location**: `TextField` with map pin icon; optional "Use current location" chip fires `LocationManager` for lat/lon → reverse geocode via Open-Meteo Geocoding (already in eWeather stack — or just store the string without geocoding if simpler)
    - **Notes**: multiline `TextField`, expandable
    - **URL**: single-line `TextField` with link icon; validated as URL format
    - **Colour**: horizontal row of 8 colour circles; tap to select; overrides calendar colour for this event
    - **Travel time buffer**: dropdown (None / 5 / 15 / 30 / 60 / 90 min)
  - Toolbar: "Cancel" (left) — prompts discard confirmation if dirty; "Add" / "Save" (right, accent colour)
  - For editing existing events: pre-fills all fields from `EventSeries` via `ICalParser`
  - Calls `CalendarRepository.createEvent` or `updateEvent` on save → Room updated → sync queued
  - **Key files:** `ui/event/EventEditScreen.kt`, `ui/event/EventViewModel.kt`
  - **Exit test:** Create event with all fields filled; it appears in Room; appears in all views on correct date; save → `SyncQueueEntity` has `CREATE` item

---

- [x] **Session 4.2 — Attendees + Recurrence** ✅ Done 2026-03-25
  - **Attendees section** (add to `EventEditScreen`):
    - Chip input field: type email → "Add" creates `ATTENDEE` chip; each chip shows email + X to remove
    - "Invite via Thunderbird" note: small info text explaining that attendees will be notified when you send the invite ICS via Thunderbird (covered in Phase 7)
  - **Recurrence picker** (add to `EventEditScreen`):
    - Row showing current recurrence: "Does not repeat" / "Daily" / "Weekly on Mon" etc.
    - Tapping → `RecurrenceSheet` (bottom sheet):
      - Frequency: None / Daily / Weekly / Monthly / Yearly
      - For Weekly: day-of-week multi-select chips (M T W T F S S)
      - For Monthly: "On day 23" or "On the 4th Monday"
      - End: Never / On date (date picker) / After N occurrences (number input)
      - Preview: "Repeats every week on Monday and Wednesday until 31 Dec 2026"
    - ical4j `Recur` builder: construct from UI selections → embed in generated ICS
  - For editing a recurring event: show sheet: "Edit this event / This and following / All events"
  - **Key files:** `ui/event/RecurrenceSheet.kt`, updates to `EventEditScreen.kt` and `EventViewModel.kt`
  - **Exit test:** Create weekly event on Mon/Wed → ical4j `Recur` string is `FREQ=WEEKLY;BYDAY=MO,WE` in generated ICS; expansion in Room shows correct instances. Edit "This event only" → creates `RECURRENCE-ID` exception; other instances unchanged.

---

- [x] **Session 4.3 — Reminders (VALARM)** ✅ Done 2026-03-25
  - **Reminders section** (add to `EventEditScreen`):
    - List of reminders; each shows offset ("15 minutes before"); "Add reminder" button adds a default (from `AppPreferences.defaultReminderMins`)
    - Reminder time picker: preset chips (At time / 5 min / 10 min / 15 min / 30 min / 1 hour / 1 day / 2 days) + custom input
    - Multiple reminders supported (ical4j handles multiple `VALARM` components)
    - Swipe-to-delete individual reminders
  - `AlarmScheduler.kt`:
    - `scheduleAlarmsForEvent(event: CalendarEvent, alarms: List<AlarmTrigger>)`: for each `AlarmTrigger`, calls `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, triggerAtMs, pendingIntent)` where `triggerAtMs = event.instanceStart - offsetMs`
    - `cancelAlarmsForEvent(uid: String)`: cancels all `PendingIntent`s for this UID (use `uid + instanceStart` as request code)
    - `rescheduleAll()`: called on `BOOT_COMPLETED` — loads all future `CalendarEvent`s with `VALARM`s from Room, re-schedules all alarms
    - `PendingIntent` targets `AlarmReceiver` (a `BroadcastReceiver`) → fires `NotificationManager` notification
  - `AlarmReceiver.kt`: receives alarm → builds notification: title = event title, text = time + location, tap → opens `EventDetailScreen`; notification channel `eweather_reminders` IMPORTANCE_HIGH
  - **Key files:** `alarm/AlarmScheduler.kt`, `alarm/AlarmReceiver.kt`, updates to `EventEditScreen.kt`
  - **Exit test:** Create event 10 minutes in the future with a 2-minute-before reminder; wait → notification fires at correct time. Reboot device → reminder still fires (BOOT_COMPLETED reschedules).

---

- [x] **Session 4.4 — Event Detail Screen** ✅ Done 2026-03-25
  - `EventDetailScreen.kt`: shows all event fields read-only; entry point for edit and delete
  - Header: event colour as a full-bleed top strip (40dp tall); title in large text below; calendar name + colour dot
  - Fields shown (only if non-empty): date + time (or "All day"); recurrence description ("Weekly on Mon, Wed until Dec 31"); location (with map pin icon; tapping → opens maps intent `geo:lat,lon?q=address` — works without GMS); notes; URL (tappable); attendees (list of emails with their RSVP status if available); reminders; travel time buffer
  - Toolbar: back arrow; edit pencil icon; delete trash icon (with confirmation dialog; for recurring → "Delete this event / All events")
  - For read-only sources (iCloud): edit and delete icons hidden; subtle "This calendar is read-only" banner
  - For Zoho events: edit writes back to Zoho; delete removes from Zoho
  - Shared element: event title animates in from the originating event chip
  - **Key files:** `ui/event/EventDetailScreen.kt`
  - **Exit test:** Tap event in any view → detail screen opens with all fields; edit → `EventEditScreen` pre-filled; delete with confirmation → event removed from all views; read-only iCloud event shows no edit controls

---

**Phase 4 Exit Criteria:** Create a fully-featured recurring event with attendees, custom reminder, travel time, and colour. Edit a single occurrence of a recurring event without affecting others. Delete all occurrences. Everything syncs to Synology.

---

### Phase 5 — Zoho Integration (4 Accounts)

**Goal:** All 4 Zoho CalDAV accounts synced bidirectionally.

---

- [x] **Session 5.1 — Zoho Account Setup** ✅ Done 2026-03-25
  - `AccountsScreen.kt` + `AccountSetupScreen.kt`
  - `AccountsScreen`: list of configured accounts with type icon (Synology/Zoho/iCloud), display name, last synced time, enabled toggle, edit/delete actions; "Add account" FAB
  - `AccountSetupScreen`: tabbed or wizard-style; account type selector (Synology / Zoho / iCloud / CalDAV — generic); for Zoho: email field + app-specific password field + "Verify connection" button
  - "Verify connection" calls `CalDavDiscovery.discoverAccount` → shows discovered calendars list → "Save" stores account + per-calendar `CalendarSource` entries in Room; password stored in `CredentialStore`
  - For Zoho, discovery URL: `https://calendar.zoho.com/caldav/[email]/` — input email, construct URL
  - Multiple Zoho accounts: each is a separate `CalendarAccount` with its own `baseUrl` = `"https://calendar.zoho.com/caldav/$email/"`; separate credentials per account
  - Calendar visibility toggles per account: user can hide individual Zoho calendars from the main view
  - **Key files:** `ui/accounts/AccountsScreen.kt`, `ui/accounts/AccountSetupScreen.kt`, `ui/accounts/AccountSetupViewModel.kt`
  - **Exit test:** Add all 4 Zoho accounts; discovery finds correct calendars for each; all accounts show in AccountsScreen; disable one account → its events disappear from calendar views

---

- [x] **Session 5.2 — Zoho Sync** ✅ Done 2026-03-25
  - `SyncCoordinator.syncAll()` already iterates all accounts — Zoho accounts synced automatically once `CalendarAccount` + `CalendarSource` entries exist in Room
  - Zoho-specific sync consideration: Zoho CalDAV sometimes returns `text/plain` for error responses instead of proper HTTP error codes; add Zoho-specific error detection in `CalDavSyncEngine` — check response body for "No calendar found" or "Invalid credentials" even on 200 responses
  - Zoho write: `EventEditScreen` calendar picker includes Zoho calendars for Zoho-owned events (events where `CalendarSource.accountType == ZOHO`); editing a Zoho event → `UPDATE` queue item targeting the Zoho CalDAV URL directly
  - Colour sync: Zoho stores calendar colour as `X-APPLE-CALENDAR-COLOR` in PROPFIND response (Zoho adopted Apple's extension); parse this in `CalDavDiscovery` and store in `CalendarSourceEntity.colorHex`
  - **Key files:** `caldav/CalDavSyncEngine.kt` (Zoho-specific handling), `sync/SyncCoordinator.kt` (no changes needed if architecture is clean)
  - **Exit test:** All 4 Zoho accounts sync events into Room; create event in Zoho web → appears in app after sync; create event assigned to Zoho calendar in app → appears in Zoho web after sync

---

- [x] **Session 5.3 — Zoho Request Rate Limiter** ✅ Done 2026-03-25
  - Zoho's CalDAV endpoint allows ~500 requests/hour per account. With 4 accounts, each potentially doing per-event `GET` requests during a sync with many changes, this is reachable — especially on first sync with a large Zoho history.
  - `ZohoRateLimiter.kt`: per-account token bucket; 500 requests per rolling 60-minute window; shared across all CalDAV operations for a given Zoho `accountId`
  - ```kotlin
    class ZohoRateLimiter {
        private val requestLog = ArrayDeque<Long>() // timestamps of recent requests
        private val mutex = Mutex()

        suspend fun acquire() {
            mutex.withLock {
                val now = System.currentTimeMillis()
                val windowStart = now - 3_600_000L // 1 hour ago
                // Drop entries older than the window
                while (requestLog.isNotEmpty() && requestLog.first() < windowStart) {
                    requestLog.removeFirst()
                }
                if (requestLog.size >= 490) { // 490 not 500 — leave headroom
                    val oldestInWindow = requestLog.first()
                    val waitMs = (oldestInWindow + 3_600_000L) - now + 1000L
                    // Log: "Zoho rate limit reached — waiting ${waitMs/1000}s"
                    delay(waitMs)
                    requestLog.removeFirst()
                }
                requestLog.addLast(System.currentTimeMillis())
            }
        }
    }
    ```
  - `CalDavClient` for Zoho accounts wraps every HTTP call with `zohoRateLimiter.acquire()` before the request fires; Synology and iCloud subscription fetches bypass the limiter entirely
  - `ZohoRateLimiter` is `@Singleton` per Zoho `accountId` (Hilt keyed map); 4 accounts each have their own independent limiter — one account's rate limit does not block another's
  - When the limiter suspends (waiting for the window to reset), the coroutine simply suspends — it doesn't block a thread; other accounts continue syncing in parallel via `async {}`
  - Limiter state is in-memory only — resets on app restart; this is correct since the Zoho rate limit window is server-side and also resets
  - `SyncCoordinator`: log a `Timber.w` when any Zoho account limiter suspends for > 60s, so it's visible in debug logs during first-launch sync
  - **Key files:** `caldav/ZohoRateLimiter.kt`, `caldav/CalDavClient.kt` (update — inject limiter for Zoho clients only), `di/CalDavModule.kt` (update)
  - **Exit test:** Mock `ZohoRateLimiter` to trigger at 10 requests instead of 490; run a sync that fires 15 requests for one Zoho account; verify the 11th request suspends and resumes after the mock window resets; verify other accounts are unaffected during the wait

---

**Phase 5 Exit Criteria:** All 4 Zoho accounts fully synced. Bidirectional write works. Rate limiter prevents 429 errors on large syncs; limiter suspends gracefully without blocking other accounts or crashing.

---

### Phase 6 — iCloud Integration (iCal Subscription → Synology Mirror)

**Goal:** Subscribe to the shared iCloud calendar via its public iCal URL, mirror all events into a dedicated Synology calendar, and treat them as read-only in the app. Synology remains the single source of truth — Room reads from Synology as it always does.

---

**Data flow:**
```
iCloud subscription URL
        ↓  GET (full .ics file)
  ICalSubscriptionSyncer
        ↓  parse with ical4j
  Auto-create "iCloud – Shared" calendar on Synology if missing
        ↓  DELETE all events in that calendar
        ↓  PUT each event (preserving original UID, ORGANIZER, ATTENDEE fields)
  Synology CalDAV
        ↓  next regular Synology quickSync
  Room  →  all four calendar views
```

The subscription fetch is a separate job from normal Synology sync. It runs first, writes to Synology, then the regular Synology sync picks up those events into Room as if they were any other Synology event — except their `CalendarSource` is the "iCloud – Shared" calendar, which is flagged read-only.

---

- [x] **Session 6.1 — iCal Subscription Fetcher** ✅ Done 2026-03-25
  - **New `AccountType.ICAL_SUBSCRIPTION`** — stored in `AccountEntity`; no username, no password; `baseUrl` holds the `https://` subscription URL
  - **`AccountSetupScreen` for iCal subscription:** single URL field ("Paste your iCloud calendar URL"); display name field (default "iCloud – Shared"); refresh interval selector (1h / 6h / 24h / Manual); note: "Get the URL from Calendar on your Mac → right-click calendar → Get Info → change webcal:// to https://"
  - **`ICalSubscriptionSyncer.kt`:**
    ```kotlin
    suspend fun sync(account: CalendarAccount, targetSynoClient: CalDavClient) {
        // 1. Fetch the full .ics file
        val response = okHttpClient.newCall(
            Request.Builder().url(account.baseUrl).build()
        ).execute()
        if (!response.isSuccessful) return // silent retry next interval
        val icsBody = response.body?.string() ?: return

        // 2. Parse all VEVENTs
        val calendar = CalendarBuilder().build(icsBody.byteInputStream())
        val vevents = calendar.getComponents<VEvent>(Component.VEVENT)

        // 3. Ensure "iCloud – Shared" calendar exists on Synology
        val mirrorCalUrl = ensureMirrorCalendarExists(targetSynoClient, account.displayName)

        // 4. Fetch all existing event hrefs in that calendar
        val existing = targetSynoClient.report(mirrorCalUrl, allEventHrefsBody())
        val existingHrefs = parseHrefs(existing) // list of full URLs

        // 5. DELETE all existing events in the mirror calendar
        existingHrefs.forEach { href ->
            targetSynoClient.delete(href, etag = "*") // wildcard etag = force delete
        }

        // 6. PUT each subscription event
        vevents.forEach { vevent ->
            val uid = vevent.uid?.value ?: UUID.randomUUID().toString()
            val icsPayload = wrapInVCalendar(vevent) // VCALENDAR wrapper around single VEVENT
            targetSynoClient.put(
                url = "$mirrorCalUrl${uid}.ics",
                icsContent = icsPayload,
                etag = null // If-None-Match: * not used here — we just deleted everything
            )
        }
    }
    ```
  - `ensureMirrorCalendarExists(client, name)`: `PROPFIND` the Synology calendar home → check if a calendar with `displayName == name` exists → if not, `MKCALENDAR` to create it → return its URL. `MKCALENDAR` is standard CalDAV (RFC 4791 §5.3.1): `Request.Builder().method("MKCALENDAR", body)` with an XML body setting `DAV:displayname` and `apple:calendar-color`
  - `wrapInVCalendar(vevent)`: wraps a single `VEvent` in a `VCALENDAR` with correct `PRODID` and `VERSION:2.0` — required for valid iCalendar format on PUT
  - Subscription sync runs as a **separate `PeriodicWorkRequest`** (`ICalSubscriptionWorker`) with the user-selected interval; it runs *before* the regular `SyncWorker` in a `WorkContinuation` chain: `WorkManager.getInstance().beginWith(subscriptionWork).then(regularSyncWork).enqueue()` — ensures Synology has the latest mirrored events before Room reads them
  - **Key files:** `caldav/ICalSubscriptionSyncer.kt`, `sync/ICalSubscriptionWorker.kt`, `ui/accounts/AccountSetupScreen.kt` (update)
  - **Exit test:** Paste subscription URL → save → sync runs → Synology web UI shows "iCloud – Shared" calendar auto-created with all events inside → next regular Synology sync → events appear in all four app views with correct times and recurrences

---

- [x] **Session 6.2 — Mirror Calendar Read-Only Enforcement** ✅ Done 2026-03-25
  - After `ensureMirrorCalendarExists` creates (or finds) the mirror calendar, store its `CalendarSource` in Room with `isReadOnly = true` and a new flag `isMirror = true` (add column to `CalendarSourceEntity`)
  - `isMirror` flag is the signal throughout the app that this calendar's events are managed by the subscription syncer — not by the user
  - `EventDetailScreen`: events whose `calendarSourceId` points to a mirror source → no edit button, no delete button; footer note: "Synced from iCloud — edit on your Mac. Updates every [interval]."
  - `EventEditScreen` calendar picker: mirror calendars never appear as destinations
  - `OfflineQueueProcessor`: skip `CREATE`, `UPDATE`, `DELETE` operations targeting a mirror calendar's URL — belt-and-suspenders guard against any accidental write attempt
  - Full-replace on each sync means no conflict is possible — the mirror calendar is always exactly what the subscription says it is; any manual edits made to it in Synology web UI will be silently overwritten on next sync (this is intentional and correct)
  - `AccountsScreen` shows iCal subscription accounts with a distinct icon (chain-link or subscription icon) and "Last mirrored: X ago" timestamp; "Sync now" button triggers `ICalSubscriptionWorker` immediately
  - **Key files:** `db/entity/CalendarSourceEntity.kt` (add `isMirror` column, migration), `ui/event/EventDetailScreen.kt` (update), `ui/event/EventEditScreen.kt` (update), `sync/OfflineQueueProcessor.kt` (guard), `ui/accounts/AccountsScreen.kt` (update)
  - **Exit test:** Add event to shared calendar on MacBook → trigger sync → event appears in app; `EventDetailScreen` shows no edit controls and shows the "edit on your Mac" footer; attempt to call `CalendarRepository.updateEvent` on a mirror event → `OfflineQueueProcessor` skips it silently; mirror calendar does not appear in `EventEditScreen` calendar picker

---

**Phase 6 Exit Criteria:** All shared iCloud events mirrored to a dedicated Synology calendar and visible in all four app views. "iCloud – Shared" calendar auto-created on Synology on first sync. Events are read-only throughout the app. Adding events on the Mac → appears in app within one sync cycle. Synology remains the single source of truth — Room never reads directly from the subscription URL.

---

### Phase 7 — Thunderbird RSVP + ICS Import

**Goal:** Tap a calendar invite in Thunderbird → it opens in the app → you save it to Synology + generate a reply for Thunderbird to send.

---

- [x] **Session 7.1 — ICS Intent Handler** ✅ Done 2026-03-25
  - Register `eCalendar` as a handler for `text/calendar` and `application/ics` MIME types in `AndroidManifest.xml`:
    ```xml
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/calendar" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="application/ics" />
    </intent-filter>
    ```
  - `MainActivity` handles `intent.action == ACTION_VIEW` with `text/calendar` MIME on `onNewIntent` and `onCreate`: reads the ICS file from `intent.data` (URI) via `contentResolver.openInputStream`; parses with `ICalParser`; navigates to `IcsImportScreen`
  - `IcsImportScreen.kt`: shows the event details read-only (title, date/time, organiser, attendees, location, notes); "Calendar" picker to choose which Synology calendar to save to; "Save to Calendar" button; "Decline" button
  - On "Save": calls `CalendarRepository.createEvent` with the parsed data → saved to Synology; then navigates to `RsvpScreen`
  - On "Decline": skips to `RsvpScreen` with `PartStat.DECLINED`
  - If the ICS already has a matching `UID` in Room (duplicate invite): show "This event is already in your calendar — Update it?" instead
  - **Key files:** `ui/ics/IcsImportScreen.kt`, updates to `ui/MainActivity.kt`
  - **Exit test:** Send a `.ics` file to the phone via Thunderbird; tap attachment; system shows app picker including eCalendar; select eCalendar; `IcsImportScreen` shows correct event details; save → event in Room + Synology queue

---

- [x] **Session 7.2 — RSVP Reply Generation + Thunderbird Intent** ✅ Done 2026-03-25
  - `RsvpScreen.kt`: shown after ICS import; "Accept / Tentative / Decline" button row; shows reply preview: "Your reply to [organiser@email.com]"
  - On button tap:
    1. `RsvpGenerator.generateRsvpReply(originalIcs, myEmail, partStat)` → produces reply ICS string
    2. Write reply ICS to a temp file in `cacheDir/rsvp/[uid]-reply.ics` via `FileProvider`
    3. Build `ACTION_SEND` intent:
       ```kotlin
       Intent(Intent.ACTION_SEND).apply {
           type = "message/rfc822"
           putExtra(Intent.EXTRA_EMAIL, arrayOf(organizerEmail))
           putExtra(Intent.EXTRA_SUBJECT, "Re: ${event.title}")
           putExtra(Intent.EXTRA_TEXT, replyBody) // plain text "I have accepted..."
           putExtra(Intent.EXTRA_STREAM, fileProviderUri) // the .ics file
           addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
       }
       startActivity(Intent.createChooser(intent, "Send reply via"))
       ```
    4. Thunderbird appears in the chooser; user taps it → Thunderbird opens a compose window with the reply pre-filled + ICS attached; user reviews + taps Send
  - `FileProvider` declared in manifest with `android:authorities="dev.ecalendar.fileprovider"`; `res/xml/file_paths.xml` exposes `cacheDir/rsvp/`
  - `replyBody` text: "I have accepted / tentatively accepted / declined the invitation to [event title] on [date]."
  - After triggering the intent: navigate back to the calendar view; show `Snackbar` "RSVP sent via Thunderbird"
  - **Key files:** `ui/ics/RsvpScreen.kt`, `ical/RsvpGenerator.kt` (already stubbed), `res/xml/file_paths.xml`, manifest update
  - **Exit test:** Full flow — receive `.ics` via Thunderbird; tap → eCalendar opens; save + accept → Thunderbird compose opens with correct `To:`, subject, body, and `.ics` attachment; verify the `.ics` attachment contains `METHOD:REPLY` and `PARTSTAT:ACCEPTED`

---

**Phase 7 Exit Criteria:** Full Thunderbird → eCalendar → Thunderbird flow works end-to-end. ICS imported to correct calendar. RSVP reply opens Thunderbird compose with correct attachment. Event appears in all views.

---

### Phase 8 — Notifications

**Goal:** Reliable, precise reminders that work even after reboot.

---

- [x] **Session 8.1 — Alarm Scheduling System** ✅ Done 2026-03-25
  - `AlarmScheduler.kt` (from Phase 4.3 stub) — full implementation:
    - After any sync or local event create/update: call `scheduleAlarmsForEvent` for all future events with VALARMs
    - Only schedule alarms up to 30 days in the future; daily WorkManager job re-schedules the next batch
    - API 33+: use `AlarmManager.setExactAndAllowWhileIdle` — `USE_EXACT_ALARM` permission grants this without user approval; no permission check needed at runtime
    - API 29–32: check `AlarmManager.canScheduleExactAlarms()` first; if false, show a one-time prompt in `SettingsScreen` directing the user to grant the permission; if true, proceed with `setExactAndAllowWhileIdle`
    - **Battery optimisation exemption (GrapheneOS):** on first launch, check `PowerManager.isIgnoringBatteryOptimizations(packageName)`; if false, show a one-time dialog: "For reliable reminders, eCalendar needs to be excluded from battery optimisation" with a button that fires `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent; without this, GrapheneOS can defer exact alarms even with `USE_EXACT_ALARM`
    - `PendingIntent` uses `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`; request code = `(uid.hashCode() xor instanceStart.toInt())` for uniqueness
  - `AlarmReceiver.kt`:
    - Fires notification: channel `ecalendar_reminders` (IMPORTANCE_HIGH); title = event title; text = formatted time + location (if set); full timestamp as `setWhen()`
    - Two actions on notification: "Open" (→ `EventDetailScreen`) and "Snooze 10min" (re-schedules alarm 10 min later)
    - Notification ID = `abs(uid.hashCode() xor instanceStart.toInt())`
  - `BootReceiver.kt`: `BroadcastReceiver` for `BOOT_COMPLETED`; calls `AlarmScheduler.rescheduleAll()` + re-enqueues `SyncWorker` periodic job
  - **Key files:** `alarm/AlarmScheduler.kt`, `alarm/AlarmReceiver.kt`, `alarm/BootReceiver.kt`
  - **Exit test:** Create event 3 minutes ahead with 1-minute-before reminder; notification fires at T-1min with correct title; tap "Snooze 10min" → re-fires 10min later; reboot device → reminder still fires from `BOOT_COMPLETED`

---

- [x] **Session 8.2 — Notification Channels + Settings** ✅ Done 2026-03-25
  - `NotificationHelper.kt`: creates channels on app init:
    - `ecalendar_reminders` — IMPORTANCE_HIGH, "Event Reminders", vibration on
    - `ecalendar_sync_errors` — IMPORTANCE_DEFAULT, "Sync Issues" — for persistent sync failure alerts
  - `SettingsScreen` notification section: notifications enabled toggle; default reminder time picker; "Exact alarm permission" row (shows current status + deep-link to system settings if not granted)
  - Sync error notification: if `SyncQueueItem` reaches `retryCount >= 10` → fire a `ecalendar_sync_errors` notification "Failed to sync [event title] — tap to retry"; tapping → opens the event for re-save
  - **Key files:** `alarm/NotificationHelper.kt`, `ui/settings/SettingsScreen.kt` (update)
  - **Exit test:** Disable exact alarm permission → `SettingsScreen` shows warning; re-enable → reminders work. Force a sync failure to max retries → sync error notification fires.

---

**Phase 8 Exit Criteria:** Reminders fire at exactly the right time. Work in Doze mode. Survive reboots. Snooze works. Sync error notifications surface failed items clearly.

---

### Phase 9 — Polish, Settings & Deploy

**Goal:** Production-quality feel, complete settings, sustainable background behaviour.

---

- [x] **Session 9.1 — Settings Screen (Complete)** ✅ Done 2026-03-25
  - `SettingsScreen.kt` — complete implementation:
    - **General**: first day of week (Monday/Sunday); time format (12h/24h); default calendar (picker from all writable sources); default reminder time
    - **Accounts**: link to `AccountsScreen` (Phase 5.1); sync interval (15min / 30min / 1h / 2h / Manual); "Sync now" button with last-synced timestamp
    - **Notifications**: reminders toggle; battery optimisation exemption status + prompt button if not granted; API <33 exact alarm permission status
    - **Appearance**: dark/light/system theme; event colour display (calendar colour / event colour / mixed)
    - **About**: app version; "Data stays on your devices — no cloud service"; attribution ("Calendar data synced via CalDAV"; "Recurrence powered by ical4j")
  - **Key files:** `ui/settings/SettingsScreen.kt`
  - **Exit test:** All settings persist across app kill/restart; changing sync interval updates WorkManager schedule; "Sync now" triggers sync and updates last-synced time

---

- [x] **Session 9.2 — UI Polish + Animations** ✅ Done 2026-03-25
  - Transition polish audit: verify shared element on all event tap flows; add `spring(dampingRatio = 0.8f, stiffness = 400f)` to all navigation transitions
  - FAB: `AnimatedVisibility` hide/show when scrolling in Agenda view (hide on scroll down, show on scroll up)
  - Event creation: `BottomSheetScaffold` with `dragHandle` — feels native; keyboard avoidance via `WindowInsets.ime`
  - Calendar swipe feedback: `rememberSwipeableState` with velocity threshold — fast swipe = advance; slow swipe = snap back
  - Today button in `CalendarHeader`: pulse animation (scale 1.0 → 1.1 → 1.0) when tapped if already on today
  - Empty states: each view has a friendly empty state with a small calendar illustration (SVG via `Canvas`) and helpful copy
  - Loading skeleton: shimmer placeholders for event chips during initial data load (same `ShimmerBox` pattern as eMusic)
  - Dark mode: all colours via `MaterialTheme.colorScheme` tokens; event colours have dark variants from `ColorPalette`
  - **Key files:** All UI files (polish pass); `ui/components/ShimmerBox.kt`
  - **Exit test:** 60fps on all views (`adb shell dumpsys gfxinfo`); all transitions smooth; dark mode looks polished; empty states readable and helpful

---

- [x] **Session 9.3 — Error Handling + Edge Cases** ✅ Done 2026-03-25
  - Tailscale disconnected: Synology operations fail gracefully → queue items accumulate → `OfflineQueueProcessor` retries on reconnect; no crash; banner "Working offline — changes will sync when connected"
  - iCloud share revoked: next sync returns empty calendar or 403 → emit `CalendarSource.syncError = "Access revoked"` → show in `AccountsScreen` as "⚠ Cannot access shared calendar"; don't delete existing events (they stay visible but marked as stale)
  - Zoho app-specific password expired: 401 response → `AccountsScreen` shows "⚠ Authentication failed — update credentials"; tapping opens `AccountSetupScreen` pre-filled with account details
  - Malformed ICS from server: ical4j throws `ParserException` → catch, log, skip that event, continue sync; never crash
  - RSVP `.ics` file too large: `FileProvider` limited to `cacheDir` which is cleared by system; clean up old reply files on each launch (`cacheDir/rsvp/*.ics` older than 7 days)
  - Recurrence expansion takes too long (edge case: daily event with 10-year history): cap expansion at 500 instances per series; show "Showing first 500 occurrences" in `EventDetailScreen`
  - **Key files:** All repository + sync files (error handling audit)
  - **Exit test:** Force each error condition; verify app doesn't crash; correct error state shown in `AccountsScreen`; sync resumes correctly after error is resolved

---

- [x] **Session 9.4 — Deploy Script + Logging** ✅ Done 2026-03-25
  - `scripts/deploy.sh`: build release → sign → `adb install -r`; same pattern as eMusic; keystore from `local.properties`
  - `Timber` in debug only; release ProGuard strips all log calls
  - Fatal crash handler: writes to `filesDir/crash.log`
  - **Key files:** `scripts/deploy.sh`
  - **Exit test:** `./scripts/deploy.sh` builds and installs; release build has no log output

---

**Phase 9 Exit Criteria:** App is complete, polished, and production-ready. All error states handled. Settings all persist. Deploy script works. 60fps confirmed on all views.

---

## Claude Code Execution Notes

1. **No GMS** — `android.location.LocationManager` only; no `FusedLocationProviderClient`; no `GCMNetworkManager`
2. **ical4j Android config is critical** — always include `ical4j.properties` with `MapTimeZoneCache`; forgetting this crashes on device
3. **CalDAV is just HTTP** — use OkHttp directly; no external CalDAV library; custom method names via `Request.Builder().method("PROPFIND", body)`
4. **Single source of truth** — Room; API data always flows through Room before UI
5. **Synology is primary** — all new events go to Synology; Zoho is read/write but Zoho-authoritative; iCloud is `ICAL_SUBSCRIPTION` — fetched via HTTP GET, mirrored to Synology, never written to directly; `OfflineQueueProcessor` skips `ICAL_SUBSCRIPTION` accounts entirely
6. **Test commands:** `./gradlew lint && ./gradlew testDebug && adb install -r`
7. **minSdk 29** — use modern APIs freely

---

## Changelog

| Date | Change |
|---|---|
| 2026-03-23 | Initial plan — Phase 0 (Synology setup) + 9 build phases, 35 sessions |
| 2026-03-23 | Applied tricky-issue fixes: ical4j full ProGuard rules + CalendarBuilder thread safety; RECURRENCE-ID post-push quickSync; USE_EXACT_ALARM + GrapheneOS battery optimisation prompt; Zoho rate limiter (Session 5.3); recurrence hard cap + cleanup job |
| 2026-03-23 | Fixed four consistency issues: stale iCloud CalDAV note removed; AccountType enum corrected to SYNOLOGY/ZOHO/ICAL_SUBSCRIPTION; Phase 0 Step 6 replaced with iCal URL instructions; ZohoRateLimiter added to project structure |
