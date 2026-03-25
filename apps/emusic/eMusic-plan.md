# eMusic — Master Build Plan
> GrapheneOS · Navidrome (Subsonic API) · Tailscale · Full Offline Support
> Last updated: 2026-03-17

---

## Confirmed Decisions

| Concern | Decision |
|---|---|
| **Connection** | Tailscale subnet routing — Navidrome accessed via static LAN IP (e.g. `192.168.1.x`), no special handling needed beyond storing the URL in settings |
| **Offline** | Full download + offline playback — songs, artwork, and metadata all cached to app-scoped storage |
| **Target** | GrapheneOS, sideloaded APK, single user, no GMS |
| **API** | Subsonic REST (Navidrome is fully compatible) |
| **Build tool** | Gradle KTS, Kotlin, Jetpack Compose, Media3 |

---

## Tech Stack

| Layer | Library | Notes |
|---|---|---|
| Language | Kotlin 2.x | |
| UI | Jetpack Compose + Material 3 | Latest stable |
| Architecture | MVVM + Clean Architecture + UDF | |
| Playback | Media3 (ExoPlayer + MediaSession) | Latest stable |
| Networking | Retrofit 2 + OkHttp 4 + Kotlin serialization | |
| Local DB | Room | Latest stable |
| DI | Hilt | Latest stable |
| Async | Coroutines + Flow + StateFlow | |
| Image loading | Coil 3 (Compose) | Latest stable |
| Preferences | DataStore (Proto) | |
| Background work | WorkManager | |
| Download manager | Custom via OkHttp | No system DownloadManager — avoid it |
| Paging | Paging 3 | Large list virtualisation — required for 15k+ track library |
| Animations | Compose Animation + Spring physics | Spotify-like motion, no third-party anim lib needed |

> **No GMS dependencies anywhere.** Validate every transitive dependency before adding.

---

## Subsonic API — Key Endpoints Reference

| Feature | Endpoint |
|---|---|
| Auth | `ping`, `getLicense` |
| Library | `getArtists`, `getAlbumList2`, `getSongs` |
| Search | `search3` |
| Playlists | `getPlaylists`, `getPlaylist`, `createPlaylist`, `updatePlaylist`, `deletePlaylist` |
| Playback URL | `stream` (with `maxBitRate`, `format` params) |
| Cover Art | `getCoverArt` |
| Radio / Similar | `getSimilarSongs2`, `getTopSongs` |
| Starring | `star`, `unstar`, `getStarred2` |
| Scrobble | `scrobble` (track history) |
| Random | `getRandomSongs` |
| Suggestions | `getAlbumList2?type=recent\|frequent\|newest\|starred` |
| Lyrics | `getLyrics(artist, title)` — returns plain text or synced LRC |
| Track rating | `setRating(id, rating)` — 0 (remove) to 5 stars |
| Top rated | `getAlbumList2?type=highest` |
| Artist info | `getArtistInfo2(id)` — bio, similar artists, image URLs (pulls from Last.fm) |
| Album info | `getAlbumInfo2(id)` — notes, MusicBrainz ID |

> **Auth method:** MD5 token — `token = md5(password + salt)`, pass `u`, `t`, `s`, `v`, `c` on every request. Never plaintext password in URL.

---

## Project Structure

```
eMusic/
├── app/
│   └── src/main/
│       ├── data/
│       │   ├── api/
│       │   │   ├── SubsonicApiService.kt        ← Retrofit interface
│       │   │   ├── SubsonicAuthInterceptor.kt   ← MD5 token auth
│       │   │   ├── dto/                         ← Raw API response DTOs
│       │   │   └── SubsonicApiMapper.kt         ← DTO → Domain model
│       │   ├── db/
│       │   │   ├── AppDatabase.kt
│       │   │   ├── dao/                         ← TrackDao, AlbumDao, etc.
│       │   │   └── entity/                      ← Room entities
│       │   ├── download/
│       │   │   ├── DownloadManager.kt           ← Queue + execute downloads
│       │   │   ├── DownloadWorker.kt            ← WorkManager worker
│       │   │   └── DownloadRepository.kt
│       │   ├── paging/                          ← LibraryPagingSource.kt (Paging 3)
│       │   ├── radio/
│       │   │   ├── RadioBrowserApiService.kt    ← Radio Browser REST API
│       │   │   ├── RadioBrowserDto.kt           ← Station DTOs
│       │   │   └── RadioBrowserMapper.kt        ← DTO → RadioStation domain model
│       │   └── repository/                      ← Concrete repo implementations
│       ├── domain/
│       │   ├── model/
│       │   │   ├── Track.kt
│       │   │   ├── Album.kt
│       │   │   ├── Artist.kt
│       │   │   ├── ArtistInfo.kt                ← Bio, similar artists, image
│       │   │   ├── AlbumInfo.kt                 ← Notes, MusicBrainz ID
│       │   │   ├── Lyrics.kt                    ← Plain text + parsed LRC lines
│       │   │   ├── Playlist.kt
│       │   │   ├── RadioSeed.kt
│       │   │   ├── DownloadState.kt
│       │   │   └── RadioStation.kt
│       │   ├── repository/                      ← Interfaces only
│       │   │   ├── LibraryRepository.kt
│       │   │   ├── PlaylistRepository.kt
│       │   │   ├── DownloadRepository.kt
│       │   │   ├── RadioRepository.kt
│       │   │   └── InternetRadioRepository.kt   ← Radio Browser + favourites
│       │   └── usecase/
│       │       ├── GetHomeScreenUseCase.kt
│       │       ├── StartRadioUseCase.kt
│       │       ├── SyncLibraryUseCase.kt
│       │       ├── DownloadTrackUseCase.kt
│       │       └── GetSuggestionsUseCase.kt
│       ├── playback/
│       │   ├── PlaybackService.kt               ← MediaSessionService (foreground)
│       │   ├── QueueManager.kt
│       │   ├── RadioEngine.kt                   ← Dynamic queue refill logic
│       │   ├── OfflineStreamResolver.kt         ← Local file vs stream URL decision
│       │   ├── NotificationHelper.kt            ← Channel setup, heads-up config
│       │   ├── SleepTimerManager.kt             ← Countdown, fade-out, notification
│       │   ├── IcyMetadataHandler.kt            ← Extracts live now-playing from stream
│       │   └── BatteryAwareQualityManager.kt
│       └── ui/
│           ├── MainActivity.kt
│           ├── navigation/
│           │   └── NavGraph.kt
│           ├── home/
│           ├── library/
│           │   ├── artists/
│           │   ├── albums/
│           │   └── tracks/
│           ├── player/
│           │   ├── NowPlayingScreen.kt
│           │   └── QueueScreen.kt
│           ├── playlists/
│           ├── internetradio/
│           │   ├── RadioBrowseScreen.kt         ← Country picker + station grid
│           │   ├── RadioSearchScreen.kt         ← Global station search
│           │   ├── RadioFavouritesScreen.kt     ← Starred stations
│           │   └── RadioNowPlayingOverlay.kt    ← Adapted Now Playing for live streams
│           ├── downloads/
│           ├── search/
│           ├── stats/
│           │   └── StatsScreen.kt               ← Most played, top artists, listening history
│           ├── settings/
│           │   └── equalizer/
│           │       └── EqualizerScreen.kt        ← 5-band EQ + bass boost
│           └── components/                      ← Shared Compose components
```

---

## How to Use This Plan with Claude Code

Each **Session** below is one Claude Code working block. At the start of every session, paste this into Claude Code:

```
This is session X.Y of the eMusic Android app build.
Reference file: eMusic-plan.md (attached or pasted).
Previous sessions are complete. Build only what is listed under session X.Y.
Do not refactor earlier sessions unless explicitly listed as a deliverable.
```

**Session sizing:** Each session produces 1–4 files or a single coherent feature. If Claude Code seems to be going wide, stop it and split the session.

---

## Phase Breakdown

### Status Key
- `[ ]` Not started
- `[~]` In progress
- `[x]` Complete

---

### Phase 1 — Foundation & Playback Core
**Goal:** Connect to Navidrome, browse the library, play a track end-to-end.

---

- [x] **Session 1.1 — Gradle Project Setup**
  - New Android project: `applicationId = "dev.emusic"`, minSdk 29, targetSdk 35, compileSdk 35
  - `libs.versions.toml` with all dependencies pinned (Kotlin 2.x, Compose BOM latest stable, Media3, Retrofit 2, OkHttp 4, Room, Hilt, Coil 3, DataStore Proto, WorkManager, Paging 3, Kotlin serialization, EncryptedSharedPreferences)
  - `build.gradle.kts` (app): KSP for Room + Hilt, Compose compiler enabled, R8/ProGuard rules for Retrofit + Room + Kotlin serialization
  - `AndroidManifest.xml`: `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`
  - `EMusicApp.kt`: `@HiltAndroidApp Application` subclass
  - **Key files:** `libs.versions.toml`, `app/build.gradle.kts`, `AndroidManifest.xml`, `EMusicApp.kt`
  - **Exit test:** `./gradlew assembleDebug` — clean build with zero errors and zero warnings

---

- [x] **Session 1.2 — Domain Models**
  - Pure Kotlin data classes, no Android or Room imports: `Track`, `Album`, `Artist`, `ArtistInfo`, `AlbumInfo`, `Playlist`, `DownloadState` (sealed), `QueueItem`
  - `Track` must include: `id`, `title`, `artist`, `artistId`, `album`, `albumId`, `duration`, `trackNumber`, `year`, `genre`, `starred`, `playCount`, `userRating: Int?`, `localPath: String?`, `trackGain: Float?`, `albumGain: Float?`, `trackPeak: Float?`, `albumPeak: Float?`
  - **Key files:** `domain/model/*.kt` (7 files)
  - **Exit test:** `./gradlew testDebug` — no compilation errors; models are fully serializable to/from JSON by hand-written unit test

---

