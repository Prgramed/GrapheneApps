# eMessages Web Companion — Self-hosted Relay Architecture

## Context
Browser-based interface to read/send SMS/MMS where the phone remains the physical transmitter. Phone sends via `SmsManager` as today; browser is a remote control + live viewer. Selected design: **self-hosted relay** so it works from anywhere (not just LAN), with **React + Vite** web UI, **Kotlin + Ktor** relay, and **shared secret + per-browser token** auth.

## Current eMessages Architecture (findings — nothing to change in data layer)
- Messages live in Android Telephony ContentProviders (`content://sms/`, `content://mms/`). No local DB.
- `MessageRepositoryImpl.kt:90-238` sends via `SmsManager`; already observable via `ContentObserver` on `content://mms-sms/`.
- `ConversationRepositoryImpl` exposes `observeConversations()` + `getMessages(threadId)`.
- `ContactLookupRepositoryImpl` resolves names/photos from `ContactsContract`.
- Receive path: `SmsDeliverReceiver` / `MmsDeliverReceiver` / `MmsDownloadedReceiver` — write directly to telephony providers, which in turn notify the ContentObserver.
- No existing network layer for messaging (only WebDAV backup + link previews).

All of the above means we can build the relay client as a pure consumer of existing repositories — no refactor of data or domain layers.

## Architecture

```
┌──────────┐      WS (outbound,     ┌────────────┐      WS/HTTPS      ┌────────┐
│ Android  │◄──── always-on,       │   Relay    │◄─────────────────►│Browser │
│eMessages │ ──── phone-initiated)─►│ (Ktor)     │                   │(React) │
└──────────┘                        └────────────┘                   └────────┘
    │                                     │                              │
    ├── ContentObserver → WS events       ├── Forwards between phone     ├── Renders conversations
    ├── SmsManager.send() ← commands      │   and browser sessions       ├── Sends commands
    └── Hilt-injected repositories        └── Ephemeral cache only       └── Lives on localStorage token
                                             (no persistent message store)
```

**Key design rule**: phone is the authoritative store. Relay holds an in-memory/short-TTL cache to smooth reconnects but is NOT the system of record. If phone is offline, the browser shows last-known state + "phone offline" banner.

## Workstream 1 — Android relay client (~1 week)

New module: `apps/eMessages/feature/relayclient/`

