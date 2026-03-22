# Repository Architecture Overview

## System Design

ARVIO's media streaming architecture is built around two core repositories that work together to provide a unified streaming experience combining Stremio addons and IPTV sources.

```
┌─────────────────────────────────────────────────────────────┐
│                      ARVIO Media Stack                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────┐         ┌──────────────────┐          │
│  │ StreamRepository │◄────────┤ IptvRepository   │          │
│  │ (Stremio Addons) │ Depends │ (IPTV/Xtream)    │          │
│  └──────────────────┘         └──────────────────┘          │
│          │                             │                     │
│          │                             │                     │
│  ┌───────▼─────────┐         ┌─────────▼────────┐           │
│  │ Stremio Addons  │         │ Xtream Codes API │           │
│  │ - Torrentio     │         │ - Live TV        │           │
│  │ - MediaFusion   │         │ - VOD Movies     │           │
│  │ - OpenSubtitles │         │ - Series/Episodes│           │
│  │ - Custom Addons │         │ - EPG Data       │           │
│  └─────────────────┘         └──────────────────┘           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
           │                             │
           └──────────┬──────────────────┘
                      │
           ┌──────────▼──────────┐
           │  Unified Stream     │
           │  Resolution Result  │
           └──────────┬──────────┘
                      │
           ┌──────────▼──────────┐
           │   PlayerViewModel   │
           │   (playback logic)  │
           └─────────────────────┘
```

---

## Repository Roles

### StreamRepository
**Primary Focus:** External addon integration and stream orchestration

**Core Functions:**
- Manage Stremio addon lifecycle (install, enable, disable, remove)
- Query multiple addons in parallel for streams
- Process and normalize stream responses
- Handle subtitle fetching
- Resolve playback URLs (redirects, headers, etc.)
- Cache stream results with intelligent TTL
- **Delegate IPTV lookups to IptvRepository**

**Key Characteristic:** Stateless stream coordinator - relies on external addons for content

---

### IptvRepository
**Primary Focus:** IPTV provider integration and catalog management

**Core Functions:**
- Parse M3U playlists (standard and Xtream)
- Process EPG (Electronic Program Guide)
- Index and search VOD catalogs
- Resolve series episodes with fuzzy matching
- Cache massive datasets (100MB+ EPG, 10k+ VOD entries)
- Provide instant episode playback via cached episode data
- Manage profile-scoped IPTV configurations

**Key Characteristic:** Stateful catalog manager - maintains large in-memory/disk indexes

---

## Integration Points

### 1. Movie Stream Resolution

```kotlin
// StreamRepository.resolveMovieStreams()
suspend fun resolveMovieStreams(imdbId: String, title: String, year: Int?): StreamResult {
    // 1. Query Stremio addons in parallel
    val addonStreams = queryAddonsParallel(imdbId)
    
    // 2. Append IPTV VOD sources (if available)
    val iptvStreams = iptvRepository.findMovieVodSource(
        title = title,
        year = year,
        imdbId = imdbId,
        allowNetwork = true
    )
    
    // 3. Merge results (deduplicate by URL)
    return StreamResult(
        streams = mergeStreams(addonStreams, iptvStreams),
        subtitles = subtitles
    )
}
```

**Flow:**
1. StreamRepository queries all enabled Stremio addons
2. Simultaneously requests IPTV sources from IptvRepository
3. IptvRepository searches its VOD catalog using fuzzy title matching
4. Results are merged, duplicates removed
5. Combined list returned to caller (DetailsViewModel or PlayerViewModel)

**Timeout Strategy:**
- Addon queries: 20s per addon (slow debrid providers)
- IPTV lookup: 12s (fast startup priority)
- If addons already returned results: IPTV lookup reduced to 3s

---

### 2. Episode Stream Resolution

```kotlin
// StreamRepository.resolveEpisodeStreams()
suspend fun resolveEpisodeStreams(
    imdbId: String,
    season: Int,
    episode: Int,
    title: String,
    tmdbId: Int?
): StreamResult {
    // 1. Anime detection
    val isAnime = animeMapper.isAnimeContent(tmdbId, genreIds, language)
    val animeQuery = if (isAnime) mapToKitsuId(...) else null
    
    // 2. Query addons (use Kitsu ID for anime addons)
    val addonStreams = queryAddonsParallel(animeQuery ?: imdbId)
    
    // 3. Query IPTV series resolver
    val iptvStreams = iptvRepository.findEpisodeVodSource(
        title = title,
        season = season,
        episode = episode,
        tmdbId = tmdbId,
        allowNetwork = true
    )
    
    // 4. Merge and return
    return StreamResult(streams = merge(addonStreams, iptvStreams), subtitles)
}
```