- [x] **Session 1.3 — Subsonic API Layer**
  - `SubsonicAuthInterceptor.kt`: adds `u`, `t` (md5(password+salt)), `s` (random salt), `v=1.16.1`, `c=emusic`, `f=json` to every request; password read from DataStore
  - `SubsonicApiService.kt`: Retrofit interface — declare all endpoints from the API reference table (`ping`, `getArtists`, `getAlbumList2`, `getArtist`, `getAlbum`, `search3`, `stream`, `getCoverArt`, `getSimilarSongs2`, `getTopSongs`, `star`, `unstar`, `getStarred2`, `scrobble`, `getRandomSongs`, `getLyrics`, `setRating`, `getArtistInfo2`, `getAlbumInfo2`, `getPlaylists`, `getPlaylist`, `createPlaylist`, `updatePlaylist`, `deletePlaylist`)
  - DTOs in `data/api/dto/`: `SubsonicResponse<T>`, `ArtistListDto`, `AlbumDto`, `TrackDto` (Child), `PlaylistDto`, `ArtistInfoDto`, `LyricsDto`
  - `SubsonicApiMapper.kt`: DTO → domain model for all types; all fields nullable-safe
  - Hilt module providing `OkHttpClient` (with interceptor), `Retrofit`, `SubsonicApiService`; server URL injected from DataStore `serverUrlFlow`
  - `getCoverArtUrl(id, size)` and `getStreamUrl(id, maxBitRate)` — URL builder helpers (not Retrofit calls)
  - **Key files:** `SubsonicAuthInterceptor.kt`, `SubsonicApiService.kt`, `dto/*.kt`, `SubsonicApiMapper.kt`, `di/NetworkModule.kt`
  - **Exit test:** Unit test: `SubsonicAuthInterceptor` produces correct MD5 token for a known password+salt pair. `./gradlew testDebug` passes.

---

- [x] **Session 1.4 — Room Database**
  - Entities: `TrackEntity`, `AlbumEntity`, `ArtistEntity`, `PlaylistEntity`, `PlaylistTrackCrossRef` — all fields match domain models; explicit `@Index` on every FK + every column used in WHERE/ORDER BY (see index spec in Confirmed Decisions)
  - `TrackFtsEntity`: FTS5 virtual table with `unicode61` tokeniser; columns: `rowid` (references TrackEntity), `title`, `artist`, `album`, `genre`; Room triggers keep it in sync with `TrackEntity` inserts/updates/deletes
  - DAOs: `TrackDao`, `AlbumDao`, `ArtistDao`, `PlaylistDao` — all return `Flow<T>` for reactive queries; include both `Flow` and `PagingSource<Int, T>` variants for all list queries
  - `AppDatabase.kt`: `@Database` version 1, lists all entities including FTS; `TypeConverters` for `List<String>` (genres)
  - Migration strategy: `fallbackToDestructiveMigration()` at v1 (document: replace with real migrations from v2 onwards)
  - Hilt module providing `AppDatabase` and all DAOs
  - **Key files:** `db/entity/*.kt`, `db/dao/*.kt`, `db/AppDatabase.kt`, `di/DatabaseModule.kt`
  - **Exit test:** `./gradlew testDebug` — Room schema compile-time verification passes; DAO unit tests for insert + query on `TrackEntity` and FTS match query both pass

---

- [x] **Session 1.5 — DataStore + Preferences**
  - Proto DataStore: define `app_preferences.proto` — fields: `serverUrl: string`, `username: string`, `maxBitrate: int32`, `wifiOnlyDownloads: bool`, `forceOfflineMode: bool`, `scrobblingEnabled: bool`, `headsUpNotificationsEnabled: bool`
  - `EncryptedSharedPreferences` wrapper (`CredentialStore.kt`) for password only — NOT stored in DataStore
  - `AppPreferencesRepository.kt`: wraps DataStore, exposes typed `Flow<AppPreferences>` and `suspend fun update*` methods
  - `NetworkMonitor.kt`: uses `ConnectivityManager.NetworkCallback` → `StateFlow<Boolean>` (isOnline); integrates `forceOfflineMode` preference
  - Hilt module providing DataStore and `AppPreferencesRepository`
  - **Key files:** `app_preferences.proto`, `CredentialStore.kt`, `AppPreferencesRepository.kt`, `NetworkMonitor.kt`, `di/PreferencesModule.kt`
  - **Exit test:** `./gradlew testDebug` — unit test writes serverUrl to DataStore, reads it back; `CredentialStore` stores and retrieves password without crashing

---

- [x] **Session 1.6 — Repository Layer**
  - Domain repository interfaces: `LibraryRepository`, `PlaylistRepository` (in `domain/repository/`)
  - `LibraryRepositoryImpl`: all API calls flow through Room (API → upsert Room → emit from Room); never emits API data directly to callers
    - `syncArtists()`, `syncAlbums()`, `syncTracks()` — suspend functions for one-shot sync
    - `observeArtists(): Flow<PagingData<Artist>>`, `observeAlbums(…): Flow<PagingData<Album>>`, `observeTracks(albumId): Flow<List<Track>>` — Room-backed reactive streams
    - `getStreamUrl(trackId, maxBitRate): String`, `getCoverArtUrl(id, size): String` — URL helpers delegating to mapper
    - `starTrack(id)`, `unstarTrack(id)` — optimistic Room update then API call
  - `PlaylistRepositoryImpl`: `syncPlaylists()`, `observePlaylists(): Flow<List<Playlist>>`, `createPlaylist()`, `updatePlaylist()`, `deletePlaylist()`
  - **Key files:** `domain/repository/LibraryRepository.kt`, `domain/repository/PlaylistRepository.kt`, `data/repository/LibraryRepositoryImpl.kt`, `data/repository/PlaylistRepositoryImpl.kt`, `di/RepositoryModule.kt`
  - **Exit test:** `./gradlew testDebug` — integration test: mock `SubsonicApiService` returns fake albums → `syncAlbums()` → `observeAlbums()` emits them from Room

---

- [x] **Session 1.7 — PlaybackService + QueueManager**
  - `PlaybackService.kt`: extends `MediaSessionService`; creates and holds `ExoPlayer` + `MediaSession`; starts as foreground service on `onStartCommand`; handles `ACTION_AUDIO_BECOMING_NOISY`; stops self when queue finishes and playback has been paused for 30 min
  - `QueueManager.kt`: wraps ExoPlayer `MediaItem` list; `add(track)`, `addNext(track)`, `remove(index)`, `moveItem(from, to)`, `shuffle()`, `clear()`; exposes `currentTrack: StateFlow<Track?>`, `queue: StateFlow<List<QueueItem>>`; persists track IDs + position to DataStore on every change, restores on service start
  - `OfflineStreamResolver.kt`: `resolveUri(track): Uri` — checks `track.localPath`, verifies file exists; returns `Uri.fromFile()` if available, else constructs Subsonic `stream` URL via `getCoverArtUrl`
  - Service declared in manifest with `android:foregroundServiceType="mediaPlayback"`, exported with `MediaSessionService` intent filter
  - `MediaController` factory method in a `@Singleton` Hilt binding (UI layer uses this to control playback — never binds to service directly)
  - **Key files:** `playback/PlaybackService.kt`, `playback/QueueManager.kt`, `playback/OfflineStreamResolver.kt`, `di/PlaybackModule.kt`
  - **Exit test:** Manual — install APK, background the app, confirm playback notification appears and lock screen controls work

---

- [x] **Session 1.8 — Notification System**
  - `NotificationHelper.kt`: creates 3 channels on `Application.onCreate`: `emusic_playback` (IMPORTANCE_LOW), `emusic_headsup` (IMPORTANCE_HIGH), `emusic_download` (IMPORTANCE_DEFAULT)
  - Override `DefaultMediaNotificationProvider` in `PlaybackService`: load album art via Coil into `notificationBitmap` async; set as large icon; include prev/play-pause/next actions
  - Expanded notification (API 33+): add scrub `setProgress(duration, position, false)` updated on 1s ticker in service
  - `setOngoing(true)` while playing; `setOngoing(false)` when paused (allows swipe-dismiss)
  - Heads-up on track change: fire one-shot `emusic_headsup` notification with `setTimeoutAfter(4000)`; check `NotificationManager.currentInterruptionFilter` before firing — skip if DND active; only fire on explicit skip or radio auto-advance, not on first play
  - All `PendingIntent`s: `FLAG_IMMUTABLE` flag required (API 31+)
  - **Key files:** `playback/NotificationHelper.kt` (updated), modifications to `PlaybackService.kt`
  - **Exit test:** Manual — play a track, verify silent persistent notification; skip track, verify heads-up appears and auto-dismisses in ~4s; enable DND, skip track, verify no heads-up fires

---

- [x] **Session 1.9 — UI Navigation Shell**
  - `MainActivity.kt`: single-activity, `@AndroidEntryPoint`; sets up `NavHost` with `rememberNavController()`
  - `NavGraph.kt`: sealed `Screen` routes — `Home`, `Library`, `ArtistDetail(artistId)`, `AlbumDetail(albumId)`, `NowPlaying`, `Search`, `Downloads`, `Settings`; all non-terminal screens start as placeholder `Box(Modifier.fillMaxSize())`
  - Bottom navigation bar: 5 tabs — Home, Library, Search, Downloads, Settings; `NavigationBar` + `NavigationBarItem`; correct selected-state highlighting
  - `MiniPlayerBar.kt`: persistent bar above bottom nav; shows when `QueueManager.currentTrack != null`; `AnimatedVisibility(slideInVertically)`; displays album art thumbnail (Coil), track title (marquee), artist, play/pause button, skip next — tapping bar navigates to `NowPlaying`
  - `Scaffold` in `MainActivity` with `bottomBar` = `Column { MiniPlayerBar(); NavigationBar() }`
  - **Key files:** `ui/MainActivity.kt`, `ui/navigation/NavGraph.kt`, `ui/components/MiniPlayerBar.kt`
  - **Exit test:** App installs, all 5 tabs tap without crash; mini-player appears when a track is playing; tapping it navigates to NowPlaying

---

