# Repository Quick Reference Guide

## 🚀 Quick Start

Looking for information on ARVIO? Here's where to start:

### For Developers

**New to the codebase?**
→ Start with [APPLICATION_ARCHITECTURE.md](./APPLICATION_ARCHITECTURE.md) - Complete app overview and system design

**Need repository API reference?**
→ [StreamRepository_API.md](./StreamRepository_API.md) - Stremio addon integration
→ [IptvRepository_API.md](./IptvRepository_API.md) - IPTV/Xtream integration

**Understanding how repositories work together?**
→ [Repository_Architecture.md](./Repository_Architecture.md) - System integration patterns

**Looking for specific info?**
→ [README.md](./README.md) - Comprehensive index with quick lookup tables

### For AI Assistants (Claude, etc.)

These docs are optimized for AI consumption:
- **[APPLICATION_ARCHITECTURE.md](./APPLICATION_ARCHITECTURE.md)** - Complete system understanding with architecture diagrams
- **[Repository_Architecture.md](./Repository_Architecture.md)** - Repository integration patterns
- **API References** - Comprehensive method signatures with parameter types and return values
- **Usage examples** - Real-world integration patterns
- **Performance characteristics** - Actual measurements with optimization notes
- **Error handling patterns** - Graceful degradation strategies
- **Architecture diagrams** - Text/ASCII format for easy parsing

---

## 📁 Documentation Structure

```navigation hub)
├── QUICK_REFERENCE.md            # This file (quick navigation & FAQs)
├── APPLICATION_ARCHITECTURE.md    # Complete app architecture ⭐ START HERE
├── StreamRepository_API.md        # StreamRepository complete API docs
├── IptvRepository_API.md          # IptvRepository complete API docs
└── Repository_Architecture.md     # Repository system design & integration
├── IptvRepository_API.md          # IptvRepository complete API docs
└── Repository_Architecture.md     # System design & integration patterns
```

---