**Special Features:**
- **Anime Support**: Uses Kitsu IDs for anime-aware addons (Torrentio, MediaFusion)
- **Series Caching**: IptvRepository caches episode lists for instant next-episode playback
- **Fuzzy Matching**: IptvRepository uses sophisticated title/season/episode matching

---

### 3. Instant Next Episode (Continue Watching)

```kotlin
// PlayerViewModel: Auto-advance to next episode
val seriesContext = iptvRepository.getStoredSeriesContext(tmdbId)
if (seriesContext != null) {
    // Instant playback: Build URL from cached episode data
    val url = iptvRepository.buildEpisodeUrlFromCache(
        seriesId = seriesContext.seriesId,
        season = nextSeason,
        episode = nextEpisode
    )
    if (url != null) {
        playStream(url) // No source picker needed!
        return
    }
}

// Fallback: Regular stream resolution
val streams = streamRepository.resolveEpisodeStreams(...)
showSourcePicker(streams)
```

**Optimization:**
- First episode: Normal resolution (shows source picker)
- Subsequent episodes: Instant playback from cache (O(1) lookup, <50ms)
- Cache persists across app restarts

---

## Data Flow Examples

### Example 1: User Opens Movie Details Page

```
User opens "Inception (2010)"
         │
         ▼
DetailsViewModel.loadDetails()
         │
         ├──► DetailsViewModel.prefetchStreams()
         │        │
         │        ▼
         │    StreamRepository.resolveMovieStreams(imdbId="tt1375666", title="Inception", year=2010)
         │        │
         │        ├──► Query Torrentio addon → returns 45 streams (20s)
         │        ├──► Query MediaFusion addon → returns 23 streams (18s)
         │        ├──► Query custom addons → returns 8 streams (12s)
         │        │
         │        └──► IptvRepository.findMovieVodSource(title="Inception", year=2010)
         │                  │
         │                  ├──► Normalize title: "inception" (canonical key)
         │                  ├──► Search VOD catalog index (O(1) hash lookup)
         │                  ├──► Found 3 matches: "Inception (2010) 1080p", "Inception 4K", "Inception HD"
         │                  └──► Return as StreamSource objects
         │
         └──► Cache result for 6 hours
         
User clicks "Play"
         │
         └──► Source picker shows: 79 streams (45 + 23 + 8 + 3)
```

---

### Example 2: User Plays TV Episode

```
User plays "Breaking Bad S01E01"
         │
         ▼
PlayerViewModel.loadStreamsForContent()
         │
         ▼
StreamRepository.resolveEpisodeStreams(imdbId="tt0959621", s=1, e=1, title="Breaking Bad")
         │
         ├──► Check cache → MISS (first time)
         │
         ├──► Query addons in parallel
         │        ├──► Torrentio: 15 streams
         │        ├──► MediaFusion: 8 streams
         │        └──► Custom addons: 2 streams
         │
         └──► IptvRepository.findEpisodeVodSource(title="Breaking Bad", season=1, episode=1)
                  │
                  ├──► SeriesResolver.findMatches("Breaking Bad")
                  │        │
                  │        ├──► Search series catalog: Found 2 providers
                  │        │    - "Breaking Bad (2008)" (series_id=1234)
                  │        │    - "Breaking Bad HD" (series_id=5678)
                  │        │
                  │        └──► Fetch episode lists for both series
                  │
                  ├──► Match season 1, episode 1:
                  │        - Series 1234: "S01E01 - Pilot.mkv" ✓ MATCH
                  │        - Series 5678: "Breaking Bad S1E1.mp4" ✓ MATCH
                  │
                  ├──► Build StreamSource objects with Xtream URLs
                  │
                  └──► Cache episode lists for instant next-episode playback
                  
User clicks stream → Plays S01E01

User finishes episode, clicks "Next Episode"
         │
         ▼
PlayerViewModel.loadNextEpisode()
         │
         └──► IptvRepository.buildEpisodeUrlFromCache(seriesId=1234, s=1, e=2)
                  │
                  └──► Returns URL instantly (cached episode list)
                  
Playback starts immediately (no source picker!)
```