- [x] **Session 1.10 — Now Playing Screen**
  - `NowPlayingScreen.kt`: full-screen composable bound to `NowPlayingViewModel`
  - `NowPlayingViewModel`: observes `QueueManager.currentTrack`, `PlaybackService` play state, position (1s polling via `timerFlow`); exposes `UiState` sealed class
  - Layout: full-bleed album art (square, Coil `AsyncImage` with `crossfade(300)`); track title (`displaySmall`), artist name; scrub bar (`Slider`); play/pause, prev, next buttons; shuffle toggle, repeat toggle (Off/One/All); overflow menu (Add to playlist, Rate, Go to Artist)
  - Background: Palette API extracts dominant colour from album art → soft radial gradient, updates with `animateColorAsState(300ms)` on track change
  - Swipe down gesture: dismisses screen (navigate back to previous screen)
  - **Key files:** `ui/player/NowPlayingScreen.kt`, `ui/player/NowPlayingViewModel.kt`
  - **Exit test:** Start playback → Now Playing opens; scrub bar updates in real time; play/pause/skip buttons work; background colour changes on track change

---

- [x] **Session 1.11 — Library Screens (Artists, Albums, Tracks)**
  - `LibraryScreen.kt`: top-level tab container with 3 tabs — Artists, Albums, Tracks
  - `ArtistListScreen.kt`: `LazyColumn`, Paging 3 from `LibraryRepository.observeArtists()`; each row: `AsyncImage` avatar + artist name; tap → `ArtistDetail` route; A–Z fast-scroll index bar on right edge
  - `AlbumGridScreen.kt`: `LazyVerticalGrid(columns = Fixed(2))`; Paging 3; each cell: cover art + album title + artist name; tap → `AlbumDetail` route
  - `TrackListScreen.kt`: `LazyColumn`; flat list of all tracks with album art thumbnail + title + artist + duration; tap → plays track (adds to queue); long-press → context menu (Add to queue, Add to playlist, Download, Star)
  - All screens: shimmer skeleton while first page loads; empty state with copy if library is empty
  - **Key files:** `ui/library/LibraryScreen.kt`, `ui/library/artists/ArtistListScreen.kt`, `ui/library/albums/AlbumGridScreen.kt`, `ui/library/tracks/TrackListScreen.kt`, `ui/library/LibraryViewModel.kt`
  - **Exit test:** Library syncs from Navidrome; scroll through 500+ albums at 60fps (`adb shell dumpsys gfxinfo`); tap album navigates to stub detail screen

---

- [x] **Session 1.12 — Artist Detail Screen**
  - `ArtistDetailScreen.kt` + `ArtistDetailViewModel.kt`
  - ViewModel: launches parallel coroutines for `getTopSongs`, `getArtistInfo2`, `getAlbums(artistId)` — each exposed as separate `StateFlow`; `ArtistInfoEntity` cached to Room with 7-day TTL
  - Layout (single `LazyColumn` with fixed header):
    - Full-bleed header image (`getArtistInfo2.imageUrl` → Coil, fallback to first album art); artist name overlaid with bottom gradient scrim
    - "Shuffle All" button
    - Top Tracks: numbered rows, play count badge
    - Discography: tab row (Albums / Singles / Compilations) + `LazyVerticalGrid` below — tabs filter by album type from metadata
    - Similar Artists: `LazyRow` of artist chips, tappable → navigate to their detail screen
    - Bio: collapsible `Text` (3 lines collapsed → "Show more" expands); hidden if bio is null or empty
  - Each section has its own shimmer skeleton while its coroutine loads; sections that fail to load show a quiet "Couldn't load" inline (not a full-screen error)
  - **Key files:** `ui/library/artists/ArtistDetailScreen.kt`, `ui/library/artists/ArtistDetailViewModel.kt`
  - **Exit test:** Tap an artist → header loads, top tracks play on tap, similar artists are tappable, bio collapses/expands

---

- [x] **Session 1.13 — Album Detail Screen**
  - `AlbumDetailScreen.kt` + `AlbumDetailViewModel.kt`
  - ViewModel: loads `getAlbum(id)` (track list from Room), `getAlbumInfo2(id)` (notes), `observeDownloadState(albumId)`; exposes single `UiState`
  - Layout:
    - Full-bleed header: album art, title, artist name (tappable → `ArtistDetail`), year, genre; track count + total duration below header; "Play" and "Shuffle" buttons (Material 3 `FilledButton` + `OutlinedButton`)
    - Tracklist: `LazyColumn`; row = track number, title, duration, star icon, download badge; tap → play album from this track; long-press → context menu (Add to queue, Add to playlist, Download, Star, Rate)
    - Album notes: collapsible `Text` from `getAlbumInfo2`, hidden if null
    - "More by [Artist]": `LazyRow` of album art cards for other albums; tappable → navigate to their detail
    - "Download Album" button: queues all tracks; button state changes to "Downloaded ✓" once all complete
  - Starred state: optimistic — Room update first, `star`/`unstar` API in background; star icon toggles immediately
  - **Key files:** `ui/library/albums/AlbumDetailScreen.kt`, `ui/library/albums/AlbumDetailViewModel.kt`
  - **Exit test:** Album detail loads; tapping "Play" starts album from track 1; star a track → icon changes immediately; "Download Album" queues all tracks (verify in Downloads screen stub)

---

- [x] **Session 1.14 — Settings Screen (Phase 1 Subset) + Library Sync**
  - `SettingsScreen.kt`: server URL text field + "Test Connection" button (calls `ping`, shows success/error); username field; a "Save" button that writes to DataStore + `CredentialStore`; note: "Connect via Tailscale using your home IP address"
  - `SyncLibraryUseCase.kt`: fetches artists in one call → artists synced; then albums in batches of 500 → upsert Room progressively; then tracks in batches of 500; emits `SyncProgress(artistsDone, albumsDone, albumsTotal, tracksDone, tracksTotal)` via Flow
  - Library header: shows "Syncing… X of Y albums" progress bar while sync runs; app fully usable during sync; pull-to-refresh triggers re-sync
  - First launch: if `serverUrl` is empty → navigate directly to Settings; after saving valid credentials → trigger initial sync automatically
  - **Key files:** `ui/settings/SettingsScreen.kt`, `domain/usecase/SyncLibraryUseCase.kt`, modifications to `LibraryScreen.kt`
  - **Exit test:** Fresh install → Settings screen appears; enter Navidrome URL + credentials → Test Connection succeeds; library syncs and tracks appear in Library tab

---

**Phase 1 Exit Criteria:** Browse full library, tap a track, it plays via Navidrome stream with artwork. Lock screen controls work. Skip track → heads-up notification fires. Settings persist across app restarts.

---

### Phase 2 — Offline / Downloads
**Goal:** Download tracks + albums for fully offline playback.

---

- [x] **Session 2.1 — Download Infrastructure**
  - `DownloadWorker.kt`: `CoroutineWorker`; receives `trackId`, `streamUrl`, `destPath` as `inputData`; streams audio via OkHttp to `files/downloads/{artistId}/{albumId}/{trackId}.{ext}`; supports Range header resume — on retry, checks if partial file exists and sends `Range: bytes=X-`; on completion, writes `localPath` to `TrackEntity` via Room; reports progress via `setProgress(workDataOf("pct" to pct))`
  - `DownloadManager.kt`: accepts `DownloadRequest(track)`, enqueues unique `WorkManager` `OneTimeWorkRequest` per track, deduplicated by `trackId` as `WorkManager` tag; exposes `Flow<Map<String, DownloadState>>` by observing `WorkManager.getWorkInfosByTag`; default constraint `NetworkType.UNMETERED`
  - Artwork download: after track download completes, download `getCoverArtUrl(albumId)` → `files/artwork/{albumId}.jpg` if not already cached
  - Storage path constants in a single `StoragePaths.kt` object
  - **Key files:** `data/download/DownloadWorker.kt`, `data/download/DownloadManager.kt`, `data/download/StoragePaths.kt`
  - **Exit test:** Enqueue one track download; `adb shell ls` confirms file exists at expected path after completion; `TrackEntity.localPath` is non-null in Room

---

- [x] **Session 2.2 — OfflineStreamResolver Update + Download Notifications**
  - Update `OfflineStreamResolver.resolveUri(track)`: check `track.localPath`, verify `File(localPath).exists()` before returning; log and fall back gracefully if file is missing or 0 bytes
  - Download notifications (in `DownloadWorker` and `DownloadManager`):
    - In-progress: ongoing notification on `emusic_download` channel with `setProgress`; grouped by album if batch (`setGroup("album_$albumId")`)
    - Completion: `setAutoCancel(true)` notification "X downloaded"
    - Failure after retries: persistent notification "Failed to download X" with `Retry` `PendingIntent` re-enqueuing the worker
  - **Key files:** `playback/OfflineStreamResolver.kt` (update), `data/download/DownloadWorker.kt` (update)
  - **Exit test:** Download a track; kill network mid-download; resume — partial file resumes from correct byte offset; notification shows failure after 3 retries if network stays down

---

- [x] **Session 2.3 — Downloads Screen**
  - `DownloadsScreen.kt` + `DownloadsViewModel.kt`
  - ViewModel: observes `DownloadManager.downloadStates` merged with Room query of `TrackEntity WHERE localPath IS NOT NULL`
  - Layout:
    - "Active Downloads" section: tracks currently downloading with `LinearProgressIndicator`, cancel button
    - "Downloaded" section: list of downloaded albums (grouped); each row shows album art, title, track count, total size in MB
    - Storage summary card at top: "X GB used of Y GB available" (via `StatFs` on `filesDir`)
    - "Clear all downloads" in overflow menu (deletes all files + nulls `TrackEntity.localPath` for all)
  - Per-album remove: long-press → "Remove downloads" — deletes files + clears `localPath` for all tracks in that album
  - Downloaded tracks throughout the app: show download badge icon on track rows (sourced from `TrackEntity.localPath != null`)
  - **Key files:** `ui/downloads/DownloadsScreen.kt`, `ui/downloads/DownloadsViewModel.kt`
  - **Exit test:** Download an album; Downloads screen shows it in completed list with correct size; remove it; files deleted; badge disappears from album detail tracklist

---

- [x] **Session 2.4 — Offline Mode + Settings Additions**
  - `NetworkMonitor` update: surface `NetworkMonitor.isOnline` as a top-level `Banner` composable in `MainActivity` — a dismissible "You're offline — showing cached content" bar that appears when `isOnline = false`
  - All screens already read from Room (single source of truth from Phase 1) — verify they degrade gracefully: API calls in ViewModels should be wrapped in `if (networkMonitor.isOnline)` guards; failures don't crash, just don't refresh
  - Settings additions (add to `SettingsScreen.kt`): "Download on WiFi only" toggle (default on); "Force offline mode" toggle; "Download quality: Match streaming / Always original" radio buttons
  - Storage management section in Settings: total download size label, "Clear all downloads" button, per-album remove button (link to Downloads screen)
  - **Key files:** `ui/settings/SettingsScreen.kt` (update), `ui/components/OfflineBanner.kt`, `ui/MainActivity.kt` (update)
  - **Exit test:** Enable "Force offline mode" → offline banner appears; library still browsable from Room cache; disable → banner disappears