- **Dependencies**: `ktor-client-cio`, `ktor-client-websockets`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json` (client side, lighter than server).
- **`RelayService`** — foreground service (`foregroundServiceType="dataSync"`), maintains a reconnecting WebSocket to the relay.
  - On start: authenticates with device token.
  - On `ContentObserver` fire: emits `THREAD_UPDATED` / `NEW_MESSAGE` envelopes.
  - On incoming command (`SEND_SMS` / `SEND_MMS` / `GET_MESSAGES` / `GET_CONTACT` / `FETCH_ATTACHMENT`): dispatches to the relevant repository, replies with correlation ID.
- **`RelayProtocol.kt`** — shared DTOs (`Envelope { id, type, payload }`) lives in a small `core:relay-protocol` module so the relay server can use the same `@Serializable` classes.
- Pairing flow:
  1. User opens Settings → "Web access" → Pair device.
  2. App contacts relay `POST /pair/init` → gets `pairingCode` (6-digit).
  3. User types pairing code into browser once → relay binds browser session to phone's device id.
  4. Phone stores `deviceId + deviceSecret` in EncryptedSharedPreferences.
- **Reusing existing code**: `MessageRepositoryImpl`, `ConversationRepositoryImpl`, `ContactLookupRepositoryImpl` injected via Hilt — no changes.

Files added/touched on phone side:
| File | Change |
|------|--------|
| `apps/eMessages/feature/relayclient/` (new module) | Service + WS client + command handlers |
| `core/relay-protocol/` (new shared module) | `@Serializable` envelopes, command + event types |
| `apps/eMessages/feature/settings/.../SettingsScreen.kt` | "Web Access" section: relay URL field, pair button, pairing status, sign-out-other-browsers |
| `apps/eMessages/app/src/main/AndroidManifest.xml` | Register `RelayService` with `foregroundServiceType="dataSync"` |
| `apps/eMessages/app/build.gradle.kts` | Include new module |
| `gradle/libs.versions.toml` | Add `ktor-client-*` versions (if not already present) |

## Workstream 2 — Relay server (~1.5 weeks)

New top-level directory: `servers/emessages-relay/` (Kotlin Ktor, Gradle project separate from the Android build but in the same monorepo).

- **Stack**: Ktor Netty engine, `ktor-server-websockets`, `ktor-server-auth` (Bearer), `ktor-serialization-kotlinx-json`, `logback`.
- **Endpoints**:
  - `POST /pair/init` → phone creates pairing code (expires in 5 min), returns code + deviceId.
  - `POST /pair/complete` → browser submits pairing code → relay issues browser token, informs phone.
  - `POST /browser/token/revoke` → phone revokes a specific browser token.
  - `WS /phone` → phone WebSocket (authenticated by `deviceId + deviceSecret`).
  - `WS /browser` → browser WebSocket (authenticated by per-browser token).
- **Routing logic**: every browser WS is associated with exactly one phone. Commands from browser → forwarded to phone's WS. Events from phone → fanned out to all paired browser sessions. No message persistence.
- **Rate limiting**: per-device sending cap (e.g. 30 msgs/min) to prevent rogue browser flooding.
- **Deployment**: Dockerfile producing ~100MB image. `docker-compose.yml` snippet for running alongside Immich. TLS via reverse proxy (Caddy/nginx — user already runs Immich behind one).
- **Shared DTOs**: pulls in `core:relay-protocol` module as a Gradle dependency via included build.

Files:
| File | Change |
|------|--------|
| `servers/emessages-relay/build.gradle.kts` | New Ktor project |
| `servers/emessages-relay/src/main/kotlin/.../Application.kt` | Server bootstrap |
| `servers/emessages-relay/src/main/kotlin/.../Routes.kt` | Pair + WS endpoints |
| `servers/emessages-relay/src/main/kotlin/.../SessionRegistry.kt` | In-memory device↔browser mapping |
| `servers/emessages-relay/src/main/kotlin/.../Auth.kt` | Bearer token + pairing code logic |
| `servers/emessages-relay/Dockerfile` | Multi-stage build (gradle → jre-slim) |
| `servers/emessages-relay/docker-compose.example.yml` | Example deployment next to Immich |
| `settings.gradle.kts` | Include the new Gradle project |

## Workstream 3 — Web UI (~1.5 weeks)

New directory: `servers/emessages-relay/web/` (served as static files by the relay).

- **Stack**: React 18 + Vite + TypeScript + Tailwind. Single-page app.
- **State**: Zustand for UI state; reconnecting WebSocket hook for live events.
- **Pages / components**:
  - **Pair page** — first-run: paste the 6-digit code from the phone.
  - **Conversation list** — threads sorted by `timestamp`, unread badge, snippet, contact avatar.
  - **Chat view** — bubble layout matching the app style; virtualized list for long threads; message status indicators (pending/sent/delivered/failed).
  - **Composer** — text input, attachment picker (image → upload → phone fetches from relay → sends MMS), send button, dual-SIM selector when applicable.
  - **Contact search** — for "New message" flow.
  - **Offline banner** — shown when phone's WS is down.
- **Real-time updates**: subscribe to `NEW_MESSAGE`, `MESSAGE_STATUS_CHANGED`, `THREAD_UPDATED`.
- **Auth**: browser token in `localStorage`, attached on WS connect + every REST call.

The relay's Ktor static file serving (`staticFiles("/", File("web"))`) ships the built `dist/` as part of the Docker image.

## Workstream 4 — Integration, polish, docs (~3–5 days)

- **End-to-end test**: scripted scenario — phone sends/receives while browser is connected; kill phone WS briefly and verify reconnect; verify rate limiter.
- **Foreground service UX**: silent notification with "Web access active" and a stop button.
- **Battery audit**: verify < 2%/day when idle — WS ping interval tuned (e.g. 30s).
- **Security review**: tokens only over TLS (enforced by Caddy/nginx in front of relay); per-browser token revocation UI; master secret rotation.
- **README.md** in `servers/emessages-relay/` with deployment + pairing walkthrough.

## Total Effort Estimate

| Workstream | Duration |
|---|---|
| Android relay client | ~1 week |
| Relay server (Kotlin/Ktor + Docker) | ~1.5 weeks |
| React + Vite web UI | ~1.5 weeks |
| Integration, polish, docs | ~3–5 days |
| **Total for MVP** | **~4–5 weeks** (one dev, full-time) |

Scope can be trimmed to ~3 weeks by deferring MMS attachments and dual-SIM selection.

## Verification Plan
1. Deploy relay container next to Immich (`docker compose up`). Confirm `https://emessages-relay.your-domain/` responds with 200.
2. On phone, Settings → Web Access → enter relay URL → tap Pair → confirm foreground notification appears.
3. Phone shows 6-digit pairing code. In browser at the same URL, paste code → browser loads conversation list matching the app.
4. Send a message from the browser → phone logs command received → `SmsManager.sendTextMessage` fires → recipient gets real SMS → browser updates bubble to `SENT`/`DELIVERED`.
5. Reply from another phone → eMessages app receives via `SmsDeliverReceiver` → ContentObserver fires → phone pushes `NEW_MESSAGE` over WS → browser renders new bubble in < 1s.
6. Airplane-mode the phone → browser shows offline banner; re-enable → relay reconnects automatically, missed events replay from the in-memory relay cache.
7. Revoke the browser token from phone Settings → browser session closes within 2s.
8. 24-hour battery test — relay service adds less than 2% to phone battery while idle.
