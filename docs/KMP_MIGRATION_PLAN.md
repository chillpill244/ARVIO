# MUVIO → KMP / Compose Multiplatform (iOS + Android mobile)

> Status: **Approved** — implementation in progress on branch `cmp`.
> This document is the authoritative migration plan and supersedes the earlier draft.

## Context

MUVIO (`com.muvio.tv`, pkg `com.arflix.tv`) is today a single-module, **Android-TV-first**
Kotlin/Compose app: Hilt DI (~15 `@HiltViewModel`, ~811 `@Inject`, 4 modules),
Retrofit + Gson + OkHttp (10 APIs), Media3/ExoPlayer (+ Jellyfin FFmpeg decoder), Room
(downloads), DataStore, `tv-foundation`/D-pad UI, a JS addon/scraper runtime, and
WorkManager sync. ~204 Kotlin files, the bulk TV-specific.

We want **native iOS and Android *touch* apps** that reuse the valuable, already-correct
business logic (per-addon timeouts, threading, caching, stream/IPTV resolution, sync)
instead of rewriting it. We have a near-perfect reference: **NuvioMobile**
(`/Users/rahulvemula/chill/NuvioMobile`), a production KMP/Compose-Multiplatform media app
with the exact architecture we're targeting (`androidTarget()` + `iosArm64`, CMP UI, Ktor +
supabase-kt, **Media3 on Android / libmpv MPV+Metal on iOS** behind one player interface,
**QuickJS-kt** JS plugins, Coil3, androidx-navigation-compose). We will lift its
platform-seam code (player, downloads, MPV bridge, JS runtime) and migrate MUVIO's own
logic into shared code.

### Locked decisions
1. **Scope:** new CMP **touch** app for **iOS + Android mobile**, sharing logic + UI. The
   **existing Android TV app stays** (repointed at `:shared`) as a regression baseline.
2. **iOS player:** **MPV (libmpv + Metal)**, ported from NuvioMobile. Plays mp4/mkv/HLS;
   downloads mp4/mkv.
3. **HLS downloads:** **accepted gap on iOS** (Nuvio's URLSession downloader blocks `.m3u8`).
   **Android mobile keeps HLS download** via MUVIO's existing `HlsDownloadUtil.kt`.
4. **Distribution:** **sideload only** (single full-featured iOS build via AltStore/TestFlight).
5. **DI:** **Koin** (maps 1:1 onto the current Hilt graph; preserves constructor injection + MockK tests).

## Target structure

```
muvio/
├─ shared/                         # KMP module (new)
│  └─ src/
│     ├─ commonMain/   # models, repositories, viewmodels, parsing, Koin DI, CMP touch UI, JS-plugin runtime
│     ├─ androidMain/  # actual: OkHttp engine, DataStore, Room/SQLDelight, Media3 player, HLS downloader, QuickJS-android
│     └─ iosMain/      # actual: Darwin engine, iOS storage, MPV player bridge, URLSession downloader, QuickJS-kt
├─ androidApp/                     # EXISTING Android TV app → depends on :shared (regression baseline; TV UI untouched)
├─ androidMobileApp/  (or a flavor)# CMP touch app for Android phones/tablets
└─ iosApp/                         # Xcode project; CMP UI on :shared + MPV/Metal Swift bridge
```

UI: **Compose Multiplatform**, one new **touch** UI shared by iOS + Android mobile,
consuming shared viewmodels. The current `tv-foundation`/D-pad UI is **not** reused (it is
TV-only and the wrong interaction model) — the TV app keeps it as-is.

## Dependency mapping

| Current (Android) | KMP replacement | Effort |
|---|---|---|
| Hilt (4 modules, ~811 @Inject) | **Koin** (`commonMain`): `module { single { } }`, `viewModel { }`, qualifiers→`named()` | Medium |
| Retrofit + converter-gson + OkHttp (10 APIs) | **Ktor Client** + `kotlinx.serialization` (OkHttp engine Android / Darwin engine iOS) | Medium |
| Gson | **kotlinx.serialization** | Low–Med (mechanical) |
| DataStore Preferences | **androidx DataStore multiplatform (1.1+)** | Low |
| Room (downloads DB) | **Room-KMP (2.7+)** (minimizes change) | Low |
| Media3/ExoPlayer (+FFmpeg, OkHttp datasource) | `expect/actual` player → **Media3 (Android)** / **libmpv MPV (iOS)** | High (Nuvio code ports) |
| Coil 2 | **Coil 3 (multiplatform)** | Low |
| WorkManager (Trakt/IPTV refresh) | Android: WorkManager · iOS: **BGTaskScheduler** via `expect/actual` | Medium |
| Android JS addon runtime | **QuickJS-kt** (multiplatform), per Nuvio's `PluginRuntime` | Medium–High |
| Navigation Compose | **androidx-navigation-compose (multiplatform)** | Medium |

Reference versions from NuvioMobile `gradle/libs.versions.toml`: Kotlin 2.3.0, Compose
Multiplatform 1.11.1, AGP 8.13.2, Ktor 3.4.1, supabase-kt 3.4.1, Coil 3.5.0-beta01,
Media3 1.8.0, QuickJS-kt 1.0.5, KSoup 0.2.6. (MUVIO is on Kotlin 2.1.0 / Compose BOM
2024.06.00 today — the `:shared` module will move up to the Nuvio toolchain; the TV app's
pinned `tv-foundation` constraint stays isolated to `androidApp`.)

## `expect/actual` platform seams (port from Nuvio where noted)

- **HttpClientEngine** — OkHttp (Android) / Darwin (iOS). DoH/custom interceptors are
  Android-only today → make optional on iOS.
- **Player** — `PlayerEngineController` interface in `commonMain`; Android `actual` = Media3,
  iOS `actual` = MPV. **Port** Nuvio `PlayerEngine.kt` / `.android.kt` / `.ios.kt`,
  `MPVPlayerBridge.swift`, `MetalLayer.swift`. Supports custom HTTP headers
  (`http-header-fields`), external + embedded subs (ASS/SSA/SRT).
- **Downloads** — `DownloadsPlatformDownloader` expect/actual. iOS `actual` = port Nuvio's
  URLSession downloader (mp4/mkv). Android `actual` = keep MUVIO's `HlsDownloadUtil` (mp4/mkv
  **+ HLS**). Make the shared `DownloadsRepository.isSupportedDownloadUrl()` **platform-aware**
  (block `.m3u8` on iOS only) rather than copying Nuvio's both-platforms block verbatim.