---

**Phase 2 Exit Criteria:** Download an album on WiFi, enable airplane mode, play the album fully offline with artwork. Downloads screen shows correct size and allows removal.

---

### Phase 3 — Playlists & Track Rating
**Goal:** Full playlist management synced with Navidrome, plus 1–5 star track ratings.

---

- [x] **Session 3.1 — Playlist Sync + Room**
  - `PlaylistEntity` + `PlaylistTrackCrossRef` already in Room from Phase 1 — verify schema is complete (columns: `playlistId`, `name`, `trackCount`, `duration`, `coverArtId`, `public`, `comment`, `createdAt`, `changedAt`)
  - `PlaylistRepositoryImpl.syncPlaylists()`: calls `getPlaylists()` → for each playlist calls `getPlaylist(id)` to get track list → upserts Room; strips playlists deleted from server
  - Sync triggered on app foreground resume (`DefaultLifecycleObserver` in `MainActivity`)
  - Two-way edit: local changes (add/remove/reorder track) write to Room optimistically → then call `updatePlaylist(id, …)` API → on API failure, rollback Room and show error snackbar
  - Conflict policy: Navidrome is source of truth; on foreground sync, if Navidrome version differs from local, overwrite local (no merge)
  - **Key files:** `data/repository/PlaylistRepositoryImpl.kt` (update), `domain/repository/PlaylistRepository.kt` (update)
  - **Exit test:** Create a playlist in Navidrome web UI; open app → playlist appears; add a track in-app → visible in Navidrome web UI after sync

---

- [x] **Session 3.2 — Playlist UI**
  - `PlaylistsScreen.kt` (new Library tab or sub-tab): grid of playlist cards; each card: 4-tile artwork mosaic from first 4 track covers, playlist name, track count + duration
  - FAB: create new playlist → `AlertDialog` with name text field → calls `createPlaylist(name)` API + Room insert
  - `PlaylistDetailScreen.kt` + `PlaylistDetailViewModel.kt`: track list with `ReorderableColumn` (drag handle per row via `rememberReorderableLazyListState`); changes saved to `updatePlaylist`; play button starts playlist from track 1; shuffle button; download playlist button
  - Long-press on playlist card: context menu — Rename (`AlertDialog`), Delete (confirmation dialog + `deletePlaylist` API)
  - "Add to playlist" bottom sheet: reachable from track long-press context menu, album overflow, Now Playing overflow; shows list of user playlists, tapping one calls `updatePlaylist` to append track
  - **Key files:** `ui/playlists/PlaylistsScreen.kt`, `ui/playlists/PlaylistDetailScreen.kt`, `ui/playlists/PlaylistDetailViewModel.kt`, `ui/components/AddToPlaylistSheet.kt`
  - **Exit test:** Create playlist; add 3 tracks; drag to reorder; verify new order persists after app restart and in Navidrome web UI

---

- [x] **Session 3.3 — Queue Persistence + Smart Playlists**
  - Queue persistence: `QueueManager` already writes to DataStore — verify it stores track IDs + current index + position ms; restore on `PlaybackService.onStartCommand` if queue is non-empty
  - "Save queue as playlist": action in `QueueScreen.kt` overflow → name dialog → `createPlaylist` + batch `updatePlaylist`
  - Smart local playlists (read-only, generated on-device, shown at top of Playlists screen with a "♦ Smart" badge):
    - **Starred Tracks**: `TrackEntity WHERE starred = 1 ORDER BY title`
    - **Most Played**: `ScrobbleEntity GROUP BY trackId ORDER BY COUNT DESC LIMIT 50` joined with `TrackEntity`
    - **Never Played**: `TrackEntity WHERE playCount = 0`
    - **Recent 50**: last 50 distinct `trackId`s from `ScrobbleEntity ORDER BY timestamp DESC`
  - These are computed as Room queries in `PlaylistViewModel`, presented as non-editable playlist cards (no drag, no delete)
  - **Key files:** `ui/playlists/PlaylistsViewModel.kt`, `ui/queue/QueueScreen.kt` (update)
  - **Exit test:** Play 10 tracks; restart app; queue restores at correct position. Smart playlists appear and contain correct tracks.

---

- [x] **Session 3.4 — Track Rating (1–5 Stars)**
  - `TrackEntity.userRating: Int?` already in schema from Phase 1 — verify it's mapped from DTOs (`Track.userRating`)
  - `setRating(id, rating)` API call in `LibraryRepositoryImpl`: takes `trackId: String, rating: Int` (0 to clear, 1–5 to set); optimistic Room update first, API call in background, Room rollback on failure
  - `RatingBottomSheet.kt`: 5 large tappable star icons; pre-selects current rating; tapping current rating sends `setRating(0)` to clear; spring-bounce scale animation on selection (see Phase 7 for polish)
  - Rating entry points wired up:
    - Track row long-press context menu → "Rate" → `RatingBottomSheet`
    - Now Playing overflow menu → "Rate track" → `RatingBottomSheet`
    - Album detail track row → swipe-left action → `RatingBottomSheet`
  - Star badge on track rows: show filled star icon with rating number only if `userRating != null` (no empty stars cluttering unrated tracks)
  - Album header: average rating of all rated tracks in the album (Room aggregate query); shown as small "★ 3.8 avg" label below track count
  - Home screen "Top Rated" row: `getAlbumList2?type=highest` → horizontal album carousel
  - **Key files:** `ui/components/RatingBottomSheet.kt`, updates to `AlbumDetailScreen.kt`, `NowPlayingScreen.kt`, `LibraryRepositoryImpl.kt`, `HomeViewModel.kt`
  - **Exit test:** Rate a track 4 stars → badge shows on track row → reopen app → still 4 stars → visible in Navidrome web UI. Clear rating → badge disappears.

---

**Phase 3 Exit Criteria:** Create, edit, reorder, delete playlists. Changes survive restart and visible in Navidrome web UI. Rate a track 1–5 stars, verify it persists after app restart and is visible in Navidrome. Smart playlists show correct tracks.

---

### Phase 4 — Radio Engine & Discovery
**Goal:** Spotify-like radio and a personalised Home screen.

---

- [x] **Session 4.1 — Scrobble Engine**
  - `ScrobbleEntity`: `(id: Long PK autoincrement, trackId: String, timestamp: Long, durationMs: Long)` — add to Room schema (migration v1→v2)
  - `ScrobbleManager.kt` (in `playback/`): observes `PlaybackService` position via 10s ticker; when position passes 30s → calls `scrobble(trackId, submission=true)`; on every new track start → calls `scrobble(trackId, submission=false)` (now-playing signal); writes `ScrobbleEntity` to Room on `submission=true`; respects `scrobblingEnabled` preference
  - Add `ScrobbleDao` with queries needed by later phases: `countByTrackId`, `getRecentDistinctAlbums`, `getTopTracks(since, limit)`, `getPlayedDays`
  - Hilt: inject `ScrobbleManager` into `PlaybackService`, start observing in `onStartCommand`
  - **Key files:** `db/entity/ScrobbleEntity.kt`, `db/dao/ScrobbleDao.kt`, `playback/ScrobbleManager.kt`
  - **Exit test:** Play a track past 30s → check Room: `ScrobbleEntity` row exists with correct `trackId` and `timestamp`. Disable scrobbling in settings → play past 30s → no new row.

---

- [x] **Session 4.2 — Radio Engine**
  - `RadioEngine.kt`: injected with `LibraryRepository` and `DataStore`
  - `startRadioFromTrack(trackId)`: calls `getSimilarSongs2(id, count=25)` → builds queue; plays first track
  - `startRadioFromArtist(artistId)`: calls `getTopSongs(artist)` for seeds → `getSimilarSongs2` on the top seed
  - `refillQueue()`: called by `QueueManager` when ≤5 tracks remain; fetches 20 more similar songs; deduplicates against `playedIds: Set<String>` (in-memory, cleared on new radio session start)
  - "Ban track": `DataStore` set of banned IDs, filtered out of every `refillQueue` result
  - Fallback: if `getSimilarSongs2` returns < 5 results → supplement with `getRandomSongs(size=20, genre=track.genre)`
  - Wire into context menus: "Start Radio" action on track long-press and artist detail
  - **Key files:** `playback/RadioEngine.kt`
  - **Exit test:** Long-press track → Start Radio → queue builds with 25 tracks; play to ≤5 remaining → queue auto-refills; banned track never appears

---

- [x] **Session 4.3 — Home Screen**
  - `HomeScreen.kt` + `HomeViewModel.kt`
  - `GetHomeScreenUseCase.kt`: fires all sections as parallel coroutines using `async { … }.await()` inside `coroutineScope`; each section wrapped in `runCatching` so one failure doesn't block others
  - Sections: **Jump Back In** (last 3 albums from `ScrobbleEntity`), **Recently Added** (`getAlbumList2 newest 10`), **Frequently Played** (`getAlbumList2 frequent 10`), **Starred Albums** (`getAlbumList2 starred 10`), **Top Rated** (`getAlbumList2 highest 10`), **Quick Mix** button (`getRandomSongs`), **Browse by Genre** chips
  - Each section: `LazyRow` of album cards; shimmer skeleton (`ShimmerBox`) while its coroutine is loading; section hidden (not shown as empty) if API returns 0 results
  - Pull-to-refresh resets all sections to loading state and re-fires the use case
  - **Key files:** `ui/home/HomeScreen.kt`, `ui/home/HomeViewModel.kt`, `domain/usecase/GetHomeScreenUseCase.kt`
  - **Exit test:** Home loads all sections; pull-to-refresh refreshes; tap "Quick Mix" → plays a random queue; kill network → Home loads from Room cache with no crash

---

