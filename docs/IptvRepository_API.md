# IptvRepository API Documentation

## Overview
`IptvRepository` is the comprehensive IPTV management system in ARVIO. It handles M3U playlist parsing, EPG (Electronic Program Guide) processing, Xtream Codes API integration, VOD (Video On Demand) catalog management, and series episode resolution.

**Package:** `com.arflix.tv.data.repository`  
**Singleton:** Yes  
**Dependencies:** OkHttpClient, ProfileManager

---

## Key Responsibilities

1. **M3U Playlist Management**: Parse and cache IPTV playlists (standard M3U and Xtream Codes)
2. **EPG Processing**: Parse XMLTV EPG data and provide now/next program information
3. **Xtream Codes Integration**: Full Xtream API support for live TV, VOD, and series
4. **VOD Catalog**: Index and search movies from Xtream providers
5. **Series Resolution**: Advanced series/episode matching with multiple fallback strategies
6. **Profile-Scoped Config**: Independent IPTV config per user profile
7. **Background Refresh**: Automatic playlist/EPG refresh with configurable intervals
8. **Disk Caching**: Persistent disk cache for large catalogs (VOD/Series)

---

## Public API Methods

### Configuration Management

#### `fun observeConfig(): Flow<IptvConfig>`
**Purpose:** Observable flow of IPTV configuration for the current profile  
**Returns:** Flow<IptvConfig> with m3uUrl, epgUrl, xtreamUsername, xtreamPassword  
**Used In:**
- `TvViewModel` - Load TV channel configuration
- `SettingsViewModel` - Display IPTV settings UI
- `HomeViewModel` - Conditional IPTV features

**Security:** Config values are encrypted using Android Keystore (AES-256-GCM)

**Config Format:**
```kotlin
data class IptvConfig(
    val m3uUrl: String = "",
    val epgUrl: String = "",
    val xtreamUsername: String = "",
    val xtreamPassword: String = ""
)
```

---

#### `suspend fun saveConfig(m3uUrl: String, epgUrl: String, xtreamUsername: String = "", xtreamPassword: String = "")`
**Purpose:** Save IPTV configuration with automatic Xtream Codes normalization  
**Parameters:**
- `m3uUrl` - M3U playlist URL or Xtream host
- `epgUrl` - XMLTV EPG URL or Xtream EPG endpoint
- `xtreamUsername` - Xtream username (optional)
- `xtreamPassword` - Xtream password (optional)

**Used In:**
- `SettingsViewModel.saveIptvConfig()` - User configuration update

**Normalization:**
1. **Xtream Triplet Detection**: Recognizes `host user pass` format (space or newline separated)
2. **Protocol Handling**: Supports `xtream://`, `xstream://`, `stremio://` prefixes
3. **Auto-URL Building**: Constructs proper `/get.php` or `/xmltv.php` URLs
4. **Encryption**: Encrypts sensitive data before storing

**Supported Input Formats:**
```
# Full Xtream URLs
https://host:port/get.php?username=U&password=P&type=m3u_plus&output=ts
https://host:port/xmltv.php?username=U&password=P

# Space-separated triplet
host:port user pass

# Line-separated triplet
host:port
user
pass

# Protocol prefixes
xtream://host:port user pass
```

---

#### `suspend fun clearConfig()`
**Purpose:** Clear IPTV configuration and invalidate cache  
**Used In:**
- `SettingsViewModel.clearIptvConfig()` - User-initiated config reset

---

#### `fun observeFavoriteGroups(): Flow<List<String>>`
**Purpose:** Observable flow of favorite channel group names  
**Used In:**
- `TvViewModel` - Filter and display favorite groups
- `SettingsViewModel` - Manage favorites UI

---

#### `fun observeFavoriteChannels(): Flow<List<String>>`
**Purpose:** Observable flow of favorite channel IDs  
**Used In:**
- `TvViewModel` - Filter favorite channels
- `HomeViewModel` - Show favorite channels on home screen

---

#### `suspend fun toggleFavoriteGroup(groupName: String)`
**Purpose:** Add or remove a group from favorites  
**Used In:**
- `TvViewModel.toggleFavoriteGroup()` - User toggles favorite

---

