# Muvio KMP — Implementation Progress

## Status: ALL PHASES COMPLETE ✅

### Phases Complete
- **Phase 1**: `:shared` KMP scaffold (androidTarget + iosArm64 + iosSimulatorArm64)
- **Phase 2**: Stremio network PoC (StremioModels, StremioClient, HttpClientFactory, basic DI)
- **Phase 3**: Full data/domain layer in commonMain
  - Domain models: MediaModels, CatalogModels, IptvModels, ProfileModels, AppException, AppResult
  - Network clients: TmdbClient (Ktor), TraktClient (Ktor), StremioClient (Ktor)
  - Storage: PlatformPreferences expect/actual (SharedPreferences Android / NSUserDefaults iOS)
  - Util: currentTimeMillis / randomUUID expect/actual
  - Repositories: MediaRepository, WatchHistoryRepository, AddonRepository
- **Phase 4**: ViewModels in commonMain
  - HomeViewModel, DetailsViewModel, SearchViewModel, PlayerViewModel
- **Phase 5**: Platform seams
  - PlayerEngine expect/actual (Android: ExoPlayer bridge / iOS: MPV bridge via MuvioPlayerBridgeFactory)
  - PlatformPreferences (done in Phase 3)
  - PlatformUtils (done in Phase 3)
- **Phase 6**: CMP Touch UI
  - SharedModule.kt: AppConfig + 7 Koin modules
  - androidApp/ module: MuvioApplication, MainActivity, themes, strings
  - Shared UI: App, AppTheme, AppNavigation (type-safe), MediaCard, HomeScreen, SearchScreen, DetailsScreen, PlayerScreen, SettingsScreen
- **Phase 7**: iOS app shell + sideload packaging ✅
- **Phase 8**: P0 component ports + Settings page ✅
  - `MediaCategoryRail` — horizontal lazy category row; replaces inline CategoryRow in HomeScreen + SearchScreen
  - `MobileHeroBanner` — full-bleed hero with gradient scrim, metadata, Play/Info buttons; extracted from HomeScreen
  - `ContinueWatchingCard` — 16:9 card with progress bar, type badge, play overlay
  - `AppBottomBar` — extracted navigation bar component; wired into App.kt via `AppBottomBar`
  - `AppTopBar` — logo + search/settings icon buttons; can overlay hero content
  - `Sidebar` — vertical icon-only nav for tablet/desktop with animated selection state
  - `SettingsViewModel` — loads/installs/toggles/removes Stremio addons via `AddonRepository`
  - `SettingsScreen` — full settings page: addon management (list + URL install + toggle + remove), Content / Playback / Account / About sections, Snackbar toast
  - `MuvioPlayerBridge.kt` (iosMain) — Kotlin protocol Swift implements
  - `MuvioPlayerBridgeFactory` — singleton factory registered at app startup
  - `PlayerEngine.ios.kt` — polls bridge every 250ms; dispatches to PlayerEngineListener
  - `MainViewController.kt` (iosMain) — `ComposeUIViewController { App() }` entry point
  - `iosApp/iosApp/iOSApp.swift` — SwiftUI @main entry with orientation delegate
  - `iosApp/iosApp/ContentView.swift` — `ComposeView` + `RootComposeViewController`
  - `iosApp/iosApp/OrientationLockCoordinator.swift` — portrait/landscape lock + `RootComposeViewController`
  - `iosApp/iosApp/Player/MetalLayer.swift` — custom CAMetalLayer with EDR guard
  - `iosApp/iosApp/Player/MPVPlayerBridge.swift` — full MPV implementation (ported from NuvioMobile)
  - `iosApp/iosApp/Info.plist` — bundle metadata, NSAllowsArbitraryLoads, orientations
  - `iosApp/iosApp.xcodeproj/project.pbxproj` — Xcode 16 project (file-synced, MPVKit SPM dep, Gradle build phase)
  - `iosApp/Configuration/Config.xcconfig` — Team ID / bundle ID slot for the developer

### Build Status
- `./gradlew :shared:compileDebugKotlinAndroid` — ✅ PASSING
- `./gradlew :shared:compileKotlinIosSimulatorArm64` — ✅ PASSING
- `./gradlew :androidApp:assembleDebug` — ✅ PASSING
- iOS Xcode build — requires: MPVKit submodule + Apple Developer signing (see build instructions below)

---

## iOS Build Instructions

### Prerequisites
1. **Xcode 16+** on macOS
2. **Java 17** (for Kotlin/Gradle compilation)
3. **Apple Developer account** — free accounts work for 7-day sideload via AltStore

### Build steps
1. Set your Team ID in `iosApp/Configuration/Config.xcconfig`:
   ```
   DEVELOPMENT_TEAM = YOUR10CHARID
   ```
2. Open the project in Xcode:
   ```bash
   open muvio-kmp/iosApp/iosApp.xcodeproj
   ```