- [x] **Session 4.4 — On-Device Suggestions + Genre Browse**
  - `GetSuggestionsUseCase.kt`: for each track in library, computes `score = (playCount × 0.4) + (starredBonus × 0.3) + (recencyScore × 0.3)` where `recencyScore = max(0, 1 - daysSinceLastPlay / 30)`; returns top 20 by score as a "You might like" row on Home (added to `HomeScreen`)
  - Suggestion scores computed lazily in ViewModel on `Dispatchers.IO`; result cached in `StateFlow` until next library sync
  - `GenreBrowseScreen.kt`: `LazyVerticalGrid`; genre list from `SELECT DISTINCT genre, COUNT(*) FROM TrackEntity GROUP BY genre ORDER BY COUNT(*) DESC`; genres with < 5 tracks grouped into "Other"; each card: deterministic colour (hash genre name to `MaterialTheme.colorScheme` variant); genre name in bold; track count; tap → filtered albums screen
  - Featured genre chips on Home screen: top 8 genres by track count as horizontal `LazyRow` of chips above the genre grid entry point
  - **Key files:** `domain/usecase/GetSuggestionsUseCase.kt`, `ui/library/genres/GenreBrowseScreen.kt`, `ui/home/HomeViewModel.kt` (update)
  - **Exit test:** "You might like" row shows on Home after 10+ scrobbles. Genre Browse screen shows all genres; tap "Jazz" → shows only Jazz albums.

---

- [x] **Session 4.5 — Recently Played Screen**
  - `RecentlyPlayedScreen.kt` + `RecentlyPlayedViewModel.kt`
  - Reachable from: Library tab filter chip "Recent", and Home "Jump Back In" → "See all"
  - Three mode chips at top: **Albums** (default), **Artists**, **Tracks**
  - Albums mode: `ScrobbleEntity` deduplicated by `albumId` via Room query; most recent first; Paging 3 (page 30); each card shows "Played X ago" subtitle
  - Artists mode: aggregated via Room join; most recently played artist first; Paging 3
  - Tracks mode: raw `ScrobbleEntity` ordered by `timestamp DESC`; no deduplication; Paging 3; shows time of play as secondary text
  - "Clear history" in overflow → confirmation dialog → `DELETE FROM ScrobbleEntity` (does not affect Navidrome play counts)
  - **Key files:** `ui/library/recent/RecentlyPlayedScreen.kt`, `ui/library/recent/RecentlyPlayedViewModel.kt`
  - **Exit test:** Play 5 albums; open Recently Played → albums appear in correct order; switch to Tracks mode → individual plays listed; clear history → list is empty

---

**Phase 4 Exit Criteria:** Long-press track → Start Radio → queue auto-refills as tracks play. Home shows personalised sections with skeleton loading. Genre browse shows all genres with correct counts. Recently Played shows accurate history in all three modes.

---

### Phase 5 — Search, Sorting & Filtering
**Goal:** Instant search and fluid browsing across 15k+ tracks.

---

- [x] **Session 5.1 — FTS5 Search**
  - `TrackFtsEntity` already created in Phase 1 — verify Room triggers are correct and FTS index is populated after sync
  - `SearchViewModel.kt`: debounce input 250ms; fires two queries in parallel: `SubsonicApiService.search3` (online only, skipped offline) + `TrackDao.searchFts(query)` (always); merges by deduplicating on `id`; emits `SearchUiState(artists, albums, tracks)` with FTS rank ordering
  - `SearchScreen.kt`: search bar with `SearchBar` composable; results in 3 sections — Artists (max 5), Albums (max 10), Tracks (max 20); "Show all X" expansion per section (navigates to filtered full list); last 20 queries in DataStore shown as chips below the bar before typing
  - FTS query format: `"${query}*"` for prefix matching; escape special chars before passing to FTS `MATCH`
  - Background FTS index rebuild: after `SyncLibraryUseCase` completes a batch upsert, trigger `TrackDao.rebuildFts()` on `Dispatchers.IO` — non-blocking
  - **Key files:** `ui/search/SearchScreen.kt`, `ui/search/SearchViewModel.kt`, `db/dao/TrackDao.kt` (update)
  - **Exit test:** Type "Ra" → results appear within 150ms including "Radiohead" and any albums/tracks matching; verify with `adb shell systrace`; works offline (no network)

---

- [x] **Session 5.2 — Sorting + Filtering**
  - Sort preferences persisted in DataStore per view (key pattern: `sort_$viewName`)
  - Sort menus (overflow or `DropdownMenu` in Library screen toolbar):
    - Albums: Name A–Z, Year ↑↓, Recently Added, Most Played, Random (uses `ORDER BY RANDOM()` in Room — reseeded on each "Random" selection)
    - Artists: Name, Track Count
    - Tracks within album: Track Number (default), Title, Duration
    - Playlists: Name, Date Created, Size
  - All sorts as Room query `ORDER BY` on indexed columns — never Kotlin-side sort of a full list
  - Filter bottom sheet (`FilterSheet.kt`): Genre multi-select chips (from Room genre list), Decade chips (2000s, 2010s, 2020s, Other), "Downloaded only" toggle, "Starred only" toggle; "Apply" button; active filter count badge on filter icon in toolbar
  - Combined filter + sort = single Room query with `WHERE … AND … ORDER BY …`; filters persisted per session in `StateFlow` (not DataStore — intentionally cleared on app restart)
  - **Key files:** `ui/components/FilterSheet.kt`, `ui/library/LibraryViewModel.kt` (update), `db/dao/AlbumDao.kt` (update)
  - **Exit test:** Sort albums by Year descending → order changes in grid. Filter by genre "Jazz" + "Downloaded only" → only downloaded Jazz albums shown. Clear filters → full library returns.

---

- [x] **Session 5.3 — Large Library Performance**
  - Audit every `LazyColumn` / `LazyVerticalGrid` in the app — ensure `key = { it.id }` is set on all item lambdas
  - Audit all ViewModels — no `Context` references; all image ops off main thread (Coil handles this)
  - Paging 3 audit: `pageSize=50, prefetchDistance=100` for Albums and Artists; `pageSize=30` for Tracks within album (typically smaller)
  - Shimmer skeleton: `ShimmerBox.kt` composable using `InfiniteTransition` + gradient sweep; used in all list/grid screens as `placeholder(visible = item == null)` in Paging 3 item lambda
  - A–Z fast-scroll: `AlphaIndexBar.kt` composable overlaid on Artists `LazyColumn`; tapping a letter calls `lazyListState.scrollToItem(indexForLetter)` on precomputed index map
  - Incremental sync: `SyncLibraryUseCase` adds `lastModified` comparison — on non-first sync, only fetch records where server `lastModified > localLastSyncedAt` (stored in DataStore per entity type); full re-sync available in Settings
  - Memory cap: Artists/Albums ViewModel caches max 200 items in `StateFlow`; beyond that relies on Paging 3 to manage memory
  - **Key files:** `ui/components/ShimmerBox.kt`, `ui/components/AlphaIndexBar.kt`, `domain/usecase/SyncLibraryUseCase.kt` (update), all ViewModel files (audit)
  - **Exit test:** Scroll through a 500+ album grid with `adb shell dumpsys gfxinfo` — confirm 0 janky frames. Incremental sync on second launch only fetches changed records (verify via OkHttp log interceptor).

---

**Phase 5 Exit Criteria:** Search returns top results in < 150ms on a 15k track library. Scrolling through 500-album grid is jank-free at 60fps. Library sync shows progress without blocking UI. Sort and filter combinations work correctly.

---

### Phase 6 — Battery Awareness & Performance
**Goal:** Run all day without impacting battery life.

---

- [x] **Session 6.1 — Adaptive Streaming Quality**
  - `BatteryAwareQualityManager.kt`: registers `BroadcastReceiver` for `ACTION_BATTERY_CHANGED` in `PlaybackService`; computes max bitrate based on battery level and charging state (see bitrate table in tech spec); exposes `Flow<Int>`; user "Always maximum" override reads from `AppPreferencesRepository`
  - `PlaybackService` passes current max bitrate to `OfflineStreamResolver.resolveUri(track, maxBitRate)` → appends `maxBitRate` param to Subsonic `stream` URL; ignored for local files
  - Settings: "Max streaming bitrate" override selector (96 / 128 / 192 / 320 / Original) shown when "Always maximum" is toggled on
  - **Key files:** `playback/BatteryAwareQualityManager.kt`, updates to `playback/OfflineStreamResolver.kt`, `playback/PlaybackService.kt`
  - **Exit test:** Unplug device, drop battery to < 20% in Developer Options emulation → verify OkHttp log shows `maxBitRate=96` in stream URL. Plug in → `maxBitRate=320`.

---

- [x] **Session 6.2 — ReplayGain / Audio Normalisation**
  - `ReplayGainAudioProcessor.kt`: implements ExoPlayer `AudioProcessor`; receives PCM frames; applies volume multiplier = `10^(gainDb / 20)` clamped by peak value; no-ops if gain is null; zero-latency (applied in real-time)
  - Wire into ExoPlayer via `ExoPlayer.Builder().setAudioProcessors(listOf(replayGainProcessor))`
  - `ReplayGainProcessor` exposed as a Hilt `@Singleton`; `PlaybackService` updates it with each new track's `trackGain`/`albumGain`/`trackPeak`/`albumPeak` from `QueueManager.currentTrack`
  - Mode selection: `ReplayGainMode` enum (Track / Album / Auto / Off); `Auto` = Album Gain if `QueueManager` detects consecutive tracks from same album in-order, else Track Gain
  - Gain mode change applies immediately to current track without restart
  - Settings: "Normalisation" selector (Track Gain / Album Gain / Auto / Off), "Pre-amp" slider −6dB to +6dB
  - **Key files:** `playback/ReplayGainAudioProcessor.kt`, `playback/PlaybackService.kt` (update), `ui/settings/SettingsScreen.kt` (update)
  - **Exit test:** Play a known loud track and a known quiet track. With Track Gain on, both play at similar perceived volume. With Off, loudness difference is audible.

---

