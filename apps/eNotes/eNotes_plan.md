# eNotes — App Development Plan

A privacy-first, Apple Notes-inspired note-taking app for Pixel devices running GrapheneOS, with WebDAV bidirectional sync and biometric/password note locking.

---

## 1. Project Overview

| Property | Detail |
|---|---|
| **App Name** | eNotes |
| **Platform** | Android (targeting GrapheneOS on Pixel devices) |
| **Min SDK** | API 33 (Android 13) |
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose |
| **Architecture** | MVVM + Clean Architecture |
| **Sync Protocol** | WebDAV (bidirectional) |
| **Local Storage** | Room (SQLite) + encrypted file store |

---

## 2. Core Goals

- Mirror the Apple Notes experience: fast, fluid, distraction-free writing
- Full offline-first operation — notes are always available without a network connection
- Bidirectional WebDAV sync (Nextcloud, ownCloud, any WebDAV server)
- Per-note locking via fingerprint (BiometricPrompt) and/or password
- Privacy-hardened for GrapheneOS — no Google Play Services dependencies, no telemetry

---

## 3. Feature Set

### 3.1 Notes & Organization

- **Folders** — hierarchical folder structure (unlimited depth)
- **Smart Folders** — auto-populated views: All Notes, Recently Deleted, Locked Notes, Shared
- **Tags** — inline `#tag` support with a tag browser sidebar
- **Quick Note** — widget and notification shortcut to instantly create a note (see §3.8 for full widget spec)
- **Pin to top** — pin important notes within any folder
- **Sort options** — by date edited, date created, title (A–Z / Z–A)
- **Search** — full-text search across title and body, with folder-scoped search

### 3.2 Rich Text Editor

- **Formatting toolbar** — Bold, Italic, Underline, Strikethrough, Inline Code
- **Headings** — Title, Heading, Subheading, Body (mirrors Apple Notes styles)
- **Lists** — bulleted, numbered, and checklist (with tappable checkboxes)
- **Tables** — insert/resize/delete rows and columns inline
- **Code blocks** — monospaced, syntax-highlighted code blocks
- **Dividers** — horizontal rule insertion
- **Undo / Redo** — deep history stack
- **Markdown import/export** — round-trip conversion to `.md` files
- **Note linking** — `[[Note Title]]` style wikilinks between notes

### 3.3 Media & Attachments

- **Images** — inline image insertion from gallery or camera
- **Sketches** — built-in drawing canvas (pressure-sensitive with Pixel stylus support)
- **File attachments** — attach any file, previewed inline for common types (PDF, images)
- **Scanning** — document scanning via camera with auto-crop and perspective correction
- **Audio recordings** — inline voice memos with waveform display

### 3.4 Note Locking & Security

- **Per-note lock** — any note can be individually locked
- **Unlock methods**
  - Fingerprint via Android `BiometricPrompt` API
  - Device PIN / Password fallback
  - Custom app-level password (independent of device credentials)
- **Lock behaviour** — locked notes display only title in the list; content is hidden until unlocked
- **Auto-lock** — configurable timeout (immediately, 1 min, 5 min, on app close)
- **Encryption** — locked note content encrypted at rest using AES-256-GCM; key derived via PBKDF2 / stored in Android Keystore
- **No plaintext sync** — locked notes are synced in their encrypted form; the WebDAV server never sees plaintext

### 3.5 WebDAV Sync

- **Bidirectional sync** — changes on device push to server; remote changes pull to device
- **Conflict resolution** — last-write-wins with a conflict copy created (surfaced in a "Conflicts" smart folder)
- **Sync triggers** — on app open, on app close, on note save (debounced 3 s), manual pull-to-refresh
- **Background sync** — periodic WorkManager job (configurable interval: 15 min / 30 min / 1 hr / manual only)
- **Multiple accounts** — support for more than one WebDAV endpoint
- **Supported servers** — Synology DSM (WebDAV Server package), Nextcloud, ownCloud, Hetzner Storage Box, any RFC 4918-compliant server
- **Storage format** — one `.enote` file per note (JSON envelope + encrypted or plaintext content blob); folder structure mirrors app hierarchy on server
- **Certificate pinning / custom CA** — supports self-signed certs and user-imported CAs; particularly relevant for Synology users with a self-signed DSM certificate or a Let's Encrypt cert on a local DDNS hostname
- **Sync status indicators** — per-note sync badge (synced ✓, pending ⏳, conflict ⚠, error ✗)