- **JS plugin runtime** — QuickJS-kt (`commonMain` API; QuickJS-android `.aar` / QuickJS-kt iOS).
- **Settings/secure storage**, **background refresh scheduler** (WorkManager / BGTaskScheduler),
  **platform info** (UA, device type, file/cache paths).

## Phased plan

1. **Scaffold `:shared`** KMP module (androidTarget + iosArm64/iosSimulatorArm64). Point the
   **existing Android TV app** at `:shared` so it keeps building (regression baseline) while
   code migrates in. Adopt the Nuvio toolchain for `:shared`.
2. **Proof-of-concept (first code step):** migrate **one networking-heavy repository
   end-to-end** — `StreamRepository` or `IptvRepository` — to `commonMain` with
   Ktor + kotlinx.serialization + Koin, with iOS Darwin engine, exercised from a tiny iOS
   test. Proves native networking + timeouts + JSON + DI on device before committing.
3. **Migrate data/domain into `commonMain`** slice-by-slice: models → API clients
   (Retrofit→Ktor) → repositories (Hilt→Koin, Gson→kotlinx.serialization). Verify each
   against the TV app after each slice. Migrate Room→Room-KMP, DataStore→DataStore-MP.
4. **Move the 15 viewmodels** to `commonMain` (keep `UiState`/`StateFlow`; swap Hilt for Koin
   `viewModel { }`).
5. **Wire platform seams** via expect/actual: player (Media3 + MPV), downloads (HLS-aware
   split), JS plugin runtime (QuickJS-kt), background refresh, storage.
6. **Build the touch UI** (Compose Multiplatform) screen-by-screen on the shared viewmodels —
   home, search, details, player, downloads, profiles, settings, IPTV. Ship to **iOS + Android
   mobile**.
7. **iOS app + sideload packaging** (AltStore/TestFlight); retire the abandoned Capacitor/web
   attempt.

## Critical files

**MUVIO (source of logic to migrate):** `app/.../data/repository/*` (StreamRepository,
IptvRepository, CloudSyncRepository, TraktRepository, AuthRepository, MediaRepository),
`data/api/*`, `data/model/*`, `di/{AppModule,DatabaseModule}.kt`,
`network/{OkHttpProvider,ApiProxyInterceptor}.kt`, `data/db/*` (Room),
`util/HlsDownloadUtil.kt` (Android HLS download to retain), `navigation/AppNavigation.kt`,
the JS addon runtime (`AddonRuntime*.kt`, `HttpLocalScraperRuntime.kt`).

**NuvioMobile (reference to port):**
`composeApp/src/commonMain/.../features/player/PlayerEngine.kt`,
`.../player/PlayerEngine.{android,ios}.kt`, `iosApp/.../Player/MPVPlayerBridge.swift`,
`MetalLayer.swift`; `features/downloads/DownloadsRepository.kt`,
`DownloadsPlatformDownloader.{kt,android.kt,ios.kt}`;
`features/plugins/{PluginRepository,PluginRuntime}.kt`;
`core/network/SupabaseProvider.kt`; `gradle/libs.versions.toml`; `composeApp/build.gradle.kts`.

## Verification

- **Per slice:** after each repository/VM migration, build + run the **existing Android TV
  app** on `:shared` and confirm no regression (it's the baseline). Run `./gradlew test`
  (JUnit/MockK/Turbine suites carried into `commonTest`).
- **PoC gate (Phase 2):** run the migrated repository from an iOS test (simulator) — confirm a
  real network fetch + JSON parse + timeout behavior on Darwin engine.
- **Player:** on iOS device/simulator, verify mp4, mkv, and HLS playback; custom headers on a
  scraped stream; external + embedded subtitle selection. On Android mobile, verify Media3
  parity.
- **Downloads:** iOS downloads + plays back a local mp4/mkv (`file://`). Android mobile
  downloads + plays back mp4/mkv **and** HLS (via `HlsDownloadUtil`).
- **JS plugins:** run an existing nuvio-providers scraper through QuickJS-kt on both platforms;
  confirm stream results match the Android TV app.
- **End-to-end:** sideload the iOS build (AltStore/TestFlight); sign in, browse, resolve a
  source, play, and sync watch progress to Supabase.

## Risks / open questions
- **MPV/Metal + cinterop** integration is the biggest native lift, but Nuvio's bridge ports
  largely as-is.
- **Navigation rewrite** for touch (no D-pad/focus) — new UI, not a port.
- **Room-KMP** maturity on iOS (single downloads DB; low risk) — fall back to SQLDelight if needed.
- **DNS-over-HTTPS / custom OkHttp interceptors** — confirm Darwin equivalents or drop on iOS.
- **QuickJS-android** uses a custom `.aar` in Nuvio (`quickjs-kt-...-nuvio.aar`) — confirm we
  can reuse/rebuild it.
- **Toolchain:** requires a Mac + Xcode; iOS builds can't run in CLI sandboxes.
