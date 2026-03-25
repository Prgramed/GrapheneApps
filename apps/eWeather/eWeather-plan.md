# eWeather — Master Build Plan
> GrapheneOS · Open-Meteo · RainViewer · MeteoAlarm · No GMS · Beautiful Design First
> Last updated: 2026-03-23

---

## Confirmed Decisions

| Concern | Decision |
|---|---|
| **Platform** | GrapheneOS, sideloaded APK, single user, no GMS |
| **Location service** | Android native `LocationManager` — no `FusedLocationProviderClient` (GMS) |
| **Forecast data** | Open-Meteo — free, no API key, no rate limit, ECMWF + GFS + ICON models |
| **Air quality** | Open-Meteo Air Quality API — same host, free |
| **Geocoding** | Open-Meteo Geocoding API — free, no key |
| **Radar** | RainViewer — free tile API, global coverage, past frames + nowcast |
| **Map base** | osmdroid (OpenStreetMap) — GMS-free, mature, no API key |
| **Severe alerts** | MeteoAlarm RSS feed (Sweden/Europe); graceful "not available" for Egypt |
| **Moon phase** | Computed on-device from date math — no API needed |
| **Widget** | Single clean widget, multiple size classes |
| **Locations** | GPS + manual city search + multiple saved locations |
| **Design priority** | Beautiful first — dynamic sky backgrounds, condition animations, glass cards |

---

## How to Use This Plan with Claude Code

Each **Session** below is one Claude Code working block. At the start of every session, paste this:

```
This is session X.Y of the eWeather Android app build.
Reference file: eWeather-plan.md (attached or pasted).
Previous sessions are complete. Build only what is listed under session X.Y.
Do not refactor earlier sessions unless explicitly listed as a deliverable.
```

**Session sizing:** Each session produces 1–4 files or a single coherent feature. If Claude Code seems to be going wide, stop it and split the session.

---

## Tech Stack

| Layer | Library | Notes |
|---|---|---|
| Language | Kotlin 2.x | |
| UI | Jetpack Compose + Material 3 | Latest stable |
| Architecture | MVVM + Clean Architecture + UDF | |
| Networking | Retrofit 2 + OkHttp 4 + Kotlin serialization | |
| Local DB | Room | Caching weather data |
| DI | Hilt | |
| Async | Coroutines + Flow + StateFlow | |
| Image loading | Coil 3 | Map tile caching |
| Preferences | DataStore (Proto) | |
| Background refresh | WorkManager | Battery-aware scheduling |
| Map | osmdroid 6.x | GMS-free OpenStreetMap tiles |
| Animations | Compose Animation + Canvas particle system | Custom weather animations |
| Location | `android.location.LocationManager` | No FusedLocationProviderClient |

> **No GMS dependencies anywhere.** Validate every transitive dependency.

---

## API Reference

### Open-Meteo Forecast
`GET https://api.open-meteo.com/v1/forecast`

Key parameters:
```
latitude, longitude
hourly=temperature_2m,apparent_temperature,precipitation_probability,
       precipitation,weather_code,wind_speed_10m,wind_direction_10m,
       wind_gusts_10m,relative_humidity_2m,visibility,uv_index,
       cloud_cover,is_day,dew_point_2m,surface_pressure
daily=temperature_2m_max,temperature_2m_min,weather_code,sunrise,sunset,
      uv_index_max,precipitation_sum,precipitation_hours,
      wind_speed_10m_max,wind_direction_10m_dominant
current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,
        wind_direction_10m,relative_humidity_2m,precipitation,
        surface_pressure,cloud_cover,visibility,is_day
forecast_days=10
wind_speed_unit=ms (or kmh — user preference)
temperature_unit=celsius (or fahrenheit — user preference)
```

### Open-Meteo Air Quality
`GET https://air-quality-api.open-meteo.com/v1/air-quality`
```
hourly=pm2_5,pm10,nitrogen_dioxide,ozone,european_aqi,us_aqi,
       aerosol_optical_depth,dust
```

### Open-Meteo Geocoding
`GET https://geocoding-api.open-meteo.com/v1/search`
```
name={query}&count=10&language=en
```
Returns: name, country, latitude, longitude, elevation, admin1 (region)

### RainViewer
1. `GET https://api.rainviewer.com/public/weather-maps.json` — returns frame list
2. Tile URL: `https://tilecache.rainviewer.com/v2/radar/{timestamp}/512/{z}/{x}/{y}/6/1_1.png`
   - 512 = tile size in px; color scheme 6 = standard; last params = smooth + snow

### MeteoAlarm (Sweden)
`GET https://feeds.meteoalarm.org/feeds/meteoalarm-legacy-atom-sweden`
- Atom/RSS XML feed; parse with Android's built-in XML parser
- Contains: alert type, severity (Minor/Moderate/Severe/Extreme), area, effective/expires, headline

---

## WMO Weather Code Mapping
Every Open-Meteo weather data point uses WMO codes. Build a `WmoCode.kt` mapper:
```
0      = Clear sky
1,2,3  = Mainly clear / partly cloudy / overcast
45,48  = Fog / depositing rime fog
51,53,55 = Drizzle (light/moderate/dense)
61,63,65 = Rain (slight/moderate/heavy)
71,73,75 = Snow (slight/moderate/heavy)
77     = Snow grains
80,81,82 = Rain showers (slight/moderate/violent)
85,86  = Snow showers (slight/heavy)
95     = Thunderstorm
96,99  = Thunderstorm with hail
```
Each code maps to: `label: String`, `iconRes: Int`, `isDay: Boolean → background category`

---

## Design System

### Sky Background Categories
The entire app background is a full-bleed gradient that shifts based on **time-of-day** + **weather condition**. These are the 12 sky states, each defined as a `LinearGradient` start/end color pair:

| State | Trigger | Colors (top → bottom) |
|---|---|---|
| Pre-dawn | 3–5am, any | #0D0D2B → #1A1040 |
| Sunrise | 5–7am, clear | #FF6B35 → #FFB347 → #87CEEB |
| Morning clear | 7am–12pm, clear/few clouds | #87CEEB → #B8D4E8 |
| Afternoon clear | 12–5pm, clear | #4FC3F7 → #81D4FA |
| Sunset | 5–8pm, clear | #FF6B35 → #E91E63 → #673AB7 |
| Night | 8pm–3am, clear | #0A0E27 → #1C2951 |
| Cloudy | Any, overcast | #546E7A → #78909C |
| Rain | Precipitation | #37474F → #546E7A |
| Storm | Thunderstorm | #212121 → #37474F |
| Snow | Snow codes | #90A4AE → #CFD8DC |
| Fog | Fog codes | #78909C → #B0BEC5 |
| Night cloudy | Night + cloud | #1A1A2E → #2D3561 |

Interpolate smoothly between adjacent states using `animateColorAsState`.

### Glass Card Style
All data cards use a consistent frosted-glass look:
```kotlin
Modifier
  .background(
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.20f),
    shape = RoundedCornerShape(20.dp)
  )
  .border(
    width = 0.5.dp,
    color = Color.White.copy(alpha = 0.25f),
    shape = RoundedCornerShape(20.dp)
  )
```
On API 31+, add `RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)` as a `graphicsLayer` blur behind each card.

