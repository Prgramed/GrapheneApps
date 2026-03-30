# eGallery — Master Build Plan
> GrapheneOS · Synology Photos API · Tailscale · Offline-First Rolling Window · No GMS
> Last updated: 2026-03-23

---

## Confirmed Decisions

| Concern | Decision |
|---|---|
| **Platform** | GrapheneOS, sideloaded APK, single user, no GMS |
| **Backend** | Synology Photos API (`SYNO.Foto.*`) via Tailscale |
| **Source of truth** | NAS always — phone is a smart cache, never the master |
| **Storage model** | Every photo tracked in Room; `ON_DEVICE` (≤1yr by EXIF date, full res cached) or `NAS_ONLY` (medium thumbnail cached, full res on NAS) |
| **1-year window** | Based on EXIF capture date; files older than 1 year auto-deleted locally, silently |
| **Manual download** | NAS-only photo explicitly downloaded → kept 1 month fixed, then auto-deleted regardless of views |
| **Camera ingestion** | Background service watches camera folder; new photos queued for upload; drains when NAS reachable |
| **Offline upload** | Upload queue in Room; processes when Tailscale reconnects; no duplicates (tracked by hash + NAS id) |
| **Full res viewing** | NAS-only: stream directly from NAS; dedicated "Download" button stores locally for 1 month |
| **Thumbnails** | Medium preview (~500px) cached for ALL photos regardless of age — enables smooth browsing entirely offline |
| **Video** | ON_DEVICE plays locally; NAS_ONLY must download first, then plays; download counted same as photo manual download (1 month) |
| **Editing** | Download original → edit on device → re-upload overwrite to NAS → delete local edit temp file |
| **Map view** | Clustered pins on osmdroid (GMS-free); tap cluster → expands; tap pin → opens photo |
| **Connection** | Tailscale primary; QuickConnect as optional bonus (settings field, not required) |
| **Features** | Timeline, folder browsing, manual albums, people albums (view only), tags, search, map |
| **Default gallery** | Registered as default handler for `image/*` and `video/*` via intent filters; handles `GET_CONTENT` picker mode; receives `ACTION_SEND` shares; `MANAGE_MEDIA` permission for batch deletes without per-file prompts |

---

## How to Use This Plan with Claude Code

Each **Session** is one Claude Code working block. At the start of every session paste:

```
This is session X.Y of the eGallery Android app build.
Reference file: eGallery-plan.md (attached or pasted).
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
| Photo grid | `LazyVerticalGrid` + Compose | Custom staggered layout for timeline |
| Networking | Retrofit 2 + OkHttp 4 | Custom interceptor for Synology `_sid` auth |
| Image loading | Coil 3 | Custom `OkHttpClient` with `_sid` injector for thumbnail URLs |
| Local DB | Room | Photo index, storage status, upload queue, download expiry |
| DI | Hilt | |
| Async | Coroutines + Flow | |
| Preferences | DataStore (Proto) | |
| Credentials | EncryptedSharedPreferences | NAS password |
| Background work | WorkManager | Upload queue, eviction jobs, camera watcher |
| Map | osmdroid 6.x | GMS-free OpenStreetMap; CartoDB Dark Matter tiles |
| Editing | Android `BitmapFactory` + `Matrix` + `Canvas` | Rotate/flip/crop; no external edit lib needed |
| Colour adjustments | `ColorMatrix` + `ColorMatrixColorFilter` | Brightness/contrast/saturation on `Paint` |
| File watching | `FileObserver` (API 29+) | Camera folder monitoring |
| Video | Media3 ExoPlayer | Local and streaming (HLS/progressive from NAS) |

> **No GMS dependencies anywhere.** Validate all transitive deps.

---

## Synology Photos API Reference

### Authentication
```
POST /photo/webapi/entry.cgi
  api=SYNO.API.Auth&version=7&method=login
  &account=[username]&passwd=[password]&session=SynologyPhotos&format=sid
→ { data: { sid: "abc123..." }, success: true }
```
Session `_sid` passed as query parameter on every subsequent request.
Sessions expire after ~30 min idle — implement auto re-auth on 401.

### Key Endpoints

| API | Method | Purpose |
|---|---|---|
| `SYNO.Foto.Browse.Item` | `list` | List photos in timeline or folder |
| `SYNO.Foto.Browse.Folder` | `list` | Browse folder tree |
| `SYNO.Foto.Browse.Album` | `list`, `get` | List/get manual albums |
| `SYNO.Foto.Browse.Person` | `list` | List people (face) albums |
| `SYNO.Foto.Browse.Tag` | `list` | List tags |
| `SYNO.Foto.Thumbnail` | `get` | Fetch thumbnail (sm / m / xl) |
| `SYNO.Foto.Download` | `download` | Download original file |
| `SYNO.Foto.Upload.Item` | `upload` | Upload new photo |
| `SYNO.Foto.Search.Filter` | `list` | Available search filters |
| `SYNO.Foto.Search.Suggestion` | `list` | Search suggestions |
| `SYNO.Foto.Browse.Item` | `get` | Get single photo with EXIF + GPS |
| `SYNO.Foto.Browse.Item` | `set_tag` | Add/remove tags |
| `SYNO.Foto.Browse.Album` | `add_item` | Add photo to album |
| `SYNO.Foto.Browse.Item` | `delete` | Delete from NAS |

### Thumbnail URL Pattern
```
GET /photo/webapi/entry.cgi
  ?api=SYNO.Foto.Thumbnail&version=1&method=get
  &mode=download&id={photo_id}&type=unit&size=m
  &cache_key={cache_key}&_sid={session_id}
```
- `size=sm` (~120px), `size=m` (~500px), `size=xl` (~1200px)
- `cache_key` is per-photo, returned in `SYNO.Foto.Browse.Item` response
- `_sid` must be injected by `SynologyAuthInterceptor` on every Coil image request

### Browse Item Response Shape
```json
{
  "id": 7713,
  "filename": "IMG_20240315_142301.jpg",
  "filesize": 7231588,
  "time": 1710505381,
  "folder_id": 242,
  "type": "photo",
  "additional": {
    "thumbnail": { "m": "ready", "xl": "ready", "sm": "ready",
                   "cache_key": "7713_1687701308", "unit_id": 7713 },
    "exif": { "takentime": 1710505381, "lat": 55.6761, "lng": 12.5683,
              "camera": "Google Pixel 9", "lens": "...", "iso": 100,
              "aperture": "1.7", "shutter": "1/120", "focal_length": "6.8" },
    "tag": [{ "id": 3, "name": "holiday" }]
  }
}
```

### Pagination
All list endpoints support `offset` + `limit`. Use `limit=200` for initial sync, `limit=50` for incremental. Always check `data.total` to know when done.

---

## Storage Model — The Core of the App

Every photo/video has exactly **one** `MediaEntity` row in Room. Never two rows for the same image.

```kotlin
@Entity
data class MediaEntity(
    @PrimaryKey val nasId: Int,          // Synology photo ID — unique forever
    val filename: String,
    val captureDate: Long,               // EXIF takentime (epoch ms) — drives the 1yr window
    val nasUploadDate: Long,             // when it arrived on NAS
    val fileSize: Long,
    val mediaType: MediaType,            // PHOTO / VIDEO
    val folderId: Int,
    val cacheKey: String,                // for thumbnail URL construction
    val thumbnailPath: String?,          // local path to medium thumbnail (always cached if NAS known)
    val localPath: String?,              // full res local path — non-null = ON_DEVICE
    val localExpiry: LocalExpiry?,       // null = permanent (1yr window), or FixedExpiry(date)
    val storageStatus: StorageStatus,    // ON_DEVICE / NAS_ONLY / UPLOAD_PENDING / UPLOAD_FAILED
    val lat: Double?,                    // GPS from EXIF
    val lng: Double?,
    val tags: List<String>,             // TypeConverter
    val albumIds: List<Int>,            // TypeConverter
    val nasHash: String?,               // SHA256 of original — dedup guard
    val lastSyncedAt: Long
)