3. Xcode will automatically resolve **MPVKit** via Swift Package Manager (remote reference to `https://github.com/NuvioMedia/MPVKit.git`, `main` branch — no GitHub login or submodule required; all binaries download from GitHub Releases over HTTPS)
4. Select your iPhone as the run destination and hit ▶

The "Compile Kotlin Framework" build phase runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode` automatically during each Xcode build.

### Sideload (AltStore)
1. Archive the app: **Product → Archive**
2. Export with "Ad Hoc" or "Development" signing (no App Store account required)
3. Sideload the `.ipa` via **AltStore** or **SideStore**

---

## Key File Locations
```
muvio-kmp/
├── shared/src/commonMain/kotlin/com/muvio/shared/
│   ├── di/SharedModule.kt          — Koin modules (AppConfig + 7 modules)
│   ├── domain/                     — MediaModels, IptvModels, CatalogModels, ProfileModels, AppException, AppResult
│   ├── network/                    — TmdbClient, TraktClient, StremioClient, HttpClientFactory (expect)
│   ├── stremio/                    — StremioModels (wire protocol types)
│   ├── storage/                    — PlatformPreferences (expect)
│   ├── util/                       — PlatformUtils (expect), StringUtils
│   ├── repository/                 — MediaRepository, WatchHistoryRepository, AddonRepository
│   ├── viewmodel/                  — HomeViewModel, DetailsViewModel, SearchViewModel, PlayerViewModel
│   ├── player/                     — PlayerEngine (expect), PlayerEngineListener
│   └── ui/
│       ├── App.kt                  — Root Composable
│       ├── theme/AppTheme.kt       — Dark/light M3 colors (primary = Teal #00C8A0)
│       ├── navigation/AppNavigation.kt — Type-safe @Serializable routes + NavHost
│       ├── components/MediaCard.kt
│       └── screens/home|details|search|player|settings/
├── shared/src/androidMain/kotlin/com/muvio/shared/
│   ├── network/HttpClientFactory.android.kt  — OkHttp engine
│   ├── storage/PlatformPreferences.android.kt — SharedPreferences + MuvioAndroidContext
│   ├── util/PlatformUtils.android.kt
│   └── player/PlayerEngine.android.kt        — ExoPlayer bridge (pending/dispatch pattern)
├── shared/src/iosMain/kotlin/com/muvio/shared/
│   ├── network/HttpClientFactory.ios.kt      — Darwin engine
│   ├── storage/PlatformPreferences.ios.kt    — NSUserDefaults
│   ├── util/PlatformUtils.ios.kt             — NSDate
│   ├── player/MuvioPlayerBridge.kt           — Kotlin protocol Swift implements via ObjC bridge
│   ├── player/PlayerEngine.ios.kt            — Polls bridge every 250ms
│   └── MainViewController.kt                 — ComposeUIViewController entry point
├── androidApp/
│   ├── build.gradle.kts
│   └── src/androidMain/
│       ├── AndroidManifest.xml
│       ├── kotlin/com/muvio/app/MuvioApplication.kt
│       ├── kotlin/com/muvio/app/MainActivity.kt
│       └── res/values/{strings,themes}.xml
└── iosApp/
    ├── Configuration/Config.xcconfig          — Set DEVELOPMENT_TEAM here
    ├── iosApp/
    │   ├── iOSApp.swift                       — SwiftUI @main + OrientationLockAppDelegate
    │   ├── ContentView.swift                  — ComposeView + RootComposeViewController
    │   ├── OrientationLockCoordinator.swift   — Orientation lock + RootComposeViewController
    │   ├── Info.plist                         — Bundle metadata
    │   └── Player/
    │       ├── MPVPlayerBridge.swift          — MPV player (MuvioPlayerBridge impl)
    │       └── MetalLayer.swift               — Custom CAMetalLayer
    └── iosApp.xcodeproj/project.pbxproj      — Xcode 16 project file

## Architecture Decisions Made
- AppConfig data class holds tmdbApiKey, traktClientId, defaultProfileId — passed to Koin at app startup
- TMDB/Trakt keys read from BuildConfig (androidApp) — keys in local.properties or CI env vars
- PlaylistGroupKey: changed from `value class` to `data class` (KMP Native value class limitation)
- PlayerEngine: `var` in expect (not `val`) — Android needs @Volatile var, iOS reads freely
- MuvioAndroidContext.applicationContext: must be initialized before startKoin in Application.onCreate
- iOS player bridge: polling pattern (250ms interval) — matches NuvioMobile's proven approach
- MPVKit: local Swift Package reference pointing to NuvioMobile's submodule path

## Dependency Notes
- koin-compose-viewmodel: in shared/build.gradle.kts commonMain (for koinViewModel())
- compose.materialIconsExtended: in shared/build.gradle.kts commonMain (for Icons.*)
- `parameter` import: in TraktClient.kt (io.ktor.client.request.parameter)
- MPVKit: remote SPM dependency → https://github.com/NuvioMedia/MPVKit.git (branch: main, all-remote binary targets)
```