#### `suspend fun toggleFavoriteChannel(channelId: String)`
**Purpose:** Add or remove a channel from favorites  
**Used In:**
- `TvViewModel.toggleFavoriteChannel()` - User toggles favorite

---

### Playlist & EPG Loading

#### `suspend fun loadSnapshot(forcePlaylistReload: Boolean = false, forceEpgReload: Boolean = false, onProgress: (IptvLoadProgress) -> Unit = {}): IptvSnapshot`
**Purpose:** Load complete IPTV snapshot with channels and EPG data  
**Parameters:**
- `forcePlaylistReload` - Force fresh playlist fetch (ignore cache)
- `forceEpgReload` - Force fresh EPG fetch (ignore cache)
- `onProgress` - Progress callback for UI updates

**Returns:** `IptvSnapshot` containing channels, EPG, and metadata  
**Used In:**
- `TvViewModel.loadTvData()` - Initial TV screen load
- `SettingsViewModel.testIptvConfig()` - Validate IPTV configuration
- `IptvRefreshWorker` - Background refresh worker
- `HomeViewModel` - Load TV channels for home screen

**Process Flow:**
```
1. Load config (m3uUrl, epgUrl, credentials)
2. Check cache validity (24-hour TTL)
3. Fetch playlist:
   - Standard M3U: Parse M3U file
   - Xtream: Call /player_api.php?action=get_live_streams
4. Fetch EPG:
   - Standard XMLTV: Parse XML (SAX parser for performance)
   - Xtream: Call /player_api.php?action=get_short_epg (base64-encoded)
5. Match EPG programs to channels
6. Cache result (memory + disk)
7. Return snapshot
```

**Performance:**
- **Memory Cache:** Instant load if fresh (<24h)
- **Disk Cache:** Fast load for large catalogs
- **Network Fetch:** 15-60s depending on provider size
- **EPG Processing:** SAX parser handles 100MB+ EPG files efficiently

**Snapshot Model:**
```kotlin
data class IptvSnapshot(
    val channels: List<IptvChannel>,
    val nowNext: Map<String, IptvNowNext>,
    val loadedAtMs: Long,
    val hasEpg: Boolean
)
```

---

#### `suspend fun warmupFromCacheOnly()`
**Purpose:** Pre-load cache without network call (app startup optimization)  
**Used In:** App startup to prime memory cache

---

#### `suspend fun getCachedSnapshotOrNull(): IptvSnapshot?`
**Purpose:** Get cached snapshot without triggering network fetch  
**Returns:** Cached snapshot or null if no cache available  
**Used In:**
- `TvViewModel` - Fast initial load
- `HomeViewModel` - Quick favorite channel display

---

#### `fun isSnapshotStale(snapshot: IptvSnapshot): Boolean`
**Purpose:** Check if snapshot needs refresh  
**Returns:** `true` if older than 24 hours  

---

#### `fun cachedEpgAgeMs(): Long`
**Purpose:** Get age of cached EPG data in milliseconds  
**Returns:** Age in ms, or `Long.MAX_VALUE` if no cache  

---

### Advanced EPG Operations

#### `suspend fun refreshEpgForChannels(channelIds: Set<String>): Map<String, IptvNowNext>?`
**Purpose:** Force EPG refresh for specific channels (Xtream short EPG)  
**Parameters:**
- `channelIds` - Set of channel IDs to refresh

**Returns:** Updated now/next map for requested channels  
**Used In:**
- `HomeViewModel` - Refresh favorite channel EPG

**Xtream Optimization:** Uses short EPG endpoint for fast updates without full playlist reload

---

#### `fun reDeriveCachedNowNext(channelIds: Set<String>): Map<String, IptvNowNext>?`
**Purpose:** Re-compute now/next from cached EPG without network call  
**Parameters:**
- `channelIds` - Channel IDs to update

**Returns:** Updated now/next map  
**Used In:**
- `HomeViewModel` - Update EPG display without network fetch

**Use Case:** EPG programs expire over time - this recomputes "now" and "next" from cached program list

---

### VOD (Video On Demand) - Movies