---

## Caching Architecture

### StreamRepository Cache

**Scope:** Per-profile stream results  
**Storage:** In-memory + DataStore (persisted across restarts)  
**Key Format:** `{profileId}|{type}|{imdbId}|{season}|{episode}`

**TTL Strategy:**
- **Content streams** (torrents): 6 hours - longest TTL, sources are stable
- **HTTP streams**: 3 minutes - typical browse time before playing
- **Ephemeral streams** (tokenized URLs): 10 seconds - short-lived tokens

**Invalidation:**
- Addon change (install/remove/toggle)
- IPTV config change
- Profile switch
- Manual cache clear

---

### IptvRepository Cache

**Multiple Cache Layers:**

#### 1. Playlist/EPG Cache
- **TTL:** 24 hours
- **Storage:** In-memory + disk
- **Size:** ~100KB-10MB depending on provider
- **Invalidation:** Config change, manual refresh

#### 2. VOD Catalog Cache
- **TTL:** 6 hours
- **Storage:** Disk-persisted JSON (survives app restart)
- **Size:** ~1-10MB for large providers
- **Index:** Hash map for O(1) lookups

#### 3. Series Catalog Cache
- **TTL:** 6 hours
- **Storage:** Disk-persisted JSON
- **Size:** ~500KB-5MB
- **Index:** Title tokens + TMDB bindings

#### 4. Episode List Cache
- **Strategy:** LRU cache (8 series max)
- **TTL:** In-memory only (until app killed)
- **Size:** ~50KB per series
- **Purpose:** Instant next-episode playback

#### 5. Series Bindings Cache
- **Persistent:** Yes (DataStore)
- **Purpose:** TMDB ID → Xtream series_id mappings
- **Size:** ~10KB (100s of bindings)

---

## Performance Optimizations

### 1. Parallel Execution
```kotlin
// All addons queried simultaneously
val streams = streamAddons.map { addon ->
    async { queryAddon(addon) }  // Parallel coroutines
}.awaitAll().flatten()

// IPTV lookup also runs in parallel (doesn't block addon queries)
```

### 2. Timeout Management
- Each addon: 20s max (generous for debrid)
- IPTV: 12s primary, 3s append
- Timeout exceptions caught, don't fail entire operation

### 3. Disk Caching
```kotlin
// IptvRepository: Large catalogs persisted to disk
private fun writeDiskCache(file: File, items: List<T>) {
    val json = gson.toJson(items)
    file.writeText(json)  // 10k movies = ~10MB written in ~500ms
}
```

**Benefit:** Cold app start loads 10k VOD catalog in ~100ms instead of 15s network fetch

### 4. Index Pre-computation
```kotlin
// IptvRepository: Build search index
private fun buildVodCatalogIndex(vod: List<XtreamVodStream>): VodCatalogIndex {
    val byCanonicalTitle = vod.groupBy { toCanonicalTitleKey(it.name) }
    val byTmdbId = vod.filter { it.tmdbId != null }.associateBy { it.tmdbId }
    val byYear = vod.groupBy { it.year }
    // ... build token index
    return VodCatalogIndex(byCanonicalTitle, byTmdbId, byYear, tokenIndex)
}
```

**Benefit:** O(1) lookups instead of O(n) linear scans

---

## Error Handling Philosophy

### Graceful Degradation
Both repositories follow a "best effort" approach:

**Principle:** Always return a usable result, even if some sources fail

**Implementation:**
```kotlin
// StreamRepository
val streams = streamAddons.map { addon ->
    async {
        try {
            withTimeout(20_000) {
                queryAddon(addon)
            }
        } catch (e: Exception) {
            emptyList()  // This addon failed, return empty
        }
    }
}.awaitAll().flatten()  // Merge all successful results
```

**Result:** If 1 of 3 addons fails, user still gets streams from the other 2

