# ARVIO Documentation Index

## Overview
This documentation provides comprehensive references for ARVIO's architecture, repository layer, and system design. Start with the application architecture for a high-level overview, then dive into specific repository APIs as needed.

---

## Documentation Files

### 0. [APPLICATION_ARCHITECTURE.md](./APPLICATION_ARCHITECTURE.md) ⭐ **START HERE**
**Complete application architecture documentation**

**Topics Covered:**
- Technology stack & dependencies (Kotlin, Compose, Hilt, ExoPlayer, etc.)
- MVVM + Clean Architecture pattern explanation
- Complete module structure & file organization
- All screens, ViewModels, and repositories overview
- Data flow examples (user interactions → backend → UI)
- Navigation graph with D-pad handling
- Feature modules deep-dive (Live TV, Stremio, Continue Watching, Multi-Profile, ARVIO Cloud)
- External integrations (TMDB, Trakt, Supabase, IPTV, Stremio, OpenSubtitles)
- Background processing (WorkManager)
- Storage & caching strategies (multi-tier caching)
- Security & encryption (Android Keystore)
- Build variants (debug, beta, staging, release)
- Performance optimizations

**Use When:**
- Onboarding new developers
- Understanding overall system architecture
- Planning major features that span multiple modules
- Making architectural decisions
- Getting a bird's-eye view of the entire app

---

### 1. [QUICK_REFERENCE.md](./QUICK_REFERENCE.md)
**Fast navigation guide & FAQ**

**Topics Covered:**
- Documentation map (how to navigate docs efficiently)
- Common questions with direct links
- Development workflows (adding features, debugging)
- Performance metrics & targets
- Pro tips for developers and AI assistants

**Use When:**
- Looking for fast answers
- Unsure which doc to read
- Need troubleshooting guidance
- Want quick method lookups

---

### 2. [StreamRepository_API.md](./StreamRepository_API.md)
**Complete API documentation for StreamRepository**

**Topics Covered:**
- Addon management (install, remove, enable/disable)
- Stream resolution (movies, episodes, anime)
- Subtitle fetching
- Playback URL resolution
- Cache management
- IPTV integration points

**Use When:**
- Adding new Stremio addon features
- Implementing stream source handling
- Optimizing stream resolution performance
- Debugging addon-related issues
- Understanding cache TTL strategies

---

### 3. [IptvRepository_API.md](./IptvRepository_API.md)
**Complete API documentation for IptvRepository**

**Topics Covered:**
- M3U playlist parsing
- EPG (Electronic Program Guide) processing
- Xtream Codes API integration
- VOD catalog management
- Series episode resolution  
- Profile-scoped IPTV configuration
- Background refresh scheduling
- Disk caching strategies

**Use When:**
- Adding IPTV provider support
- Implementing VOD/series features
- Optimizing EPG performance
- Series matching/resolution issues
- Understanding Xtream API integration

---

### 4. [Repository_Architecture.md](./Repository_Architecture.md)
**System design and integration documentation**

**Topics Covered:**
- How StreamRepository and IptvRepository work together
- Data flow examples (movie playback, episode playback, instant next episode)
- Caching architecture (multi-layer strategy)
- Performance optimizations
- Error handling philosophy
- Profile isolation
- Testing strategies
- Future improvements

**Use When:**
- Understanding overall system architecture
- Planning new features that span both repositories
- Optimizing cross-repository data flow
- Debugging complex integration issues
- Onboarding new developers

---

## Quick Reference

### Common Development Tasks