#### `suspend fun findMovieVodSource(title: String, year: Int?, imdbId: String?, tmdbId: Int?, allowNetwork: Boolean = true): List<StreamSource>`
**Purpose:** Find Xtream VOD streams for a movie using advanced matching  
**Parameters:**
- `title` - Movie title
- `year` - Release year (improves matching accuracy)
- `imdbId` - IMDb ID (optional)
- `tmdbId` - TMDB ID (optional)
- `allowNetwork` - Allow network fetch if cache unavailable

**Returns:** List of StreamSource objects (Xtream VOD streams)  
**Used In:**
- `StreamRepository.resolveMovieStreams()` - Append IPTV sources to addon streams

**Matching Strategy:**
```
1. Normalize title (remove special chars, year, quality tags)
2. Build search index:
   - Canonical title key (lowercase, alphanumeric only)
   - Title tokens (individual words)
   - TMDB ID
   - Year
3. Search VOD catalog:
   - Exact canonical title match (highest score)
   - Fuzzy title token match
   - TMDB ID match
   - Year match bonus
4. Rank candidates by score
5. Return top matches as StreamSource objects
```

**Quality Inference:** Extracts quality from title (4K, 1080p, 720p, etc.)

**Caching:** VOD catalog cached for 6 hours (disk-persisted for fast restart)

---

#### `suspend fun getVodCategories(): List<XtreamVodCategory>`
**Purpose:** Get all VOD categories from Xtream provider  
**Returns:** List of categories (Movies > Action, Movies > Comedy, etc.)  
**Used In:**
- `MoviesViewModel` - Display category list in Movies screen

**Network/Cache:** Fetches from network if cache older than 6 hours, otherwise returns cached

---

#### `suspend fun getCachedVodCategories(): List<XtreamVodCategory>`
**Purpose:** Get cached VOD categories without network call  
**Returns:** Cached categories or empty list  

---

#### `suspend fun getMoviesByCategory(categoryId: String): List<XtreamVodStream>`
**Purpose:** Get all VOD movies in a specific category  
**Parameters:**
- `categoryId` - Category ID from `getVodCategories()`

**Returns:** List of XtreamVodStream objects  
**Used In:**
- `MoviesViewModel` - Load movies for selected category

---

#### `suspend fun convertVodStreamToMediaItem(stream: XtreamVodStream): MediaItem?`
**Purpose:** Convert Xtream VOD stream to ARVIO MediaItem for UI display  
**Returns:** MediaItem with poster, title, year, etc.  

---

### Series (TV Shows)

#### `suspend fun findEpisodeVodSource(title: String, season: Int, episode: Int, imdbId: String?, tmdbId: Int?, allowNetwork: Boolean = true): List<StreamSource>`
**Purpose:** Find Xtream series episode streams using intelligent resolution  
**Parameters:**
- `title` - Show title
- `season` - Season number
- `episode` - Episode number
- `imdbId` - IMDb ID (optional)
- `tmdbId` - TMDB ID (optional)
- `allowNetwork` - Allow network fetch

**Returns:** List of StreamSource objects for the episode  
**Used In:**
- `StreamRepository.resolveEpisodeStreams()` - Append IPTV sources to addon streams

**Resolution Strategy:**
```
1. Query SeriesResolver for series matches:
   - Canonical title matching
   - TMDB ID binding (if available)
   - Fuzzy title token matching
2. For each matched series:
   - Load episode list (from cache or API)
   - Match season/episode using:
     * Direct season/episode number match
     * Episode title parsing
     * Container info parsing
3. Build StreamSource with resolved episode URL
4. Return all matched episode streams
```

**Caching:**
- **Series Catalog:** 6 hours (disk-persisted)
- **Episode List:** In-memory LRU cache (8 series max)
- **Series Bindings:** Persistent across sessions

---

#### `suspend fun findSeriesMatches(title: String, tmdbId: Int? = null, imdbId: String? = null): List<IptvSeriesMatch>`
**Purpose:** Find Xtream series that match a show title  
**Returns:** List of matches with match score  
**Used In:**
- Episode resolution helper
- Series pre-caching

---

#### `suspend fun getSeriesCategories(): List<XtreamSeriesCategory>`
**Purpose:** Get all series categories from Xtream provider  
**Used In:**
- `SeriesViewModel` - Display category list in Series screen

---

#### `suspend fun getCachedSeriesCategories(): List<XtreamSeriesCategory>`
**Purpose:** Get cached series categories without network call  