## 🔍 Common Questions
es the appes navigation work?
1. Check [APPLICATION_ARCHITECTURE.md - Navigation](./APPLICATION_ARCHITECTURE.md#navigation)
2. Review [Navigation Flow diagram](./APPLICATION_ARCHITECTURE.md#navigation-flow)
3. Understand [D-pad Navigation Pattern](./APPLICATION_ARCHITECTURE.md#d-pad-navigation-pattern)

### What external services are integrated?
1. See [APPLICATION_ARCHITECTURE.md - External Integrations](./APPLICATION_ARCHITECTURE.md#external-integrations)
2. Review authentication flows for Trakt, Supabase
3. Check API endpoints and rate limits

### How do architecture work?
1. Read [APPLICATION_ARCHITECTURE.md](./APPLICATION_ARCHITECTURE.md) - Complete overview
2. Focus on [MVVM + Clean Architecture section](./APPLICATION_ARCHITECTURE.md#architecture-pattern)
3. Review [Data Flow examples](./APPLICATION_ARCHITECTURE.md#data-flow)

### What screens and ViewModels exist?
1. See [APPLICATION_ARCHITECTURE.md - Core Components](./APPLICATION_ARCHITECTURE.md#core-components)
2. Check [Module Structure](./APPLICATION_ARCHITECTURE.md#module-structure) for file locations
3. Review [Feature Modules](./APPLICATION_ARCHITECTURE.md#feature-modules) for detailed explanations

### How do
### APPLICATION_ARCHITECTURE.md - Continue Watching Feature](./APPLICATION_ARCHITECTURE.md#3-continue-watching)
2. [IptvRepository_API.md - Instant Episode Playback](./IptvRepository_API.md#instant-episode-playback-task_17-feature)
3. [Repository_Architecture.md - Instant Next Episode](./Repository_Architecture.md#3-instant-next-episode-continue-watching)

### What background tasks run?
1. [APPLICATION_ARCHITECTURE.md - Background Processing](./APPLICATION_ARCHITECTURE.md#background-processing)
2. Review WorkManager tasks (TraktSyncWorker, IptvRefreshWorker)
3. Check scheduling constraints and frequencies

---
[APPLICATION_ARCHITECTURE.md](./APPLICATION_ARCHITECTURE.md) for system overview
   - Read relevant API docs (StreamRepository, IptvRepository)
## 🛠️ Development Workflows

### Understanding the Codebase (First Time)

1. **Read architecture overview**
   - [APPLICATION_ARCHITECTURE.md](./APPLICATION_ARCHITECTURE.md) - Complete system understanding
   - Focus on Technology Stack and Architecture Pattern sections

2. **Explore module structure**
   - Review [Module Structure diagram](./APPLICATION_ARCHITECTURE.md#module-structure)
   - Check file organization in `app/src/main/kotlin/com/arflix/tv/`

3. **Study data flow**
   - Follow [Example: User Plays a Movie](./APPLICATION_ARCHITECTURE.md#example-user-plays-a-movie)
   - Understand ViewModel → Repository → API → UI pattern

4. **Review feature implementations**
   - Read [Feature Modules](./APPLICATION_ARCHITECTURE.md#feature-modules) for deep-dives
   - Check specific features (Live TV, Stremio, Continue Watching, etc.)ng work?
1. Check [IptvRepository_API.md - findMovieVodSource](./IptvRepository_API.md#suspend-fun-findmovievod sourcestring-int-string-int-bool--liststre amsource)
2. Review matching strategy details in section "VOD (Video On Demand) - Movies"
3. See [Repository_Architecture.md - Movie Stream Resolution](./Repository_Architecture.md#1-movie-stream-resolution)

### How is caching implemented?
1. Overview: [Repository_Architecture.md - Caching Architecture](./Repository_Architecture.md#caching-architecture)
2. StreamRepository specifics: [StreamRepository_API.md - Caching Strategy](./StreamRepository_API.md#caching-strategy)
3. IptvRepository disk cache: [IptvRepository_API.md - Disk Cache](./IptvRepository_API.md#disk-cache)

### What's the anime resolution flow?
1. [StreamRepository_API.md - resolveEpisodeStreams](./StreamRepository_API.md#suspend-fun-resolveepisodestreamsimdbid-string-season-int-episode-int--streamresult)
2. Look for "Anime Support" section with 5-tier fallback explanation

### How does instant next-episode work?
1. [IptvRepository_API.md - Instant Episode Playback](./IptvRepository_API.md#instant-episode-playback-task_17-feature)
2. [Repository_Architecture.md - Instant Next Episode](./Repository_Architecture.md#3-instant-next-episode-continue-watching)
Review [APPLICATION_ARCHITECTURE.md - Stremio Integration](./APPLICATION_ARCHITECTURE.md#2-stremio-integration)
2. Check StreamRepository logs for timeout/error messages
3. Verify addon enabled: StreamRepository.installedAddons
4. Test cache clear: StreamRepository.clearStreamCache()
5. Check IPTV config: IptvRepository.observeConfig()
6
### Adding a New Feature

1. **Understand current implementation**
   Read [APPLICATION_ARCHITECTURE.md - Live TV Feature](./APPLICATION_ARCHITECTURE.md#1-live-tv-iptv)
2. Verify config format: IptvRepository_API.md - saveConfig()
3. Check M3U parser logs
4. Validate EPG URL (Xtream vs standard XMLTV)
5. Test with forcePlaylistReload=true
6  - Identify affected methods
   - Consider cache invalidation
   - Plan error handling strategy

3. **Implement**
   - Follow existing patterns (see "Common Patterns" in README.md)
   - Maintain graceful degradation philosophy
   - Add comprehensive KDoc to public methods

4. **Test**
   - Unit tests with mocked dependencies
   - Integration tests with real data
   - Performance validation

5. **Document**
   - Update method KDoc
   - Add to relevant API doc file if significant
   - Update architecture doc if integration changed

### Debugging Issues

**Stream resolution problems:**
```
1. Check StreamRepository logs for timeout/error messages
2. Verify addon enabled: StreamRepository.installedAddons
3. Test cache clear: StreamRepository.clearStreamCache()
4. Check IPTV config: IptvRepository.observeConfig()
5. Force refresh: forceRefresh=true parameter
```

**IPTV loading issues:**
```
1. Verify config format: IptvRepository_API.md - saveConfig()
2. Check M3U parser logs
3. Validate EPG URL (Xtream vs standard XMLTV)
4. Test with forcePlaylistReload=true
5. Review timeout settings (60s playlist, 90s EPG)
```

**Performance problems:**
```
1. Check cache hit rate in logs
2. Verify disk cache exists (VOD/Series catalogs)
3. Review timeout values (too high = slow)
4. Check parallel execution (all addons should query simultaneously)
5. Profile with Android Studio profiler
```

---

## 📊 Key Metrics

### StreamRepository Performance Targets
- **Cache hit rate:** >70%
- **Addon response time:** <15s average
- **Stream resolution:** <20s total
- **Instant cache hit:** <50ms

### IptvRepository Performance Targets
- **Playlist load:** <30s for 10k channels
- **EPG parse:** <60s for 100MB EPG
- **VOD match:** <5ms (cached)
- **Series resolution:** <10s first episode, <50ms subsequent

### Combined Targets
- **Time to first stream:** <20s
- **Instant next-episode:** >80% success rate

---
Complete app architecture:** [APPLICATION_ARCHITECTURE.md](./APPLICATION_ARCHITECTURE.md) ⭐
- **
## 🔗 Related Documentation

- **Project-wide guide:** [../AGENTS.md](../AGENTS.md)
- **Quick start:** [../.github/copilot-instructions.md](../.github/copilot-instructions.md)
- **Dependencies:** [../app/build.gradle.kts](../app/build.gradle.kts)

---

## 💡 Pro Tips

### For Fast APPLICATION_            │
           │ ARCHITECTURE.md         │ ◄── Complete app overview
           │                         │
           └─────────────────────────┘
                        │
        ┌───────────────┴───────────────┐
        │                               │
        ▼                               ▼
┌──────────────────┐         ┌──────────────────┐
│QUICK_REFERENCE.md│         │    README.md     │
│  (this file)     │         │ (master index)   │
└──────────────────┘         └──────────────────┘
        │                               │
        ├──────────┬────────────────────┤
        │          │                    │
        ▼          ▼                    ▼
┌─────────────┐  ┌──────────────┐  ┌─────────────┐
│StreamRepo   │  │  IptvRepo    │  │Repository   │
│   API       │  │    API       │  │Architecture
---

## 🗺️ Documentation Map

```
                    START HERE
                        │
                        ▼
           ┌─────────────────────────┐
           │   QUICK_REFERENCE.md    │ ◄── You are here
           │  (navigation & FAQs)    │
           └─────────────────────────┘
                        │
        ┌───────────────┴───────────────┐
        │                               │
        ▼                               ▼
┌──────────────────┐         ┌──────────────────┐
│    README.md     │         │ Architecture.md  │
│ (master index)   │         │ (system design)  │
└──────────────────┘         └──────────────────┘
        │                               │
        ├──────────┬────────────────────┤
        │          │                    │
        ▼          ▼                    ▼
┌─────────────┐  ┌──────────────┐  ┌─────────────┐
│StreamRepo   │  │  IptvRepo    │  │ Integration │
│   API       │  │    API       │  │  Examples   │
└─────────────┘  └──────────────┘  └─────────────┘
```

---

## 📝 Documentation Standards

### What to Include in KDoc
✅ **Method purpose** (one-line summary)  
✅ **Parameter descriptions** with types and constraints  
✅ **Return value** description  
✅ **"Used In" section** showing real caller context  
✅ **Performance characteristics** (timeouts, cache behavior)  
✅ **Error handling** approach  
✅ **Side effects** (cache invalidation, config changes)  

### What NOT to Include
❌ **Implementation details** (those belong in inline comments)  
❌ **TODO notes** (use GitHub issues instead)  
❌ **Obvious information** ("This function returns a list of X" when signature shows `List<X>`)  
❌ **Outdated information** (update or remove, don't leave stale docs)  

---

**Last Updated:** March 2026  
**ARVIO Version:** 1.8.2+
