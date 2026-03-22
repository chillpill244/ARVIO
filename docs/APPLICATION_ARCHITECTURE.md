# ARVIO - Application Architecture

**Version:** 2.0.0  
**Last Updated:** March 15, 2026  
**Platform:** Android TV (Min SDK 28, Target SDK 34)

---

## Table of Contents

1. [Overview](#overview)
2. [Technology Stack](#technology-stack)
3. [Architecture Pattern](#architecture-pattern)
4. [Module Structure](#module-structure)
5. [Core Components](#core-components)
6. [Data Flow](#data-flow)
7. [Navigation](#navigation)
8. [Feature Modules](#feature-modules)
9. [External Integrations](#external-integrations)
10. [Background Processing](#background-processing)
11. [Storage & Caching](#storage--caching)
12. [Security](#security)
13. [Build Variants](#build-variants)
14. [Performance Optimizations](#performance-optimizations)

---

## Overview

**ARVIO** is a media hub application for Android TV that aggregates content from multiple streaming sources. It provides a unified interface for browsing, discovering, and playing media from:

- **Stremio addons** (torrent/streaming sources)
- **IPTV providers** (M3U playlists, Xtream Codes API)
- **TMDB** (metadata discovery)
- **Trakt.tv** (watch history, watchlists, catalogs)
- **Supabase** (cloud sync, authentication)

### Key Features

✨ **Live TV (IPTV)** - M3U playlist support with EPG, group navigation, mini-player  
✨ **Stremio Integration** - Connect custom addons for torrent/streaming sources  
✨ **ARVIO Cloud** - Optional cloud sync with QR sign-in  
✨ **Multi-profile** - Multiple user profiles per account  
✨ **Catalog Management** - Built-in + custom Trakt/MDBList catalogs  
✨ **Continue Watching** - Resume from last position with saved stream data  
✨ **Auto-play** - Next episode auto-play with countdown  
✨ **Watchlist** - Save items to watch later  
✨ **Subtitle & Audio** - Multiple tracks with language selection  
✨ **D-pad Navigation** - Fully optimized for TV remote control  

---

## Technology Stack

### Core Technologies

| Category | Technology | Version | Purpose |
|----------|-----------|---------|---------|
| **Language** | Kotlin | 1.9.20 | Primary language |
| **UI Framework** | Jetpack Compose | 1.5.5 | Declarative UI |
| **TV Components** | AndroidX TV | 1.0.0 | D-pad navigation, focus handling |
| **DI** | Hilt (Dagger) | 2.48 | Dependency injection |
| **Networking** | Retrofit + OkHttp | 2.9.0 / 4.12.0 | HTTP client, API integration |
| **Image Loading** | Coil | 2.5.0 | Image caching & display |
| **Video Player** | Media3 (ExoPlayer) | 1.3.1 | Video playback |
| **Coroutines** | Kotlin Coroutines | 1.7.3 | Async/concurrency |
| **Serialization** | Kotlinx Serialization | 1.6.0 | JSON parsing |
| **Database** | DataStore | 1.0.0 | Key-value storage |
| **Background Tasks** | WorkManager | 2.9.0 | Background sync |

### Player Capabilities

**Powered by ExoPlayer with FFmpeg extension:**

- **Video Codecs:** H.264, H.265/HEVC, VP9, AV1, Dolby Vision
- **Audio Codecs:** AAC, AC3, EAC3, DTS, DTS-HD, TrueHD, Dolby Atmos
- **Containers:** MKV, MP4, WebM, HLS, DASH
- **Quality:** Up to 4K HDR
- **Subtitles:** SRT, VTT, SSA/ASS (embedded & external)

---

## Architecture Pattern

### MVVM + Clean Architecture

ARVIO follows **MVVM (Model-View-ViewModel)** with Clean Architecture principles:

```
┌─────────────────────────────────────────────────────────┐
│                      PRESENTATION                        │
│  ┌──────────────┐         ┌──────────────────────────┐ │
│  │   Screens    │ ◄─────► │   ViewModels (Hilt)     │ │
│  │  (Compose)   │         │   - StateFlow           │ │
│  └──────────────┘         │   - UiState             │ │
│                            └──────────────────────────┘ │
└─────────────────────────────────────┬───────────────────┘
                                      │
┌─────────────────────────────────────▼───────────────────┐
│                      DOMAIN/DATA                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │   Repositories (Singleton, Hilt)                │   │
│  │   - Business logic                              │   │
│  │   - Data aggregation                            │   │
│  │   - Caching strategies                          │   │
│  │   - Error handling (Result<T>)                  │   │
│  └──────────────────────────────────────────────────┘   │
│         │              │              │              │   │
│         ▼              ▼              ▼              ▼   │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐│
│  │   APIs   │  │ DataStore│  │  Models  │  │ProfileMgr││
│  │(Retrofit)│  │(Encrypted)│  │(Immutable)│  │         ││
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘│
└─────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────┐
│                   EXTERNAL SERVICES                      │
│   Stremio │ IPTV │ TMDB │ Trakt │ Supabase │ OpenSubs   │
└─────────────────────────────────────────────────────────┘
```

### Key Principles

1. **Unidirectional Data Flow** - Data flows from repositories → ViewModels → UI
2. **Reactive UI** - StateFlow emissions trigger Compose recomposition
3. **Single Source of Truth** - ViewModels hold UI state, repositories manage data
4. **Dependency Injection** - Hilt provides dependencies at compile-time
5. **Graceful Degradation** - Features degrade gracefully when services unavailable
6. **Result Pattern** - All repository operations return `Result<T>` (never throw exceptions)

---

## Module Structure

```
app/src/main/kotlin/com/arflix/tv/
├── ArflixApplication.kt       # Application entry point (Hilt)
├── MainActivity.kt             # Single activity host
│
├── data/                       # Data layer
│   ├── api/                    # Retrofit API interfaces
│   │   ├── StreamApi.kt        # Stremio addon API
│   │   ├── TmdbApi.kt          # TMDB metadata API
│   │   ├── TraktApi.kt         # Trakt.tv API
│   │   ├── SupabaseApi.kt      # Supabase cloud API
│   │   ├── SkipIntroApi.kt     # Skip intro providers (IntroDB, AniSkip, ARM)
│   │   └── OpenSubtitlesApi.kt # Subtitle API
│   │
│   ├── model/                  # Data models (@Immutable)
│   │   ├── MediaItem.kt        # Core media representation
│   │   ├── StreamSource.kt     # Stream provider data
│   │   ├── Addon.kt            # Stremio addon configuration
│   │   ├── IptvConfig.kt       # IPTV provider configuration
│   │   └── Profile.kt          # User profile data
│   │
│   └── repository/             # Business logic (@Singleton)
│       ├── StreamRepository.kt     # Stremio addon management (~1662 lines)
│       ├── IptvRepository.kt       # IPTV integration (~4700 lines)
│       ├── MediaRepository.kt      # TMDB metadata
│       ├── TraktRepository.kt      # Trakt sync & catalogs
│       ├── SupabaseRepository.kt   # Cloud sync
│       └── WatchlistRepository.kt  # Local watchlist
│
├── di/                         # Dependency Injection
│   ├── AppModule.kt            # App-wide dependencies (APIs, OkHttp, etc.)
│   └── WorkerModule.kt         # Worker dependencies
│
├── navigation/                 # Navigation graph
│   └── AppNavigation.kt        # Compose Navigation routes
│
├── network/                    # Networking utilities
│   ├── AuthInterceptor.kt      # Auth token injection
│   ├── CacheInterceptor.kt     # HTTP caching strategy
│   └── ProfileInterceptor.kt   # Profile isolation
│
├── ui/                         # Presentation layer
│   ├── components/             # Reusable Compose components
│   │   ├── NavigateLayout.kt   # Universal sidebar navigation
│   │   ├── MediaCategoryRail.kt# Category rail with favorites
│   │   ├── MediaCard.kt        # Media item card
│   │   └── FrostedGlassBackground.kt # Glass-morphism effect
│   │
│   ├── screens/                # Feature screens
│   │   ├── home/               # Home screen + ViewModel
│   │   ├── movies/             # Movies browser + ViewModel
│   │   ├── series/             # Series browser + ViewModel
│   │   ├── details/            # Media details + ViewModel
│   │   ├── player/             # Video player + ViewModel
│   │   ├── tv/                 # Live TV (IPTV) + ViewModel
│   │   ├── search/             # Search + ViewModel
│   │   ├── watchlist/          # Watchlist + ViewModel
│   │   ├── settings/           # Settings + ViewModel
│   │   ├── profile/            # Profile selection + ViewModel
│   │   └── login/              # Login/QR auth + ViewModel
│   │
│   ├── theme/                  # Material 3 theme
│   │   ├── Color.kt            # Color palette
│   │   ├── Theme.kt            # App theme provider
│   │   └── Type.kt             # Typography
│   │
│   └── startup/                # App startup logic
│       └── StartupViewModel.kt # Initialization flow
│
├── util/                       # Utilities & extensions
│   ├── DataStores.kt           # DataStore instances
│   ├── ProfileManager.kt       # Multi-profile management
│   ├── AnimeMapper.kt          # Anime metadata mapper
│   └── Extensions.kt           # Kotlin extensions
│
└── worker/                     # Background tasks
    ├── TraktSyncWorker.kt      # Periodic Trakt sync
    └── IptvRefreshWorker.kt    # IPTV playlist refresh
```

---

## Core Components

### Screens & ViewModels

| Screen | ViewModel | Purpose |
|--------|-----------|---------|
| **HomeScreen** | HomeViewModel | Dashboard with continue watching, featured content |
| **MoviesScreen** | MoviesViewModel | Browse movies by category with favorites |
| **SeriesScreen** | SeriesViewModel | Browse TV shows by category with favorites |
| **DetailsScreen** | DetailsViewModel | Media details, seasons/episodes, stream sources |
| **PlayerScreen** | PlayerViewModel | Video playback with controls, subtitles, audio tracks |
| **TvScreen** | TvViewModel | Live TV (IPTV) with EPG, channel groups, mini-player |
| **SearchScreen** | SearchViewModel | Search across TMDB catalog |
| **WatchlistScreen** | WatchlistViewModel | User's saved items |
| **SettingsScreen** | SettingsViewModel | App configuration, addons, IPTV, profiles |
| **ProfileSelectionScreen** | ProfileViewModel | Profile switching |
| **LoginScreen** | LoginViewModel | Supabase QR authentication |

**Common ViewModel Pattern:**
```kotlin
@HiltViewModel
class ExampleViewModel @Inject constructor(
    private val repository: ExampleRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    // UI State
    data class UiState(
        val isLoading: Boolean = false,
        val data: List<Item> = emptyList(),
        val error: String? = null
    )
    
    // StateFlow for reactive UI
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    // Actions
    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = repository.getData()) {
                is Result.Success -> _uiState.update { 
                    it.copy(isLoading = false, data = result.data) 
                }
                is Result.Error -> _uiState.update { 
                    it.copy(isLoading = false, error = result.exception.message) 
                }
            }
        }
    }
}
```

### Repositories

| Repository | Lines | Purpose |
|------------|-------|---------|
| **StreamRepository** | ~1662 | Stremio addon management, stream resolution, subtitle fetching |
| **IptvRepository** | ~4700 | IPTV integration, M3U parsing, EPG processing, VOD catalog |
| **MediaRepository** | ~800 | TMDB metadata, search, discover, recommendations |
| **TraktRepository** | ~1200 | Watch history, watchlist, catalogs, ratings |
| **SupabaseRepository** | ~600 | Cloud sync, profile backup, settings sync |
| **WatchlistRepository** | ~400 | Local watchlist management |

**Common Repository Pattern:**
```kotlin
@Singleton
class ExampleRepository @Inject constructor(
    private val api: ExampleApi,
    private val profileManager: ProfileManager
) {
    // In-memory cache
    private val cache = mutableMapOf<String, CacheEntry<Data>>()
    
    // Suspend function returning Result
    suspend fun getData(id: String): Result<Data> {
        return try {
            // Check cache first
            cache[id]?.let { entry ->
                if (!entry.isExpired()) {
                    return Result.Success(entry.data)
                }
            }
            
            // Fetch from API
            val data = api.getData(id)
            cache[id] = CacheEntry(data, System.currentTimeMillis())
            Result.Success(data)
        } catch (e: Exception) {
            Result.Error(e.toAppException())
        }
    }
}
```

### Dependency Injection Modules

**AppModule.kt** - Provides:
- Retrofit API clients (TMDB, Trakt, Supabase, Stremio, etc.)
- OkHttpClient with interceptors (auth, cache, profile)
- ProfileManager
- Context dependencies

**WorkerModule.kt** - Provides:
- Worker dependencies (repositories for background tasks)

---

## Data Flow

### Example: User Plays a Movie

```
1. USER ACTION
   DetailsScreen: User clicks "Play" button
   │
   ▼
2. VIEWMODEL
   DetailsViewModel.onPlayClicked(mediaId, mediaType)
   - Checks for saved stream (continue watching)
   - If no saved stream, resolves streams from sources
   │
   ▼
3. REPOSITORIES (Parallel Execution)
   StreamRepository.resolveMovieStreams(imdbId)
   ├─► Query all enabled Stremio addons (20s timeout each)
   │   └─► Returns List<StreamSource>
   │
   IptvRepository.findMovieVodSource(name, year, tmdbId)
   ├─► Search VOD catalog (cached on disk)
   │   └─► Returns StreamSource? (IPTV stream)
   │
   MediaRepository.getOpenSubtitlesHash(streamUrl)
   └─► Calculate file hash for subtitle matching
       └─► Returns String? (hash)
   │
   ▼
4. VIEWMODEL (Aggregation)
   DetailsViewModel merges results:
   - Deduplicate streams
   - Sort by quality (4K > 1080p > 720p > 480p)
   - Add IPTV stream if found
   - Update uiState with available sources
   │
   ▼
5. UI UPDATE (Compose Recomposition)
   DetailsScreen observes uiState.collectAsState()
   - Shows source picker dialog with sorted streams
   │
   ▼
6. USER SELECTION
   User selects preferred stream
   │
   ▼
7. NAVIGATION
   navController.navigate(
       Screen.Player.createRoute(
           mediaType, mediaId, 
           streamUrl, addonId, sourceName
       )
   )
   │
   ▼
8. PLAYER
   PlayerScreen initializes
   PlayerViewModel.initPlayer(streamUrl)
   - Creates ExoPlayer instance
   - Loads video
   - Fetches subtitles
   - Records watch history (TraktRepository)
   │
   ▼
9. PLAYBACK
   ExoPlayer plays video
   PlayerViewModel tracks:
   - Current position (for continue watching)
   - Completion percentage (for Trakt scrobble)
   - Next episode data (for auto-play)
```

### Data Synchronization Flow

```
APP START
│
├─► ProfileManager loads active profile
│   └─► Reads from DataStore (encrypted)
│
├─► TraktRepository syncs watch history
│   └─► GET /sync/history (last 30 days)
│       └─► Updates local continue watching
│
├─► SupabaseRepository syncs cloud data (if enabled)
│   ├─► GET /profiles → Merge with local profiles
│   ├─► GET /addons → Merge with local addons
│   └─► GET /settings → Merge with local settings
│
└─► IptvRepository loads snapshot
    ├─► Reads M3U from disk cache (if exists)
    ├─► Reads EPG from disk cache (if exists)
    └─► Starts IptvRefreshWorker (periodic sync)

BACKGROUND SYNC (WorkManager)
│
├─► TraktSyncWorker (every 6 hours)
│   └─► Syncs watch history, watchlist, ratings
│
└─► IptvRefreshWorker (every 24 hours)
    └─► Re-downloads M3U playlist and EPG
```

---

## Navigation

### Navigation Graph

ARVIO uses **Jetpack Compose Navigation** with sealed classes for type-safe routes:

```kotlin
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object Search : Screen("search")
    object Movies : Screen("movies")
    object Series : Screen("series")
    object Watchlist : Screen("watchlist")
    object Settings : Screen("settings")
    object ProfileSelection : Screen("profile_selection")
    
    object Tv : Screen("tv?channelId={channelId}&streamUrl={streamUrl}") {
        fun createRoute(channelId: String? = null, streamUrl: String? = null): String
    }
    
    object Details : Screen("details/{mediaType}/{mediaId}?initialSeason={initialSeason}&initialEpisode={initialEpisode}") {
        fun createRoute(mediaType: MediaType, mediaId: Int, ...): String
    }
    
    object Player : Screen("player/{mediaType}/{mediaId}?seasonNumber={seasonNumber}&...") {
        fun createRoute(mediaType: MediaType, mediaId: Int, ...): String
    }
}
```

### Navigation Flow

```
LoginScreen
   │
   ▼ (Auth Success)
ProfileSelectionScreen
   │
   ▼ (Profile Selected)
HomeScreen ◄──────────────┐
   │                      │
   ├─► MoviesScreen       │
   ├─► SeriesScreen       │
   ├─► SearchScreen       │
   ├─► TvScreen           │
   ├─► WatchlistScreen    │
   └─► SettingsScreen     │
       │                  │
       ├─► ProfileSelectionScreen
       └─► (Back) ─────────┘

MovieScreen / SeriesScreen / SearchScreen / WatchlistScreen
   │
   ▼ (Item Click)
DetailsScreen
   │
   ├─► (Play) → PlayerScreen
   │              │
   │              └─► (Auto-play next) → PlayerScreen (new episode)
   │
   └─► (Back) → Previous Screen
```

### D-pad Navigation Pattern

ARVIO implements custom focus management for TV navigation:

```
NavigateLayout Component (Universal Sidebar)
├── LEFT/RIGHT: Switch between sidebar ↔ content
├── UP/DOWN: Navigate within current zone
└── ENTER: Select item / Change page

Screen Content (Movies, Series, etc.)
├── UP/DOWN: Navigate categories / items
├── LEFT/RIGHT: Switch focus zones
└── ENTER: Select item
```

**Key Implementation:**
- `NavigateLayout.kt` - Universal sidebar navigation component
- `MoviesFocusZone`, `SeriesFocusZone` - Screen-specific focus zones
- `LocalFocusManager` - Compose focus management API

---

## Feature Modules

### 1. Live TV (IPTV)

**Components:**
- `TvScreen` - Main IPTV interface
- `TvViewModel` - Channel management, EPG data, playback control
- `IptvRepository` - M3U parsing, Xtream API, EPG processing

**Features:**
- M3U playlist support (local/remote)
- Xtream Codes API integration
- EPG (Electronic Program Guide) with XMLTV parsing
- Channel groups with filtering
- Mini-player with picture-in-picture style UI
- Direct stream playback (no source selection)

**Data Flow:**
```
Settings: User adds IPTV provider
   ↓
IptvRepository.saveConfig() → Encrypted DataStore
   ↓
IptvRepository.loadSnapshot() → Downloads M3U + EPG
   ↓
Parse M3U → Extract channels, groups, logos
Parse EPG → Match programs to channels (tvg-id)
   ↓
In-memory cache + Disk cache (for offline)
   ↓
TvScreen displays channels with EPG data
   ↓
User selects channel → Direct ExoPlayer playback
```

### 2. Stremio Integration

**Components:**
- `SettingsScreen` - Addon management UI
- `StreamRepository` - Addon queries, stream resolution
- `DetailsScreen` - Source picker

**Features:**
- Add custom Stremio addons (manifest URL)
- Enable/disable addons
- Parallel stream resolution (all addons queried simultaneously)
- Quality sorting (4K > 1080p > 720p > 480p)
- Debrid service support (Real-Debrid, AllDebrid, etc.)
- Subtitle integration with OpenSubtitles

**Data Flow:**
```
Settings: User adds Stremio addon
   ↓
StreamRepository.addCustomAddon(manifestUrl)
   ↓
Fetch manifest.json → Parse addon metadata
   ↓
Store in encrypted DataStore (profile-scoped)
   ↓
Details: User clicks "Play"
   ↓
StreamRepository.resolveMovieStreams(imdbId)
   ↓
Query all enabled addons in parallel (timeout: 20s each)
   ↓
Aggregate results → Deduplicate → Sort by quality
   ↓
Show source picker dialog
   ↓
User selects stream → Navigate to PlayerScreen
```

### 3. Continue Watching

**Components:**
- `HomeScreen` - Continue watching rail
- `TraktRepository` - Watch history storage
- `DetailsViewModel` - Saved stream restoration

**Features:**
- Resume from last position
- Direct playback with saved stream (no source picker)
- Trakt sync for cross-device resume
- Episode progress tracking
- Automatic completion detection (90%+ watched)

**Data Flow:**
```
PlayerViewModel: User watches video
   ↓
Every 10s: Save progress to local DataStore
   ↓
On pause/exit: TraktRepository.recordWatchHistory()
   ↓
Saves: tmdbId, season, episode, position, streamUrl, addonId
   ↓
HomeScreen loads: TraktRepository.getContinueWatching()
   ↓
User clicks continue watching item
   ↓
DetailsViewModel.fetchSavedStreamInfo()
   ↓
Play button uses saved stream (no source picker)
   ↓
ExoPlayer seeks to saved position
```

### 4. Multi-Profile

**Components:**
- `ProfileSelectionScreen` - Profile picker
- `ProfileManager` - Active profile management
- `ProfileInterceptor` - HTTP header injection

**Features:**
- Multiple profiles per user account
- Profile-scoped data isolation (addons, IPTV, catalogs, settings)
- Profile switching without re-authentication
- Cloud sync support (optional)

**Implementation:**
```
ProfileManager holds active profile ID
   ↓
All repositories use profileManager.activeProfileId
   ↓
DataStore keys prefixed with profileId
   ├─► addons_profile1
   ├─► iptv_config_profile1
   └─► settings_profile1
   ↓
ProfileInterceptor adds header: X-Profile-Id: {id}
   ↓
All HTTP requests include profile context
```

### 5. Catalog Management

**Components:**
- `HomeScreen` - Catalog rows
- `TraktRepository` - Built-in + custom catalogs
- `SettingsScreen` - Catalog ordering/management

**Features:**
- Built-in Trakt catalogs (Trending, Popular, Anticipated)
- Custom Trakt lists (by list ID)
- MDBList integration (custom movie/TV lists)
- Drag-to-reorder catalogs
- Show/hide individual catalogs

**Catalog Types:**
- **Trending** - Trending on Trakt (last 7 days)
- **Popular** - Most watched (last 30 days)
- **Recommended** - Personalized for user (requires Trakt account)
- **Watchlist** - User's Trakt watchlist
- **Custom List** - Any public Trakt/MDBList URL

### 6. ARVIO Cloud (Optional)

**Components:**
- `LoginScreen` - QR auth flow
- `SupabaseRepository` - Cloud sync
- `SettingsScreen` - Cloud settings

**Features:**
- QR code authentication (sign in from browser)
- Profile backup & restore
- Addon sync across devices
- IPTV config sync
- Settings sync
- Watch history sync (via Trakt)

**Authentication Flow:**
```
LoginScreen generates auth session
   ↓
Display QR code + 6-digit PIN
   ↓
User scans QR → Opens browser → Supabase Auth UI
   ↓
User signs in (email/OAuth)
   ↓
Supabase Edge Function: tv-auth-approve
   ↓
App polls: tv-auth-status (every 2s)
   ↓
On success: Store access token + refresh token
   ↓
Navigate to ProfileSelectionScreen
```

---

## External Integrations

### 1. Stremio Addons

**Purpose:** Torrent/streaming source aggregation

**API Pattern:**
```
GET {addonUrl}/manifest.json
GET {addonUrl}/stream/{type}/{id}.json
```

**Example Response:**
```json
{
  "streams": [
    {
      "title": "1080p BluRay (2.5 GB)",
      "url": "https://example.com/stream.mp4",
      "infoHash": "abc123...",
      "quality": "1080p"
    }
  ]
}
```

**Timeout:** 20s per addon  
**Caching:** In-memory, 5-minute TTL  
**Error Handling:** Graceful degradation (skip failing addons)

### 2. IPTV Providers

**Supported Formats:**
- **M3U Playlist** (local file or HTTP URL)
- **Xtream Codes API** (username/password authentication)

**M3U Example:**
```m3u
#EXTM3U
#EXTINF:-1 tvg-id="channel1" tvg-logo="logo.png" group-title="Sports",ESPN
http://example.com/stream.m3u8
```

**Xtream API Endpoints:**
```
GET {url}/player_api.php?username={u}&password={p}&action=get_live_categories
GET {url}/player_api.php?username={u}&password={p}&action=get_live_streams
GET {url}/player_api.php?username={u}&password={p}&action=get_vod_streams
GET {url}/player_api.php?username={u}&password={p}&action=get_series
GET {url}/xmltv.php?username={u}&password={p}
```

**Caching:** Disk cache (24-hour refresh)

### 3. TMDB (The Movie Database)

**Purpose:** Metadata discovery (titles, posters, ratings, etc.)

**Key Endpoints:**
```
GET /movie/{id}
GET /tv/{id}
GET /tv/{id}/season/{season_number}
GET /search/multi?query={q}
GET /trending/{media_type}/{time_window}
GET /discover/movie
GET /discover/tv
```

**Caching:** In-memory with 1-hour TTL  
**Rate Limiting:** 40 requests per 10 seconds  
**Error Handling:** Fallback to cached data

### 4. Trakt.tv

**Purpose:** Watch history, watchlist, catalogs, ratings

**Authentication:** OAuth 2.0 (device flow)

**Key Endpoints:**
```
GET /sync/history
GET /sync/watchlist
GET /sync/collection
POST /sync/history (scrobble)
POST /sync/watchlist (add/remove)
GET /users/{user}/lists/{list}/items
GET /movies/trending
GET /shows/trending
```

**Caching:** Background sync (6-hour interval via WorkManager)  
**Error Handling:** Queue failed requests for retry

### 5. Supabase

**Purpose:** Cloud sync, authentication

**Services Used:**
- **Auth** - User authentication (email, OAuth)
- **Database** - Profile storage, settings backup
- **Edge Functions** - TV auth flow helpers

**Edge Functions:**
```
POST /functions/v1/tv-auth-start       → Create auth session
POST /functions/v1/tv-auth-approve     → Approve from browser
GET  /functions/v1/tv-auth-status      → Poll auth status
POST /functions/v1/tv-auth-complete    → Exchange for tokens
```

**Database Tables:**
- `profiles` - User profile metadata
- `user_settings` - App settings backup
- `auth_audit_log` - Auth event logging

### 6. OpenSubtitles

**Purpose:** Subtitle fetching

**Authentication:** API key (optional, increases rate limit)

**Search Methods:**
1. **By file hash** - Most accurate (requires video file download start)
2. **By IMDb ID** - Fallback for undownloaded files
3. **By query** - Least accurate (title + year search)

**Caching:** In-memory cache for subtitle URLs  
**Rate Limiting:** 40 requests per 10 seconds (with API key)

### 7. Skip Intro Providers

**Purpose:** Automatic intro/outro skipping

**Providers:**
- **IntroDB** - Community-sourced skip data
- **AniSkip** - Anime-specific skip timestamps
- **ARM (Anime Relations Mapper)** - MAL ID resolution for anime

**API Pattern:**
```
GET {introDbUrl}/skip/{tmdbId}/{season}/{episode}
GET {aniSkipUrl}/skip/{malId}/{episode}
GET {armUrl}/map/{anilistId}
```

**Caching:** In-memory, episode-scoped  
**Error Handling:** Skip silently if providers fail

---

## Background Processing

### WorkManager Tasks

| Worker | Purpose | Frequency | Constraints |
|--------|---------|-----------|-------------|
| **TraktSyncWorker** | Sync watch history, watchlist, ratings | Every 6 hours | Network required |
| **IptvRefreshWorker** | Refresh M3U playlist and EPG | Every 24 hours | Network + unmetered |

**Implementation:**
```kotlin
@HiltWorker
class TraktSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val traktRepository: TraktRepository
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            traktRepository.syncAll()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
```

**Scheduling:**
```kotlin
val constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

val syncWork = PeriodicWorkRequestBuilder<TraktSyncWorker>(6, TimeUnit.HOURS)
    .setConstraints(constraints)
    .build()

WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "trakt_sync",
    ExistingPeriodicWorkPolicy.KEEP,
    syncWork
)
```

---

## Storage & Caching

### Storage Layers

| Layer | Technology | Purpose | TTL |
|-------|-----------|---------|-----|
| **Memory Cache** | `MutableMap<K, CacheEntry<V>>` | Hot data (streams, metadata) | 5-60 min |
| **Disk Cache** | OkHttp DiskLruCache | HTTP responses | 7 days |
| **DataStore** | Encrypted Preferences | User settings, profiles, configs | Persistent |
| **StreamRepository Cache** | In-memory | Addon streams | 5 min |
| **IptvRepository Cache** | Disk (JSON) | VOD catalogs, series index | 24 hours |
| **MediaRepository Cache** | In-memory | TMDB metadata | 1 hour |

### Caching Strategy

**1. Hot Path (In-Memory)**
- Stream resolution results (5-minute TTL)
- TMDB metadata (1-hour TTL)
- Active channel EPG data (30-minute TTL)

**2. Warm Path (Disk Cache)**
- IPTV VOD catalogs (24-hour TTL)
- Series episode index (24-hour TTL)
- HTTP responses (7-day TTL)

**3. Cold Path (DataStore)**
- User profiles (persistent)
- Addon configurations (persistent)
- IPTV provider configs (persistent)
- App settings (persistent)

### Cache Invalidation

**Automatic:**
- TTL expiration
- Profile switch (clears profile-scoped caches)
- App version change (clears all caches)

**Manual:**
- Settings: "Clear cache" button
- Addon toggle (clears stream cache)
- IPTV config change (clears playlist cache)

### Encryption

**DataStore Encryption:**
- Uses Android Keystore (AES-256-GCM)
- Profile-scoped encryption keys
- Automatic key rotation on profile deletion

**Encrypted Data:**
- Trakt OAuth tokens
- Supabase access/refresh tokens
- IPTV provider credentials
- User profile data

---

## Security

### Authentication

**Supabase OAuth:**
- TV auth flow with QR code + PIN
- Access token (JWT, 1-hour expiry)
- Refresh token (persistent, 30-day expiry)
- Automatic token refresh

**Trakt OAuth:**
- Device flow (enter code on website)
- Access token (persistent, no expiry)
- Client ID + Client Secret (stored in BuildConfig)

### Data Protection

**Encrypted Storage:**
- All sensitive data encrypted via Android Keystore
- Profile-scoped encryption keys
- Automatic key rotation

**Network Security:**
- HTTPS enforced for all API calls
- Certificate pinning (optional, disabled by default)
- TLS 1.2+ required

**Content Security:**
- No DRM support (user-provided streams only)
- No credential storage for Debrid services (user enters in addon manifest)

### Privacy

- **No analytics** by default (opt-in only)
- **No crash reporting** in debug builds
- **No server-side logging** of stream URLs
- **Local-first architecture** (cloud sync is optional)

---

## Build Variants

### debug

**Purpose:** Development builds

- **APK Size:** ~93 MB
- **Minification:** Disabled
- **Obfuscation:** Disabled
- **Debugging:** Enabled
- **Crash Reporting:** Disabled
- **Analytics:** Disabled
- **Signing:** Debug keystore

### beta

**Purpose:** Public beta testing

- **APK Size:** ~46 MB
- **Minification:** Enabled (R8)
- **Obfuscation:** Disabled (for easier debugging)
- **Debugging:** Disabled
- **Crash Reporting:** Enabled
- **Analytics:** Enabled
- **Signing:** Debug keystore
- **Performance:** 5-8x faster than debug

### staging

**Purpose:** Pre-release testing

- **APK Size:** ~46 MB
- **Minification:** Disabled
- **Obfuscation:** Disabled
- **Debugging:** Disabled
- **Crash Reporting:** Enabled
- **Analytics:** Disabled
- **Signing:** Debug keystore

### release

**Purpose:** Production builds

- **APK Size:** ~93 MB (minification disabled for stability)
- **Minification:** Disabled
- **Obfuscation:** Disabled
- **Debugging:** Disabled
- **Crash Reporting:** Enabled
- **Analytics:** Enabled
- **Signing:** Release keystore (if configured)

**Note:** R8 minification disabled in release builds to avoid runtime regressions and maintain stability.

---

## Performance Optimizations

### 1. Parallel Execution

**Stream Resolution:**
```kotlin
val streams = coroutineScope {
    listOf(
        async { streamRepository.resolveMovieStreams(imdbId) },
        async { iptvRepository.findMovieVodSource(title, year, tmdbId) }
    ).awaitAll().flatten()
}
```

**Benefits:**
- Reduces total wait time from 40s to ~20s
- All addons queried simultaneously
- IPTV search runs in parallel

### 2. Caching Strategies

**Multi-Tier Caching:**
- **L1 (Memory):** Hot data, 5-60 min TTL
- **L2 (Disk):** Warm data, 24-hour TTL
- **L3 (DataStore):** Cold data, persistent

**Cache Hit Rates (Target):**
- StreamRepository: >70%
- IptvRepository VOD: >90%
- MediaRepository: >80%

### 3. Lazy Loading

**Compose LazyColumn/LazyRow:**
- Only visible items rendered
- Automatic recycling/reuse
- Prefetch 5 items ahead

**Image Loading (Coil):**
```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(posterUrl)
        .crossfade(true)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .build(),
    contentDescription = null
)
```

### 4. Focus Management

**TV Navigation Optimizations:**
- Custom focus order (avoid unnecessary focus jumps)
- Focus restoration on screen return
- Prefetch data on focus gain (anticipatory loading)

### 5. Database Optimizations

**DataStore:**
- Batch writes (reduce I/O)
- Profile-scoped keys (avoid read-all operations)
- Async reads/writes (never block main thread)

### 6. Network Optimizations

**OkHttp:**
- Connection pooling (5 connections max)
- HTTP/2 support (multiplexing)
- Gzip compression
- Disk cache (100 MB max)

**Timeouts:**
- Connect: 15s
- Read: 30s
- Write: 30s
- Addon stream query: 20s

### 7. Memory Management

**Lifecycle-Aware Caching:**
- Clear memory caches on low memory
- Cancel pending jobs in onStop()
- Dispose ExoPlayer instances promptly

**ViewModelScope:**
- All coroutines tied to ViewModel lifecycle
- Automatic cancellation on ViewModel clear

---

## Testing Strategy

### Unit Tests

**Framework:** JUnit 4, MockK, Turbine, Truth, Robolectric

**Coverage:**
- ViewModels: State management, business logic
- Repositories: API integration, caching, error handling
- Utilities: Extensions, mappers, parsers

**Example:**
```kotlin
class StreamRepositoryTest {
    @Test
    fun `resolveMovieStreams returns cached data when fresh`() = runTest {
        val result = repository.resolveMovieStreams("tt1234567")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.data).hasSize(5)
    }
}
```

### Integration Tests

**Not currently implemented** (future roadmap)

### Manual Testing

**Devices:**
- Xiaomi Mi Box S (Android 9)
- Fire TV Stick 4K (Fire OS 7)
- Google Chromecast with Google TV (Android 12)
- Emulator (Android TV 34)

**Test Scenarios:**
- D-pad navigation across all screens
- Stream playback (various qualities, formats)
- IPTV channel switching
- Profile switching
- Addon management
- Trakt sync
- Continue watching resume

---

## Future Roadmap

### Planned Features
- [ ] Offline downloads (cache streams locally)
- [ ] Parental controls (PIN-protected profiles)
- [ ] Custom themes (user-selectable color schemes)
- [ ] Advanced filters (genre, year, rating)
- [ ] Smart recommendations (ML-based)
- [ ] Voice search (Android TV Assistant integration)
- [ ] Cast support (Chromecast)
- [ ] External player support (VLC, MX Player)

### Technical Improvements
- [ ] Integration tests with real APIs
- [ ] UI tests with Espresso/Compose Testing
- [ ] Baseline profiles for faster startup
- [ ] ProGuard rules for smaller APK (stable R8 config)
- [ ] Background pre-caching for continue watching
- [ ] GraphQL for Trakt API (reduce over-fetching)

---

## Troubleshooting

### Common Issues

**1. Streams not loading**
- Check addon enabled status
- Clear stream cache (Settings)
- Verify internet connection
- Check addon manifest URL validity

**2. IPTV channels not showing**
- Verify M3U URL accessibility
- Check Xtream API credentials
- Force playlist reload (Settings)
- Review EPG URL (must match tvg-id format)

**3. Continue watching not resuming**
- Ensure Trakt sync enabled
- Check watch history sync status
- Verify saved stream data exists (local DataStore)

**4. Profile switching not working**
- Verify ProfileManager initialization
- Check DataStore encryption keys
- Clear app cache and restart

**5. Build failures**
- Ensure Java 17 set: `export JAVA_HOME=$(brew --prefix openjdk@17)`
- Clean build: `./gradlew clean`
- Check `local.properties` for correct SDK path

---

## Contributing

### Code Style

- Follow Kotlin official style guide
- Use `kotlin.code.style=official` in IntelliJ
- Run Detekt before commit: `./gradlew detekt`
- Run unit tests: `./gradlew test`

### Pull Request Guidelines

1. Fork repository
2. Create feature branch
3. Implement changes with tests
4. Run linters and tests
5. Update documentation
6. Submit PR with description

### Commit Message Format

```
feat: Add support for custom Trakt catalogs
fix: Resolve stream playback freeze on Fire TV
refactor: Simplify cache invalidation logic
docs: Update architecture diagram
test: Add unit tests for IptvRepository
```

---

## License

**ARVIO** is licensed under the MIT License. See [LICENSE](../LICENSE) for details.

---

## Contact

- **GitHub:** https://github.com/ProdigyV21/ARVIO
- **Issues:** https://github.com/ProdigyV21/ARVIO/issues
- **Discussions:** https://github.com/ProdigyV21/ARVIO/discussions

---

**Last Updated:** March 15, 2026  
**Document Version:** 1.0  
**App Version:** 2.0.0