### Non-Blocking IPTV
IPTV lookups don't block addon results:
```kotlin
try {
    val iptvStreams = withTimeoutOrNull(12_000) {
        iptvRepository.findMovieVodSource(...)
    } ?: emptyList()
    streams.addAll(iptvStreams)
} catch (e: Exception) {
    // IPTV failed, but user still has addon streams
}
```

---

## Profile Isolation

Both repositories support multi-profile architecture:

### StreamRepository
- **Addon Lists:** Per-profile (profile_123 has different addons than profile_456)
- **Cache:** Profile-scoped cache keys
- **OpenSubtitles:** Always present (enforced)

### IptvRepository
- **Configuration:** Fully isolated per profile
- **Favorites:** Profile-scoped favorite groups/channels
- **VOD Cache:** Shared across profiles (same Xtream provider = shared catalog)
- **Episode Cache:** Profile-scoped (different watch patterns)

---

## Testing Strategies

### Unit Testing
- **StreamRepository:** Mock StreamApi and IptvRepository
- **IptvRepository:** Mock OkHttpClient

### Integration Testing
- **End-to-End:** Real Stremio addons + test Xtream provider
- **Performance:** Measure cache hit rates, query latencies
- **Resilience:** Simulate addon timeouts, network failures

### Test Data
- **M3U Playlists:** Test with 100 channels, 1000 channels, 10k channels
- **EPG:** Test with 100MB EPG files
- **VOD:** Test with 10k movie catalogs
- **Series:** Test with multi-season shows

---

## Known Edge Cases

### 1. Duplicate Streams
**Scenario:** Same movie available from both addon and IPTV  
**Handling:** URL-based deduplication  
**Code:**
```kotlin
val existingUrls = streams.mapNotNull { it.url }.toSet()
streams.addAll(iptvStreams.filter { it.url !in existingUrls })
```

### 2. Expired URLs
**Scenario:** Cached stream URL expires before playback  
**Handling:** Short TTL for tokenized URLs (10s), fallback to fresh fetch  

### 3. Mismatched Anime IDs
**Scenario:** Kitsu query returns no results  
**Handling:** Fallback to IMDb format  
**Code:**
```kotlin
if (addonStreams.isEmpty() && useKitsu && contentId != imdbId) {
    val fallbackStreams = queryAddon(imdbId)  // Retry with IMDb ID
    ...
}
```

### 4. Large VOD Catalogs
**Scenario:** Provider has 50k+ movies  
**Handling:**
- Disk caching (no re-download on restart)
- Indexed search (O(1) lookups)
- Lazy loading (only load categories on demand)

---

## Future Architecture Improvements

### 1. Stream Quality Filtering
Allow users to set quality preferences (e.g., "only 1080p+"):
```kotlin
val filteredStreams = streams.filter { 
    it.quality.contains("1080p") || it.quality.contains("4K")
}
```

### 2. Addon Priority
Allow users to prioritize certain addons:
```kotlin
val prioritizedStreams = streams.sortedBy { stream ->
    addonPriority[stream.addonId] ?: Int.MAX_VALUE
}
```

### 3. Predictive Caching
Pre-cache next episode when user is 80% through current episode:
```kotlin
if (playbackProgress > 0.8 && hasNextEpisode) {
    launch { 
        streamRepository.resolveEpisodeStreams(nextSeason, nextEpisode)
    }
}
```

### 4. Multi-Provider IPTV
Support multiple Xtream providers simultaneously:
```kotlin
val iptvProvider1Streams = iptvRepository1.findMovieVodSource(...)
val iptvProvider2Streams = iptvRepository2.findMovieVodSource(...)
streams.addAll(iptvProvider1Streams + iptvProvider2Streams)
```

---

## Metrics & Monitoring

### Key Performance Indicators

**StreamRepository:**
- Cache hit rate (target: >70%)
- Average addon response time (target: <15s)
- Addon failure rate (target: <10%)
- Stream resolution success rate (target: >95%)

**IptvRepository:**
- Playlist load time (target: <30s for 10k channels)
- EPG parse time (target: <60s for 100MB EPG)
- VOD match accuracy (target: >90%)
- Series episode match accuracy (target: >85%)

**Combined:**
- Time to first stream (target: <20s)
- Instant next-episode success rate (target: >80%)

---

**Last Updated:** March 2026  
**ARVIO Version:** 1.8.2+