---

#### `suspend fun getSeriesByCategory(categoryId: String): List<XtreamSeriesItem>`
**Purpose:** Get all series in a specific category  

---

#### `suspend fun getSeriesByCategoryMap(): Map<String, List<XtreamSeriesItem>>`
**Purpose:** Get all series grouped by category (optimized for UI)  
**Used In:**
- `SeriesViewModel` - Load all series categories at once

---

#### `suspend fun convertSeriesItemToMediaItem(series: XtreamSeriesItem): MediaItem?`
**Purpose:** Convert Xtream series to ARVIO MediaItem  

---

### Instant Episode Playback (TASK_17 Feature)

#### `suspend fun buildEpisodeUrlFromCache(seriesId: Int, se ason: Int, episode: Int): String?`
**Purpose:** Build episode playback URL from cached episode data (instant playback)  
**Parameters:**
- `seriesId` - Xtream series ID
- `season` - Season number
- `episode` - Episode number

**Returns:** Direct episode stream URL or null if not cached  
**Used In:**
- `PlayerViewModel.loadNextEpisode()` - Skip source picker for next episode

**Performance:** O(1) lookup from memory cache - instant playback

---

#### `suspend fun getCachedSeriesEpisodes(seriesId: Int): List<IptvSeriesEpisodeInfo>?`
**Purpose:** Get cached episode list for a series  
**Returns:** Episode list or null if not cached  

---

#### `suspend fun fetchAndCacheSeriesEpisodes(seriesId: Int): List<IptvSeriesEpisodeInfo>`
**Purpose:** Fetch and cache episode list for future instant playback  
**Called By:** Episode resolution flow (automatic caching)

---

#### `suspend fun hasSeriesEpisodeCache(seriesId: Int): Boolean`
**Purpose:** Check if series episode data is cached  
**Returns:** true if cached and fresh

---

#### `suspend fun storeSeriesContext(tmdbId: Int, seriesId: Int, seriesName: String)`
**Purpose:** Store TMDB→Xtream series mapping for instant resume  
**Used In:**
- `PlayerViewModel` - Save series context after playback

---

#### `suspend fun getStoredSeriesContext(tmdbId: Int): IptvSeriesContext?`
**Purpose:** Get stored series context for instant playback  
**Returns:** Cached context or null  
**Used In:**
- `PlayerViewModel.loadNextEpisode()` - Retrieve series ID for next episode

---

### Prefetching / Background Optimization

#### `suspend fun prefetchEpisodeVodResolution(title: String, season: Int, episode: Int, imdbId: String?, tmdbId: Int?)`
**Purpose:** Pre-resolve episode VOD data in background (warm cache)  
**Used In:**
- `StreamRepository.prefetchEpisodeVod()` - Called when details screen opens

**Benefit:** Instant stream availability when user clicks play

---

#### `suspend fun prefetchSeriesInfoForShow(title: String, imdbId: String?, tmdbId: Int?)`
**Purpose:** Pre-load series catalog and episode list  
**Used In:**
- `StreamRepository.prefetchSeriesVodInfo()` - Called when browsing show details

---

#### `suspend fun warmXtreamVodCachesIfPossible()`
**Purpose:** Warm VOD/Series caches in background if refresh needed  
**Used In:**
- App startup (ArflixApplication)
- Home screen load (HomeViewModel)

**Behavior:** Non-blocking - returns immediately if cache fresh

---

#### `suspend fun forceWarmXtreamVodCaches()`
**Purpose:** Force immediate cache refresh  
**Used In:**
- IPTV config save (SettingsViewModel)

---

#### `suspend fun isVodCacheRefreshNeeded(): Boolean`
**Purpose:** Check if VOD cache needs refresh  
**Returns:** true if older than 6 hours  

---

### Refresh Interval Management

#### `fun observeRefreshInterval(): Flow<IptvRefreshInterval>`
**Purpose:** Observable refresh interval setting  
**Returns:** Flow of `IptvRefreshInterval` enum (DISABLED, EVERY_12_HOURS, DAILY, EVERY_48_HOURS)  
**Used In:**
- `ArflixApplication` - Schedule background refresh worker
- `SettingsViewModel` - Display current interval

