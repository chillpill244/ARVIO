# StreamRepository API Documentation

## Overview
`StreamRepository` is the central repository for managing Stremio addon integration and stream resolution in ARVIO. It handles addon management, stream fetching from multiple sources, caching, subtitle management, and playback URL resolution.

**Package:** `com.arflix.tv.data.repository`  
**Singleton:** Yes  
**Dependencies:** StreamApi, OkHttpClient, ProfileManager, AnimeMapper, IptvRepository

---

## Key Responsibilities

1. **Addon Management**: Install, remove, enable/disable Stremio addons
2. **Stream Resolution**: Fetch and process streams from multiple addons in parallel
3. **Caching**: Smart caching of stream results with TTL-based invalidation
4. **Subtitle Management**: Fetch and manage subtitles from providers like OpenSubtitles
5. **Playback Resolution**: Resolve final playback URLs including redirect handling
6. **IPTV Integration**: Seamlessly append IPTV VOD sources to stream results

---

## Public API Methods

### Addon Management

#### `val installedAddons: Flow<List<Addon>>`
**Purpose:** Observable flow of installed addons for the current profile  
**Returns:** Flow emitting list of addons whenever the configuration changes  
**Used In:**
- `PlayerViewModel` - For checking available addons during stream selection
- `SettingsViewModel` - For displaying addon list in settings
- `HomeViewModel` - For displaying addon status

**Implementation Notes:**
- Profile-scoped: Each profile has independent addon list
- Always includes OpenSubtitles by default (enforced)
- Combines active profile ID with DataStore preferences

---

#### `suspend fun addCustomAddon(url: String, customName: String? = null): Result<Addon>`
**Purpose:** Add a custom Stremio addon from URL  
**Parameters:**
- `url` - Addon manifest URL (supports `stremio://`, `https://`, or bare URLs)
- `customName` - Optional custom display name for the addon

**Returns:** `Result.success(Addon)` or `Result.failure(Exception)`  
**Used In:**
- `SettingsViewModel.addAddon()` - User-initiated addon installation

**Process Flow:**
1. Normalizes input URL (handles `stremio://` protocol, fixes common typos)
2. Fetches manifest from `{url}/manifest.json`
3. Converts API manifest to internal `AddonManifest` model
4. Generates unique addon ID using hash of URL
5. Saves to profile-scoped DataStore
6. Clears stream cache to force refresh

**Error Handling:** Returns failure if network error, invalid manifest, or URL parsing fails

---

#### `suspend fun removeAddon(addonId: String)`
**Purpose:** Remove an installed addon  
**Parameters:**
- `addonId` - Unique ID of the addon to remove

**Used In:**
- `SettingsViewModel.removeAddon()` - User-initiated addon removal

**Implementation Notes:**
- Cannot remove OpenSubtitles (silently ignored)
- Profile-scoped removal

---

#### `suspend fun toggleAddon(addonId: String)`
**Purpose:** Enable or disable an addon without removing it  
**Parameters:**
- `addonId` - ID of addon to toggle

**Used In:**
- `SettingsViewModel.toggleAddon()` - Toggle addon enabled state

**Implementation Notes:**
- Cannot toggle OpenSubtitles (silently ignored)
- Disabled addons remain installed but aren't queried for streams

---

### Stream Resolution

#### `suspend fun resolveMovieStreams(imdbId: String, title: String = "", year: Int? = null, forceRefresh: Boolean = false): StreamResult`
**Purpose:** Resolve all available streams for a movie from enabled addons + IPTV sources  
**Parameters:**
- `imdbId` - IMDb ID (format: `tt1234567`)
- `title` - Movie title (used for IPTV matching)
- `year` - Release year (optional, improves IPTV matching)
- `forceRefresh` - Skip cache and force fresh fetch

**Returns:** `StreamResult` containing streams and subtitles  
**Used In:**
- `PlayerViewModel.loadStreamsForContent()` - Initial stream loading
- `DetailsViewModel.prefetchStreams()` - Pre-fetch streams for details page