### Typography Hierarchy
- Temperature display: `fontWeight = FontWeight.Thin`, large `sp` (72sp for current temp)
- Location name: `fontWeight = FontWeight.Medium`, 22sp
- Card labels: `fontWeight = FontWeight.Normal`, 12sp, 60% alpha
- Card values: `fontWeight = FontWeight.SemiBold`, 16sp

### Weather Icon Style
Use Lottie animations for weather icons (free Lottie weather packs exist). Fallback to custom SVG-based `Canvas`-drawn icons if Lottie adds too much APK weight — Claude Code's call.

---

## Condition Animations (Canvas Particle System)

Each sky state has an optional Canvas-drawn overlay animation in `WeatherAnimationLayer.kt`:

| Condition | Animation |
|---|---|
| Rain | Falling translucent streaks, randomised angle ±15°, speed varies by intensity |
| Snow | Slowly drifting circles, size 2–6dp, gentle horizontal drift, fade at edges |
| Thunderstorm | Rain + occasional full-screen white flash (`alpha` 0 → 0.6 → 0, 80ms) |
| Clear day | Subtle sun-ray shimmer (rotating semi-transparent wedges, very low alpha) |
| Clear night | Twinkling star field (fixed points, alpha pulse on random cycle) |
| Fog | Horizontal semi-transparent layers drifting left/right |
| Wind | Horizontal streak particles moving fast |

All animations use `rememberInfiniteTransition` + `Canvas` + `drawIntoCanvas`. Target 60fps; use `nativeCanvas` for particle-heavy scenes, bail to static if frame time exceeds 16ms.

---

## Project Structure

```
eWeather/
├── app/src/main/
│   ├── data/
│   │   ├── api/
│   │   │   ├── OpenMeteoForecastService.kt
│   │   │   ├── OpenMeteoAirQualityService.kt
│   │   │   ├── OpenMeteoGeocodingService.kt
│   │   │   ├── RainViewerService.kt
│   │   │   ├── MeteoAlarmService.kt        ← Retrofit XML converter
│   │   │   └── dto/                        ← Raw API DTOs
│   │   ├── db/
│   │   │   ├── AppDatabase.kt
│   │   │   ├── dao/                        ← WeatherDao, LocationDao, AlertDao
│   │   │   └── entity/                     ← Room entities
│   │   └── repository/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── WeatherCondition.kt         ← Current + hourly + daily
│   │   │   ├── AirQuality.kt
│   │   │   ├── SavedLocation.kt
│   │   │   ├── WeatherAlert.kt
│   │   │   ├── RadarFrame.kt
│   │   │   └── AstronomyData.kt            ← Sunrise/sunset/moon
│   │   └── repository/                     ← Interfaces
│   ├── location/
│   │   └── LocationProvider.kt             ← LocationManager wrapper
│   ├── ui/
│   │   ├── MainActivity.kt
│   │   ├── navigation/NavGraph.kt
│   │   ├── weather/
│   │   │   ├── WeatherScreen.kt            ← Main screen
│   │   │   ├── WeatherViewModel.kt
│   │   │   ├── components/
│   │   │   │   ├── SkyBackground.kt        ← Gradient + animation layer
│   │   │   │   ├── WeatherAnimationLayer.kt← Canvas particle system
│   │   │   │   ├── CurrentConditionsCard.kt
│   │   │   │   ├── HourlyForecastStrip.kt
│   │   │   │   ├── DailyForecastCard.kt
│   │   │   │   ├── DetailCard.kt           ← Wind / humidity / UV / AQI / pressure
│   │   │   │   └── AstronomyCard.kt
│   │   ├── radar/
│   │   │   ├── RadarScreen.kt
│   │   │   └── RadarViewModel.kt
│   │   ├── locations/
│   │   │   ├── LocationsScreen.kt
│   │   │   └── LocationSearchScreen.kt
│   │   ├── alerts/
│   │   │   └── AlertsScreen.kt
│   │   ├── settings/
│   │   │   └── SettingsScreen.kt
│   │   └── widget/
│   │       └── WeatherWidgetProvider.kt
│   └── util/
│       ├── WmoCode.kt                      ← WMO code → label/icon/category
│       ├── MoonPhase.kt                    ← On-device moon phase math
│       └── AqiDescription.kt              ← AQI level → label/color
```

---

## Phase Breakdown

### Status Key
- `[ ]` Not started
- `[~]` In progress
- `[x]` Complete

---

### Phase 1 — Foundation & Data Layer

**Goal:** Fetch real weather data, cache it, serve it to the UI layer.

---