---

#### `suspend fun setRefreshInterval(interval: IptvRefreshInterval)`
**Purpose:** Set refresh interval  
**Used In:**
- `SettingsViewModel.setRefreshInterval()` - User change

---

#### `fun observeLastRefreshTime(): Flow<Long?>`
**Purpose:** Observable last refresh timestamp  
**Used In:**
- `SettingsViewModel` - Display "last refreshed" time

---

#### `suspend fun setLastRefreshTime(timestampMs: Long)`
**Purpose:** Update last refresh timestamp  
**Used In:**
- `IptvRefreshWorker` - Mark successful refresh

---

### Cloud Sync (Profile Integration)

#### `suspend fun importCloudConfig(m3uUrl: String, epgUrl: String, favoriteGroups: List<String>, favoriteChannels: List<String>)`
**Purpose:** Import IPTV config from Supabase cloud sync  
**Used In:**
- Profile sync (SettingsViewModel)

---

#### `suspend fun exportCloudConfigForProfile(profileId: String): IptvCloudProfileState`
**Purpose:** Export IPTV config for cloud sync  
**Returns:** State object for JSON serialization  

---

#### `suspend fun importCloudConfigForProfile(profileId: String, state: IptvCloudProfileState)`
**Purpose:** Import IPTV config for specific profile  

---

## Advanced Features

### Series Resolution System

The `IptvSeriesResolverService` (inner class) provides multi-tier series-to-episode matching:

#### Resolution Tiers
1. **TMDB Binding**: Direct TMDB ID → series mapping (instant, O(1))
2. **Canonical Title**: Exact normalized title match (fast, O(1) via hash map)
3. **Fuzzy Token Match**: Word-level matching with scoring (moderate speed)
4. **Provider Title Parse**: Extract season/episode from provider title (fallback)

#### Caching Layers
1. **Catalog Index**: Series list with normalized titles (6-hour cache)
2. **Episode Cache**: Recently accessed episode lists (LRU, 8 series)
3. **Binding Cache**: TMDB→series mappings (persistent)
4. **Resolved Episode Cache**: Previously matched episodes (session-scoped)

#### Performance Optimizations
- **Lazy Regex**: Regex patterns compiled once on first use
- **Token Pre-computation**: Title tokens computed during index build
- **Disk Persistence**: Large catalogs (100MB+) persisted to disk
- **Incremental Loading**: Catalogs loaded on-demand, not all at once

---

### Xtream Codes API Integration

#### Supported Endpoints
- `/player_api.php?action=get_live_streams` - Live TV channels
- `/player_api.php?action=get_vod_streams` - VOD movies
- `/player_api.php?action=get_series` - Series catalog
- `/player_api.php?action=get_series_info&series_id=X` - Episode list
- `/player_api.php?action=get_vod_info&vod_id=X` - VOD metadata
- `/player_api.php?action=get_live_categories` - Channel groups
- `/player_api.php?action=get_vod_categories` - Movie categories
- `/player_api.php?action=get_series_categories` - Series categories
- `/player_api.php?action=get_short_epg&stream_id=X` - Fast EPG (base64 encoded)
- `/xmltv.php` - Full XMLTV EPG

#### Authentication
All endpoints require `username` and `password` query parameters.

#### Response Parsing
- **JSON**: Standard endpoints return JSON (Gson deserialization)
- **Base64 EPG**: Short EPG base64-encodes title/description to support Unicode
- **XMLTV**: Full EPG is gzipped XML (SAX parser for memory efficiency)

---

## Data Models

### IptvConfig
```kotlin
data class IptvConfig(
    val m3uUrl: String = "",
    val epgUrl: String = "",
    val xtreamUsername: String = "",
    val xtreamPassword: String = ""
)
```

### IptvSnapshot
```kotlin
data class IptvSnapshot(
    val channels: List<IptvChannel>,
    val nowNext: Map<String, IptvNowNext>,
    val loadedAtMs: Long,
    val hasEpg: Boolean
)
```

### IptvChannel
```kotlin
data class IptvChannel(
    val id: String,
    val name: String,
    val url: String,
    val logo: String?,
    val group: String?,
    val tvgId: String?,  // EPG channel ID
    val xtreamStreamId: Int?  // Xtream stream ID
)
```