**Process Flow:**
1. Check cache (6-hour TTL for content, shorter for ephemeral URLs)
2. Query all enabled streaming addons in parallel (20s timeout per addon)
3. Process raw streams into `StreamSource` objects
4. Append IPTV VOD sources if available (12s timeout)
5. Return combined result
6. Cache result with appropriate TTL

**Caching Strategy:**
- **Content TTL:** 6 hours (stable stream sources)
- **HTTP TTL:** 3 minutes (typical details page browse time)
- **Ephemeral TTL:** 10 seconds (tokenized/expiring URLs)

**Performance:** Parallel fetching with timeouts ensures ~20s max latency even if some addons are slow

---

#### `suspend fun resolveEpisodeStreams(imdbId: String, season: Int, episode: Int, ...): StreamResult`
**Purpose:** Resolve streams for a TV episode with anime support  
**Parameters:**
- `imdbId` - IMDb ID
- `season` - Season number
- `episode` - Episode number
- `tmdbId` - TMDB ID (optional, for anime detection)
- `tvdbId` - TVDB ID (optional, for anime resolution)
- `genreIds` - TMDB genre IDs (for anime detection)
- `originalLanguage` - Original language (for anime detection)
- `title` - Show title (for IPTV matching)
- `forceRefresh` - Skip cache

**Returns:** `StreamResult` with streams and subtitles  
**Used In:**
- `PlayerViewModel.loadStreamsForContent()` - Episode stream loading
- `DetailsViewModel.prefetchStreams()` - Pre-fetch episode streams

**Anime Support:**
1. Detects anime using `AnimeMapper.isAnimeContent()`
2. Resolves Kitsu ID using 5-tier fallback (TMDB→TVDB→title→IMDb→MAL)
3. Uses Kitsu format for anime-supporting addons (Torrentio, MediaFusion, etc.)
4. Falls back to IMDb format if Kitsu query returns no results

**IPTV Integration:**
- Queries IPTV series resolver for Xtream VOD episodes
- Appends results to addon streams
- 12s timeout - doesn't block playback if slow

---

#### `suspend fun resolveMovieVodOnly(imdbId: String?, title: String = "", year: Int? = null, tmdbId: Int? = null, timeoutMs: Long = 15_000L): List<StreamSource>`
**Purpose:** Fetch only IPTV VOD sources for a movie (no addon sources)  
**Parameters:**
- `imdbId` - IMDb ID (optional)
- `title` - Movie title
- `year` - Release year
- `tmdbId` - TMDB ID
- `timeoutMs` - Timeout in milliseconds (default 15s)

**Returns:** List of IPTV stream sources only  
**Used In:**
- `PlayerViewModel.fetchAdditionalVodStreamsInBackground()` - Background VOD append

**Use Case:** Append IPTV sources after initial stream list is already shown to avoid UI delay

---

#### `suspend fun resolveEpisodeVodOnly(imdbId: String?, season: Int, episode: Int, ...): List<StreamSource>`
**Purpose:** Fetch only IPTV series/VOD sources for an episode  
**Parameters:**
- `imdbId` - IMDb ID
- `season` - Season number
- `episode` - Episode number
- `title` - Show title
- `tmdbId` - TMDB ID
- `timeoutMs` - Timeout (default 45s)

**Returns:** List of IPTV stream sources only  
**Used In:**
- `PlayerViewModel.fetchAdditionalVodStreamsInBackground()`

**Timeout:** 45s default (longer than movie) due to series episode resolution complexity

---

### Playback Resolution

#### `suspend fun resolveStreamForPlayback(stream: StreamSource): StreamSource?`
**Purpose:** Final resolution of stream URL before playback (handles redirects, headers, P2P)  
**Parameters:**
- `stream` - Unresolved stream source

**Returns:** Resolved `StreamSource` with playable URL, or null if unresolvable  
**Used In:**
- `PlayerViewModel.playStream()` - Called immediately before playback
- `PlayerViewModel.selectSecondaryStream()` - Fallback stream resolution