- [x] **Session 6.3 — Network & Memory Efficiency**
  - OkHttp client: confirm connection pool `keepAliveDuration = 5min`, `maxIdleConnections = 5` (reuse connections to Navidrome)
  - Metadata responses: add `ETag` / `Last-Modified` caching via `OkHttp Cache` (50MB on-disk cache in `cacheDir`) — Navidrome respects these headers
  - Artwork: all `getCoverArt` calls include `size` param matching the display pixel size (128, 256, 512) — never fetch full-res for thumbnails
  - Coil: `diskCachePolicy = CachePolicy.ENABLED` with `maxSizeBytes = 100MB`; `memoryCachePolicy = CachePolicy.ENABLED` with `maxSizePercent = 0.15`
  - WorkManager sync jobs: `setRequiresBatteryNotLow(true)` for batch full-sync; incremental sync has no battery constraint
  - **Key files:** `di/NetworkModule.kt` (update), `di/CoilModule.kt`
  - **Exit test:** `adb shell dumpsys meminfo dev.emusic` — heap size under 150MB while browsing 500-album grid. `adb shell dumpsys power` — no wake lock held while paused.

---

- [x] **Session 6.4 — Doze Mode + Jank Audit**
  - Verify `PlaybackService` declared as `foregroundServiceType="mediaPlayback"` — exempt from Doze during active playback (already true, but verify)
  - Test: play music with screen off for 30 min; confirm no audio dropout via logcat
  - Jank audit: run `adb shell dumpsys gfxinfo dev.emusic reset` → scroll every screen → `adb shell dumpsys gfxinfo dev.emusic` — fix any screen with > 0 janky frames
  - Verify: no `Context` held in any `ViewModel`; all `viewModelScope` coroutines cancel on `ViewModel.onCleared()`; all `lifecycleScope` coroutines tied to correct lifecycle
  - **Key files:** Any ViewModel files found with issues during audit
  - **Exit test:** 30-minute background playback with screen off — no dropout. All screens pass gfxinfo jank check.

---

**Phase 6 Exit Criteria:** 8 hours continuous streaming playback. Battery drain within 5% of a local file player. All screens 60fps. Verified with `adb shell dumpsys batterystats`. Volume consistent across tracks in Track Gain mode.

---

### Phase 7 — Polish & Hardening
**Goal:** Production-quality feel, robust edge case handling.

---

- [x] **Session 7.1 — Now Playing Motion**
  - Album art pulse: `InfiniteTransition` scale 1.0 → 1.02 → 1.0 (3s loop) while playing, `animateFloatAsState` to 1.0 when paused
  - Background colour: Palette API extracts dominant swatch from current album art bitmap (Coil `target { onSuccess { bitmap → … } }`); colour transitions via `animateColorAsState(tween(300))`; wrap in `derivedStateOf` to prevent recomposition cascade
  - Track info change: `AnimatedContent(targetState = currentTrack) { slideInVertically + fadeIn vs slideOutVertically + fadeOut }`
  - Scrub bar: custom `Canvas`-drawn bar with draggable thumb that scales up on touch (`animateFloatAsState` spring); buffered range in lighter colour
  - Play/Pause: `AnimatedVectorDrawable` morphing icon OR Compose path morphing between play and pause shapes (choose whichever compiles cleanly)
  - Swipe down gesture to dismiss: `detectVerticalDragGestures` with velocity-aware snap back or dismiss
  - Swipe left/right on artwork: `detectHorizontalDragGestures` → skip next/prev with card slide animation
  - **Key files:** `ui/player/NowPlayingScreen.kt` (update)
  - **Exit test:** 60fps on all Now Playing animations (`adb shell dumpsys gfxinfo`). Swipe down dismisses. Art pulse visible during playback.

---

- [x] **Session 7.2 — Mini-Player + Screen Transitions**
  - Mini-player: `AnimatedVisibility(slideInVertically)` on first appearance; track title `InfiniteMarquee` composable for long titles; thin progress `LinearProgressIndicator` across full width; tap → Now Playing with `SharedElement` transition on album art; swipe right → skip next
  - Navigation transitions in `NavGraph`: default `fadeIn(0.95 scaleIn) / fadeOut(0.95 scaleOut)`; Album Grid → Album Detail: shared element on cover art + title (`SharedTransitionLayout`); Artist → Albums: slide right/left
  - `LazyRow` / `LazyVerticalGrid` press state: `indication = rememberRipple(bounded = true)` on all tappable items
  - Long-press context menus: `DropdownMenu` with `transformOrigin` at touch point; scale-in animation 0.85 → 1.0 via `AnimatedVisibility`
  - Swipe-to-reveal on track rows: `SwipeableRow.kt` composable — right swipe reveals "Add to queue" (`Icons.Filled.AddCircle`), left swipe reveals "Download" (`Icons.Filled.Download`) or "Remove" if downloaded
  - Album art in grids: `crossfade(300)` on all `AsyncImage` calls — no blank flash
  - Pull-to-refresh: custom `PullRefreshIndicator` using Material 3 styled spinner matching accent colour
  - **Key files:** `ui/components/MiniPlayerBar.kt` (update), `ui/navigation/NavGraph.kt` (update), `ui/components/SwipeableRow.kt`
  - **Exit test:** Navigate App Grid → Album Detail → Now Playing → back twice: all transitions are smooth with no flash. Swipe album row left to reveal download action.

---

- [x] **Session 7.3 — Loading States + Micro-interactions**
  - Skeleton shimmer: audit every screen — every `LazyColumn`/`LazyGrid` item placeholder must use `ShimmerBox` at the correct dimension matching the real content (round for artist avatars, square for album art, rectangle for track rows); `AnimatedContent(fadeIn)` from skeleton → real content
  - Star/Unstar: `animateFloatAsState(spring(dampingRatio=0.4))` scale 1.0 → 1.4 → 1.0 when star is tapped
  - Download state icon: `CrossfadeIcon` morph — idle → spinning arc (`CircularProgressIndicator` sized to icon) → checkmark → uses `AnimatedContent`
  - Shuffle/Repeat toggle buttons: `animateColorAsState` fill colour change, not just icon swap
  - Add to queue: `Snackbar` slides in from bottom with track thumbnail and "Added to queue" text
  - System volume change while in Now Playing: overlay `VolumeIndicator` composable (brief `AnimatedVisibility` auto-dismiss after 2s)
  - Haptics: `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)` on all long-press context menus; `TextHandleMove` on drag-to-reorder; light click on skip prev/next
  - Dark/light theme: follows system via `isSystemInDarkTheme()`; user override in Settings
  - **Key files:** `ui/components/ShimmerBox.kt` (update), `ui/components/CrossfadeIcon.kt`, updates throughout
  - **Exit test:** Tap star → spring animation plays. All list screens show shimmer before data loads. Dark mode toggle in Settings works.

---

- [x] **Session 7.4 — Edge Cases**
  - Tailscale disconnect mid-playback: `PlaybackService` `onPlayerError` catches `HttpDataSourceException` → if `track.localPath` is non-null, no-op (local file plays fine); else emit `PlaybackError.NetworkLost` → show `Snackbar` "Playback stopped — connection lost" with "Retry" action
  - Missing track on server (404 stream): `ExoPlayer` `onPlayerError` with `ERROR_CODE_IO_FILE_NOT_FOUND` → skip to next track; log to local error file; show brief `Toast` "Track unavailable, skipped"
  - Storage full before download: `DownloadManager` checks `StatFs.availableBytes > file.estimatedSize + 200MB` before enqueuing; if insufficient → `Snackbar` "Not enough storage — need X MB free"
  - Partial download on app kill: `DownloadWorker` resumes from partial file on retry (Range header already implemented in Session 2.1 — verify it triggers correctly on `WorkManager` retry)
  - Long metadata (title > 100 chars, artist > 80 chars): `maxLines = 1, overflow = TextOverflow.Ellipsis` on all metadata `Text` composables; full text on long-press `Tooltip`
  - **Key files:** `playback/PlaybackService.kt` (update), `data/download/DownloadManager.kt` (update), UI composables audit
  - **Exit test:** Disable Tailscale mid-song → "connection lost" shows and playback stops gracefully. Start download with < 200MB free → warning shown, download not started.

---

- [x] **Session 7.5 — Settings (Complete) + Deployment Script**
  - Complete `SettingsScreen.kt` with all sections:
    - **Server**: URL, credentials, test connection, server version from `ping`
    - **Playback**: Normalisation mode, pre-amp slider, crossfade 0–12s (`ExoPlayer` `CrossfadeMediaSource`), gapless toggle, EQ shortcut, sleep timer defaults
    - **Downloads**: WiFi only toggle, download quality, storage usage, clear all
    - **Library**: Scrobbling toggle, heads-up notifications toggle, force offline mode, force full resync
    - **Appearance**: Dark/light/system theme
    - **About**: App version (`BuildConfig.VERSION_NAME`), Navidrome server version
  - Crossfade: `ExoPlayer.Builder().setCrossfadeMediaSourceFactory(…)` with user-selected duration; 0 = `DefaultMediaSourceFactory`; gapless toggle passes `AudioAttributes` `USAGE_MEDIA` (ExoPlayer handles gapless natively when this is set)
  - `scripts/deploy.sh`: `./gradlew assembleRelease && jarsigner … && adb devices && adb install -r app-release.apk`; keystore config from `local.properties`; exits non-zero if `adb devices` returns no device
  - `Timber` initialised only in debug (`BuildConfig.DEBUG`) in `EMusicApp.onCreate`; all log calls use `Timber.d/w/e`; fatal crash handler writes to `filesDir/crash.log`
  - **Key files:** `ui/settings/SettingsScreen.kt` (complete), `scripts/deploy.sh`
  - **Exit test:** All settings persist across app kill/restart. `./scripts/deploy.sh` builds and installs to connected device successfully.

---