### IptvNowNext
```kotlin
data class IptvNowNext(
    val now: IptvProgram?,
    val next: IptvProgram?
)
```

### IptvProgram
```kotlin
data class IptvProgram(
    val title: String,
    val description: String?,
    val start: Long,  // Unix timestamp (ms)
    val end: Long
)
```

### XtreamVodStream
```kotlin
data class XtreamVodStream(
    val streamId: Int,
    val name: String,
    val streamIcon: String?,
    val categoryId: String?,
    val containerExtension: String?,
    val tmdbId: String?,
    val year: String?
)
```

### XtreamSeriesItem
```kotlin
data class XtreamSeriesItem(
    val seriesId: Int,
    val name: String,
    val cover: String?,
    val categoryId: String?,
    val tmdbId: String?,
    val year: String?
)
```

---

## Performance Characteristics

### Timeouts
- **Full Playlist Load**: 60s (accommodates large Xtream providers)
- **EPG Load**: 90s (100MB+ EPG files)
- **VOD Lookup**: 12s (fast playback startup)
- **Episode Resolution**: 45s (complex matching)
- **Short EPG**: 8s (Xtream fast EPG endpoint)

### Memory Usage
- **Channels**: ~100KB per 1000 channels
- **EPG Programs**: ~500KB per 1000 programs
- **VOD Catalog**: ~1MB per 10,000 movies
- **Series Catalog**: ~500KB per 5,000 series
- **Episode Cache**: ~50KB per series (8 series max = 400KB)

### Disk Cache
- **VOD Catalog**: Up to 10MB (persisted JSON)
- **Series Catalog**: Up to 5MB (persisted JSON)
- **Refresh**: 6-hour TTL for catalogs, 24-hour TTL for playlists/EPG

---

## Thread Safety

- `@Volatile` variables for multi-threaded read safety
- `Mutex` locks for critical sections:
  - `loadMutex` - Prevents concurrent `loadSnapshot()` calls
  - `xtreamDataMutex` - Serializes VOD/series catalog access
  - `xtreamSeriesEpisodeCacheMutex` - Episode cache modification lock
- `ConcurrentHashMap` for safe now/next updates

---

## Error Handling

### Network Errors
- **Timeout**: Results in empty snapshot/catalog (graceful degradation)
- **HTTP Errors**: Logged and returns empty result
- **Parse Errors**: Returns empty result, doesn't crash

### Invalid Config
- **Empty URLs**: `loadSnapshot()` returns empty snapshot
- **Invalid Credentials**: Xtream returns `{"status":"error"}` - handled gracefully
- **Malformed M3U**: Skips invalid entries, parses valid ones

### EPG Errors
- **Missing EPG**: Channels returned without program info
- **Malformed XMLTV**: SAX parser handles most format variations
- **Expired Programs**: `reDeriveCachedNowNext()` updates `now` field as programs expire

---

## Testing Considerations

1. **Xtream Triplet Parsing**: Test all input format variations
2. **EPG Matching**: Test channels with/without `tvg-id`
3. **VOD Matching**: Test with special characters, non-English titles, quality tags
4. **Series Resolution**: Test multi-season shows, episode title parsing
5. **Cache Invalidation**: Verify cache clears on config change
6. **Profile Isolation**: Verify each profile has independent config

---

## Known Limitations

1. **Large EPG Files**: 100MB+ EPG files take 30-60s to parse (SAX parser is fast but XML parsing is inherently slow)
2. **Xtream Short EPG**: Only provides 3-5 hours of program data
3. **Series Matching**: Requires good provider naming (season/episode in title)
4. **VOD TMDB IDs**: Not all providers include TMDB IDs
5. **Multiformat URIs**: Some exotic M3U extensions not supported

---

## Future Improvements

1. **Incremental EPG Updates**: Only fetch changed programs
2. **EPG Compression**: Store EPG in binary format for faster load
3. **Smart Series Binding**: ML-based series matching
4. **VOD Artwork Fallback**: TMDB artwork if provider doesn't supply
5. **Multi-Provider**: Support multiple Xtream providers simultaneously

---

**Last Updated:** March 2026  
**ARVIO Version:** 1.8.2+