**Resolution Steps:**
1. Rejects magnet-only streams (P2P not supported for HTTP playback)
2. Normalizes URL format (adds missing scheme, handles `//` prefix)
3. Splits embedded headers from URL (format: `url|header1=value1&header2=value2`)
4. Merges all headers: behaviorHints + embedded + proxy headers
5. Returns resolved stream with final URL and headers

**Timeout:** 15s per stream to prevent hanging

---

#### `suspend fun isHttpStreamReachable(stream: StreamSource, timeoutMs: Long = 10_000L): Boolean`
**Purpose:** Verify HTTP stream accessibility before playback  
**Parameters:**
- `stream` - Stream to verify
- `timeoutMs` - Verification timeout (default 10s)

**Returns:** `true` if stream returns 2xx or 416 (range not satisfiable = valid stream)  
**Used In:**
- `PlayerViewModel.selectSecondaryStream()` - Validate fallback stream before switching

**Verification Method:**
1. Sends HEAD or GET request with `Range: bytes=0-1`
2. Checks response code (200-299 or 416 = valid)
3. Rejects `text/html` content-type (addon websites, not streams)
4. Derives Origin header from Referer if needed

---

### Subtitle Management

#### `suspend fun fetchSubtitlesForSelectedStream(mediaType: MediaType, imdbId: String, season: Int?, episode: Int?, stream: StreamSource?): List<Subtitle>`
**Purpose:** Fetch external subtitles for content with stream hints  
**Parameters:**
- `mediaType` - `MediaType.MOVIE` or `MediaType.TV`
- `imdbId` - IMDb ID
- `season` - Season number (for TV)
- `episode` - Episode number (for TV)
- `stream` - Currently selected stream (provides videoHash/videoSize for OpenSubtitles)

**Returns:** List of subtitles from all subtitle addons  
**Used In:**
- `PlayerViewModel.loadStreamsForContent()` - Load subtitles during stream resolution
- `PlayerViewModel.reloadSubtitlesForCurrentStream()` - Reload subtitles after stream switch

**OpenSubtitles Integration:**
- Uses `videoHash` and `videoSize` from stream's `behaviorHints` for accurate matching
- Filters to English subtitles only for OpenSubtitles
- Returns all languages for other providers