enum class StorageStatus { ON_DEVICE, NAS_ONLY, UPLOAD_PENDING, UPLOAD_FAILED }
sealed class LocalExpiry {
    object Rolling : LocalExpiry()            // auto-evict when captureDate > 1yr ago
    data class Fixed(val expiresAt: Long) : LocalExpiry() // manual download: 1 month fixed
}
```

**Key invariants enforced by the app:**
1. `nasId` is the single identity key — a photo from the camera that hasn't been uploaded yet uses a temporary negative ID until NAS assigns a real one
2. A photo is `ON_DEVICE` only if `localPath != null` AND the file exists on disk
3. Medium thumbnail is always cached for every known photo — even `NAS_ONLY` — so scrolling is always smooth
4. `UPLOAD_PENDING` photos appear in the gallery immediately; a sync icon overlays the thumbnail

---

## Project Structure

```
eGallery/
├── app/src/main/
│   ├── api/
│   │   ├── SynologyAuthInterceptor.kt  ← Injects _sid; auto re-auths on 401
│   │   ├── SynologyPhotoService.kt     ← Retrofit interface for all SYNO.Foto.* calls
│   │   ├── dto/                        ← Raw API response DTOs
│   │   └── SynologyPhotoMapper.kt      ← DTO → domain model
│   ├── data/
│   │   ├── db/
│   │   │   ├── AppDatabase.kt
│   │   │   ├── dao/
│   │   │   │   ├── MediaDao.kt
│   │   │   │   ├── AlbumDao.kt
│   │   │   │   ├── AlbumMediaDao.kt     ← Insert/delete album↔photo associations
│   │   │   │   ├── TagDao.kt
│   │   │   │   ├── MediaTagDao.kt       ← Insert/delete photo↔tag associations
│   │   │   │   ├── UploadQueueDao.kt
│   │   │   │   └── PersonDao.kt
│   │   │   └── entity/
│   │   │       ├── MediaEntity.kt
│   │   │       ├── AlbumEntity.kt
│   │   │       ├── AlbumMediaEntity.kt  ← Join table: album ↔ photo (many-to-many)
│   │   │       ├── TagEntity.kt
│   │   │       ├── MediaTagEntity.kt    ← Join table: photo ↔ tag (many-to-many)
│   │   │       ├── PersonEntity.kt
│   │   │       └── UploadQueueEntity.kt
│   │   └── repository/
│   ├── domain/
│   │   └── model/
│   │       ├── MediaItem.kt
│   │       ├── Album.kt
│   │       ├── Person.kt
│   │       └── UploadQueueItem.kt
│   ├── sync/
│   │   ├── NasSyncEngine.kt            ← Core sync logic: fullSync() + incrementalSync()
│   │   ├── NasSyncWorker.kt            ← WorkManager worker wrapping NasSyncEngine
│   │   ├── UploadWorker.kt             ← Drains upload queue
│   │   ├── EvictionWorker.kt           ← Deletes expired local files
│   │   ├── ThumbnailPrefetchWorker.kt  ← Pre-fetches medium thumbnails for new items
│   │   └── CameraWatcher.kt            ← Foreground service + FileObserver on DCIM/Camera
│   ├── edit/
│   │   ├── PhotoEditor.kt             ← Bitmap rotate/flip/crop/adjust
│   │   └── EditUploadCoordinator.kt   ← Download → edit → re-upload pipeline
│   ├── ui/
│   │   ├── MainActivity.kt
│   │   ├── navigation/NavGraph.kt
│   │   ├── timeline/
│   │   │   ├── TimelineScreen.kt
│   │   │   └── TimelineViewModel.kt
│   │   ├── folder/
│   │   │   ├── FolderScreen.kt
│   │   │   └── FolderViewModel.kt
│   │   ├── album/
│   │   │   ├── AlbumsScreen.kt
│   │   │   ├── AlbumDetailScreen.kt
│   │   │   └── AlbumViewModel.kt
│   │   ├── people/
│   │   │   ├── PeopleScreen.kt
│   │   │   └── PersonDetailScreen.kt
│   │   ├── viewer/
│   │   │   ├── PhotoViewerScreen.kt
│   │   │   ├── VideoPlayerScreen.kt
│   │   │   └── ViewerViewModel.kt
│   │   ├── editor/
│   │   │   ├── EditScreen.kt
│   │   │   └── EditViewModel.kt
│   │   ├── map/
│   │   │   ├── MapScreen.kt
│   │   │   └── MapViewModel.kt
│   │   ├── search/
│   │   │   ├── SearchScreen.kt
│   │   │   └── SearchViewModel.kt
│   │   └── settings/
│   │       └── SettingsScreen.kt
│   └── util/
│       ├── ThumbnailUrlBuilder.kt     ← Constructs SYNO.Foto.Thumbnail URLs
│       ├── StorageManager.kt          ← Manages local file paths, eviction
│       └── HashUtil.kt                ← SHA256 dedup
```

---

## Phase 1 — Foundation & API Layer

**Goal:** Auth to Synology, fetch photo list, construct thumbnail URLs, load images.

---

- [x] **Session 1.1 — Project Scaffolding** ✅ Done 2026-03-26
  - `applicationId = "dev.egallery"`, minSdk 34 (convention default, not 29 as originally planned — all target devices are API 34+)
  - All deps already in `libs.versions.toml` — no changes needed to version catalog
  - Convention plugins: `grapheneapps.android.application`, `application.compose`, `hilt`, `room`, `kotlin.serialization`
  - Full permissions: INTERNET, ACCESS_NETWORK_STATE, READ_MEDIA_IMAGES/VIDEO, READ_MEDIA_VISUAL_USER_SELECTED, MANAGE_MEDIA, FOREGROUND_SERVICE/DATA_SYNC, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, ACCESS_COARSE/FINE_LOCATION
  - Dropped `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` (not needed at minSdk 34)
  - Intent filters: ACTION_VIEW (image/*, video/*), GET_CONTENT (picker), ACTION_SEND/SEND_MULTIPLE (share)
  - Intent handling stubs in `MainActivity.handleIntent()` — actual navigation deferred to later sessions
  - `EGalleryApp.kt`: HiltAndroidApp + HiltWorkerFactory + crash handler
  - ProGuard: OkHttp, Retrofit, Room, WorkManager, osmdroid, Media3, Tink, Kotlin serialization
  - **Default app prompt:** On first launch, AlertDialog asks "Set eGallery as your default gallery app?" → opens `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS`. Tracked via `FIRST_LAUNCH_DONE` DataStore preference (only prompts once).
  - **MANAGE_MEDIA runtime permission:** On first launch, if `MediaStore.canManageMedia()` is false → opens `Settings.ACTION_REQUEST_MANAGE_MEDIA` with package URI.
  - **Intent handling fully wired:**
    - `ACTION_VIEW` → sets `startUri` → NavGraph starts on `URI_VIEWER` route → `UriViewerScreen` shows the image via Coil with zoom/pan
    - `GET_CONTENT` → sets `pickerMode=true` → TimelineScreen title changes to "Select a photo" → tap returns content URI via `setResult(RESULT_OK)` + `finish()`
    - `ACTION_SEND` / `SEND_MULTIPLE` → copies URI(s) to `filesDir/shared_imports/`, inserts MediaEntity (temp negative nasId) + UploadQueueEntity, enqueues UploadWorker
  - **Key files:** `settings.gradle.kts`, `app/build.gradle.kts`, `AndroidManifest.xml`, `EGalleryApp.kt`, `ui/MainActivity.kt`, `proguard-rules.pro`, `ui/viewer/UriViewerScreen.kt`, `ui/navigation/Routes.kt`, `ui/navigation/NavGraph.kt`

---

- [x] **Session 1.2 — Domain Models** ✅ Done 2026-03-26
  - `MediaItem`: nasId, filename, captureDate, nasUploadDate, fileSize, mediaType, folderId, cacheKey, thumbnailPath, localPath, localExpiry, storageStatus, lat, lng, nasHash, lastSyncedAt
  - Removed `tags: List<String>` and `albumIds: List<Int>` from domain model — these will be handled via join tables in Room (session 1.5)
  - `StorageStatus` enum: ON_DEVICE, NAS_ONLY, UPLOAD_PENDING, UPLOAD_FAILED
  - `LocalExpiry` sealed class: Rolling (1yr window), Fixed(expiresAt: Long) (manual download, 1 month)
  - `MediaType` enum: PHOTO, VIDEO
  - `Album`: id, name, coverPhotoId, photoCount, type (AlbumType: MANUAL/PEOPLE)
  - `Person`: id, name, coverPhotoId, photoCount
  - `UploadQueueItem`: id, localPath, targetFolderId, enqueuedAt, retryCount, status (UploadStatus: PENDING/UPLOADING/FAILED)
  - **Key files:** `domain/model/MediaItem.kt`, `domain/model/Album.kt`, `domain/model/Person.kt`, `domain/model/UploadQueueItem.kt`

---

- [x] **Session 1.3 — Synology Auth + Session Management** ✅ Done 2026-03-26
  - `SynologySession.kt`: `@Singleton`; holds current `_sid` in `StateFlow<String?>`; `suspend fun login()` — calls `SYNO.API.Auth` login method; `fun logout()`; `fun isLoggedIn(): Boolean`
  - `SynologyAuthInterceptor.kt`: OkHttp `Interceptor`; appends `&_sid={sid}` to every request URL; on 401/403 → re-auths via `runBlocking { session.login() }` and retries once
  - `CredentialStore.kt`: `EncryptedSharedPreferences`; stores `nasUrl`, `username`, `password`; `isConfigured` check
  - `NetworkModule.kt`: Hilt module providing `OkHttpClient` (with auth interceptor, 30s/60s/60s timeouts), `Retrofit` (kotlinx.serialization converter), `Json`
  - **Note:** `SynologyPhotoService` Retrofit interface deferred to session 1.4 (needs DTOs first). Retrofit base URL is placeholder — actual NAS URL injected per-request.
  - **Key files:** `api/SynologySession.kt`, `api/SynologyAuthInterceptor.kt`, `data/CredentialStore.kt`, `di/NetworkModule.kt`

---

- [x] **Session 1.4 — Synology Photo Service + DTOs** ✅ Done 2026-03-26
  - `api/dto/SynologyDtos.kt`: All DTOs in one file — `SynologyResponse<T>` generic wrapper, `ItemDto`, `AdditionalDto`, `ThumbnailDto`, `ExifDto`, `TagDto`, `FolderDto`, `AlbumDto`, `PersonDto` + list data wrappers. All `@Serializable` with nullable fields.
  - `api/SynologyPhotoService.kt`: Retrofit interface using `@GET` + `@Url` + `@QueryMap` for dynamic NAS URL. Methods: listItems, getItem, listFolders, listAlbums, listPersons, listTags, searchItems, deleteItem, downloadItem (streaming).
  - `SynologyApiHelper` object: builds param maps for each API call (listItemsParams, getItemParams, listFoldersParams, etc.). All use the same `entry.cgi` endpoint with different `api`/`method`/`version` params. Added `listAlbumItemsParams` for album-specific photo listing.
  - `api/SynologyPhotoMapper.kt`: `ItemDto.toDomain()`, `AlbumDto.toDomain()`, `PersonDto.toDomain()`, `ItemDto.extractTags()`. Converts epoch seconds → millis. Default `storageStatus = NAS_ONLY` (caller overrides).
  - `util/ThumbnailUrlBuilder.kt`: `small()` / `medium()` / `large()` URL builders. `_sid` NOT embedded — injected by SynologyAuthInterceptor.
  - `di/NetworkModule.kt`: Added `@Provides` for `SynologyPhotoService`.
  - `SynologyPhotoService.setItemTags()` + `SynologyApiHelper.setItemTagsParams(id, tagIds)` — calls `SYNO.Foto.Browse.Item` method `set_tag` with `id` and `tag` array params.
  - `SynologyPhotoService.addItemToAlbum()` + `SynologyApiHelper.addItemToAlbumParams(albumId, itemIds)` — calls `SYNO.Foto.Browse.Album` method `add_item` with `id` and `item` array params.
  - **Key files:** `api/dto/SynologyDtos.kt`, `api/SynologyPhotoService.kt`, `api/SynologyPhotoMapper.kt`, `util/ThumbnailUrlBuilder.kt`, `di/NetworkModule.kt`

---

- [x] **Session 1.5 — Room Database** ✅ Done 2026-03-27
  - 7 entities: `MediaEntity` (indexes: captureDate, folderId, storageStatus, compound lat/lng), `AlbumEntity`, `PersonEntity`, `UploadQueueEntity` (autoGenerate PK), `AlbumMediaEntity` (composite PK albumId+nasId, index nasId), `TagEntity`, `MediaTagEntity` (composite PK nasId+tagId, indexes both)
  - Enums stored as plain Strings — no TypeConverters needed. `LocalExpiry` sealed class flattened to `localExpiryType: String?` + `localExpiryAt: Long?` columns directly on MediaEntity.
  - 7 DAOs: `MediaDao` (PagingSource for timeline, Flow for folder/album/tag, JOIN queries for album_media and media_tag, expired local files query, filename search, upsert/upsertAll/updateStorageStatus), `AlbumDao`, `PersonDao`, `UploadQueueDao`, `AlbumMediaDao`, `TagDao`, `MediaTagDao`
  - `AppDatabase` v1 with all 7 entities, schema export enabled (convention plugin)
  - `DatabaseModule`: Room.databaseBuilder with `fallbackToDestructiveMigration(dropAllTables = true)`, individual `@Provides` for all 7 DAOs
  - `MediaDao.getByHash(hash)` query + index on `nasHash` column for hash-based dedup. `HashUtil.sha256(file)` utility (64KB chunked). `CameraWatcher` now hashes files on capture, checks `getByHash()` before inserting — skips duplicates. `nasHash` set on MediaEntity at insert time.
  - `Converters.kt` not needed — all enums stored as Strings directly.
  - **Key files:** `data/db/entity/*.kt` (7 files), `data/db/dao/*.kt` (7 files), `data/db/AppDatabase.kt`, `di/DatabaseModule.kt`

---

- [x] **Session 1.6 — Coil + Thumbnail Loading** ✅ Done 2026-03-27
  - Followed eMusic pattern: `SingletonImageLoader.Factory` on `EGalleryApp` directly — no separate `CoilModule.kt` needed.
  - Shared Hilt-injected `OkHttpClient` (with `SynologyAuthInterceptor`) via `OkHttpNetworkFetcherFactory` — `_sid` injected into every thumbnail request automatically.
  - Disk cache: 500MB in `cacheDir/thumbnails/`. Memory cache: 25% of app memory. Crossfade enabled.
  - **Dropped from plan:** `LocalMediaFetcher.kt` — Coil 3 handles `file://` URIs natively, no custom fetcher needed. `CoilModule.kt` — not needed with Application-level factory pattern.
  - **Key files:** `EGalleryApp.kt`

---

- [x] **Session 1.7 — Repository Layer + DataStore** ✅ Done 2026-03-27
  - **DataStore Preferences** (not Proto) — `datastore-preferences` already in build.gradle, simpler than proto for flat key-value settings. Keys: ACTIVE_VIEW (default "TIMELINE"), LAST_SYNC_AT, SYNC_INTERVAL_HOURS (default 6), WIFI_ONLY_UPLOAD (default true), AUTO_EVICT_ENABLED (default true). NAS credentials stay in CredentialStore (encrypted).
  - `AppPreferencesRepository`: typed Flow + suspend setters for each preference.
  - `MediaRepository` interface + `MediaRepositoryImpl`: observeTimeline (Pager with pageSize=50, prefetchDistance=20 wrapping Room PagingSource → mapped to domain), observeFolder, observeAlbum, getItemDetail, deleteFromNas (stub), getCount.
  - Entity ↔ Domain mappers as extension functions: `MediaEntity.toDomain()`, `MediaItem.toEntity()`, `AlbumEntity.toDomain()`, `Album.toEntity()`, `PersonEntity.toDomain()`, `Person.toEntity()`.
  - `AlbumRepository` + impl: observeAll, getById. `PersonRepository` + impl: observeAll.
  - `PreferencesModule`: provides `DataStore<Preferences>`. `RepositoryModule`: `@Binds` for all 3 repositories.
  - `deleteFromNas(nasId)`: calls `SynologyPhotoService.deleteItem()` → deletes local file via StorageManager → deletes from Room (media + media_tag + album_media join tables). Full implementation, no stub.
  - `setTags(nasId, tagIds)`: calls `SynologyPhotoService.setItemTags()` → updates local media_tag join table (delete + insertAll). Full implementation.
  - `addToAlbum(nasId, albumId)`: calls `SynologyPhotoService.addItemToAlbum()` → inserts AlbumMediaEntity locally. Full implementation.
  - `MediaRepositoryImpl` now injects `MediaTagDao`, `AlbumMediaDao`, `SynologyPhotoService`, `CredentialStore`, `StorageManager` in addition to `MediaDao`.
  - **Changed from plan:** Used DataStore Preferences instead of Proto (simpler, already in deps). Dropped `nas_url`/`username` from preferences (already in CredentialStore).
  - **Key files:** `data/preferences/AppPreferences.kt`, `data/preferences/AppPreferencesRepository.kt`, `data/repository/MediaRepository.kt`, `data/repository/MediaRepositoryImpl.kt`, `data/repository/AlbumRepository.kt`, `data/repository/AlbumRepositoryImpl.kt`, `data/repository/PersonRepository.kt`, `data/repository/PersonRepositoryImpl.kt`, `di/PreferencesModule.kt`, `di/RepositoryModule.kt`

---

**Phase 1 Exit Criteria:** Authenticate to NAS, fetch item list, load thumbnails in a basic `LazyVerticalGrid`, all images loading from Room via Paging 3. `_sid` injected correctly on thumbnail requests.

---

## Phase 2 — NAS Sync Engine

**Goal:** Reliable incremental sync keeping Room in perfect sync with Synology Photos.

---

- [x] **Session 2.1 — Full Sync** ✅ Done 2026-03-27
  - `NasSyncEngine.kt`: `@Singleton`, `suspend fun fullSync(): Result<SyncStats>`. Paginates items (PAGE_SIZE=200), maps DTO → domain → entity via `SynologyPhotoMapper` + `toEntity()`, upserts each page immediately. Preserves local storage status (localPath, storageStatus, localExpiry) for items already on device.
  - Tag sync per-item: extracts tags from each DTO, upserts TagEntity definitions, then replaces MediaTagEntity associations (delete + insertAll per nasId).
  - Reconcile deletions: after all pages, diffs API nasIds vs Room `getAllNasIds()` → deletes orphans from media, media_tag, and album_media tables.
  - Album sync: paginates `listAlbums` (limit=100) → upserts AlbumEntity rows.
  - People sync: single `listPersons` call → upserts PersonEntity rows.
  - Tags sync: single `listTags` call → upserts TagEntity rows.
  - Updates `lastSyncAt` in AppPreferencesRepository on completion.
  - Returns `SyncStats` (itemsSynced, itemsDeleted, albumsSynced, peopleSynced, tagsSynced, durationMs).
  - Added `MediaDao.getAllNasIds(): List<Int>` for reconciliation.
  - **Note:** `incrementalSync()` deferred to session 2.2. Album↔media join table sync (album item membership) deferred — requires per-album item listing which is expensive; will add when album browsing is built.
  - **Key files:** `sync/NasSyncEngine.kt`, `domain/model/SyncStats.kt`, `data/db/dao/MediaDao.kt`

---

- [x] **Session 2.2 — Incremental Sync** ✅ Done 2026-03-27
  - `NasSyncEngine.incrementalSync()`: fetches items sorted by `time DESC`, stops early when reaching items older than lastSyncAt. Uses same preserve-local-status logic as fullSync. Daily reconcile (>24h since last): lightweight ID-only fetch (`additional=[]`, limit=5000), diffs against Room, deletes orphans.
  - Added `LAST_RECONCILE_AT` preference key + `getLastSyncAtOnce()`/`getLastReconcileAtOnce()` suspend helpers (using `Flow.first()`) to AppPreferencesRepository.
  - `NasSyncWorker.kt`: `@HiltWorker` + `CoroutineWorker`; if lastSyncAt==0 or >7 days → fullSync, else incrementalSync. Returns `Result.retry()` on failure.
  - `SyncScheduler.kt`: `@Singleton` wrapping `WorkManager.getInstance(context)`; `schedulePeriodic(intervalHours)` with KEEP policy + CONNECTED constraint; `syncNow()` one-shot; `cancel()`.
  - `BootReceiver.kt`: `@AndroidEntryPoint` BroadcastReceiver for BOOT_COMPLETED → `syncScheduler.schedulePeriodic()`. Registered in manifest.
  - `SyncViewModel.kt`: `SyncState` sealed interface (Idle/Syncing/Error); `syncNow()` runs sync directly in viewModelScope (for immediate UI feedback); `schedulePeriodicSync()` delegates to SyncScheduler.
  - **Design choice:** `SyncViewModel.syncNow()` calls `syncEngine` directly (not via WorkManager one-shot) for immediate feedback. `SyncScheduler.syncNow()` still available for background-only use.
  - **Key files:** `sync/NasSyncEngine.kt`, `sync/NasSyncWorker.kt`, `sync/SyncScheduler.kt`, `sync/BootReceiver.kt`, `sync/SyncViewModel.kt`, `data/preferences/AppPreferences.kt`, `data/preferences/AppPreferencesRepository.kt`, `AndroidManifest.xml`

---

- [x] **Session 2.3 — Thumbnail Prefetch Worker** ✅ Done 2026-03-27
  - `ThumbnailPrefetchWorker.kt`: `@HiltWorker` CoroutineWorker. Queries 50 items where `thumbnailPath IS NULL` (newest first), builds medium thumbnail URL via `ThumbnailUrlBuilder`, executes Coil `ImageRequest` with disk-only caching. Marks `thumbnailPath = "cached"` on success, `"none"` if no cacheKey. Self-chains another run if more items remain.
  - Added to `MediaDao`: `getUnprefetched(limit)` and `updateThumbnailPath(nasId, path)`.
  - `NasSyncWorker` now enqueues `ThumbnailPrefetchWorker` after successful sync.
  - Viewport-priority prefetching: `ThumbnailPrefetchWorker` accepts `KEY_PRIORITY_IDS` IntArray via input data — prefetches those first before falling back to newest-first unprefetched items. `TimelineViewModel.prefetchVisibleThumbnails()` enqueues the worker with visible nasIds. `TimelineScreen` uses `LazyGridState` + `LaunchedEffect` on `isScrollInProgress` to extract visible nasIds when scroll settles.
  - **Key files:** `sync/ThumbnailPrefetchWorker.kt`, `sync/NasSyncWorker.kt`, `data/db/dao/MediaDao.kt`

---

**Phase 2 Exit Criteria:** Full library syncs from NAS. Incremental sync picks up new photos within 1h. Thumbnails prefetched for entire library. Room stays consistent with NAS after adds and deletes.

---

## Phase 3 — Camera Ingestion & Upload Queue

**Goal:** New phone photos enter the app automatically and upload to NAS reliably.

---

- [x] **Session 3.1 — Camera Folder Watcher** ✅ Done 2026-03-27
  - `CameraWatcher.kt`: `@AndroidEntryPoint` foreground service with `FileObserver` on `DCIM/Camera`. `IMPORTANCE_MIN` persistent notification. Watches for `CREATE` and `MOVED_TO` events. On new file: 2s debounce → validate extension → extract EXIF captureDate → insert MediaEntity (negative temp nasId via AtomicInteger, `UPLOAD_PENDING`) → insert UploadQueueEntity. `START_STICKY` for restart. Registered in manifest with `foregroundServiceType="dataSync"`.
  - `util/MediaFileUtil.kt`: `isMediaFile()` (jpg/jpeg/png/heic/heif/webp/mp4/mov/mkv/webm), `mediaTypeFromFile()`, `extractCaptureDate()` (EXIF datetime → millis, fallback lastModified). Uses platform `android.media.ExifInterface` (no extra dep needed at minSdk 34).
  - Started from `MainActivity.onCreate()` via `startForegroundService` and from `BootReceiver` on BOOT_COMPLETED.
  - **Deferred:** `HashUtil.kt` / SHA-256 dedup — expensive for large video files, will validate at upload time (session 3.2) instead. SyncViewModel badge emission — will wire when TimelineScreen exists (Phase 4).
  - **Changed from plan:** Extension-based validation instead of magic bytes (simpler, sufficient). No hash dedup at capture time. Started in `onCreate` instead of `onResume` (only need to start once per activity lifecycle).
  - **Key files:** `sync/CameraWatcher.kt`, `util/MediaFileUtil.kt`, `sync/BootReceiver.kt`, `ui/MainActivity.kt`, `AndroidManifest.xml`

---

- [x] **Session 3.2 — Upload Worker** ✅ Done 2026-03-27
  - `UploadWorker.kt`: `@HiltWorker` CoroutineWorker. Drains `UploadQueueDao.getPending()` one at a time. Verifies file exists. Uploads via multipart POST to `SYNO.Foto.Upload.Item`. On success: `MediaDao.replaceEntity(tempNasId, newEntity)` atomically swaps temp→real nasId + sets ON_DEVICE; deletes UploadQueueEntity. On failure: increments retryCount, marks UPLOAD_FAILED after 10 retries. After successful uploads: enqueues NasSyncWorker for incremental sync to pull server metadata (cacheKey, etc.).
  - Added to `SynologyPhotoService`: `@Multipart @POST uploadItem()` with `@Part` file + `@PartMap` params.
  - Added `UploadResultData` DTO (itemId field).
  - Added `SynologyApiHelper.uploadItemParts()` — builds multipart form parts for api/version/method/folder_id/filename/mtime.
  - Added to `MediaDao`: `insert()`, `replaceEntity(oldNasId, newEntity)` — `@Transaction` delete + insert.
  - `CameraWatcher` now enqueues `UploadWorker` immediately after inserting UploadQueueEntity.
  - `findTempNasId(localPath)`: uses `MediaDao.getByLocalPath(localPath)` — direct Room query, no scanning.
  - WiFi-only upload constraint: `CameraWatcher.enqueueUploadWorker()` and `MainActivity.importSharedUri()` read `wifiOnlyUpload` preference — use `UNMETERED` if true, `CONNECTED` if false.
  - `OfflineBanner.kt` built in session 9.2.
  - **Key files:** `sync/UploadWorker.kt`, `api/SynologyPhotoService.kt`, `api/dto/SynologyDtos.kt`, `data/db/dao/MediaDao.kt`, `sync/CameraWatcher.kt`

---

- [x] **Session 3.3 — Local Eviction Worker** ✅ Done 2026-03-27
  - `EvictionWorker.kt`: `@HiltWorker` CoroutineWorker. Uses `MediaDao.getExpiredLocalFiles(rollingCutoff = now - 1yr, now)` which handles both Rolling and Fixed expiry in a single query. Skips UPLOAD_PENDING and UPLOAD_FAILED items. Deletes local file via `StorageManager.deleteLocalFile()`, updates Room to NAS_ONLY + null localPath. Silent — logs count via Timber only.
  - `EvictionScheduler.kt`: Schedules daily `PeriodicWorkRequest` with KEEP policy. Called from `BootReceiver` and `MainActivity.onCreate()`.
  - `StorageManager.kt`: `@Singleton`. `localFilePath(nasId, filename)` → `filesDir/media/{nasId}/{filename}`. `deleteLocalFile(path)` → deletes file + cleans empty parent dir.
  - Updated `BootReceiver` and `MainActivity` to schedule eviction.
  - `EvictionWorker.doWork()` reads `preferencesRepository.autoEvictEnabled` — returns early with `Result.success()` if disabled.
  - **Key files:** `sync/EvictionWorker.kt`, `sync/EvictionScheduler.kt`, `util/StorageManager.kt`, `sync/BootReceiver.kt`, `ui/MainActivity.kt`

---

**Phase 3 Exit Criteria:** Take a photo → appears in app immediately → uploads to NAS when connected. Old photos auto-evicted silently. Thumbnails remain browseable after eviction.

---

## Phase 4 — Timeline & Grid Views

**Goal:** A beautiful, fast photo timeline. The primary screen.

---

- [x] **Session 4.1 — Timeline Screen** ✅ Done 2026-03-27
  - `TimelineScreen.kt` + `TimelineViewModel.kt` — first real UI screen.
  - `TimelineViewModel`: `@HiltViewModel` with `MediaRepository.observeTimeline()` → `insertSeparators` for month date headers → `cachedIn(viewModelScope)`. Exposes `Flow<PagingData<TimelineItem>>` where `TimelineItem` is sealed (DateHeader/PhotoCell). Inline sync via `syncNow()` (full or incremental based on lastSyncAt). `thumbnailUrl()` helper builds URLs via `ThumbnailUrlBuilder.medium()`.
  - `TimelineScreen`: `LazyVerticalGrid(GridCells.Fixed(3))` with 2dp spacing. DateHeaders span full width via `GridItemSpan(maxLineSpan)`. PhotoCells are square (`aspectRatio(1f)`) `AsyncImage` with `ContentScale.Crop`. UPLOAD_PENDING overlay (sync icon). Pull-to-refresh via Material3 `PullToRefreshBox`. Loading/empty/error states. Tap → navigates to photo viewer route.
  - `NavGraph.kt`: NavHost with TIMELINE (start) and PHOTO_VIEWER/{nasId} routes. Fade transitions (250ms/200ms). Photo viewer is placeholder until session 4.2.
  - `Routes.kt`: Route constants + `photoViewer(nasId)` builder.
  - `MainActivity.kt`: Replaced placeholder Box with `EGalleryNavGraph()`.
  - **Multi-select:** Long-press on PhotoGridCell enters multi-select mode (`combinedClickable`). `selectedNasIds: StateFlow<Set<Int>>` + `isMultiSelectMode` in ViewModel. In multi-select: tap toggles selection, TopAppBar swaps to action bar (`AnimatedVisibility(slideInVertically)`) showing count + Delete + Add to Album + Close buttons. `CheckCircle` overlay on selected cells with dark scrim. `deleteSelected()` and `addSelectedToAlbum(albumId)` call `MediaRepository`.
  - **Scroll position persistence:** `savedScrollIndex` / `savedScrollOffset` in ViewModel. `rememberLazyGridState(initialFirstVisibleItemIndex, initialFirstVisibleItemScrollOffset)` restores on recompose. `DisposableEffect(onDispose)` saves position when leaving.
  - **Date headers:** Styled with `titleMedium`, white text on dark background (`Color(0xFF1A1A1A)`), more padding. True sticky headers not possible in `LazyVerticalGrid` — would require migration to `LazyColumn` with manual row layout.
  - **Key files:** `ui/timeline/TimelineScreen.kt`, `ui/timeline/TimelineViewModel.kt`, `ui/navigation/NavGraph.kt`, `ui/navigation/Routes.kt`, `ui/MainActivity.kt`

---

- [x] **Session 4.2 — Photo Viewer** ✅ Done 2026-03-27
  - `PhotoViewerScreen.kt` + `ViewerViewModel.kt` — full-screen photo viewer.
  - `ViewerViewModel`: `@HiltViewModel` with `SavedStateHandle` for initial nasId. Loads timeline nasIds from `MediaDao.getAllNasIdsOrdered()` (flat ID list for pager). `imageUrl(item)` returns `File(localPath)` for ON_DEVICE or xl thumbnail URL for NAS_ONLY. `downloadForOffline(nasId)` streams original via `SYNO.Foto.Download` → `StorageManager.localFilePath()` → 64KB chunked write → updates Room (ON_DEVICE, Fixed 30-day expiry). `DownloadState` sealed: Idle/Downloading(progress)/Done/Error.
  - `PhotoViewerScreen`: `HorizontalPager` for swipe between photos. `ZoomableImage` composable with `detectTransformGestures` (pinch 1×–4×) + `detectTapGestures` (double-tap toggle 1×/2×). Tap anywhere toggles UI visibility (top bar + bottom info). Bottom info bar: filename, formatted date, `StorageChip` (green ON_DEVICE / grey NAS_ONLY / amber UPLOAD_PENDING / red UPLOAD_FAILED). Download FAB for NAS_ONLY items with progress indicator.
  - Added `MediaDao.getAllNasIdsOrdered()` for timeline pager navigation.
  - NavGraph updated: replaced placeholder with `PhotoViewerScreen(onBack)`.
  - **Deferred:** Shared element transition (Compose API experimental). Full EXIF detail sheet (camera, GPS, resolution). Share/Edit/Delete actions. Swipe-down dismiss.
  - **Key files:** `ui/viewer/PhotoViewerScreen.kt`, `ui/viewer/ViewerViewModel.kt`, `data/db/dao/MediaDao.kt`, `ui/navigation/NavGraph.kt`

---

- [x] **Session 4.3 — Video Player** ✅ Done 2026-03-27
  - `VideoPlayerScreen.kt`: full-screen Media3 ExoPlayer wrapped in `AndroidView(PlayerView)`. ON_DEVICE videos play immediately from `localPath`. NAS_ONLY videos download first (progress bar with percentage), then play. `DisposableEffect` releases ExoPlayer on leave. Auto-play on load.
  - `VideoPlayerViewModel.kt`: `@HiltViewModel` with `SavedStateHandle`. Loads item, resolves `playerUri` (local file URI for ON_DEVICE, downloads for NAS_ONLY with 30-day Fixed expiry). Reuses same download streaming pattern as `ViewerViewModel`.
  - `PhotoViewerScreen.kt`: Added play icon overlay (`PlayCircle`, 72dp, white) on video items in HorizontalPager. Tapping navigates to VIDEO_PLAYER route. Added `onVideoPlay` callback.
  - Routes: Added `VIDEO_PLAYER/{nasId}` + `videoPlayer(nasId)` builder. NavGraph wired with `VideoPlayerScreen(onBack)`.
  - **Double-tap seek:** Left/right halves of the screen are transparent gesture zones (`detectTapGestures(onDoubleTap)`). Left double-tap → `seekTo(currentPosition - 10s)`. Right double-tap → `seekTo(currentPosition + 10s)`. Seek indicator overlay ("−10s" / "+10s") shown for 800ms via coroutine delay.
  - **Mute toggle:** VolumeUp/VolumeOff IconButton in TopAppBar actions. Toggles `player.volume` between 0f and 1f.
  - **Transport controls:** Default `PlayerView` controls retained (play/pause, scrubber, time) — enhanced with the double-tap seek zones and mute toggle above.
  - **Key files:** `ui/viewer/VideoPlayerScreen.kt`, `ui/viewer/VideoPlayerViewModel.kt`, `ui/viewer/PhotoViewerScreen.kt`, `ui/navigation/Routes.kt`, `ui/navigation/NavGraph.kt`

---

**Phase 4 Exit Criteria:** Timeline loads 5,000 photos at 60fps. Photo viewer opens with shared element transition. Swipe between photos. NAS-only streams correctly. Download to device works with correct 1-month expiry. Videos play.

---

## Phase 5 — Folders, Albums & People

**Goal:** All three non-timeline browsing modes.

---

- [x] **Session 5.1 — Folder Browser** ✅ Done 2026-03-27
  - `FolderViewModel.kt`: `@HiltViewModel`. Fetches sub-folders on-demand from API via `SynologyPhotoService.listFolders()` (not cached in Room — lightweight). Photos from Room via `MediaRepository.observeFolder()`. Breadcrumb navigation stack with push/pop/jump-to-index. `thumbnailUrl()` helper.
  - `FolderScreen.kt`: Breadcrumb bar (horizontal scrollable row with NavigateNext chevrons). LazyColumn of folder rows (folder icon + name + chevron). LazyVerticalGrid(3-col) photo grid below folders, same cell pattern as TimelineScreen.
  - **Bottom Navigation added to NavGraph:** `NavigationBar` with Timeline + Folders tabs. Hidden on viewer/player screens. Uses `saveState`/`restoreState` for tab switching. Scaffold wraps NavHost with padding.
  - Routes: Added `FOLDER_BROWSER`. Wired in NavGraph with `onPhotoClick` → photo viewer.
  - **Folder photo count + cover:** `MediaDao.getCountByFolder(folderId)` and `getFirstByFolder(folderId)` queries. `Folder` data class extended with `photoCount`, `coverNasId`, `coverCacheKey`. `FolderViewModel.fetchFolders()` loads counts/covers from Room after API fetch. `FolderRow` updated: shows cover thumbnail (AsyncImage 48dp) or folder icon fallback, photo count text below name.
  - **Key files:** `ui/folder/FolderViewModel.kt`, `ui/folder/FolderScreen.kt`, `ui/navigation/Routes.kt`, `ui/navigation/NavGraph.kt`

---

- [x] **Session 5.2 — Albums** ✅ Done 2026-03-27
  - `AlbumsScreen.kt`: 2-column grid of album cards (cover thumbnail, name, count). `AlbumViewModel` observes `AlbumRepository.observeAll()` from Room.
  - `AlbumDetailScreen.kt`: TopAppBar with album name + back. 3-column photo grid (same pattern). `AlbumDetailViewModel` lazy-syncs album items from API on first open via `NasSyncEngine.syncAlbumItems(albumId)` → populates album_media join table → then observes from Room via `MediaRepository.observeAlbum()`.
  - `NasSyncEngine.syncAlbumItems(albumId)`: paginates `listAlbumItems` API, clears + repopulates album_media join table, upserts any media items not yet in Room (preserving local status).
  - Added Albums tab (PhotoAlbum icon) to bottom navigation. Routes: ALBUMS, ALBUM_DETAIL/{albumId}.
  - **Multi-select "Add to Album":** Timeline's multi-select action bar "Add to Album" button opens `AlbumPickerSheet` (Material3 `ModalBottomSheet`) showing all albums from `AlbumRepository.observeAll()`. Tap album → `viewModel.addSelectedToAlbum(albumId)` → clears selection.
  - **"Remove from album":** `AlbumDetailScreen` has its own multi-select (long-press → checkmarks). Action bar shows "Remove from album" (`RemoveCircle` icon) → `AlbumDetailViewModel.removeSelectedFromAlbum()` → `AlbumMediaDao.deleteByAlbumAndNasId()`.
  - **AlbumPickerSheet:** `ui/album/AlbumPickerSheet.kt` — `ModalBottomSheet` with `LazyColumn` of album rows (name + count). `onAlbumSelected(albumId)` callback.
  - **Key files:** `ui/album/AlbumViewModel.kt`, `ui/album/AlbumsScreen.kt`, `ui/album/AlbumDetailViewModel.kt`, `ui/album/AlbumDetailScreen.kt`, `sync/NasSyncEngine.kt`, `ui/navigation/Routes.kt`, `ui/navigation/NavGraph.kt`

---

- [x] **Session 5.3 — People (Face Albums)** ✅ Done 2026-03-27
  - `PeopleScreen.kt`: 2-column grid of person cards. Named people sorted alphabetically first, unnamed at bottom with 50% alpha dimming. Cover thumbnail, name (or "Unknown"), photo count.
  - `PersonDetailScreen.kt`: TopAppBar with person name + back. 3-column photo grid.
  - `PersonDetailViewModel.kt`: Fetches person's photos on-demand from API via `listPersonItemsParams(personId)` — paginates all pages. Photos held in local `StateFlow<List<MediaItem>>` (not stored in Room join table — face membership is ephemeral and managed by Synology).
  - `PeopleViewModel.kt`: Observes `PersonRepository.observeAll()`, sorts named first then unnamed.
  - Added `SynologyApiHelper.listPersonItemsParams(personId)` for `person_id`-filtered item listing.
  - Added People tab (People icon) to bottom navigation. Routes: PEOPLE, PERSON_DETAIL/{personId}. Bottom nav now has 4 tabs: Timeline, Folders, Albums, People.
  - **Design note:** Unlike albums (which use a join table), person photos are API-fetched on-demand only. No local join table — Synology re-processes faces frequently, and stale local state would be confusing.
  - **Key files:** `ui/people/PeopleViewModel.kt`, `ui/people/PeopleScreen.kt`, `ui/people/PersonDetailViewModel.kt`, `ui/people/PersonDetailScreen.kt`, `api/SynologyPhotoService.kt`, `ui/navigation/Routes.kt`, `ui/navigation/NavGraph.kt`

---

**Phase 5 Exit Criteria:** All three browsing modes work. Folder tree navigable. Albums add/remove photos. People grid loads faces with correct photos.

---

## Phase 6 — Search

**Goal:** Fast, useful search across the entire library.

---

- [x] **Session 6.1 — Search Screen** ✅ Done 2026-03-27
  - `SearchViewModel.kt`: Dual search — local Room (`MediaDao.searchByFilename`) results appear instantly (300ms debounce), NAS results (`SynologyPhotoService.searchItems`) merge in asynchronously, deduped by nasId. Recent searches stored in DataStore (unit-separator delimited, max 10).
  - `SearchScreen.kt`: Material3 `SearchBar` with `InputField`. Recent searches shown as `AssistChip` in `FlowRow` when query empty. Results in 3-column `LazyVerticalGrid`. "No results" empty state with search icon. `LinearProgressIndicator` while NAS query in-flight. Tap photo → photo viewer.
  - Added `RECENT_SEARCHES` preference key + `addRecentSearch()` / `clearRecentSearches()` / `recentSearches: Flow<List<String>>` to AppPreferencesRepository.
  - Search accessible from timeline top bar (search icon added to `TimelineScreen` actions, `onSearchClick` callback).
  - Route: `SEARCH` (not a bottom nav tab — accessed from timeline top bar).
  - **Filter chips:** `FlowRow` below search bar with `FilterChip` for Photos/Videos type filter + tag chips (first 10 from `TagDao.getAll()`). `SearchViewModel` tracks `selectedTagId`, `selectedMediaType`, `fromDate`, `toDate`. `MediaDao.searchFiltered()` — parameterized query with optional mediaType, fromDate, toDate, tagId (LEFT JOIN on media_tag). NAS results also filtered client-side. `setTagFilter()`, `setMediaTypeFilter()`, `setDateRange()`, `clearFilters()`.
  - **Room FTS:** Not needed — EXIF text fields (camera, lens) not stored in MediaEntity. Only filename is searchable, and LIKE query is sufficient for that.
  - **Key files:** `ui/search/SearchViewModel.kt`, `ui/search/SearchScreen.kt`, `data/preferences/AppPreferences.kt`, `data/preferences/AppPreferencesRepository.kt`, `ui/timeline/TimelineScreen.kt`, `ui/navigation/Routes.kt`, `ui/navigation/NavGraph.kt`

---

**Phase 6 Exit Criteria:** Search returns results from both local cache and NAS. Tag and date filters work. Results deduplicated correctly.

---

## Phase 7 — Map View

**Goal:** Pins on a map showing where photos were taken.

---

- [x] **Session 7.1 — Map Screen** ✅ Done 2026-03-27
  - `MapViewModel.kt`: Loads all geotagged items from Room (`MediaDao.getWithLocation()`). Grid-based clustering: cell size = `360 / (1 << zoom)`, groups items by lat/lng cell, averages positions per cluster. Returns `List<PhotoCluster>` (lat, lng, items, count, isSingle).
  - `MapScreen.kt`: osmdroid `MapView` in `AndroidView` with CartoDB Dark Matter tiles (same as eWeather). `DisposableEffect` for osmdroid config. Markers re-added on zoom change via `MapListener`. Single-photo pins → photo viewer. Cluster pins → `zoomToBoundingBox` with 1.3x scale animation. Cluster icons: colored `GradientDrawable` ovals — white (1), light blue (2–5), blue (6–20), dark blue (20+), sized by count. Centres on most recent geotagged photo at zoom 6.
  - Added Map tab (Map icon) to bottom navigation — now 5 tabs: Timeline, Folders, Albums, People, Map.
  - **My location button:** `FloatingActionButton` (bottom-end) with `MyLocation` icon. Checks `ACCESS_FINE_LOCATION` permission — if not granted, launches `ActivityResultContracts.RequestMultiplePermissions`. On grant: `LocationManager.getLastKnownLocation(FUSED/GPS/NETWORK)` → `mapView.controller.animateTo()` + zoom 14.
  - **Count text on cluster markers:** `createClusterDrawable` now draws count > 1 as `Bitmap` with `Canvas` — background circle + border + centered bold text (black on light, white on dark clusters). Size scales with count (36dp/44dp/52dp).
  - **Thumbnail on single-photo pins:** Single-photo markers load thumbnail via Coil `ImageLoader.execute()` (96px) in a coroutine. On success: creates circular bitmap with white border via `BitmapShader` + `Canvas.drawCircle`, sets as marker `icon`. Falls back to plain white circle while loading.
  - **Key files:** `ui/map/MapViewModel.kt`, `ui/map/MapScreen.kt`, `ui/navigation/Routes.kt`, `ui/navigation/NavGraph.kt`

---

**Phase 7 Exit Criteria:** All geotagged photos appear on dark map. Clustering works at all zoom levels. Single-photo pins tap to open photo viewer. Location button centres on device position.

---

## Phase 8 — Photo Editing

**Goal:** Non-destructive-feeling but actually overwrites — rotate, crop, adjust, push to NAS.

---

- [x] **Session 8.1 — Edit Pipeline** ✅ Done 2026-03-27
  - `PhotoEditor.kt`: Stateless utility — `rotate(bitmap, degrees)` via Matrix, `flip(bitmap, horizontal)`, `crop(bitmap, rect)` with bounds safety, `adjustColors(bitmap, brightness, contrast, saturation)` via combined ColorMatrix (saturation + brightness + contrast in single pass), `save(bitmap, file, quality)` as JPEG.
  - `EditUploadCoordinator.kt`: `@Singleton` orchestrator. `downloadOriginal(item)` — reuses local file if ON_DEVICE, otherwise streams from NAS to `cacheDir/edit_temp/`. `decodeBitmap(file)` — ImageDecoder primary (handles HEIC on API 34+), BitmapFactory fallback. `saveAndUpload(item, editedBitmap)` — saves JPEG → multipart upload to NAS (same filename = overwrite) → re-fetches item metadata via `getItem` for new cacheKey → updates Room → cleans temp files. `cleanupTempFiles()` for manual cleanup.
  - Added edit button (Edit icon, white) to PhotoViewerScreen top bar — hidden for videos. `onEdit(nasId)` callback.
  - Added EDIT/{nasId} route in Routes + NavGraph (placeholder composable until session 8.2).
  - **Note:** HEIC decoding works at minSdk 34 via ImageDecoder. No need for the API 29 fallback logic in the original plan.
  - **Key files:** `edit/PhotoEditor.kt`, `edit/EditUploadCoordinator.kt`, `ui/viewer/PhotoViewerScreen.kt`, `ui/navigation/Routes.kt`, `ui/navigation/NavGraph.kt`

---

- [x] **Session 8.2 — Edit Screen UI** ✅ Done 2026-03-27
  - `EditViewModel.kt`: `@HiltViewModel` with `SavedStateHandle`. On init: downloads original via `EditUploadCoordinator.downloadOriginal()`, decodes bitmap via `decodeBitmap()`. `previewBitmap: StateFlow<Bitmap?>` updated immediately on each edit. Operations: `rotateLeft()`, `rotateRight()`, `flipH()`, `flipV()` via `PhotoEditor`, `applyCrop(aspectRatio)` — center crop at ratio (null = no crop), `adjustColors(brightness, contrast, saturation)`. `save()` calls `EditUploadCoordinator.saveAndUpload()`. Tracks `dirty` state.
  - `EditScreen.kt`: Scaffold with TopAppBar (Cancel + Save button with spinner). Image preview via `Image(bitmap.asImageBitmap())` with `ContentScale.Fit`. `TabRow` with 3 tabs: Rotate (4 IconButtons: RotateLeft/RotateRight/FlipH/FlipV), Crop (4 AssistChips: Free/1:1/4:3/16:9 — applies center crop immediately), Adjust (3 Sliders: brightness -1..1, contrast -1..1, saturation 0..2 — applies on `onValueChangeFinished`). Auto-navigates back on successful save.
  - NavGraph: replaced edit placeholder with `EditScreen(onBack)`.
  - **Undo stack:** `undoStack: MutableList<Bitmap>` (max 10). `pushUndo()` before each edit. `undo()` pops and restores. Undo button (AutoMirrored.Undo) in TopAppBar, visible when `canUndo`.
  - **Cancel confirmation dialog:** `BackHandler(enabled = dirty)` intercepts back press. `AlertDialog` "Discard changes?" with Discard/Cancel buttons. Back arrow also shows dialog when dirty.
  - **Real-time color preview:** `colorBaseBitmap` snapshot taken on `beginColorAdjust()` (when Adjust tab selected). `adjustColorsPreview()` applies ColorMatrix to base bitmap on every `onValueChange` (real-time). `commitColorAdjust()` on `onValueChangeFinished` pushes undo and finalizes. No cumulative degradation.
  - **Drag-handle crop overlay:** `CropOverlay` composable — `Canvas` draws dimmed outside area + dashed white rect + 4 corner handle circles. Normalized `RectF` (0..1). `detectDragGestures` moves the whole rect. Aspect ratio chips snap rect via `snapCropToRatio()`. Check icon applies crop via `applyCropFromRect()`. Overlay enabled/disabled per tab selection.
  - **Key files:** `ui/editor/EditViewModel.kt`, `ui/editor/EditScreen.kt`, `ui/navigation/NavGraph.kt`

---

**Phase 8 Exit Criteria:** All edit operations produce correct output. Pipeline downloads original, applies edits, re-uploads to NAS. Thumbnail updates after re-upload.

---

## Phase 9 — Settings, Polish & Deploy

**Goal:** Complete settings, production feel, battery efficiency, deploy script.

---

- [x] **Session 9.1 — Settings Screen** ✅ Done 2026-03-27
  - `SettingsViewModel.kt`: `@HiltViewModel`. Exposes credential fields (nasUrl, username, password) as MutableStateFlow bound to CredentialStore. Preference StateFlows (syncIntervalHours, wifiOnlyUpload, autoEvictEnabled, lastSyncAt). `testConnection()` calls `SynologySession.login()` with ConnectionTestState (Idle/Testing/Success/Failed). `setSyncInterval()` updates preference + reschedules/cancels WorkManager via SyncScheduler. `forceFullResync()` resets lastSyncAt to 0 + syncs. `retryFailedUploads()` resets failed queue items to PENDING. `pendingUploadCount` from UploadQueueDao.
  - `SettingsScreen.kt`: Scrollable Column with 5 sections. **Connection:** 3 OutlinedTextFields + Test Connection button with live status. **Storage:** WiFi-only uploads Switch, auto-eviction Switch, rolling window info text. **Sync:** ExposedDropdownMenu for interval (Manual/1h/2h/6h), last sync timestamp, Sync Now + Force Full Resync buttons. **Upload Queue:** pending count + Retry Failed button. **About:** version + privacy note.
  - Route: SETTINGS (not bottom nav — accessed from timeline gear icon). Added Settings icon to TimelineScreen top bar (`onSettingsClick` callback).
  - **QuickConnect ID fallback:** `CredentialStore.quickConnectId` field. `SynologySession.login()` tries primary `nasUrl` first, if fails and `quickConnectId` is set, falls back to `https://{qcId}.quickconnect.to`. `attemptLogin()` private helper extracted. QuickConnect field in SettingsScreen.
  - **Upload folder configuration:** `UPLOAD_FOLDER_ID` preference in DataStore. `AppPreferencesRepository.uploadFolderId` flow + setter. `SettingsScreen` shows text field for folder ID. `SettingsViewModel.setUploadFolderId()`.
  - **Estimated local storage:** `MediaDao.getLocalStorageBytes()` — `SUM(fileSize) WHERE localPath IS NOT NULL`. `SettingsViewModel.localStorageBytes` StateFlow loaded on init. Displayed in Storage section as "Local storage used: X.XX GB" via `formatBytes()` helper.
  - **Key files:** `ui/settings/SettingsViewModel.kt`, `ui/settings/SettingsScreen.kt`, `ui/navigation/Routes.kt`, `ui/navigation/NavGraph.kt`, `ui/timeline/TimelineScreen.kt`

---

- [x] **Session 9.2 — Navigation + Bottom Bar** ✅ Done 2026-03-28
  - **Most of 9.2 was already built incrementally:** 5-tab bottom nav (5.1/5.2/5.3/7.1), search icon (6.1), settings gear (9.1), saveState/restoreState tab switching (5.1).
  - **New in this session:**
    - Upload badge on Timeline tab: `BadgedBox` with `Badge` showing pending upload count. Count observed from `UploadQueueDao.getAll()` in `MainActivity`, passed to `EGalleryNavGraph(pendingUploadCount, isNasReachable)`.
    - `OfflineBanner.kt`: error-container colored banner with CloudOff icon — "X photos pending upload — NAS unreachable". Animated expand/shrink. Shown at top of TimelineScreen when NAS unreachable + uploads pending.
    - `MainActivity` now injects `UploadQueueDao` + `SynologySession` and observes their flows for badge/banner state.
  - **Shared element transitions:** `SharedTransitionLayout` wraps `NavHost` in `NavGraph`. `TimelineScreen` and `PhotoViewerScreen` receive `AnimatedVisibilityScope` + `SharedTransitionScope` params. `PhotoGridCell`'s `AsyncImage` gets `Modifier.sharedElement(key = "photo_$nasId")`. `ZoomableImage` in viewer gets matching `sharedElement` key. Thumbnail morphs into full-screen image on navigation. Params nullable for backward compatibility when scopes aren't available.
  - **Key files:** `ui/components/OfflineBanner.kt`, `ui/navigation/NavGraph.kt`, `ui/timeline/TimelineScreen.kt`, `ui/MainActivity.kt`

---

- [x] **Session 9.3 — Performance & Battery** ✅ Done 2026-03-28
  - **Paging config:** Bumped to `pageSize=100, prefetchDistance=100` (was 50/20). Stable keys already present on all grids.
  - **Coil placeholders:** All grid AsyncImage calls already use `.background(surfaceVariant)` on the modifier which acts as a visual placeholder during load. No explicit `placeholder` Painter needed — the background shows through the transparent AsyncImage while loading. Consistent pattern across all 8 grid screens.
  - **Battery constraints added:**
    - `EvictionScheduler`: `setRequiresBatteryNotLow(true)` on daily periodic work
    - `ThumbnailPrefetchWorker`: `setRequiresBatteryNotLow(true)` on self-chained runs
    - `SyncScheduler.schedulePeriodic()`: `setRequiresBatteryNotLow(true)` on periodic sync
    - `SyncScheduler.syncNow()`: NO battery constraint (user-initiated, immediate)
    - `UploadWorker`: NO battery constraint (user just took a photo)
  - **CameraWatcher:** Uses `FileObserver` (kernel inotify) — no wakelock when idle, only fires on events. `START_STICKY` for restart.
  - **Map:** `getWithLocation()` is a one-shot Room query, no continuous GPS tracking.
  - **Key files:** `data/repository/MediaRepositoryImpl.kt`, `sync/EvictionScheduler.kt`, `sync/ThumbnailPrefetchWorker.kt`, `sync/SyncScheduler.kt`

---

- [x] **Session 9.4 — Error States + Edge Cases** ✅ Done 2026-03-28
  - **Local file missing:** `ViewerViewModel.imageUrl()` now checks `File(localPath).exists()` — if deleted externally, auto-updates Room to NAS_ONLY and falls back to xl thumbnail URL.
  - **Download error:** `PhotoViewerScreen` download FAB shows errorContainer color on `DownloadState.Error` — tap to retry. (404 handling: Synology returns error in JSON, caught by existing exception handler.)
  - **CameraWatcher SecurityException:** Wrapped `FileObserver` constructor + `startWatching()` in try/catch SecurityException — logs error gracefully if DCIM not accessible.
  - **Edit save failure:** `EditScreen` now has `SnackbarHost` — shows error message from `saveResult.exceptionOrNull()` when save fails. Only auto-backs on success.
  - **Auto-login on cold start:** `EGalleryApp.onCreate()` now calls `autoLogin()` — if `CredentialStore.isConfigured`, launches `SynologySession.login()` on IO scope. Thumbnails load immediately without waiting for user to trigger sync.
  - **Upload failures:** Already handled — `UPLOAD_FAILED` badge shown on timeline cells (session 4.1), settings shows pending count + retry button (session 9.1).
  - **NAS unreachable thumbnails:** Coil fails gracefully (no crash) — surfaceVariant background shows through. OfflineBanner shows when NAS unreachable + uploads pending (session 9.2).
  - **Key files:** `ui/viewer/ViewerViewModel.kt`, `ui/viewer/PhotoViewerScreen.kt`, `sync/CameraWatcher.kt`, `ui/editor/EditScreen.kt`, `EGalleryApp.kt`

---

- [x] **Session 9.5 — UI Polish** ✅ Done 2026-03-28
  - **Empty states with icons:** Added Material icons (48dp, 50% alpha) above text in all empty states — Timeline (PhotoLibrary), Albums (PhotoAlbum), People (People), Map (Map). Consistent Column layout with 12dp spacer.
  - **Black grid backgrounds:** All photo grid `LazyVerticalGrid` instances now have `Color.Black` background — Timeline, AlbumDetail, PersonDetail, FolderScreen, SearchScreen. Photos look better on black regardless of theme.
  - **Bottom nav label visibility:** `alwaysShowLabel = false` — labels only shown on selected tab (cleaner with 5 tabs).
  - **Coil crossfade:** Already enabled globally in EGalleryApp ImageLoader (session 1.6) — no additional work needed.
  - **Fast scroll bar:** Custom overlay on timeline grid. White scroll thumb (4dp wide, RoundedCornerShape) positioned by `firstVisibleItemIndex / itemCount` fraction. `animateFloatAsState` fades in on scroll (150ms), fades out on stop (1500ms). Month/year label popup (inverseSurface chip) appears alongside thumb showing current date header.
  - **Swipe-down dismiss:** `detectVerticalDragGestures` on viewer. Tracks `dismissOffsetY` (downward only). Content offset + scale-down (1→0.9×) + background alpha fade (1→0.5). Snaps dismiss when `offsetY > 300` threshold → `onBack()`. Resets on cancel.
  - **Spring pager animations:** `PagerDefaults.flingBehavior(snapAnimationSpec = spring(dampingRatio = 0.8f, stiffness = 400f))` on HorizontalPager. Bouncy page turns.
  - **Multi-select animations:** `animateFloatAsState(targetValue = if(isSelected) 0.92f else 1f, spring(dampingRatio = 0.6f, stiffness = 400f))` on PhotoGridCell. Selected cells scale down with spring bounce.
  - **Key files:** `ui/timeline/TimelineScreen.kt`, `ui/album/AlbumsScreen.kt`, `ui/album/AlbumDetailScreen.kt`, `ui/people/PeopleScreen.kt`, `ui/people/PersonDetailScreen.kt`, `ui/folder/FolderScreen.kt`, `ui/search/SearchScreen.kt`, `ui/map/MapScreen.kt`, `ui/navigation/NavGraph.kt`

---

- [x] **Session 9.6 — Deploy Script + Logging** ✅ Done 2026-03-28
  - `apps/egallery/scripts/deploy.sh`: Same pattern as eMusic — checks ADB device, builds release APK (`:apps:egallery:app:assembleRelease`), installs via `adb install -r`. Made executable.
  - Release build verified — `assembleRelease` completes successfully with R8 minification + resource shrinking.
  - **Already done in session 1.1:** Timber debug-only (`BuildConfig.DEBUG` gate), crash handler (`filesDir/crash.log`), ProGuard rules for all dependencies.
  - **Key files:** `apps/egallery/scripts/deploy.sh`

---

**Phase 9 Exit Criteria:** App is complete and polished. All error states handled. 60fps on all screens. Deploy script works. Battery usage minimal.

---

## Known Tricky Points for Claude Code

**1. `_sid` injection in Coil**
The same `OkHttpClient` instance must be shared between Retrofit and Coil. Do not create a separate `OkHttpClient` for Coil — the `SynologyAuthInterceptor` on the shared instance is what makes thumbnail URLs work. If Claude Code creates a separate client for Coil, thumbnails will return 401.

**2. Negative `nasId` for pending uploads**
Photos taken before upload use a temporary negative `nasId`. Any Room query that fetches "all photos" must handle the case where `nasId` is negative. Paging queries ordered by `captureDate DESC` will work fine since `nasId` isn't in the sort; but any lookup by `nasId` must account for this. When NAS assigns the real ID post-upload, update ALL references including `AlbumMediaEntity` and `MediaTagEntity` rows.

**3. Coil disk cache vs thumbnail path in Room**
`thumbnailPath` in `MediaEntity` is a sentinel (`"cached"`) not an actual file path — Coil manages its own disk cache keyed by URL. Do not try to manage thumbnail files manually. The `thumbnailPath` field just indicates "this item's thumbnail has been downloaded at least once." On subsequent loads, Coil serves from its own cache.

**4. `CameraWatcher` must be a foreground service**
GrapheneOS has stricter background execution limits than stock Android. `CameraWatcher` must call `startForeground()` within 10 seconds of `onStartCommand` or it will be killed. The persistent notification must use `IMPORTANCE_MIN` (`NotificationCompat.PRIORITY_MIN`) so it doesn't clutter the notification shade — users can fully hide min-priority notifications in system settings without losing the service.

**5. Edit overwrite via Synology API**
Synology Photos doesn't have an explicit "overwrite" endpoint. The overwrite is achieved by uploading to the same folder with the same filename — Synology's `SYNO.Foto.Upload.Item` replaces files with matching names. Test this against your specific DSM version before relying on it.

**6. Thumbnail `cache_key` changes after edit**
When a photo is overwritten on NAS, Synology regenerates its thumbnails and assigns a new `cache_key`. After the edit upload completes, the app must re-fetch the item metadata (`SYNO.Foto.Browse.Item.get`) to get the new `cache_key` and update `MediaEntity.cacheKey` in Room. Otherwise the thumbnail URL will be stale and return 404. This is specified in `EditUploadCoordinator` Step 8 — do not skip it.

**7. Album and tag queries require join tables — not blobs**
`albumIds` and `tags` are NOT stored as `List<Int>` / `List<String>` fields on `MediaEntity`. They live in `AlbumMediaEntity` and `MediaTagEntity` join tables. Room cannot query inside a serialised blob. Any attempt to store these as TypeConverter lists and query with `LIKE` or `IN` on them will either not compile or perform a catastrophic full table scan. The join table approach is the only correct one.

**9. `GET_CONTENT` picker mode changes the entire UI**
When another app launches eGallery with `ACTION_GET_CONTENT` (e.g. WhatsApp asking "choose a photo to send"), the app must behave as a modal picker, not a gallery. The difference: no bottom navigation bar, no settings access, the top bar shows "Select a photo" instead of "eGallery", tapping a photo calls `setResult(RESULT_OK, Intent().setData(contentUri))` and `finish()` instead of opening the viewer. `MainActivity` detects this mode via `intent.action == Intent.ACTION_GET_CONTENT` and passes a `isPickerMode: Boolean` flag through the NavGraph. Every screen that appears in picker mode must suppress normal navigation and surface the selection behaviour instead. This is a non-trivial UI branch — don't let Claude Code treat it as a minor edge case.
Use `ImageDecoder.createSource(file)` (available at minSdk 29) as the primary decoder for all image formats — it handles HEIC on most devices. Do not use `BitmapFactory` for HEIC as it cannot decode this format. Always wrap `ImageDecoder` HEIC decoding in `try/catch DecodeException` because HEIC codec availability is device-dependent on API 29; show a graceful error rather than crashing if it fails.

---

## Claude Code Execution Notes

1. **No GMS** — `android.location.LocationManager` only; no `FusedLocationProviderClient`
2. **Shared OkHttpClient** — Retrofit and Coil must use the same instance; `_sid` injector must cover all HTTP requests
3. **NAS is source of truth** — never delete from Room without confirming the NAS operation succeeded first (except eviction, which is safe by design)
4. **nasId uniqueness** — enforce `@Index(nasId, unique = true)` in Room; temporary negative IDs are local-only
5. **Test commands:** `./gradlew lint && ./gradlew testDebug && adb install -r`
6. **minSdk 29** — use `FileObserver(File, mask)` constructor (API 29+), not the deprecated string constructor

---

## Changelog

| Date | Change |
|---|---|
| 2026-03-23 | Initial plan — 9 phases, 28 sessions |
| 2026-03-23 | Added default gallery registration: intent filters for image/*/video/*, GET_CONTENT picker mode, ACTION_SEND share target, MANAGE_MEDIA permission, default app prompt; Tricky Point 9 added for picker mode UI branch |