### 3.7 Joplin Import (via WebDAV)

- **Source** — connect directly to the Joplin WebDAV sync directory on a Synology NAS (via Synology's built-in WebDAV Server package)
- **Synology URL format** — `https://<nas-ip-or-ddns>:5006/joplin` (port 5006 for HTTPS, 5005 for HTTP; the trailing path must match the folder Joplin was configured to sync into on the NAS)
- **Credential entry** — same WebDAV setup UI reused; user enters the Synology WebDAV URL, a DSM username, and that account's password (or an app-specific password if 2FA is enabled on DSM)
- **Discovery** — PROPFIND the sync root to enumerate all `.md` resource files and the `info.json` manifest
- **Note parsing** — each Joplin note is a `.md` file: a Markdown body followed by a `---`-delimited metadata footer containing `id`, `title`, `parent_id`, `created_time`, `updated_time`, `is_todo`, `todo_completed`, `source_url`, and `_type`
- **Notebook → Folder mapping** — Joplin notebooks are also `.md` files with `_type: folder`; the importer resolves the full `parent_id` chain to reconstruct nested folder hierarchy in eNotes
- **Tag import** — Joplin tags (`_type: tag`) and their `note_tag` join records are parsed and attached to imported notes as eNotes `#tags`
- **Resource / attachment handling** — Joplin resource files (UUID-named blobs in the sync root) are referenced from note bodies as `![](:/uuid)` links; the importer downloads each resource, re-uploads it as an eNotes attachment, and rewrites the inline reference
- **Markdown → Rich Text conversion** — Joplin Markdown is converted to eNotes rich-text body format, preserving headings, bold/italic, lists, checkboxes (`- [ ]` / `- [x]`), tables, code blocks, and math expressions
- **To-do notes** — Joplin to-dos (`_type: todo`) are imported as notes with a checklist item at the top reflecting the `todo_completed` state; a `#todo` tag is applied automatically
- **E2EE-protected notes** — if Joplin E2EE is active, the importer detects encrypted blobs, warns the user that those notes cannot be imported without the master password, and skips them gracefully (or prompts for the password if E2EE decryption support is added in a later version)
- **Import modes**
  - *Preview* — dry-run showing a breakdown of notes, notebooks, tags, and resources found before committing
  - *Import all* — full one-time migration
  - *Selective import* — user picks individual notebooks to import
- **Duplicate handling** — notes with a matching Joplin `id` already present in eNotes are skipped by default, with an option to overwrite
- **Progress UI** — paginated progress screen: "Fetching index… Importing notebooks… Importing notes (42/317)… Downloading resources (12/58)…"
- **Post-import report** — summary of imported notes, skipped encrypted notes, failed resources, and any parse errors

### 3.6 GrapheneOS / Privacy Considerations

- **No Google Play Services dependency** — use only AOSP / Jetpack APIs; no Firebase, no Google Maps, no Crashlytics
- **No analytics or telemetry** — zero data collection
- **Network permissions scoped** — `INTERNET` permission used solely for WebDAV; no other outbound connections
- **Sandboxed storage** — all local data in app-private directory; no `READ_EXTERNAL_STORAGE` unless user explicitly attaches a file
- **Reproducible builds** — build pipeline configured for reproducibility

### 3.8 Quick Note Widget & Lock-Screen Behaviour

- **Widget type** — Glance-based (Jetpack Glance API) home-screen widget; two sizes: 2×1 (single "New Note" tap target) and 2×2 (tap to create + last 3 note titles as shortcuts)
- **Lock-screen availability** — widget is **not** exposed on the GrapheneOS lock screen; note content must never appear without authentication. The widget is home-screen only and only reachable after device unlock
- **Locked app state** — if eNotes' own auto-lock has triggered (app was backgrounded past the timeout), tapping the widget opens the app directly to the biometric/password prompt rather than creating a note; the new note is created immediately after successful auth
- **No content preview on widget** — the 2×2 widget shows note titles only, never body text or attachment thumbnails, to prevent shoulder-surfing on the home screen
- **Notification shortcut** — a persistent low-priority notification with a "New Note" action is optionally available as an additional quick-access path; disabled by default
- **GrapheneOS-specific** — no `RECEIVE_BOOT_COMPLETED` broadcast used for the widget; Glance handles its own scheduling via WorkManager without needing boot permission

### 3.9 Backup & Restore

- **What is backed up** — full SQLCipher Room database (all notes, folders, tags, revision history) + all attachment blobs + app preferences (WebDAV credentials excluded by default for security)
- **Backup format** — single encrypted `.enotesbackup` archive (ZIP container + AES-256-GCM encryption); protected by a user-set backup password independent of the biometric/note-lock password
- **Export destinations**
  - Local file via Android Storage Access Framework (user picks any folder, including USB storage)
  - Directly to the Synology NAS via WebDAV into a `/eNotes-backups/` subdirectory
- **Pre-migration safety** — a backup is automatically created before any Room schema migration runs; stored in app-private storage for 24 hours so a bad migration can be rolled back
- **Restore flow** — user selects a `.enotesbackup` file → enters backup password → app validates archive integrity (SHA-256 checksum embedded in archive header) → existing DB is replaced → app restarts
- **Backup schedule** — manual on demand; optionally automated weekly via WorkManager, exported to the NAS WebDAV path
- **Backup rotation** — when auto-backup to NAS is enabled, the 5 most recent backup files are retained and older ones deleted automatically

### 3.10 Note History & Versioning

- **Per-note revision log** — every save of a note body creates a revision entry; stored in a `note_revisions` Room table
- **Retention policy** — last **20 revisions** per note retained locally; older revisions pruned automatically on save. No server-side revision storage (revisions are device-local only)
- **What is stored per revision** — full body snapshot (or encrypted snapshot for locked notes), timestamp, and a character-delta size for display
- **History viewer** — accessible from the note's overflow menu → "Note History"; presents a chronological list of revisions with timestamp and approximate size change (e.g. "+340 chars")
- **Restore from revision** — tapping a revision shows a read-only preview; a "Restore this version" button replaces the current body (which itself becomes a new revision, so the restore is non-destructive)
- **Locked notes** — revision snapshots for locked notes are encrypted with the same AES-256-GCM key as the live note; they cannot be viewed without unlocking first
- **Storage budget** — revision snapshots are stored as compressed diffs where possible (Kotlin `java.util.zip`); Room enforces a per-note cap of 20 revisions regardless of size

---

## 4. Architecture

```
┌───────────────────────────────────┐
│           UI Layer                │
│  (Jetpack Compose + ViewModels)   │
└────────────┬──────────────────────┘
             │
┌────────────▼──────────────────────┐
│         Domain Layer              │
│  (Use Cases, Business Logic)      │
└────────────┬──────────────────────┘
             │
┌────────────▼──────────────────────┐
│         Data Layer                │
│  ┌──────────────┐ ┌─────────────┐ │
│  │ Local (Room) │ │ WebDAV Repo │ │
│  └──────────────┘ └─────────────┘ │
└───────────────────────────────────┘
```

### Key Components

- **NoteRepository** — single source of truth; merges local Room DB with remote WebDAV state
- **SyncEngine** — handles diff computation, upload queue, download queue, and conflict detection
- **CryptoManager** — wraps Android Keystore; handles key generation, AES-GCM encrypt/decrypt for locked notes
- **BiometricManager** — wraps `BiometricPrompt`; handles auth flow and fallback
- **EditorViewModel** — manages editor state, formatting commands, undo stack
- **WebDavClient** — Sardine-Android (or custom OkHttp-based client) for RFC 4918 operations (PROPFIND, PUT, GET, DELETE, MKCOL, MOVE)
- **JoplinImporter** — standalone import pipeline; connects to a Joplin WebDAV root, parses the flat file store, reconstructs hierarchy, converts Markdown to eNotes rich-text, and downloads resources; runs entirely in a background coroutine with progress callbacks
- **BackupManager** — produces and consumes `.enotesbackup` archives; handles AES-256-GCM encryption, checksum validation, pre-migration auto-backup, and Synology WebDAV export
- **RevisionStore** — manages the `note_revisions` Room table; handles snapshot creation on save, retention pruning, diff compression, and restore operations
- **GlanceWidget** — Jetpack Glance home-screen widget; integrates with BiometricManager to gate note creation behind auth when auto-lock is active

---

## 5. Data Model

### Note

```kotlin
data class Note(
    val id: UUID,
    val title: String,
    val bodyJson: String,          // Lexical / custom JSON rich text format
    val folderId: UUID?,
    val tags: List<String>,
    val isPinned: Boolean,
    val isLocked: Boolean,
    val encryptedBody: ByteArray?, // Non-null when isLocked = true
    val createdAt: Instant,
    val editedAt: Instant,
    val syncStatus: SyncStatus,
    val remoteEtag: String?,
    val attachments: List<Attachment>
)
```

### Folder

```kotlin
data class Folder(
    val id: UUID,
    val name: String,
    val parentId: UUID?,
    val iconEmoji: String?,
    val createdAt: Instant,
    val syncStatus: SyncStatus
)
```

### SyncStatus

```kotlin
enum class SyncStatus { SYNCED, PENDING_UPLOAD, PENDING_DELETE, CONFLICT, ERROR }
```

### NoteRevision

```kotlin
data class NoteRevision(
    val id: UUID,
    val noteId: UUID,
    val bodySnapshot: String,           // Full rich-text JSON snapshot (plaintext notes)
    val encryptedSnapshot: ByteArray?,  // AES-256-GCM snapshot (locked notes)
    val createdAt: Instant,
    val deltaChars: Int                 // Signed char-count delta vs previous revision
)
```

---

## 6. UI / UX Design

### Navigation Structure

```
Bottom Nav / Side Rail
├── 📁 Folders (home)
│   ├── All Notes
│   ├── [User Folders...]
│   └── Recently Deleted
├── 🔍 Search
├── 🔒 Locked Notes
└── ⚙️  Settings
```

### Key Screens

| Screen | Description |
|---|---|
| **Folder List** | Two-pane on tablets; single column on phone. Swipe to delete/pin. |
| **Note List** | Gallery or list view toggle. Pull-to-refresh triggers sync. |
| **Editor** | Full-screen immersive editor. Floating format toolbar appears on text selection. |
| **Lock Screen** | Biometric prompt overlay; password entry as fallback. |
| **WebDAV Setup** | Server URL, username, password, test connection, cert trust dialog. |
| **Joplin Import** | Step-by-step wizard: enter WebDAV URL → preview index → select notebooks → live progress → post-import report. |
| **Note History** | Chronological revision list with timestamps and delta sizes; read-only preview with one-tap restore. |
| **Backup & Restore** | Export to local storage or Synology NAS; restore from `.enotesbackup` file with password entry and integrity check. |
| **Settings** | Sync interval, auto-lock timeout, theme, font size, export/import. |

### Design Language

- Follow **Material You (Material 3)** with dynamic color theming
- **Dark mode** first (GrapheneOS users skew toward dark themes)
- Fluid transitions — shared element transitions between note list and editor
- Typography: `Lato` or system `sans-serif` for UI; `Merriweather` option for reading mode
- Toolbar collapses on scroll (CollapsingToolbarLayout behaviour in Compose)

---

## 7. Technology Stack

| Layer | Library / Tool |
|---|---|
| UI | Jetpack Compose, Material 3 |
| Navigation | Navigation Compose |
| State | ViewModel, StateFlow, Kotlin Coroutines |
| Local DB | Room + SQLCipher (encrypted DB) |
| Rich Text | Custom Compose editor (or port of Quill delta model) |
| WebDAV | OkHttp + custom RFC 4918 client |
| Crypto | Android Keystore + AES-256-GCM |
| Biometrics | AndroidX Biometric (`BiometricPrompt`) |
| Background Sync | WorkManager |
| Image Loading | Coil |
| DI | Hilt |
| Testing | JUnit 5, MockK, Compose UI Testing, Robolectric |
| Build | Gradle (Kotlin DSL), R8 minification |
| Distribution | F-Droid / direct APK sideload |

---

## 8. Security Model

```
┌─────────────────────────────────────────────────────┐
│                  Threat Model                       │
├─────────────────┬───────────────────────────────────┤
│ Threat          │ Mitigation                        │
├─────────────────┼───────────────────────────────────┤
│ Physical access │ Locked notes encrypted at rest    │
│ to device       │ with keys in Android Keystore;    │
│                 │ Keystore invalidated on new enrol- │
│                 │ ment if configured                │
├─────────────────┼───────────────────────────────────┤
│ Malicious server│ Locked notes synced as ciphertext;│
│ / network sniff │ TLS enforced; cert pinning option │
├─────────────────┼───────────────────────────────────┤
│ App data dump   │ SQLCipher encrypts the entire DB; │
│ (ADB backup)    │ `allowBackup=false` in manifest   │
├─────────────────┼───────────────────────────────────┤
│ Shoulder surf   │ Auto-lock; locked note content    │
│                 │ never rendered without auth       │
└─────────────────┴───────────────────────────────────┘
```

---

## 9. Development Phases

### Phase 1 — Core (Sessions 1.1–1.8)

- [x] **Session 1.1 — Project Scaffolding**
  - Multi-module: `app`, `domain`, `data`, `feature/notes`, `feature/editor`, `feature/settings`
  - Convention plugins, AndroidManifest, ENotesApp, MainActivity, NavHost shell
  - **Exit test:** `assembleDebug` builds clean

- [x] **Session 1.2 — Domain Models**
  - Note, Folder, SyncStatus, NoteRevision, Attachment data classes
  - **Exit test:** compiles

- [x] **Session 1.3 — Room Database + DAOs**
  - Entities with indices, FTS4, DAOs, AppDatabase v1, mappers, DatabaseModule
  - **Exit test:** schema validation passes

- [x] **Session 1.4 — Repository + DataStore**
  - NoteRepository, FolderRepository interfaces + impls, AppPreferencesRepository
  - **Exit test:** `testDebug` passes

- [x] **Session 1.5 — Navigation Shell + Note List**
  - Routes, FolderListScreen, NoteListScreen, FAB → editor
  - **Exit test:** app installs, shows list, FAB opens editor

- [x] **Session 1.6 — Rich Text Editor**
  - EditorScreen, formatting toolbar, headings, lists, checklists, undo/redo, auto-save
  - **Exit test:** create note, format, reopen — persisted

- [x] **Session 1.7 — Full-Text Search**
  - SearchScreen, FTS query, debounce
  - **Exit test:** search finds notes

- [x] **Session 1.8 — Folder CRUD + Smart Folders**
  - Create/rename/delete folders, move notes, nested folders, smart folders (All/Deleted/Pinned)
  - **Exit test:** folder ops work, soft-delete works

### Phase 2 — WebDAV Sync (Sessions 2.1–2.4)

- [x] **Session 2.1 — WebDAV Client**
  - RFC 4918: PROPFIND, GET, PUT, DELETE, MKCOL, MOVE via OkHttp, trust-all SSL, XML parsing
- [x] **Session 2.2 — SyncEngine + .enote Format**
  - JSON .enote files, diff by etag, upload/download, folder mirroring via MKCOL
- [x] **Session 2.3 — Conflict Resolution + Background Sync**
  - Last-write-wins + conflict copies, sync badges (✓⏳⚠✗), WorkManager periodic
- [x] **Session 2.4 — WebDAV Setup Screen**
  - URL/credentials, test connection, cert trust dialog, interval picker

### Phase 3 — Security (Sessions 3.1–3.3)

- [x] **Session 3.1 — CryptoManager + Note Locking**
  - Android Keystore + AES-256-GCM, per-note lock toggle, encrypted body field
- [x] **Session 3.2 — BiometricPrompt + Auto-lock**
  - BiometricPrompt integration, custom password fallback, auto-lock timer
- [x] **Session 3.3 — SQLCipher DB Encryption**
  - Full database encryption with SQLCipher

### Phase 4 — Media, Polish & Joplin Import (Sessions 4.1–4.8)
- [ ] Inline image support
- [ ] Drawing / sketch canvas
- [ ] Document scanning
- [ ] Audio recordings
- [ ] File attachments
- [ ] Tables in editor
- [ ] Markdown import/export
- [ ] Note linking (`[[wikilinks]]`)
- [ ] Tags and tag browser
- [ ] **JoplinImporter** — WebDAV discovery + flat-file enumeration via PROPFIND
- [ ] **Joplin metadata parser** — extract `id`, `title`, `parent_id`, `created_time`, `updated_time`, `_type`, `is_todo`, `todo_completed` from `.md` footer blocks
- [ ] **Notebook hierarchy reconstruction** — resolve `parent_id` chains into eNotes folder tree
- [ ] **Tag + note_tag join parsing** — import Joplin tags and re-attach them to notes
- [ ] **Markdown → rich-text converter** — headings, bold/italic, lists, checkboxes, tables, code blocks
- [ ] **Resource downloader** — fetch UUID blob files, re-link inline `![](:/uuid)` references as eNotes attachments
- [ ] **To-do note handling** — map `_type: todo` + `todo_completed` to checklist note with `#todo` tag
- [ ] **Encrypted note detection** — detect Joplin E2EE blobs, surface skip/warn UI
- [ ] **GlanceWidget** — home-screen widget (2×1 and 2×2), auth-gated note creation, no lock-screen exposure
- [ ] **BackupManager** — `.enotesbackup` archive creation, AES-256-GCM encryption, checksum, restore flow
- [ ] **Auto-backup before migration** — hook into Room's `RoomDatabase.Builder.addMigrations` to snapshot DB first
- [ ] **Backup export to Synology NAS** via WebDAV + local SAF export
- [ ] **RevisionStore** — `note_revisions` table, snapshot-on-save, 20-revision cap, pruning
- [ ] **Note History UI** — revision list screen, read-only preview, restore action
- [ ] **Encrypted revision snapshots** for locked notes

### Phase 5 — Release (Session 5.1)
- [x] GrapheneOS-specific QA pass (no GMS calls)
- [x] Reproducible build configuration
- [x] F-Droid metadata (`fastlane/` structure, screenshots)
- [x] Accessibility audit (TalkBack, font scaling)
- [x] Performance profiling (Compose recomposition, sync latency)
- [x] Beta via direct APK → F-Droid submission

---

## 10. Joplin Import — Technical Deep Dive

### 10.1 Joplin WebDAV File Store Structure

When Joplin syncs to WebDAV it writes a **flat directory** of UUID-named files to the sync root — there are no subdirectories per notebook. All items (notes, notebooks, tags, resources, and join records) share the same root and are distinguished solely by their `_type` metadata field.

```
<webdav-root>/
├── info.json                          # Sync manifest (client IDs, sync version)
├── locks/
│   └── <lock-uuid>.json
├── <note-uuid>.md                     # _type: note
├── <notebook-uuid>.md                 # _type: folder
├── <tag-uuid>.md                      # _type: tag
├── <note_tag-uuid>.md                 # _type: note_tag  (join: note ↔ tag)
├── <resource-uuid>.md                 # _type: resource  (attachment metadata)
└── .resource/
    └── <resource-uuid>                # Raw binary blob (the actual attachment)
```

### 10.2 Joplin `.md` File Anatomy

Each `.md` file contains a Markdown body separated from a key-value metadata footer by a blank line. Metadata keys are written as `key: value` lines — there is no YAML front-matter fence.

```
Meeting notes for Q1 planning

- Attendees: Alice, Bob
- Action: update roadmap by Friday

id: 3f2504e0-4f89-11d3-9a0c-0305e82c3301
parent_id: 7c9e6679-7425-40de-944b-e07fc1f90ae7
created_time: 2025-11-14T09:12:00.000Z
updated_time: 2025-11-14T11:47:23.000Z
is_conflict: 0
latitude: 0
longitude: 0
altitude: 0
author:
source_url:
is_todo: 0
todo_due: 0
todo_completed: 0
source: joplin-mobile
source_application: net.cozic.joplin-mobile
application_data:
order: 0
user_created_time: 2025-11-14T09:12:00.000Z
user_updated_time: 2025-11-14T11:47:23.000Z
encryption_cipher_text:
encryption_applied: 0
markup_language: 1
is_shared: 0
share_id:
conflict_original_id:
master_key_id:
_type: 1
```

`_type` integer mapping: `1` = note, `2` = folder, `3` = setting, `4` = resource, `5` = tag, `6` = note_tag, `9` = master_key, `10` = item_change, `11` = note_resource.

### 10.3 Import Pipeline

```
JoplinImporter
│
├── Step 1 — Connect & Discover
│   ├── PROPFIND sync root (depth: 1)
│   ├── Parse info.json (sync version check — must be v3)
│   └── Collect all .md hrefs + .resource/ blob hrefs
│
├── Step 2 — Parse & Classify
│   ├── Download each .md file (parallelised, bounded coroutine pool)
│   ├── Split body / metadata footer on last blank-line boundary
│   ├── Parse metadata key-value pairs into JoplinItem sealed class
│   │   ├── JoplinNote
│   │   ├── JoplinNotebook
│   │   ├── JoplinTag
│   │   ├── JoplinNoteTag (join)
│   │   └── JoplinResource (metadata)
│   └── Detect encryption_applied: 1 → quarantine, warn user
│
├── Step 3 — Build Hierarchy
│   ├── Recursively resolve parent_id chains → eNotes Folder tree
│   └── Assign each JoplinNote to its resolved eNotes folderId
│
├── Step 4 — Tag Resolution
│   ├── Build tag UUID → tag name map from JoplinTag items
│   ├── Build note UUID → [tag UUIDs] map from JoplinNoteTag items
│   └── Map to eNotes tag strings per note
│
├── Step 5 — Markdown Conversion
│   ├── Convert Markdown body → eNotes rich-text JSON
│   │   ├── ATX headings (# / ## / ###) → Heading styles
│   │   ├── **bold**, *italic*, ~~strikethrough~~, `code`
│   │   ├── Bullet / ordered lists
│   │   ├── - [ ] / - [x] checkboxes → Checklist items
│   │   ├── GFM tables → eNotes Table nodes
│   │   ├── Fenced code blocks (with language hint)
│   │   ├── $…$ / $$…$$ math (stored as raw LaTeX block)
│   │   └── ![](:/resource-uuid) → placeholder (resolved in Step 6)
│   └── is_todo: 1 → prepend top-level Checklist item, apply #todo tag
│
├── Step 6 — Resource Download & Re-link
│   ├── For each resource UUID referenced in converted body:
│   │   ├── GET .resource/<uuid> blob from WebDAV
│   │   ├── Read mime_type from JoplinResource metadata .md file
│   │   ├── Store as eNotes Attachment (local + queued for eNotes WebDAV upload)
│   │   └── Rewrite placeholder in rich-text body → eNotes attachment reference
│   └── Report any 404 / download failures in post-import log
│
└── Step 7 — Persist
    ├── Insert resolved Folder tree into Room DB
    ├── Insert Notes with converted body + attachments into Room DB
    ├── Mark all imported notes as PENDING_UPLOAD (will sync on next cycle)
    └── Emit ImportResult(imported, skippedEncrypted, failedResources, errors)
```

### 10.4 Synology NAS — Connection Details

Joplin's WebDAV sync target on a Synology NAS is served by DSM's **WebDAV Server** package. Key details the importer must handle:

| Property | Detail |
|---|---|
| **HTTPS port** | `5006` (default; may be customised in WebDAV Server settings) |
| **HTTP port** | `5005` — importer should warn and prefer HTTPS |
| **Base path** | Whatever folder the user configured in Joplin, e.g. `/joplin` or `/homes/<user>/joplin` |
| **Full example URL** | `https://nas.local:5006/joplin` or `https://<QuickConnect-ID>.synology.me:5006/joplin` |
| **Auth** | HTTP Basic Auth with DSM account credentials; if DSM 2FA is on, user must generate an **app-specific password** in DSM → Personal → Account → App Passwords |
| **TLS certificate** | Synology may use a self-signed cert or a Let's Encrypt cert tied to a Synology DDNS hostname; eNotes must present the custom-CA trust dialog on first connect rather than hard-failing |
| **`.resource/` directory** | Synology WebDAV exposes hidden dot-directories; verify PROPFIND returns `.resource/` entries (some DSM versions require "Show hidden files" to be enabled in the WebDAV Server package settings) |
| **Idle connection reuse** | DSM WebDAV closes idle connections aggressively; OkHttp connection pool should be configured with a short keep-alive (≤ 30 s) |

**Setup wizard copy for the import screen:**
> *"Enter the WebDAV URL Joplin uses to sync. On Synology, this is usually* `https://<your-NAS>:5006/<folder>` *(e.g.* `https://nas.local:5006/joplin`*). Use your DSM username and password. If two-factor auth is enabled, create an app-specific password in DSM → Personal → Account."*

### 10.5 Known Joplin Edge Cases

| Edge Case | Handling |
|---|---|
| Encrypted notes (`encryption_applied: 1`) | Detected by metadata flag; skipped with user notification. E2EE decryption support can be added later using Joplin's documented key derivation (PBKDF2 + AES-256-CCM). |
| Conflicted note copies (`is_conflict: 1`) | Imported as a separate note with `[Conflict]` prepended to title, placed in eNotes "Conflicts" smart folder. |
| Notes with no `parent_id` | Placed into a top-level "Joplin Import" folder. |
| `markup_language: 2` (HTML notes) | Raw HTML is sanitised and converted to plain text with basic structure preserved; user is warned that formatting may be degraded. |
| Resources referenced but blob missing | Attachment slot created with an error placeholder; flagged in post-import report. |
| Very large sync roots (1000+ notes) | PROPFIND paginated via `Depth: 1`; download pool capped at 8 concurrent requests to avoid server rate-limiting. |
| Duplicate import attempt | Notes matched by Joplin `id` stored in eNotes `externalId` field; duplicates skipped by default, overwrite available as option. |
| Synology self-signed cert | eNotes presents a one-time "Trust this certificate?" dialog on first connect; cert fingerprint stored in app preferences for subsequent connections. |
| Synology DSM 2FA enabled | Import wizard explicitly prompts the user to generate an app-specific password in DSM and enter it in place of their main password. |
| `.resource/` not returned by PROPFIND | Importer falls back to individually fetching `<root>/.resource/<uuid>` by known UUID from resource metadata files, bypassing directory listing. |

---

## 11. File Format Specification (`.enote`)

Each note is stored as a single `.enote` file on the WebDAV server.

```json
{
  "version": 1,
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "My Note",
  "created_at": "2026-03-18T10:00:00Z",
  "edited_at": "2026-03-18T12:34:56Z",
  "folder_path": "/Work/Projects",
  "tags": ["design", "planning"],
  "is_locked": false,
  "body": {
    "type": "rich_text",
    "content": { }
  },
  "encrypted_body": null,
  "attachments": [
    {
      "id": "abc123",
      "filename": "diagram.png",
      "mime_type": "image/png",
      "size_bytes": 204800,
      "remote_path": "/eNotes/.attachments/abc123.png"
    }
  ]
}
```

When `is_locked` is `true`, `body` is `null` and `encrypted_body` contains a Base64-encoded AES-256-GCM ciphertext with a prepended IV.

---

*Last updated: March 2026*