**Timeout:** 8s per addon (doesn't block playback)

---

### Cache Management

#### `suspend fun initializeCacheFromDisk()`
**Purpose:** Load persisted stream cache from DataStore on app start  
**Called By:** Automatically on first cache access  
**Implementation:** Lazy initialization - only loads once

---

#### `suspend fun clearStreamCache()`
**Purpose:** Clear all cached stream results (memory + disk)  
**Used In:**
- `SettingsViewModel.testIptvConfig()` - Force fresh streams after IPTV config change

**Triggers:**
- IPTV config change
- Addon list change
- Manual refresh

---

### Private Helper Methods

#### `private fun getStreamAddons(addons: List<Addon>, type: String, id: String): List<Addon>`
**Purpose:** Filter addons that support streaming for the given content type  
**Filtering Logic:**
1. Must be installed and enabled
2. Skip subtitle-only addons
3. Must have a URL
4. For custom addons: require `stream` resource in manifest
5. Check manifest resource types match content type
6. Check idPrefixes if present

---

#### `private fun processStreams(streams: List<StremioStream>, addon: Addon): List<StreamSource>`
**Purpose:** Convert raw Stremio API streams to internal `StreamSource` model  
**Processing:**
1. Filter out invalid/unsupported streams
2. Extract stream URL (handles YouTube `ytId` conversion)
3. Parse quality, size, and behavior hints
4. Extract embedded subtitles from stream
5. Merge all header sources (stream headers, behaviorHints headers, proxy headers)
6. Build `StreamSource` objects

---

#### `private fun isSupportedPlaybackCandidate(stream: StremioStream): Boolean`
**Purpose:** Filter out web pages returned as "streams" by providers  
**Blocks:**
- GitHub URLs (manifest/documentation)
- YouTube watch pages (handled separately)
- Raw githubusercontent URLs

---

## Data Models

### StreamResult
```kotlin
data class StreamResult(
    val streams: List<StreamSource>,
    val subtitles: List<Subtitle>
)
```

### CachedStreamResult
```kotlin
private data class CachedStreamResult(
    val result: StreamResult,
    val createdAtMs: Long,
    val isEpisode: Boolean = false
)
```

---

## Caching Strategy

### Cache Keys
Format: `{profileId}|{type}|{imdbId}|{season}|{episode}`  
- Movies: `profile_123|movie|tt1234567|0|0`
- Episodes: `profile_123|series|tt1234567|1|5`

### TTL (Time-To-Live) Rules
1. **Content Streams** (torrents, debrid): 6 hours - longest TTL because sources are stable
2. **HTTP Streams**: 3 minutes - covers typical details page browse time
3. **Ephemeral Streams** (tokenized URLs): 10 seconds - very short due to expiring tokens
4. **Empty Results**: 2 minutes - retry sooner if no streams found

### Cache Invalidation
- Profile switch: Clears entire cache
- Addon change: Clears entire cache
- IPTV config change: Clears entire cache
- Manual refresh: `forceRefresh` parameter bypasses cache

---

## Performance Characteristics

### Timeouts
- **Per Addon**: 20s (generous for debrid/slow connections)
- **Subtitles**: 8s (non-blocking)
- **IPTV VOD Append**: 3s (fast append) or 12s (primary lookup)
- **Stream Resolution**: 15s (redirect/debrid resolution)
- **HTTP Reachability**: 10s (validation check)

### Parallel Execution
- All enabled addons queried simultaneously using `async`/`awaitAll`
- IPTV lookup runs in parallel with addon queries
- Subtitle fetching happens separately (doesn't block stream display)

---

## Integration with IptvRepository

The `StreamRepository` depends on `IptvRepository` for IPTV VOD source integration:

```kotlin
// Movie IPTV sources
val vodSources = iptvRepository.findMovieVodSource(
    title = title,
    year = year,
    imdbId = imdbId,
    tmdbId = null,
    allowNetwork = true
)

// Episode IPTV sources  
val vodSources = iptvRepository.findEpisodeVodSource(
    title = title,
    season = season,
    episode = episode,
    imdbId = imdbId,
    tmdbId = tmdbId,
    allowNetwork = true
)
```

IPTV sources are appended to addon streams, not replacing them. Duplicate URLs are filtered.

---

## Error Handling

### Addon Query Errors
- Timeouts: Return empty list for that addon (doesn't fail entire query)
- Network errors: Return empty list (graceful degradation)
- Parse errors: Return empty list

### Cache Errors
- Disk read failure: Falls back to empty cache, continues normally
- Disk write failure: Logged but doesn't affect functionality

### Playback Resolution Errors
- Returns `null` if stream unresolvable
- Caller handles fallback stream selection

---

## Thread Safety

- `@Volatile` cache variables for multi-threaded read safety
- `synchronized(streamResultCache)` for cache writes
- Profile-scoped DataStore ensures data isolation

---

## Testing Considerations

1. **Addon Timeout Handling**: Test with slow/unresponsive addons
2. **Cache TTL**: Verify different TTLs for different stream types
3. **Parallel Fetching**: Ensure all addons queried even if some fail
4. **IPTV Integration**: Test with and without IPTV config
5. **Anime Resolution**: Test Kitsu ID fallback chain
6. **Header Merging**: Test complex header scenarios (embedded + proxy + behaviorHints)

---

## Known Limitations

1. **P2P Streaming**: Magnet links require TorrServer or external player
2. **DRM Content**: Not supported (addons typically don't provide DRM streams)
3. **Live Streams**: Only Xtream live channels supported (via IptvRepository)
4. **Subtitle Formats**: Only WebVTT and SRT supported by ExoPlayer

---

## Future Improvements

1. **Incremental Stream Loading**: Show streams as each addon responds (progressive UI update)
2. **Addon Priority**: Allow user to prioritize certain addons
3. **Quality Presets**: Auto-filter by quality preference
4. **Offline Caching**: Download streams for offline viewing
5. **P2P Integration**: Native torrent support without TorrServer

---

**Last Updated:** March 2026  
**ARVIO Version:** 1.8.2+