- [x] **Session 7.6 — Synced Lyrics**
  - `LyricsEntity`: `(trackId String PK, lrcContent String?, plainText String?, fetchedAt Long)` — add to Room (migration v2→v3)
  - `LyricsRepository.getLyrics(track)`: check Room cache (TTL 30 days) → if stale/missing, call `getLyrics(artist, title)` API → parse → upsert Room → return
  - `LrcParser.kt`: parses `[mm:ss.xx] Line text` into `List<LrcLine>(timestampMs: Long, text: String)`; handles extended A2 format (word-level timestamps stripped — use line-level only); returns null if input is plain text (no `[` timestamps)
  - Lyrics panel in `NowPlayingScreen`: swipe-up gesture on album artwork → `AnimatedVisibility` lyrics layer slides up; swipe-down returns to artwork; same blurred/darkened album art background
  - `LazyColumn` of lyric lines; current line determined by `exoPlayer.currentPosition` vs `LrcLine.timestampMs`; `LazyListState.animateScrollToItem(currentLineIndex, scrollOffset)` on each position update (throttled to 500ms)
  - Line styling: current = `MaterialTheme.typography.bodyLarge`, accent colour, full alpha; past = 40% alpha; future = 60% alpha
  - Tap line → `exoPlayer.seekTo(line.timestampMs)`
  - Plain text fallback: static scrollable `Text` block if no LRC timestamps found
  - "No lyrics available" state: subtle `Icon` + message; not an error, not a spinner — just informational
  - **Key files:** `data/lyrics/LrcParser.kt`, `data/lyrics/LyricsRepository.kt`, `db/entity/LyricsEntity.kt`, `ui/player/NowPlayingScreen.kt` (update)
  - **Exit test:** Open Now Playing for a track with LRC lyrics (verify Navidrome has them); swipe up → lyrics panel opens; current line auto-scrolls and highlights; tap a line → playback jumps to that timestamp.

---

- [x] **Session 7.7 — "More from This Artist" Panel**
  - `MoreFromArtistSheet.kt`: `ModalBottomSheet` sliding up from Now Playing when user taps artist name
  - Artist name in `NowPlayingScreen` is a `ClickableText` / `TextButton`
  - Sheet content: artist image + name + album count header; "Top Tracks" `LazyColumn` (5 tracks from `getTopSongs`); "Albums" `LazyRow` carousel (all albums, tappable → `AlbumDetail`); "Go to Artist" `Button` at bottom → navigates to `ArtistDetail` + dismisses sheet
  - Data fetched lazily when sheet first opens (not pre-fetched) — skeleton shown during load
  - Swipe-down to dismiss
  - **Key files:** `ui/player/MoreFromArtistSheet.kt`, `ui/player/NowPlayingScreen.kt` (update)
  - **Exit test:** Tap artist name in Now Playing → sheet slides up with skeleton → loads top tracks and albums; tap album → sheet dismisses and Album Detail opens; tap "Go to Artist" → Artist Detail opens

---

**Phase 7 Exit Criteria:** App feels indistinguishable from a native commercial music app. 60fps confirmed on all screens. All animations smooth. Lyrics display and sync correctly. All settings persist across restarts. Edge cases handled without crashing.

---

### Phase 8 — Internet Radio & Sleep Timer
**Goal:** Browse and play live internet radio; stop playback automatically with a graceful fade-out.

> **Note on naming:** The existing "Radio Engine" in Phase 4 generates similar-song queues from your Navidrome library. This phase is entirely separate — live internet broadcast streams. They share the playback infrastructure but nothing else.

---

- [x] **Session 8.1 — Radio Browser API + Room**
  - `RadioStation` domain model: `id (uuid), name, streamUrl, homepage, logoUrl, country, countryCode, language, tags: List<String>, codec, bitrate, isHls, votes, lastCheckedOk`
  - `RadioStationEntity` in Room for favourited stations only (migration v3→v4); columns match domain model + `lastPlayedAt: Long?`
  - `CountryEntity` in Room: `code, name, stationCount`; refreshed weekly by `WorkManager`
  - `RadioBrowserApiService.kt`: separate Retrofit instance (not SubsonicApiService); base URL resolved at startup via `InetAddress.getAllByName("all.api.radio-browser.info")[0]`; retry with different IP on failure
  - Endpoints: `getCountries()`, `getStationsByCountry(code, offset, limit)`, `searchStations(name, offset, limit)`, `getStationsByTag(tag)`, `getTopVoted(count)`, `reportStationClick(uuid)` (POST — call when user starts playing)
  - `RadioBrowserMapper.kt`: DTO → `RadioStation` domain model
  - `InternetRadioRepository.kt`: wraps `RadioBrowserApiService` + `RadioStationEntity` DAO; `isFavourited(uuid)`, `addFavourite(station)`, `removeFavourite(uuid)`, `observeFavourites(): Flow<List<RadioStation>>`
  - **Key files:** `data/radio/*.kt`, `db/entity/RadioStationEntity.kt`, `db/entity/CountryEntity.kt`, `domain/model/RadioStation.kt`, `domain/repository/InternetRadioRepository.kt`
  - **Exit test:** `./gradlew testDebug` — unit test: `RadioBrowserMapper` correctly maps a sample JSON response to `RadioStation`. `CountryEntity` Room insert + query passes.

---

- [x] **Session 8.2 — Live Stream Playback**
  - `OfflineStreamResolver` update: if `mediaType == LiveStream` → skip local file check, skip bitrate param, pass URL directly to ExoPlayer
  - `IcyMetadataHandler.kt`: implements ExoPlayer `MetadataOutput`; on `IcyInfo` → extracts `streamTitle`, splits on `" - "` into artist/title fields; emits `StateFlow<IcyNowPlaying?>`
  - `PlaybackService` update: new `playLiveStream(station: RadioStation)` method — clears music queue, creates `MediaItem.fromUri(station.streamUrl)`, sets `IcyMetadataHandler` on ExoPlayer; calls `InternetRadioRepository.reportStationClick(uuid)`
  - `RadioStreamState` sealed class: `Loading`, `Playing(station, icyNowPlaying)`, `Reconnecting(attempt)`, `Offline(station)`
  - Stream reliability: `LoadErrorHandlingPolicy` with exponential backoff (1s/2s/4s/8s, max 3 attempts); emit `Offline` after 3 failures; `NetworkMonitor.isOnline` → auto-reconnect on network restore
  - `isSeekable = false` propagated to UI (hides scrub bar for live streams)
  - Current station persisted in `PlaybackService` state (not DataStore — lost on process death is acceptable for live streams)
  - **Key files:** `playback/IcyMetadataHandler.kt`, `playback/PlaybackService.kt` (update), `playback/OfflineStreamResolver.kt` (update)
  - **Exit test:** Call `playLiveStream` with a known BBC Radio stream URL → audio plays within 3 seconds. ICY metadata emits station name. Kill network → reconnect attempt shown → auto-reconnects when network returns.

---

- [x] **Session 8.3 — Sleep Timer**
  - `SleepTimerManager.kt` inside `PlaybackService`: coroutine ticker on `Dispatchers.Default`; exposes `SleepTimerState(remainingMs: Long, isActive: Boolean)` via `StateFlow`
  - `setSleepTimer(minutes)`: starts ticker, stores `targetTimeMs = System.currentTimeMillis() + minutes * 60000`
  - `cancelSleepTimer()`: cancels coroutine job, resets state
  - `extendSleepTimer(minutes)`: adds to `targetTimeMs`
  - At T-60s: begin volume fade — coroutine loop every 250ms sets `exoPlayer.volume = (remainingMs / 60000f).coerceIn(0f, 1f)`
  - At T=0: `exoPlayer.pause()`, `exoPlayer.volume = 1f`, emit `SleepTimerState.Fired`
  - Fade-out opt-in: reads `fadeOutEnabled` from `AppPreferencesRepository`
  - "Stop after current track": flag in `SleepTimerManager`; `PlaybackService` checks flag in `onMediaItemTransition` → pause if set
  - Notification: append "Sleep timer: Xm remaining" text to existing `emusic_playback` notification; update every minute (not every second); add "+15 min" and "Cancel" `PendingIntent` actions to expanded notification
  - New channel `emusic_sleep_timer` (IMPORTANCE_DEFAULT): fires single "eMusic stopped — sleep timer ended" notification on expiry; auto-dismiss after 5s
  - Custom `MediaController` commands: `COMMAND_SLEEP_TIMER_SET`, `COMMAND_SLEEP_TIMER_CANCEL`, `COMMAND_SLEEP_TIMER_EXTEND` — UI calls these via `MediaController`
  - **Key files:** `playback/SleepTimerManager.kt`, `playback/PlaybackService.kt` (update), `playback/NotificationHelper.kt` (update)
  - **Exit test:** Set 1-minute sleep timer → confirm notification shows countdown → at T-60s fade-out begins (volume drops audibly) → at T=0 playback stops and notification fires

---

- [x] **Session 8.4 — Radio Browse + Favourites UI**
  - New **Radio** tab in bottom navigation (replace Downloads tab with 5th tab; move Downloads to Library sub-screen)
  - `RadioBrowseScreen.kt`: top section "Top Stations" (`LazyRow`, from `getTopVoted(20)`); "Your Favourites" (`LazyRow`, from Room); "Browse by Country" (`LazyColumn` of country rows with flag emoji + name + station count); "Browse by Genre" chips (`LazyRow` of popular tags)
  - Country drill-down: `LazyVerticalGrid` of station cards; each card: station logo (Coil, fallback to `Icons.Filled.Radio`), name, bitrate badge, codec badge; sort by Top Voted / Name / Bitrate via `DropdownMenu`; paged 40 at a time
  - Station card long-press: context menu — Play now, Add/Remove Favourite, Copy URL, Open homepage (`Intent.ACTION_VIEW`)
  - `RadioFavouritesScreen.kt` (accessible from Radio home): list of `RadioStationEntity` from Room; swipe-to-delete; "Last played X days ago" secondary text
  - `RadioSearchScreen.kt`: `SearchBar`, debounce 300ms, calls `searchStations` (name) or `getStationsByTag` (if query starts with `#`); recent searches from DataStore as chips; results show country flag + vote count
  - **Key files:** `ui/internetradio/RadioBrowseScreen.kt`, `ui/internetradio/RadioSearchScreen.kt`, `ui/internetradio/RadioFavouritesScreen.kt`, `ui/internetradio/RadioViewModel.kt`
  - **Exit test:** Browse to a country; tap a station; audio plays; star it; kill app; reopen → station still in Favourites

---