- [x] **Session 1.1 — Project Scaffolding**
  - New Android project: `applicationId = "dev.eweather"`, minSdk 29, targetSdk 35
  - `libs.versions.toml`: Kotlin 2.x, Compose BOM, Retrofit 2, OkHttp 4, Room, Hilt, Coil 3, DataStore Proto, WorkManager, Kotlin serialization, osmdroid 6.x
  - `AndroidManifest.xml` permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`
  - `EWeatherApp.kt`: `@HiltAndroidApp` Application subclass
  - ProGuard rules: Retrofit, Room, Kotlin serialization, osmdroid
  - **Key files:** `libs.versions.toml`, `app/build.gradle.kts`, `AndroidManifest.xml`, `EWeatherApp.kt`
  - **Exit test:** `./gradlew assembleDebug` — clean build, zero errors

---

- [x] **Session 1.2 — Domain Models**
  - Pure Kotlin data classes, zero Android imports:
  - `CurrentWeather`: `temp: Float`, `feelsLike: Float`, `weatherCode: Int`, `windSpeed: Float`, `windDirection: Int`, `humidity: Int`, `pressure: Float`, `cloudCover: Int`, `visibility: Float`, `precipitation: Float`, `isDay: Boolean`, `timestamp: Long`
  - `HourlyForecast`: list of `HourlyPoint(timestamp, temp, feelsLike, weatherCode, precipProbability, precipAmount, windSpeed, windDirection, humidity, uvIndex, isDay)`
  - `DailyForecast`: list of `DailyPoint(date, tempMax, tempMin, weatherCode, sunrise, sunset, uvIndexMax, precipSum, windSpeedMax, windDirDominant)`
  - `AirQuality`: `pm25: Float`, `pm10: Float`, `no2: Float`, `ozone: Float`, `europeanAqi: Int`, `usAqi: Int` (nullable — not available everywhere)
  - `AstronomyData`: computed from daily data + on-device moon phase math
  - `SavedLocation`: `id: Long`, `name: String`, `region: String`, `country: String`, `lat: Double`, `lon: Double`, `isGps: Boolean`, `sortOrder: Int`
  - `WeatherAlert`: `id: String`, `event: String`, `severity: AlertSeverity (Minor/Moderate/Severe/Extreme)`, `headline: String`, `area: String`, `effective: Long`, `expires: Long`
  - `RadarFrame`: `timestamp: Long`, `tileUrlTemplate: String`
  - `WeatherData`: wraps `CurrentWeather + List<HourlyForecast> + List<DailyForecast> + AirQuality? + fetchedAt: Long`
  - **Key files:** `domain/model/*.kt` (8 files)
  - **Exit test:** `./gradlew testDebug` — models compile cleanly; hand-written unit test verifies data class equality

---

- [x] **Session 1.3 — WMO Code Mapper + Moon Phase**
  - `WmoCode.kt`: `object WmoCode` with `fun describe(code: Int, isDay: Boolean): WmoDescription(label, iconCategory, skyCategory, hasRain, hasSnow, hasThunder, hasFog)`
  - `SkyCategory` enum: `CLEAR_DAY, CLEAR_NIGHT, PARTLY_CLOUDY_DAY, PARTLY_CLOUDY_NIGHT, OVERCAST, RAIN, SNOW, STORM, FOG, PRE_DAWN, SUNRISE, SUNSET` — used by the entire design system
  - `AqiDescription.kt`: `fun europeanAqiLevel(aqi: Int): AqiLevel(label, color: Color, description)` — levels: Good (0–20), Fair (20–40), Moderate (40–60), Poor (60–80), Very Poor (80–100), Extremely Poor (100+)
  - `MoonPhase.kt`: compute moon phase from a `LocalDate` using the standard synodic period algorithm (no API needed); returns `MoonPhase(fraction: Float, phaseName: String, emoji: String)` — New/Waxing Crescent/First Quarter/Waxing Gibbous/Full/Waning Gibbous/Last Quarter/Waning Crescent
  - **Key files:** `util/WmoCode.kt`, `util/AqiDescription.kt`, `util/MoonPhase.kt`
  - **Exit test:** Unit test — WMO code 95 with `isDay=true` returns `skyCategory=STORM`, `hasThunder=true`. Moon phase for 2026-03-23 returns a known phase (verify with any moon calendar).

---

- [x] **Session 1.4 — Open-Meteo API Layer**
  - Three separate Retrofit services with separate base URLs (no shared interceptor needed — no auth):
    - `OpenMeteoForecastService.kt`: single `getForecast(@Query params)` call with all hourly+daily+current fields listed above
    - `OpenMeteoAirQualityService.kt`: `getAirQuality(@Query latitude, longitude)` — pm2_5, pm10, no2, ozone, european_aqi hourly
    - `OpenMeteoGeocodingService.kt`: `searchLocations(@Query name, count=10)`
  - DTOs in `data/api/dto/`: `ForecastResponseDto`, `AirQualityResponseDto`, `GeocodingResponseDto` — all `@Serializable`; every field nullable (API sometimes omits fields in unusual regions)
  - `OpenMeteoMapper.kt`: DTO → domain model; handles unit conversion (e.g. wind ms↔kmh based on preference); derives `AstronomyData` from daily sunrise/sunset strings
  - Hilt `NetworkModule`: provides `OkHttpClient` (30s connect timeout, logging interceptor in debug only), separate `Retrofit` instances for each base URL
  - `RainViewerService.kt`: single `getFrameList()` call to `api.rainviewer.com/public/weather-maps.json`; DTO maps to `List<RadarFrame>`
  - **Key files:** `data/api/*.kt`, `data/api/dto/*.kt`, `data/api/OpenMeteoMapper.kt`, `di/NetworkModule.kt`
  - **Exit test:** Unit test — `OpenMeteoMapper` converts a hardcoded sample JSON response to a `WeatherData` with correct `currentWeather.weatherCode`; `MoonPhase` is computed and non-null.

---

- [x] **Session 1.5 — Room Database**
  - `WeatherCacheEntity`: `(locationId: Long FK, json: String, fetchedAt: Long, dataType: String)` — store serialized `WeatherData` as JSON blob per location; simple and flexible
  - `LocationEntity`: mirrors `SavedLocation` domain model; `@Index(name)` on `sortOrder` and `isGps`
  - `AlertEntity`: mirrors `WeatherAlert`; `@Index` on `locationId` and `expires`
  - `WeatherDao`: `upsertCache(entity)`, `getCacheForLocation(locationId, dataType): WeatherCacheEntity?`, `deleteExpiredCache(olderThan: Long)`
  - `LocationDao`: `observeAll(): Flow<List<LocationEntity>>`, `insert/update/delete`, `getById`
  - `AlertDao`: `upsertAlerts(list)`, `observeActiveAlerts(locationId, now): Flow<List<AlertEntity>>`
  - `AppDatabase` version 1; `TypeConverters` for any enums
  - Hilt `DatabaseModule`
  - **Key files:** `db/entity/*.kt`, `db/dao/*.kt`, `db/AppDatabase.kt`, `di/DatabaseModule.kt`
  - **Exit test:** `./gradlew testDebug` — Room compile-time schema validation passes; DAO unit test inserts a cache entry and retrieves it.

---

- [x] **Session 1.6 — Location Provider**
  - `LocationProvider.kt`: wraps `android.location.LocationManager`; no GMS
  - `requestCurrentLocation(callback)`: requests `NETWORK_PROVIDER` first (fast, battery-friendly), then `GPS_PROVIDER` if network returns nothing in 10s; cancels after 30s
  - `checkPermissions(): Boolean` — `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`
  - Emits `LocationResult.Success(lat, lon)` or `LocationResult.PermissionDenied` or `LocationResult.Unavailable`
  - No continuous location tracking — one-shot fix per app open is sufficient for weather
  - `DataStore` stores the last known GPS lat/lon so the app works immediately on launch before the fix arrives
  - Hilt `@Singleton` binding
  - **Key files:** `location/LocationProvider.kt`, `di/LocationModule.kt`
  - **Exit test:** Manual — install, grant location permission, cold-launch → `LocationProvider` returns a fix within 30s (check logcat)

---

- [x] **Session 1.7 — Repository Layer + DataStore**
  - Proto DataStore: `app_preferences.proto` — `temperature_unit: enum (CELSIUS/FAHRENHEIT)`, `wind_unit: enum (MS/KMH/MPH)`, `refresh_interval_hours: int32`, `notifications_enabled: bool`, `active_location_id: int64`
  - `AppPreferencesRepository`: typed `Flow<AppPreferences>` + `suspend fun update*()` methods
  - `WeatherRepository` interface + `WeatherRepositoryImpl`:
    - `getWeatherForLocation(location): Flow<WeatherData?>` — emits cached data immediately, then fetches fresh if cache > 1h old, upserts Room, emits again
    - `refreshWeather(location): Result<WeatherData>` — force-fresh fetch from API; updates Room cache
    - `getAirQuality(location): Flow<AirQuality?>` — same cache pattern, 1h TTL
  - `LocationRepository` interface + `LocationRepositoryImpl`: CRUD for `SavedLocation` via Room; auto-inserts a GPS location when `LocationProvider` gets a fix
  - `AlertRepository` interface + `AlertRepositoryImpl`: `refreshAlerts(location)` — calls `MeteoAlarmService` if location is in Europe (lat 35–71, lon -25–45); otherwise emits empty list silently
  - **Key files:** `app_preferences.proto`, `AppPreferencesRepository.kt`, `data/repository/*.kt`, `di/RepositoryModule.kt`
  - **Exit test:** Integration test — mock `OpenMeteoForecastService` returns a fake response → `WeatherRepositoryImpl.getWeatherForLocation` emits data from Room after upsert; second call within 1h returns cached data without calling API again.

---

- [x] **Session 1.8 — MeteoAlarm Service + WorkManager Refresh**
  - `MeteoAlarmService.kt`: Retrofit with `ScalarsConverterFactory` (raw XML string); single endpoint `getFeedForCountry(country: String)` → returns raw XML; parse with `android.util.Xml` (no external XML library needed); extract `<entry>` elements → `WeatherAlert` domain models; filter out expired alerts (`expires < now`)
  - Country slug mapping: `SavedLocation.country` → MeteoAlarm feed slug (e.g. "Sweden" → "meteoalarm-legacy-atom-sweden"); if no mapping found → return empty list (graceful, not an error)
  - `WeatherRefreshWorker.kt`: `CoroutineWorker`; fetches weather + AQ + alerts for all saved locations; runs on `NetworkType.CONNECTED`; called via `PeriodicWorkRequest` with interval from DataStore preference (default 1h); respects `setRequiresBatteryNotLow(true)` for background syncs
  - `WeatherRefreshWorker` scheduled on first launch + rescheduled on `BOOT_COMPLETED`
  - **Key files:** `data/api/MeteoAlarmService.kt`, `data/worker/WeatherRefreshWorker.kt`
  - **Exit test:** Unit test — MeteoAlarm XML parser correctly extracts a `WeatherAlert` with correct severity from a hardcoded sample XML. WorkManager enqueue doesn't crash.

---

**Phase 1 Exit Criteria:** `./gradlew testDebug` passes. Cold launch → `LocationProvider` gets a fix → `WeatherRepositoryImpl` fetches from Open-Meteo → data cached in Room → emits `WeatherData` with valid `currentWeather` and 10-day `DailyForecast`.

---

### Phase 2 — Main Weather Screen

**Goal:** A genuinely beautiful, fully functional weather screen. Design is the top priority here — this screen is seen every day.

---

- [x] **Session 2.1 — Sky Background System**
  - `SkyBackground.kt`: full-bleed `Box` that fills the entire screen; takes `skyCategory: SkyCategory` as input
  - `skyGradient(category): Brush` — returns the correct `LinearGradient` for each of the 12 sky states defined in the design system
  - `skyCategory` is derived in `WeatherViewModel` from `currentWeather.weatherCode + isDay + LocalTime.now()` — time-of-day logic: pre-dawn 3–5am, sunrise 5–7am (clear), sunset 17–20pm (clear), etc.
  - `animateColorAsState(targetValue, tween(durationMillis = 2000))` for each gradient stop — the sky smoothly transitions as conditions or time changes; never snaps
  - `SkyBackground` sits at the bottom of the Compose z-order; everything else is layered on top
  - **Key files:** `ui/weather/components/SkyBackground.kt`
  - **Exit test:** Manual — set device clock to various times + simulate different WMO codes in preview → verify 12 sky states all render correctly; transition between two states is smooth animated blend

---

- [x] **Session 2.2 — Weather Animation Layer**
  - `WeatherAnimationLayer.kt`: `Canvas` overlay drawn on top of `SkyBackground`, transparent otherwise; receives `skyCategory` + `intensity: Float (0–1)` derived from precipitation amount or wind speed
  - Implement all 7 animations from the design system:
    - **Rain**: `ParticleSystem` with N streak particles (N = `(intensity * 80).toInt()`); each particle has random x-start, speed 400–800 dp/s, angle ±15°, alpha 0.3–0.6, length 8–20dp; loop with `withInfiniteAnimationFrameMillis`
    - **Snow**: slow-drifting circles, N = `(intensity * 40).toInt()`, speed 30–80 dp/s, size 2–6dp, gentle sine-wave x-drift
    - **Thunder**: rain particles + `InfiniteTransition` that fires `alpha 0 → 0.7 → 0` over 80ms every 4–15 seconds (random interval)
    - **Clear day**: 8 low-alpha (0.04) rotating wedge rays emanating from sun position (top-right); rotation 360° every 60s
    - **Clear night**: 50 fixed star points at random positions; each independently pulses alpha 0.4 → 1.0 on a random 2–5s cycle
    - **Fog**: 3 semi-transparent `drawRect` layers at different y-offsets, drifting horizontally ±20dp over 8s
    - **Wind**: 20 horizontal streaks moving left→right at 200–500dp/s, varying alpha 0.1–0.3
  - Performance: skip animation entirely if `reduceMotion` accessibility setting is on; cap frame delta to 32ms to prevent jumps after tab-switch
  - **Key files:** `ui/weather/components/WeatherAnimationLayer.kt`
  - **Exit test:** Manual with Compose Preview showing rain, snow, and star animations at 60fps (`adb shell dumpsys gfxinfo`)

---

- [x] **Session 2.3 — Current Conditions Display**
  - `CurrentConditionsCard.kt`: the hero element at the top of the main screen; NOT inside a glass card — floats directly over the sky
  - Layout: city name (22sp medium, white, with location pin icon); large temperature (72sp thin, white); weather description label below temp (18sp, 70% alpha); feels like temperature to the right ("Feels like 12°", 14sp)
  - Small weather icon or Lottie animation (40dp) beside the description label
  - High / Low for today from `DailyForecast[0]` shown as "H:18° L:5°" in 14sp
  - All text uses `shadow(color = Color.Black.copy(alpha=0.3f), blurRadius = 8.dp, offset = Offset(0f, 2f))` for legibility over any sky colour
  - Tapping the city name → navigates to `LocationsScreen`
  - **Key files:** `ui/weather/components/CurrentConditionsCard.kt`
  - **Exit test:** Connected to live API — current conditions display with correct city, temp, and description; text is legible on both light sky (afternoon clear) and dark sky (night/storm)

---

- [x] **Session 2.4 — Hourly Forecast Strip**
  - `HourlyForecastStrip.kt`: glass card containing a `LazyRow` of 24 hourly items
  - Each item: time label (12-hour or 24-hour per preference), small weather icon, temperature, precipitation probability bar (thin coloured bar at the bottom, height proportional to probability, only shown if >10%)
  - Current hour is highlighted: slightly larger temp text, rounded background highlight
  - Precipitation probability bar: colour gradient from transparent (0%) to `#4FC3F7` (100%)
  - Horizontal scroll with `flingBehavior = ScrollableDefaults.flingBehavior()`; no scroll indicator
  - **Key files:** `ui/weather/components/HourlyForecastStrip.kt`
  - **Exit test:** 24 hourly items render; scrolls smoothly; precipitation bars appear only when probability > 10%; current hour is highlighted

---

- [x] **Session 2.5 — 10-Day Daily Forecast**
  - `DailyForecastCard.kt`: glass card with 10 rows, one per day
  - Each row: day name (Mon/Tue/…, today = "Today"), weather icon, precipitation probability (if > 0, shown as small raindrop + percentage), low temp (cool colour), temperature range bar (thin rounded bar spanning from low to high relative to the week's overall range), high temp (warm colour)
  - Temperature range bar: each day's bar is positioned and sized relative to the coldest low and warmest high across all 10 days — visually shows relative warmth of each day at a glance
  - Tapping a day expands it inline to show: sunrise/sunset times, UV index, wind speed + direction, total precip; collapse on re-tap
  - Expand/collapse uses `AnimatedVisibility(expandVertically + fadeIn)`
  - **Key files:** `ui/weather/components/DailyForecastCard.kt`
  - **Exit test:** 10 days render; temperature bars are correctly proportioned relative to the full range; tap day 3 → expands with detail; tap again → collapses

---

- [x] **Session 2.6 — Detail Cards Grid**
  - `DetailCardsGrid.kt`: 2-column `LazyVerticalGrid` of glass cards, each showing one metric
  - Cards to implement (8 total):
    - **Wind**: speed + cardinal direction, animated compass needle pointing in wind direction (`animateFloatAsState` on needle angle), gust speed below
    - **Humidity**: percentage + dew point; circular arc fill visualisation (like a gauge)
    - **UV Index**: numeric value, colour-coded label (Low green/Moderate yellow/High orange/Very High red/Extreme purple), recommended protection ("No protection needed" etc.)
    - **Air Quality**: European AQI value, colour-coded label, dominant pollutant; tapping expands to show full breakdown (PM2.5, PM10, NO₂, O₃)
    - **Visibility**: in km, brief qualitative label ("Excellent / Good / Moderate / Poor")
    - **Pressure**: hPa value; tiny sparkline of last 6h pressure trend from hourly data (rising/falling/steady)
    - **Precipitation**: today's total from daily data + hourly bar chart for next 12h using Canvas `drawRect`
    - **Feels Like**: temperature with brief explanation ("Wind makes it feel cooler" / "Humidity makes it feel warmer" / "Similar to actual temperature")
  - Each card has a consistent header: icon + label in small caps at top; primary value prominent in centre; secondary detail below
  - **Key files:** `ui/weather/components/DetailCardsGrid.kt`, individual card composables
  - **Exit test:** All 8 cards render with real data; compass needle in Wind card rotates to correct bearing; AQI card shows correct colour level; tapping AQI expands to show full breakdown

---

- [x] **Session 2.7 — Astronomy Card**
  - `AstronomyCard.kt`: glass card at the bottom of the main screen
  - **Sunrise/Sunset arc**: `Canvas`-drawn semicircle arc; sun icon travels along arc from sunrise position to sunset position based on current time; arc is gold/orange; background sky fades from dark (midnight) to light (noon); current sun position shown as glowing dot
  - Sunrise time label at left end of arc; sunset time label at right end; "Golden hour in Xh Xm" if within 2 hours of sunset
  - **Moon section**: moon phase emoji (🌑🌒🌓🌔🌕🌖🌗🌘), phase name, illumination percentage; moonrise/moonset times if available
  - Day length: "Daylight: Xh Xm" computed from `sunset - sunrise`
  - **Key files:** `ui/weather/components/AstronomyCard.kt`
  - **Exit test:** Arc renders with sun at correct position for current time of day; moon phase emoji matches a known calendar for today's date; sunrise/sunset times match Navidrome… err, Open-Meteo's daily data

---

- [x] **Session 2.8 — Main Screen Assembly + ViewModel**
  - `WeatherViewModel.kt`: single source of truth for the main screen
    - Loads `activeLocationId` from DataStore → fetches that `SavedLocation` → calls `WeatherRepository.getWeatherForLocation` → combines weather + AQ + alerts into `WeatherUiState`
    - `WeatherUiState`: `Loading`, `Success(weatherData, airQuality, alerts, location, skyCategory, animationIntensity)`, `Error(message, cachedData?)`, `NoLocation`
    - Derives `skyCategory` from `currentWeather.weatherCode + isDay + ZonedDateTime.now(location.timeZone)` via `WmoCode`
    - Derives `animationIntensity` from `currentWeather.precipitation` (0→1 mapped to 0→1) or wind speed for wind animation
    - Pull-to-refresh: `fun refresh()` triggers `WeatherRepository.refreshWeather` regardless of cache age
  - `WeatherScreen.kt`: `LazyColumn` with sticky header (`CurrentConditionsCard` + `SkyBackground` + `WeatherAnimationLayer`); cards scroll up underneath while sky stays fixed using `parallaxEffect` modifier; order: hourly strip → detail grid → daily forecast → astronomy card
  - Parallax: sky background scrolls at 0.3× the scroll speed of the card content — achieved via `nestedScroll` + `graphicsLayer { translationY = scrollState.value * 0.3f }` on `SkyBackground`
  - Alert banner: if `alerts` non-empty, a sticky banner at top (below status bar) with alert severity colour, headline, and "See all" tap action; `AnimatedVisibility`
  - Empty state: `NoLocation` state → full-screen prompt "Add a location to get started" with + button
  - Error state: retains last cached data in background, shows a subtle banner "Couldn't refresh — showing cached data from X hours ago" with retry button
  - **Key files:** `ui/weather/WeatherScreen.kt`, `ui/weather/WeatherViewModel.kt`
  - **Exit test:** Full screen loads with real data; pull-to-refresh works; parallax sky effect visible while scrolling; alert banner shows for a test location in an active alert area

---

**Phase 2 Exit Criteria:** Main screen looks genuinely beautiful. Sky background changes correctly for time of day and weather. All 8 detail cards show accurate data. Hourly and daily forecasts render correctly. Astronomy arc shows sun at correct position. Animations play at 60fps.

---

### Phase 3 — Multiple Locations

**Goal:** Switch between Sweden and Egypt (and anywhere else) seamlessly.

---

- [x] **Session 3.1 — Location Management** ✅ 2026-03-23
  - `LocationsScreen.kt` + `LocationsViewModel.kt`
  - List of saved locations: each row shows city name + country + cached temperature; GPS location shown at top with pin icon
  - Tap a location → sets `activeLocationId` in DataStore → navigates back → `WeatherScreen` reacts via Flow
  - Swipe-to-delete (disabled for GPS location)
  - FAB: "Add location" (stub, wired in session 3.2)
  - Active location shown with checkmark + subtle highlight
  - `NavGraph.kt` with WEATHER + LOCATIONS routes; `MainActivity.kt` uses `EWeatherNavHost`
  - **Key files:** `ui/locations/LocationsScreen.kt`, `ui/locations/LocationsViewModel.kt`, `ui/navigation/NavGraph.kt`

---

- [x] **Session 3.2 — Location Search** ✅ 2026-03-23
  - `LocationSearchScreen.kt` + `LocationSearchViewModel.kt`
  - SearchBar in TopAppBar with auto-focus; debounce 300ms; `flatMapLatest` → `OpenMeteoGeocodingService`
  - Results: flag emoji (ISO countryCode → regional indicators), city, region, country, population
  - Tap result → `OpenMeteoMapper.mapGeocodingResult()` → `LocationRepository.insert()` → pop back
  - States: Idle, Loading, Results, Empty ("No cities found for 'xyz'"), Error with retry
  - NavGraph updated: `Routes.SEARCH` composable wired, `onAddLocation` connected
  - **Key files:** `ui/locations/LocationSearchScreen.kt`, `ui/locations/LocationSearchViewModel.kt`, `ui/navigation/NavGraph.kt`

---

- [x] **Session 3.3 — Location Switcher Carousel** ✅ 2026-03-23
  - `WeatherViewModel` refactored: multi-location `pageStates: Map<Long, WeatherUiState>`, `locations: StateFlow<List<SavedLocation>>`, `currentPageIndex`, `onPageSettled()`, `observeLocations()` with combine + auto-loading
  - `WeatherScreen` wrapped in `HorizontalPager` with `rememberPagerState`, `snapshotFlow { settledPage }` → ViewModel sync
  - `DotIndicator` composable: animated pill for active page, white dots over sky background, hidden for single location
  - Swipe updates `activeLocationId` in DataStore; LocationsScreen tap animates pager to selected page
  - **Key files:** `ui/weather/WeatherScreen.kt`, `ui/weather/WeatherViewModel.kt`

---

**Phase 3 Exit Criteria:** Add locations for Sweden and Egypt. Swipe between them. Both show accurate weather. Adding/removing/reordering locations works correctly and persists.

---

### Phase 4 — Radar Map

**Goal:** Full-screen precipitation radar with animation playback.

---

- [x] **Session 4.1 — RainViewer Data + Radar ViewModel** ✅ 2026-03-23
  - `RadarViewModel.kt`: fetches frames via `RainViewerService` → `OpenMeteoMapper.mapRadarFrames()`, 10-min auto-refresh
  - Playback: `play()`/`pause()`/`seekTo()`/`togglePlayPause()` — 500ms per frame, 1.5s pause at end before loop
  - State: `frames`, `currentFrameIndex`, `isPlaying`, `activeLocation`, `isLoading`, `error`
  - `Routes.RADAR` added to NavGraph (stub, wired in 4.2)
  - **Key files:** `ui/radar/RadarViewModel.kt`, `ui/navigation/NavGraph.kt`

---

- [x] **Session 4.2 — Radar Map Screen** ✅ 2026-03-23
  - `RadarScreen.kt`: CartoDB Dark Matter base tiles, osmdroid `MapView` via `AndroidView`, zoom 7 centered on active location
  - Custom `RainViewerTileSource` (extends `OnlineTileSourceBase`) maps `{z}/{x}/{y}` from frame URL template
  - `MapTileProviderBasic` stored in `mapView.tag` for frame swapping in update block; `clearTileCache()` + `invalidate()` on frame change
  - Location marker, back button (floating), loading spinner
  - Radar icon button added to `CurrentConditionsCard` → navigates to `Routes.RADAR`
  - **Key files:** `ui/radar/RadarScreen.kt`, `ui/weather/components/CurrentConditionsCard.kt`, `ui/navigation/NavGraph.kt`, `ui/weather/WeatherScreen.kt`

---

- [x] **Session 4.3 — Radar Playback Controls** ✅ 2026-03-23
  - Timestamp overlay (top-center): "HH:mm" for past frames, "Now +Xmin" for nowcast
  - Bottom control panel: semi-transparent dark bg, Slider scrub bar, Play/Pause icon, "Now" button
  - Color legend: Canvas gradient bar (7 dBZ colors light blue→dark red), "Light"/"Heavy" labels
  - Auto-play via `LaunchedEffect(frames.size)` on first load
  - `seekToNow()` added to RadarViewModel — finds last past frame
  - **Key files:** `ui/radar/RadarScreen.kt`, `ui/radar/RadarViewModel.kt`

---

**Phase 4 Exit Criteria:** Radar screen opens to active location centred on dark map; radar overlay plays back 12 past frames + 2 nowcast frames; scrubbing works; playback loops cleanly.

---

### Phase 5 — Alerts

**Goal:** Surface MeteoAlarm warnings clearly and without crying wolf.

---

- [x] **Session 5.1 — MeteoAlarm Integration** ✅ (built in Phase 1.8)
  - `MeteoAlarmService.kt` (from Phase 1.8 stub) — implement fully:
    - Retrofit `@GET` with raw XML return type via `ScalarsConverterFactory`
    - Feed URL pattern: `https://feeds.meteoalarm.org/feeds/meteoalarm-legacy-atom-{country_slug}`
    - Country slug map: `"Sweden" → "sweden"`, `"Germany" → "germany"`, etc. (build a comprehensive `Map<String, String>` covering EU countries)
    - XML parser: `android.util.Xml.newPullParser()` → iterate `<entry>` elements → extract `<title>` (alert type + area), `<summary>` (headline), `<cap:severity>`, `<cap:effective>`, `<cap:expires>`, `<cap:areaDesc>`
    - Filter: drop alerts where `expires < System.currentTimeMillis()`; drop "green/no warning" entries
    - Return `List<WeatherAlert>` — empty list if no active alerts; never throw for non-EU locations
  - `AlertRepository.refreshAlerts(location)`: checks if location is in Europe (lat 35–71, lon -25–50); if yes → calls MeteoAlarm; if no → returns empty list + stores a `locationHasNoAlertSupport` flag for that location
  - `WeatherRefreshWorker` already calls `AlertRepository.refreshAlerts` for each location
  - **Key files:** `data/api/MeteoAlarmService.kt` (complete implementation), `data/repository/AlertRepositoryImpl.kt` (update)
  - **Exit test:** Unit test — MeteoAlarm XML parser correctly extracts severity, headline, and expiry from a real sample feed XML (grab one from the MeteoAlarm website and hardcode it)

---

- [x] **Session 5.2 — Alert UI** ✅ 2026-03-23
  - `AlertBanner.kt`: the sticky banner on `WeatherScreen`; colour-coded by severity:
    - Minor → yellow `#FDD835`; Moderate → orange `#FB8C00`; Severe → deep red `#E53935`; Extreme → purple `#8E24AA`
    - Shows highest-severity active alert as: `[Icon] Severe Wind Warning — Tap for details`
    - Multiple alerts: show count badge "3 active alerts"
    - `AnimatedVisibility(slideInVertically)` — slides in from top when alerts arrive
  - `AlertsScreen.kt`: full list of active alerts for current location; each card shows full headline, area description, effective/expires times, severity colour bar on left edge; "No active alerts" empty state; "Alerts not available for this region" state for Egypt and other non-EU locations (shown as a subtle grey info card, not an error)
  - `AlertsScreen` accessible from: alert banner "See all" tap, and Settings (for any location)
  - Alert notification: when `WeatherRefreshWorker` finds a new Severe/Extreme alert for any saved location → fire a `POST_NOTIFICATIONS` notification on a dedicated `eweather_alerts` channel (IMPORTANCE_HIGH); notification body = alert headline; tapping opens `AlertsScreen` for that location
  - **Key files:** `ui/alerts/AlertBanner.kt`, `ui/alerts/AlertsScreen.kt`, update `WeatherRefreshWorker.kt`
  - **Exit test:** Manually insert a test `WeatherAlert` into Room with Severe severity → alert banner appears on WeatherScreen in correct orange; tap → AlertsScreen shows full detail; Egypt location shows "not available" state, not an error

---

**Phase 5 Exit Criteria:** Active MeteoAlarm alerts for Swedish location appear on WeatherScreen with correct severity colour. Egypt location shows graceful "not available" state. New Severe/Extreme alert triggers a device notification.

---

### Phase 6 — Widget

**Goal:** A single clean, beautiful home screen widget that matches the app's design language.

---

- [x] **Session 6.1 — Widget Layout + Provider** ✅ 2026-03-23
  - Single widget with two size classes: `2×2` (compact) and `4×2` (standard)
  - `WeatherWidgetProvider.kt`: `AppWidgetProvider`; `RemoteViews`-based (no Compose until API 36+)
  - **2×2 layout** (`res/layout/widget_compact.xml`):
    - Sky gradient background (pick a static colour from current `skyCategory` — `RemoteViews` can't do animated gradients; use a single dominant colour as `backgroundColor` on the root layout)
    - City name (12sp, white, truncated)
    - Large temperature (36sp thin weight, white)
    - Weather description (10sp, 70% alpha)
    - Small weather condition icon (24dp)
  - **4×2 layout** (`res/layout/widget_standard.xml`):
    - Same left column as 2×2
    - Right half: today's high/low, humidity, wind speed, feels like
    - Thin bottom strip: next 4 hours as mini temperature + icon column
  - Weather condition icon in widget: pre-rendered into `Bitmap` from `VectorDrawable` at 48dp — `RemoteViews.setImageViewBitmap`
  - Background colour: map `skyCategory` → a representative flat colour (e.g. `CLEAR_DAY → #4FC3F7`, `NIGHT → #1C2951`, `STORM → #37474F`) — `RemoteViews.setInt(R.id.root, "setBackgroundColor", color)`
  - Tap widget → launches `MainActivity` opening on `WeatherScreen` for that widget's location
  - **Key files:** `ui/widget/WeatherWidgetProvider.kt`, `res/layout/widget_compact.xml`, `res/layout/widget_standard.xml`, `res/xml/widget_info.xml`
  - **Exit test:** Add both widget sizes to home screen; they display city name and current temperature; tap opens app to correct screen

---

- [x] **Session 6.2 — Widget Update Scheduling + Data** ✅ 2026-03-23
  - `WeatherWidgetProvider` registered in manifest with `android:updatePeriodMillis=0` (system minimum is 30min which is too slow and battery-wasteful for a weather app — use WorkManager instead)
  - Widget update logic lives in `WeatherRefreshWorker` (already runs on schedule): after refreshing weather → broadcasts `AppWidgetManager.ACTION_APPWIDGET_UPDATE` → `WeatherWidgetProvider.onUpdate` fires
  - `WeatherWidgetProvider.onUpdate`: reads latest `WeatherCacheEntity` from Room synchronously (via `runBlocking` — acceptable in `AppWidgetProvider` since it's already off-main-thread in WorkManager context); builds `RemoteViews`; updates all widget instances via `AppWidgetManager.updateAppWidget(ids, remoteViews)`
  - If no cached data → show "Tap to refresh" text in widget
  - Widget also updates immediately on app foreground (one-shot broadcast from `MainActivity.onResume`)
  - `PendingIntent.FLAG_IMMUTABLE` on all widget intents
  - **Key files:** Update `WeatherWidgetProvider.kt`, update `WeatherRefreshWorker.kt`
  - **Exit test:** Set refresh interval to 1h (default); background app; wait for WorkManager to run (or trigger manually with `adb shell am broadcast`); widget updates with fresh temperature; "Tap to refresh" appears if Room is empty

---

**Phase 6 Exit Criteria:** Both widget sizes show accurate current weather. Background updates work. Tapping widget launches correct screen. "Not playing" equivalent (no data) state handled gracefully.

---

### Phase 7 — Settings, Polish & Battery

**Goal:** Complete settings, production-quality feel, sustainable battery usage.

---

- [x] **Session 7.1 — Settings Screen** ✅ 2026-03-23
  - `SettingsScreen.kt`:
    - **Units**: Temperature (°C / °F), Wind speed (m/s / km/h / mph) — changing either triggers `WeatherViewModel` to re-emit with converted values
    - **Refresh**: Background refresh interval (30min / 1h / 2h / 4h / Manual only); "Refresh on WiFi only" toggle
    - **Notifications**: Alerts toggle (on by default); "Only for Severe and Extreme" vs "All alerts"
    - **Display**: 12h/24h time format; "Show UV index in detail cards" toggle; "Reduce motion" override (also reads system accessibility setting)
    - **Widget**: "Show hourly strip in widget" toggle (only affects 4×2); "Widget background opacity" slider
    - **About**: App version; data sources attribution — "Weather data: Open-Meteo (open-meteo.com)", "Radar: RainViewer", "Alerts: MeteoAlarm"
  - Attribution is important — Open-Meteo requires attribution in the UI for free usage
  - **Key files:** `ui/settings/SettingsScreen.kt`
  - **Exit test:** Change temperature unit to °F → WeatherScreen shows °F immediately; change refresh interval → WorkManager constraint updated; attribution text visible in About section

---

- [x] **Session 7.2 — Battery & Network Efficiency** ✅ 2026-03-23
  - Audit all API calls — enforce: weather data cached 1h (configurable), air quality 1h, alerts 30min, radar frame list 10min; never fetch if cache is valid and app is in background
  - `WeatherRefreshWorker` constraints: `NetworkType.CONNECTED`, `setRequiresBatteryNotLow(true)`; use `setBackoffCriteria(BackoffPolicy.EXPONENTIAL)` on failure
  - Location: one-shot fix per foreground resume; no continuous location tracking; no background location
  - OkHttp: `Cache(cacheDir, 10MB)` for HTTP caching of API responses; `connectTimeout = 15s`; `readTimeout = 15s` (weather APIs can be slow)
  - Coil tile cache: map tiles for osmdroid cached by osmdroid's built-in tile cache (no extra config needed)
  - Radar tiles: only fetch tiles for the current viewport zoom level — osmdroid handles this natively
  - No wakelocks; no foreground service; no continuous GPS polling
  - **Key files:** `di/NetworkModule.kt` (update), `data/worker/WeatherRefreshWorker.kt` (update)
  - **Exit test:** `adb shell dumpsys batterystats` — no abnormal wakelocks. `adb shell dumpsys jobscheduler` — WorkManager scheduled at correct interval. Background refresh does not run more often than the selected interval.

---

- [x] **Session 7.3 — Empty / Error / Loading States** ✅ 2026-03-23
  - Loading state (first launch, no cache): full-screen animated shimmer over a neutral grey-blue sky; shimmer uses `InfiniteTransition` gradient sweep; skeleton cards at correct dimensions
  - Error state with cached data: subtle top banner "Couldn't update — showing data from X ago" with Retry; WeatherScreen still fully usable with stale data
  - Error state with no cache: full-screen with sky background + glassmorphism card: "Couldn't load weather" + last attempt time + Retry button + offline hint
  - No location permission state: full-screen prompt explaining why location is needed, with "Grant permission" button; also shows "Add location manually" as alternative
  - Network offline state: `NetworkMonitor` (same as eMusic) → `OfflineBanner` at top; all screens read from Room, no crash
  - For radar: if RainViewer unreachable → "Radar temporarily unavailable" over map; map tiles still show (cached by osmdroid)
  - **Key files:** `ui/components/OfflineBanner.kt`, `ui/components/ShimmerSky.kt`, error states in each screen's ViewModel
  - **Exit test:** Disable WiFi → offline banner appears; WeatherScreen shows cached data. Revoke location permission → permission prompt appears. Force API error → error banner shows with correct "X ago" time.

---

- [x] **Session 7.4 — Animation Polish + Accessibility** ✅ 2026-03-23
  - Check `LocalConfiguration.current` and `WindowManager` for accessibility reduce-motion setting (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`) — if set, `WeatherAnimationLayer` renders nothing; sky gradient transitions are instant instead of 2s
  - All `animateColorAsState` transitions respect `motionReducer` utility: `if (reduceMotion) snap() else tween(2000)`
  - Glass card blur (`RenderEffect`) is guarded: `if (Build.VERSION.SDK_INT >= 31 && !reduceMotion)` — older API and reduce-motion both get solid surface card instead
  - Verify all tappable elements have minimum 48×48dp touch target
  - Content descriptions on all weather icons: `contentDescription = "Clear sky"`, `"Heavy rain"` etc. from `WmoCode.describe(code).label`
  - Sun arc in `AstronomyCard`: `semantics { contentDescription = "Sunrise at $sunriseTime, sunset at $sunsetTime" }`
  - Dark/light theme: follows system; sky background is always full-colour regardless of theme (it's the background); cards adjust text colour based on theme
  - **Key files:** `ui/weather/components/WeatherAnimationLayer.kt` (update), `ui/theme/Theme.kt`, all screen composables (accessibility audit)
  - **Exit test:** Enable "Reduce motion" in Android Accessibility → animations stop; sky transitions are instant; cards still render correctly. TalkBack announces weather condition on focus.

---

- [x] **Session 7.5 — ADB Deploy Script + Logging** ✅ 2026-03-23
  - `scripts/deploy.sh`: `./gradlew assembleRelease && jarsigner … && adb devices && adb install -r app-release.apk`; keystore from `local.properties`; exits non-zero if no device connected
  - `Timber` initialised in debug only; all log calls via `Timber.d/e`; release builds have ProGuard strip all Timber calls
  - Global crash handler in `EWeatherApp.onCreate`: writes uncaught exceptions to `filesDir/crash.log`
  - **Key files:** `scripts/deploy.sh`
  - **Exit test:** `./scripts/deploy.sh` builds and installs to connected device cleanly

---

**Phase 7 Exit Criteria:** App feels polished and complete. Battery usage is minimal — no unnecessary wakeups. All error and empty states handled gracefully. Animations respect reduce-motion. Attribution visible in Settings. Deploy script works.

---

## Claude Code Execution Notes

Provide this context at the start of every Claude Code session:

1. **No GMS** — `android.location.LocationManager` only; reject any library with a GMS transitive dependency
2. **Design first** — if anything feels like it sacrifices visual quality for code simplicity, flag it
3. **Single source of truth** — Room cache; API never emits directly to UI
4. **Attribution required** — Open-Meteo requires attribution in the UI; ensure it's in Settings → About
5. **Test after each session:** `./gradlew lint && ./gradlew testDebug && adb install -r`
6. **No API keys** — all data sources are keyless; do not add placeholder key fields
7. **minSdk 29** — use modern APIs freely

---

## Changelog

| Date | Change |
|---|---|
| 2026-03-23 | Initial plan created — 7 phases, 28 sessions |
| 2026-03-23 | Session 1.1 complete — project scaffolding, build passes |
| 2026-03-23 | Session 1.2 complete — 9 domain model files created |
| 2026-03-23 | Session 1.3 complete — WmoCode (22 codes), MoonPhaseCalculator (synodic), AqiDescription (6 levels) |
| 2026-03-23 | Session 1.4 complete — 4 Retrofit services, 4 DTOs, OpenMeteoMapper, NetworkModule (Hilt) |
| 2026-03-23 | Session 1.5 complete — Room DB (3 entities, 3 DAOs, AppDatabase, DatabaseModule) |
| 2026-03-23 | Session 1.6 complete — LocationProvider (one-shot, suspendCancellableCoroutine, DataStore cache) + PreferencesModule |
| 2026-03-23 | Session 1.7 complete — 3 repository interfaces + impls, AppPreferences + repo, RepositoryModule, @Serializable on domain models |
| 2026-03-23 | Session 1.8 complete — MeteoAlarmService + Parser + Countries, WeatherRefreshWorker, AlertRepo wired — PHASE 1 COMPLETE |
| 2026-03-23 | Session 2.1 complete — SkyBackground (12 gradient states, 2s animated transitions) + deriveSkyCategory() |
| 2026-03-23 | Session 2.2 complete — WeatherAnimationLayer (7 animations: rain, snow, thunder, sun rays, stars, fog, wind) |
| 2026-03-23 | Session 2.3 complete — CurrentConditionsCard (72sp temp, city, description, feels like, H/L, text shadows) |
| 2026-03-23 | Session 2.4 complete — GlassCard modifier + HourlyForecastStrip (24h LazyRow, precip bars, current highlight, weather emojis) |
| 2026-03-23 | Session 2.5 complete — DailyForecastCard (10 rows, proportional temp range bars, expand/collapse detail) |
| 2026-03-23 | Session 2.6 complete — DetailCardsGrid (8 cards: wind+compass, humidity+gauge, UV, AQI, visibility, pressure, precip+barchart, feels like) |
| 2026-03-23 | Session 2.7 complete — AstronomyCard (sun arc + glowing position dot, sunrise/sunset, daylight, golden hour, moon phase) |
| 2026-03-23 | Session 2.8 complete — WeatherViewModel + WeatherScreen + MainActivity wired — PHASE 2 COMPLETE |
| 2026-03-23 | Session 3.1 complete — LocationsScreen + LocationsViewModel + NavGraph + MainActivity updated with EWeatherNavHost |
| 2026-03-23 | Session 3.2 complete — LocationSearchScreen + LocationSearchViewModel + NavGraph SEARCH route wired |
| 2026-03-23 | Session 3.3 complete — HorizontalPager carousel, multi-location ViewModel, DotIndicator — PHASE 3 COMPLETE |
| 2026-03-23 | Session 4.1 complete — RadarViewModel (playback, auto-refresh, location) + RADAR route stub |
| 2026-03-23 | Session 4.2 complete — RadarScreen (osmdroid dark map, RainViewer tile overlay, frame swapping, radar button on weather screen) |
| 2026-03-23 | Session 4.3 complete — Playback controls (timestamp, slider, play/pause, Now button, color legend, auto-play) — PHASE 4 COMPLETE |
| 2026-03-23 | Session 5.1 already complete from Phase 1.8 (MeteoAlarm data layer) |
| 2026-03-23 | Session 5.2 complete — AlertBanner (severity colors, animated), AlertsScreen + AlertsViewModel, NavGraph ALERTS route — PHASE 5 COMPLETE |
| 2026-03-23 | Session 6.1 complete — WeatherWidget (Glance, responsive compact/standard), WeatherWidgetReceiver, widget_info XML, manifest registered |
| 2026-03-23 | Session 6.2 complete — Widget updates from WeatherRefreshWorker + MainActivity.onResume — PHASE 6 COMPLETE |
| 2026-03-23 | Session 7.1 complete — SettingsScreen + SettingsViewModel (units, refresh, notifications, attribution), gear icon on weather screen |
| 2026-03-23 | Session 7.2 complete — OkHttp 10MB HTTP cache added; battery/network audit verified (all constraints already in place) |
| 2026-03-23 | Session 7.3 complete — ShimmerLoading, StaleBanner, OfflineBanner, fetchedAt tracking in WeatherUiState |
| 2026-03-23 | Session 7.4 complete — ReduceMotion utility, SkyBackground snap(), semantics on CurrentConditionsCard + AstronomyCard, 48dp touch targets |
| 2026-03-23 | Session 7.5 complete — Debug-only Timber, crash handler, deploy script — PHASE 7 COMPLETE — ALL PHASES DONE |