#### Task: Understand overall app architecture
**References:**
1. [APPLICATION_ARCHITECTURE.md](./APPLICATION_ARCHITECTURE.md) - Complete system overview
2. [Repository_Architecture.md - Integration Points](./Repository_Architecture.md#integration-points)
3. [QUICK_REFERENCE.md](./QUICK_REFERENCE.md) - Navigation guide

#### Task: Add a new screen/feature
**References:**
1. [APPLICATION_ARCHITECTURE.md - Module Structure](./APPLICATION_ARCHITECTURE.md#module-structure)
2. [APPLICATION_ARCHITECTURE.md - Navigation](./APPLICATION_ARCHITECTURE.md#navigation)
3. [APPLICATION_ARCHITECTURE.md - Data Flow](./APPLICATION_ARCHITECTURE.md#data-flow)

#### Task: Add a new stream source type
**References:**
1. [StreamRepository_API.md - Stream Resolution](./StreamRepository_API.md#stream-resolution)
2. [StreamRepository_API.md - processStreams()](./StreamRepository_API.md#private-helper-methods)
3. [Repository_Architecture.md - Integration Points](./Repository_Architecture.md#integration-points)

#### Task: Improve VOD matching accuracy
**References:**
1. [IptvRepository_API.md - findMovieVodSource()](./IptvRepository_API.md#vod-video-on-demand---movies)
2. [IptvRepository_API.md - Series Resolution System](./IptvRepository_API.md#series-resolution-system)
3. [Repository_Architecture.md - Index Pre-computation](./Repository_Architecture.md#3-index-pre-computation)

#### Task: Implement cache improvements
**References:**
1. [StreamRepository_API.md - Caching Strategy](./StreamRepository_API.md#caching-strategy)
2. [IptvRepository_API.md - Disk Cache](./IptvRepository_API.md#disk-cache)
3. [Repository_Architecture.md - Caching Architecture](./Repository_Architecture.md#caching-architecture)

#### Task: Add anime source support
**References:**
1. [StreamRepository_API.md - resolveEpisodeStreams()](./StreamRepository_API.md#suspend-fun-resolveepisodestreamsimdbid-string-season-int-episode-int--streamresult)
2. [Repository_Architecture.md - Example 2: User Plays TV Episode](./Repository_Architecture.md#example-2-user-plays-tv-episode)

#### Task: Optimize parallel stream fetching
**References:**
1. [StreamRepository_API.md - Performance Characteristics](./StreamRepository_API.md#performance-characteristics)
2. [Repository_Architecture.md - Parallel Execution](./Repository_Architecture.md#1-parallel-execution)

#### Task: Debug IPTV configuration issues
**References:**
1. [IptvRepository_API.md - saveConfig()](./IptvRepository_API.md#suspend-fun-saveconfigm3uurl-string-epgurl-string-xtreamusername-string---xtreampassword-string--)
2. [IptvRepository_API.md - Xtream Codes API Integration](./IptvRepository_API.md#xtream-codes-api-integration)

#### Task: Implement instant episode playback
**References:**
1. [IptvRepository_API.md - Instant Episode Playback](./IptvRepository_API.md#instant-episode-playback-task_17-feature)
2. [Repository_Architecture.md - Instant Next Episode](./Repository_Architecture.md#3-instant-next-episode-continue-watching)

---

## API Method Quick Lookup

### StreamRepository Public Methods

| Method | Purpose | Used In |
|--------|---------|---------|
| `installedAddons: Flow<List<Addon>>` | Observable addon list | PlayerViewModel, SettingsViewModel |
| `addCustomAddon(url, name?)` | Install addon from URL | SettingsViewModel |
| `removeAddon(addonId)` | Remove installed addon | SettingsViewModel |
| `toggleAddon(addonId)` | Enable/disable addon | SettingsViewModel |
| `resolveMovieStreams(imdbId, title, year)` | Fetch movie streams | PlayerViewModel, DetailsViewModel |
| `resolveEpisodeStreams(imdbId, s, e, ...)` | Fetch episode streams | PlayerViewModel, DetailsViewModel |
| `resolveMovieVodOnly(...)` | IPTV-only movie streams | PlayerViewModel (background) |
| `resolveEpisodeVodOnly(...)` | IPTV-only episode streams | PlayerViewModel (background) |
| `resolveStreamForPlayback(stream)` | Final URL resolution | PlayerViewModel |
| `isHttpStreamReachable(stream)` | Verify stream accessibility | PlayerViewModel |
| `fetchSubtitlesForSelectedStream(...)` | Get subtitles | PlayerViewModel |
| `clearStreamCache()` | Clear all caches | SettingsViewModel |

### IptvRepository Public Methods

| Method | Purpose | Used In |
|--------|---------|---------|
| `observeConfig()` | Observable IPTV config | TvViewModel, SettingsViewModel |
| `saveConfig(m3u, epg, user, pass)` | Save IPTV settings | SettingsViewModel |
| `clearConfig()` | Clear IPTV settings | SettingsViewModel |
| `observeFavoriteGroups()` | Observable favorites | TvViewModel |
| `observeFavoriteChannels()` | Observable favorites | TvViewModel, HomeViewModel |
| `toggleFavoriteGroup(name)` | Toggle favorite | TvViewModel |
| `toggleFavoriteChannel(id)` | Toggle favorite | TvViewModel |
| `loadSnapshot(forceReload...)` | Load channels + EPG | TvViewModel, SettingsViewModel |
| `getCachedSnapshotOrNull()` | Get cached snapshot | TvViewModel, HomeViewModel |
| `refreshEpgForChannels(ids)` | Refresh EPG | HomeViewModel |
| `reDeriveCachedNowNext(ids)` | Update now/next | HomeViewModel |
| `findMovieVodSource(...)` | Find VOD movie | StreamRepository |
| `findEpisodeVodSource(...)` | Find VOD episode | StreamRepository |
| `getVodCategories()` | VOD category list | MoviesViewModel |
| `getSeriesCategories()` | Series category list | SeriesViewModel |
| `buildEpisodeUrlFromCache(id, s, e)` | Instant episode URL | PlayerViewModel |
| `storeSeriesContext(tmdb, id, name)` | Save series mapping | PlayerViewModel |
| `getStoredSeriesContext(tmdb)` | Get series mapping | PlayerViewModel |

---

## Data Flow Diagrams

### Stream Resolution Flow
```
User Action (Play Movie/Episode)
          │
          ▼
   DetailsViewModel / PlayerViewModel
          │
          ├──► StreamRepository.resolveMovieStreams() OR
          │    StreamRepository.resolveEpisodeStreams()
          │         │
          │         ├──► Query Stremio Addons (parallel)
          │         │    - Torrentio
          │         │    - MediaFusion
          │         │    - Custom addons
          │         │    - OpenSubtitles (subtitles)
          │         │
          │         └──► IptvRepository.findMovieVodSource() OR
          │              IptvRepository.findEpisodeVodSource()
          │                   │
          │                   ├──► Search VOD catalog (movies)
          │                   │    OR
          │                   └──► SeriesResolver (episodes)
          │
          ├──► Merge results
          ├──► Cache for future use
          │
          └──► Return StreamResult to UI
                    │
                    └──► Display source picker
                         OR
                         Auto-play (instant next episode)
```

### IPTV Configuration Flow
```
User Input (M3U URL + Credentials)
          │
          ▼
   SettingsViewModel.saveIptvConfig()
          │
          ▼
   IptvRepository.saveConfig()
          │
          ├──► Normalize input (Xtream triplet detection)
          ├──► Encrypt credentials (Android Keystore)
          ├──► Save to DataStore
          └──► Invalidate cache
                    │
                    ▼
              TvViewModel observes config change
                    │
                    └──► Load fresh snapshot
```

---

## Common Patterns

### Pattern 1: Observe Repository Data
```kotlin
// ViewModel
init {
    viewModelScope.launch {
        streamRepository.installedAddons.collect { addons ->
            _uiState.update { it.copy(addons = addons) }
        }
    }
}
```

### Pattern 2: Graceful Error Handling
```kotlin
// Repository
try {
    withTimeout(20_000) {
        apiCall()
    }
} catch (e: TimeoutCancellationException) {
    emptyList()  // Return empty, don't fail
} catch (e: Exception) {
    emptyList()
}
```

### Pattern 3: Parallel + Timeout
```kotlin
// StreamRepository
val streams = addons.map { addon ->
    async {
        try {
            withTimeout(TIMEOUT) { queryAddon(addon) }
        } catch (e: Exception) { emptyList() }
    }
}.awaitAll().flatten()
```

### Pattern 4: Profile-Scoped Keys
```kotlin
// IptvRepository
private fun m3uUrlKey() = 
    profileManager.profileStringKey("iptv_m3u_url")

private fun m3uUrlKeyFor(profileId: String) = 
    profileManager.profileStringKeyFor(profileId, "iptv_m3u_url")
```

---

## Performance Guidelines

### DO ✅
- **Use parallel execution** for independent operations
- **Set reasonable timeouts** (20s addons, 45s series resolution)
- **Cache aggressively** (6-hour TTL for stable content)
- **Return early** when cached data is fresh
- **Fail gracefully** (empty list, not exception)
- **Use disk cache** for large datasets (VOD catalogs)

### DON'T ❌
- **Block UI thread** with synchronous network calls
- **Use infinite timeouts** (always set upper bound)
- **Cache forever** (respect TTL, invalidate on config change)
- **Throw exceptions** for expected failures (timeouts, empty results)
- **Load entire catalog** at once (lazy load categories)
- **Store sensitive data** unencrypted

---

## Code Quality Standards

### Documentation Requirements
- **Public methods:** KDoc with @param, @return, usage examples
- **Complex logic:** Inline comments explaining "why", not "what"
- **Data models:** KDoc describing each field's purpose
- **Constants:** Comment explaining the chosen value

### Testing Requirements
- **Unit tests:** Mock external dependencies (API, repository)
- **Integration tests:** Real network calls with test data
- **Performance tests:** Measure cache hit rates, query latencies
- **Edge case tests:** Empty results, timeouts, malformed data

---

## Troubleshooting Guide

### Issue: Streams not appearing
**Check:**
1. Addon enabled? (`StreamRepository.installedAddons`)
2. Cache stale? (`forceRefresh = true`)
3. Network timeout? (Check logs for timeout exceptions)
4. IPTV configured? (`IptvRepository.observeConfig()`)

### Issue: Slow stream resolution
**Check:**
1. How many addons enabled? (Each adds ~20s max)
2. IPTV VOD timeout too long? (Reduce to 3s for append)
3. Cache hit rate? (Should be >70%)
4. Disk cache present? (Check VOD catalog disk cache)

### Issue: Episode matching fails
**Check:**
1. Series title format? (Must include season/episode in provider title)
2. TMDB ID available? (Improves matching accuracy)
3. Episode cache populated? (First episode populates cache)
4. Provider naming convention? (Some providers use non-standard formats)

### Issue: EPG not loading
**Check:**
1. EPG URL correct? (`observeConfig()`)
2. Xtream short EPG supported? (Check logs for base64 decode errors)
3. XMLTV format valid? (SAX parser logs errors)
4. File size too large? (100MB+ takes 30-60s)

---

## Version History

- **v1.0 (March 2026):** Initial documentation
  - StreamRepository API reference
  - IptvRepository API reference
  - Architecture overview

---

## Contributing

When updating these docs:
1. **Keep examples realistic** - Use actual code patterns from the codebase
2. **Include performance numbers** - Real measurements, not guesses
3. **Document "why"** - Explain design decisions, not just "what"
4. **Update all three files** - Keep StreamRepository_API.md, IptvRepository_API.md, and Repository_Architecture.md in sync
5. **Test code snippets** - Verify code examples actually compile

---

## Additional Resources

- [AGENTS.md](../AGENTS.md) - Project-wide development guide
- [.github/copilot-instructions.md](../.github/copilot-instructions.md) - Copilot quick reference
- [build.gradle.kts](../app/build.gradle.kts) - Dependency versions

---

**Last Updated:** March 2026  
**ARVIO Version:** 1.8.2+  
**Maintained By:** ARVIO Development Team