- [x] **Session 8.5 — Radio Now Playing + Sleep Timer UI**
  - `NowPlayingScreen` adapts by `PlaybackService.currentMediaType` (`Track` vs `LiveStream`): show/hide scrub bar, skip buttons, queue button based on media type; replace scrub area with pulsing `LIVE` badge (`InfiniteTransition` red dot); `AnimatedContent` on ICY `streamTitle` updates; station logo instead of album art
  - Sleep timer bottom sheet (`SleepTimerSheet.kt`): slides from Now Playing clock icon; preset chips (15/30/45/60/90 min) + custom input when inactive; countdown arc + extend/cancel buttons when active; fade-out toggle; "Stop after current track" chip (hidden for live streams)
  - `NowPlayingScreen`: clock icon always visible top-right; tapping opens `SleepTimerSheet`
  - **Key files:** `ui/player/NowPlayingScreen.kt` (update), `ui/player/SleepTimerSheet.kt`
  - **Exit test:** Play a radio station → Now Playing shows LIVE badge, no scrub bar; ICY title updates; open sleep timer sheet → set 90 min → countdown shows; extend by 15 → time increases; cancel → timer dismissed

---

**Phase 8 Exit Criteria:** Browse stations by country, play one, hear audio within 3 seconds. ICY metadata updates in real-time in both notification and Now Playing. Set a 1-minute sleep timer: fade-out begins at T-60s, playback stops at T=0, notification fires. Favourite a station, kill app, reopen — still there and playable. Stream drops → auto-reconnects; after 3 failures shows "Station unavailable".

---

### Phase 9 — Equalizer, Home Screen Widget & Listening Stats
**Goal:** Fine-tune audio output, control playback from the home screen, and surface meaningful listening data.

---

- [x] **Session 9.1 — Equalizer**
  - `EqualizerManager.kt`: creates `android.media.audiofx.Equalizer` tied to `exoPlayer.audioSessionId`; also creates `BassBoost` and `Virtualizer` effects; releases all on `PlaybackService.onDestroy()`; all wrapped in `try/catch` (unsupported on some devices — graceful disable)
  - `EqPresetEntity`: `(id: Long PK autoincrement, name: String, band0..band4: Int, bassBoostStrength: Int)` — add to Room (migration v4→v5)
  - `EqSettings` in DataStore: `enabled: bool`, `activePresetName: String`, `band0..band4: Int`, `bassBoostStrength: Int`, `virtualizerStrength: Int`
  - Built-in presets as hardcoded `Map<String, IntArray>` (Flat/Bass Boost/Treble/Vocal/Electronic/Classical/Rock/Hip Hop)
  - `EqualizerScreen.kt`: master enable toggle; 5 vertical sliders labelled with device-reported frequencies; dB range −15 to +15; real-time apply (no Apply button); Canvas-drawn frequency response curve using `drawPath` connecting band points with cubic bezier, animates with `animateFloatAsState`; bass boost slider 0–100%; preset selector `LazyRow` of chips; "Save preset" — name dialog → `EqPresetEntity` Room insert; saved presets appear alongside built-ins; note: "EQ may not apply on all devices"
  - EQ accessible from: Settings → Playback → Equalizer, and Now Playing overflow menu
  - **Key files:** `playback/EqualizerManager.kt`, `db/entity/EqPresetEntity.kt`, `ui/settings/equalizer/EqualizerScreen.kt`
  - **Exit test:** Move bass slider → audible change on currently playing track. Save a preset named "My EQ"; kill app; reopen → custom preset still in list and selected.

---

- [x] **Session 9.2 — Home Screen Widget**
  - `SmallWidgetProvider.kt`: `AppWidgetProvider` for 2×1 widget — `RemoteViews` layout with album art `ImageView` (128×128), track title `TextView` (marquee), play/pause `ImageButton`, skip next `ImageButton`
  - `LargeWidgetProvider.kt`: 4×2 widget — full-width album art, title + artist row, prev/play-pause/next buttons, `ProgressBar` (thin, indeterminate=false)
  - `PlaybackService` broadcasts `ACTION_WIDGET_UPDATE` on: track change, play state change, every 10s (large widget only for progress bar)
  - `WidgetUpdateReceiver` (or handled in providers): updates `RemoteViews` + calls `AppWidgetManager.updateAppWidget()`
  - Album art: Coil `ImageLoader.execute(ImageRequest)` into `Bitmap` → saved to `cacheDir/widget_art.jpg` → `RemoteViews.setImageViewBitmap`; 128×128 for small, 256×256 for large
  - All `PendingIntent`s: `FLAG_IMMUTABLE`; play/pause → `PlaybackService` `PLAY_PAUSE` command; skip → `SKIP_NEXT`; prev → `SKIP_PREVIOUS`; tap widget body → `MainActivity` with `Intent.FLAG_ACTIVITY_SINGLE_TOP`
  - "Not playing" idle state: app icon, "eMusic" label, no controls
  - Widget metadata in `res/xml/widget_small_info.xml` and `res/xml/widget_large_info.xml` (correct `minWidth`, `minHeight`, `updatePeriodMillis=0` — updates pushed by service)
  - **Key files:** `ui/widget/SmallWidgetProvider.kt`, `ui/widget/LargeWidgetProvider.kt`, `playback/PlaybackService.kt` (update), `res/xml/widget_*_info.xml`, `res/layout/widget_*.xml`
  - **Exit test:** Add both widgets to home screen; start playback → both update with art and title; tap play/pause → state changes in widget within 1s; large widget progress bar advances; remove and re-add widget → works after app restart

---

- [x] **Session 9.3 — Listening Stats Screen**
  - `StatsScreen.kt` + `StatsViewModel.kt`; accessible from Library tab or top-bar icon
  - All queries on `Dispatchers.IO` from `ScrobbleEntity` + joins to `TrackEntity`/`AlbumEntity`/`ArtistEntity`; results cached in `StateFlow`; recomputed when screen opens or new scrobble added
  - **Most Played Tracks**: `SELECT trackId, COUNT(*) as count FROM scrobbles GROUP BY trackId ORDER BY count DESC LIMIT 50`; displayed as ranked list with album art thumbnail, play count badge; time-range chip filter (All Time / This Year / This Month / Last 30 Days) adds `WHERE timestamp > ?` clause
  - **Most Played Artists + Albums**: Room JOIN aggregations; ranked lists tappable to Artist/Album Detail
  - **Listening Time**: total hours card ("You've listened to 142 hours of music") using `SUM(duration)` scrobbles join tracks; 30-day daily bar chart using `Canvas.drawRect` — bars coloured with `MaterialTheme.colorScheme.primary`; x-axis dates, y-axis minutes
  - **Top Genres**: top 5 via scrobbles → tracks → genre; `Canvas` horizontal bar chart with relative widths
  - **Listening Streaks**: consecutive days with ≥1 scrobble (current streak, longest ever); flame icon card
  - **Discovery**: first scrobble ever ("First track: X on [date]"); most recently starred album
  - **CSV Export**: overflow → write `top_100_tracks_${date}.csv` to `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)`; includes rank, trackId, title, artist, playCount; launch `ShareCompat` intent after writing
  - **Key files:** `ui/stats/StatsScreen.kt`, `ui/stats/StatsViewModel.kt`, `db/dao/ScrobbleDao.kt` (update with all stat queries)
  - **Exit test:** After 50+ scrobbles: Most Played shows correct top track; Listening Time shows non-zero hours; daily chart renders 30 bars; CSV export produces a readable file in Downloads folder

---

**Phase 9 Exit Criteria:** Move EQ slider → hear change immediately. Both widgets work and update on track change. Stats screen shows accurate play counts matching Navidrome. CSV exports successfully. All features work after app restart.

---

## Claude Code Execution Notes

Provide this context at the start of every Claude Code session:

1. **Reference this plan** — don't deviate from the architecture without flagging it
2. **No GMS dependencies** — reject any library that requires Google Play Services
3. **Single source of truth** — Room is the database; API data always flows through Room before UI
4. **One phase at a time** — complete and verify exit criteria before moving to the next
5. **Test commands after each phase:**
   ```bash
   ./gradlew lint          # No new warnings
   ./gradlew testDebug     # Unit tests pass
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
6. **Subsonic auth** — MD5 token method only, never plaintext password in URL
7. **Minimum API 29** — use modern APIs freely, no legacy workarounds needed

---

## Changelog

| Date | Change |
|---|---|
| 2026-03-17 | Initial plan created |
| 2026-03-17 | Added Phase 1.8 Notification System (heads-up banners, channel config, DND awareness) |
| 2026-03-17 | Upgraded Phase 5 Search to FTS5 with prefix search + relevance ranking for 15k+ library |
| 2026-03-17 | Added Phase 5.4 Large Library Performance (Paging 3, incremental sync, A–Z index, memory budget) |
| 2026-03-17 | Expanded Phase 7.1 into full Spotify-like motion/UI spec (Now Playing, transitions, micro-interactions, dynamic colour) |
| 2026-03-17 | Upgraded Room DB spec (Phase 1.3) with explicit indexes and FTS5 |
| 2026-03-17 | Added Paging 3 and Compose Animation to Tech Stack |
| 2026-03-17 | Added Phase 8 — Internet Radio & Sleep Timer (Radio Browser API, ICY metadata, live stream handling, sleep timer with fade-out) |
| 2026-03-17 | Added getLyrics, setRating, getArtistInfo2, getAlbumInfo2 to Subsonic API reference |
| 2026-03-17 | Expanded Phase 1.6 with full Artist Detail and Album Detail screen specs |
| 2026-03-17 | Added Phase 3.5 — Track Rating (1–5 stars, setRating API, optimistic UI) |
| 2026-03-17 | Added Phase 4.5 — Genre Browse Screen (colour-coded grid, deterministic colours, filter engine integration) |
| 2026-03-17 | Added Phase 4.6 — Recently Played Screen (Albums/Artists/Tracks view modes, Paging 3, clear history) |
| 2026-03-17 | Added Phase 6.6 — ReplayGain/Normalisation (Track/Album/Auto/Off modes, pre-amp slider, ExoPlayer AudioProcessor) |
| 2026-03-17 | Updated Phase 7.4 Settings with crossfade slider, gapless toggle, normalisation mode, EQ shortcut |
| 2026-03-17 | Added Phase 7.7 — Synced Lyrics (LRC parsing, line highlight, tap-to-seek, plain text fallback) |
| 2026-03-17 | Added Phase 7.8 — More from Artist panel (bottom sheet preview from Now Playing) |
| 2026-03-17 | Restructured all phases from design-doc style into Claude Code session format — each session has a single deliverable, explicit key files, and a runnable exit test |
