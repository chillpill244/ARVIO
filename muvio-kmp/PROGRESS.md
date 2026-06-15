# Muvio KMP — Implementation Progress

## Status: Phase 6 COMPLETE ✅ — Phase 7 (iOS app shell) next

## Last commit: 213717f3 feat: implement KMP phases 3-6

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
  - PlayerEngine expect/actual (Android: ExoPlayer bridge / iOS: MPV stub)
  - PlatformPreferences (done in Phase 3)
  - PlatformUtils (done in Phase 3)
- **Phase 6**: CMP Touch UI — PARTIALLY DONE
  - SharedModule.kt updated with all Koin modules (AppConfig, networkModule, storageModule, stremioModule, tmdbModule, traktModule, repositoryModule, viewModelModule)
  - androidApp/ module created (build.gradle.kts, AndroidManifest.xml, MuvioApplication.kt, MainActivity.kt, themes.xml, strings.xml)
  - shared UI files created: App.kt, AppTheme.kt, AppNavigation.kt, MediaCard.kt, HomeScreen.kt, SearchScreen.kt, DetailsScreen.kt, PlayerScreen.kt, SettingsScreen.kt

### Build Status
- `./gradlew :shared:compileDebugKotlinAndroid` — ✅ PASSING
- `./gradlew :shared:compileKotlinIosSimulatorArm64` — ✅ PASSING
- `./gradlew :androidApp:assembleDebug` — ✅ PASSING

### Next Steps — Phase 7: iOS app shell
1. Create `iosApp/` Xcode project (or use xcodegen/swift package)
2. Add MPVPlayerBridge.swift + MetalLayer.swift (port from NuvioMobile)
3. Wire `PlayerEngine.ios.kt` to the Swift bridge via cinterop
4. Create `iosApp/src/iosMain/swift/` entrypoint calling `App()` composable
5. Configure AltStore / TestFlight sideload packaging

### Key File Locations
```
muvio-kmp/
├── shared/src/commonMain/kotlin/com/muvio/shared/
│   ├── di/SharedModule.kt          — Koin modules (AppConfig + 7 modules)
│   ├── domain/                     — MediaModels, IptvModels, CatalogModels, ProfileModels, AppException, AppResult
│   ├── network/                    — TmdbClient, TraktClient, StremioClient, HttpClientFactory (expect)
│   ├── stremio/                    — StremioModels (wire protocol types)
│   ├── storage/                    — PlatformPreferences (expect)
│   ├── util/                       — PlatformUtils (expect: currentTimeMillis, randomUUID)
│   ├── repository/                 — MediaRepository, WatchHistoryRepository, AddonRepository
│   ├── viewmodel/                  — HomeViewModel, DetailsViewModel, SearchViewModel, PlayerViewModel
│   ├── player/                     — PlayerEngine (expect), PlayerEngineListener
│   └── ui/
│       ├── App.kt                  — Root Composable
│       ├── theme/AppTheme.kt       — Dark/light M3 colors
│       ├── navigation/AppNavigation.kt — Screen sealed class + NavHost
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
│   ├── util/PlatformUtils.ios.kt             — NSDate (needs fix)
│   └── player/PlayerEngine.ios.kt            — MPV stub
└── androidApp/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── kotlin/com/muvio/app/MuvioApplication.kt  — Koin init
        ├── kotlin/com/muvio/app/MainActivity.kt
        └── res/values/{strings,themes}.xml

### Architecture Decisions Made
- AppConfig data class holds tmdbApiKey, traktClientId, defaultProfileId — passed to Koin at app startup
- TMDB/Trakt keys read from BuildConfig (androidApp) — keys in local.properties or CI env vars
- PlaylistGroupKey: changed from `value class` to `data class` (KMP Native value class limitation)
- PlayerEngine: `var` in expect (not `val`) — Android needs @Volatile var, iOS reads freely
- MuvioAndroidContext.applicationContext: must be initialized before startKoin in Application.onCreate

### Dependency Notes
- koin-compose-viewmodel: added to shared/build.gradle.kts commonMain (needed for koinViewModel())
- compose.materialIconsExtended: added to shared/build.gradle.kts commonMain (needed for Icons.*)
- `parameter` import: added to TraktClient.kt (io.ktor.client.request.parameter)
```
