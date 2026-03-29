## Plan: Offline Downloads (Mobile/touch only)

TL;DR - Add a mobile-only offline download flow: a `Download` button in the `Details` screen play row that opens the existing stream picker and enqueues a download (media + metadata + assets). Add a `Downloads` tab to `Watchlist` with two sections (Movies, Series). Persist downloads in a local DB, manage downloads via WorkManager/ExoPlayer download APIs, and prefer local files in `PlayerViewModel` so playback works offline (airplane mode).

**Steps**
1. Data model & repo
- Add a `DownloadEntity` (Room) to store: `id`, `tmdbId`, `mediaType` (MOVIE/SERIES), `season`, `episode`, `title`, `posterPath`, `localUri`, `streamInfoJson`, `fileSize`, `mimeType`, `status` (QUEUED/DOWNLOADING/PAUSED/COMPLETED/FAILED), `progress`, `downloadedAt`.
- Implement `DownloadsRepository` with methods: `enqueueDownload(stream, metadata)`, `getDownloads()`, `getByMediaId(...)`, `deleteDownload(id)`, `observeProgress(id)`.

2. Download manager & background worker
- Implement download workers using `WorkManager` and/or ExoPlayer's `DownloadManager` (use ExoPlayer for HLS/DASH manifests; use OkHttp for progressive downloads).
- `DownloadWorker` should: download stream to app files dir, download poster and subtitles if requested, update `DownloadsRepository` progress/status, and run as a foreground work when necessary.

3. UI: Details screen
- Add a `Download` button in the play-row (next to Play) for movies. Gate visibility to mobile/touch only via existing device-type or window-size check.
- For series, add a download button for each episode in the episodes list.
- On tap, reuse the existing stream picker UI used by `DetailsScreen` and `DetailsViewModel` to select the source/quality.
- On confirm, call `DownloadsRepository.enqueueDownload(...)` which creates a DB entry and schedules the worker. Show immediate UI feedback (snackbar/toast) and a progress indicator in the details screen.

4. UI: Watchlist downloads tab
- Update `WatchlistScreen` to use tabs like content screens: `Watchlist` and `Downloads`.
- The `Downloads` tab has two sections: `Movies` and `Series`. Use the same media card composable used by Watchlist to render items. Source data from `DownloadsRepository` grouped by media type.
- Clicking a media card navigates to `DetailsScreen`. For series cards or when opened from Downloads, the `DetailsScreen` should show only downloaded episodes (see question below for exact behavior scope).

5. DetailsScreen behavior for Series & Movies
- For series, when showing a details page that has downloads, filter episodes to only the downloaded episode entries (per requirement).
- For movies, show download state and actions (play local / delete / view progress).

6. Playback
- Update `PlayerViewModel` to prefer local sources: add `getPlayableSource(tmdbId, mediaType, season?, episode?)` that returns the local file URI if present, else the network URL.
- Integrate local file playback with ExoPlayer; if using ExoPlayer offline download manager, use its `DownloadIndex`/`DownloadManager` to obtain a `MediaSource`.
- Preserve and update resume positions in the existing `WatchHistoryRepository` / `TraktRepository`.

7. Metadata & assets
- Persist metadata (title, overview, poster path) and download assets (poster image, subtitles) alongside media file.
- Make UI use local poster when available.

8. Storage & housekeeping
- Provide delete UI and confirm dialogs.
- Implement storage quota checks and an optional auto-prune policy.

9. Tests & verification
- Unit tests for `DownloadsRepository` and `DownloadWorker`.
- Integration / instrumentation tests: download movie/episode, switch to airplane mode, and verify playback from local file without network.

10. Docs & migration
- Add README notes and DB migration (bump schema).
- Add any needed manifest `uses-permission` only if external storage is used.

**Relevant files (top matches to inspect & update)**
- `app/src/main/kotlin/com/arflix/tv/ui/screens/details/DetailsScreen.kt` — entrypoint for Play UI and stream picker (add Download button).
- `app/src/main/kotlin/com/arflix/tv/ui/screens/details/DetailsViewModel.kt` — resolves streams and handles prefetch logic (hook to enqueue downloads).
- `app/src/main/kotlin/com/arflix/tv/ui/screens/watchlist/WatchlistScreen.kt` — add tabs and Downloads section.
- `app/src/main/kotlin/com/arflix/tv/ui/screens/watchlist/WatchlistViewModel.kt` — feed downloads data into UI.
- `app/src/main/kotlin/com/arflix/tv/ui/screens/player/PlayerScreen.kt` — ExoPlayer integration; adapt to local playback source.
- `app/src/main/kotlin/com/arflix/tv/ui/screens/player/PlayerViewModel.kt` — prefer localUri for playback.
- `app/src/main/kotlin/com/arflix/tv/data/repository/StreamRepository.kt` — source resolution logic; reuse to obtain direct stream URL and filename metadata.
- `app/src/main/kotlin/com/arflix/tv/data/repository/IptvRepository.kt` — VOD/stream source lookup used in stream resolution.
- `app/src/main/kotlin/com/arflix/tv/data/repository/TraktRepository.kt` — existing continue-watching and resume position handling; update to integrate with downloaded content.

**Verification**
1. Download flow: choose stream in Details -> enqueue -> worker downloads media + poster + subtitles -> status updates and progress shown.
2. Downloads tab: shows Movies and Series sections with downloaded items; clicking navigates to details.
3. Details: For series, only downloaded episodes are shown (or filtered view) as required.
4. Offline playback: With airplane mode enabled, play a downloaded movie and a downloaded episode successfully; resume position is saved.
5. Persistence: Downloads survive app restart and show correct statuses.
6. Storage: deletion removes file + DB entry; UI updates.

**Decisions / Assumptions**
- Default storage: app internal files dir (no external storage permission). We can switch to external if requested.
- Scope: Initially support non-DRM progressive and HLS/DASH (via ExoPlayer). DRM-offline license flows (Widevine) are out of scope unless confirmed.
- Background downloads: use `WorkManager` with foreground execution for long-running jobs.
- Use Room DB for downloads metadata for reliability across restarts.

**Further Considerations**
1. DRM: supporting Widevine offline licenses adds complexity (license acquisition, secure storage). If needed, plan separately.
2. Quality & size selection: present filesize estimates or quality options in stream picker.
3. Subtitles & audio: allow user to include selected subtitles/audio tracks; store them locally.

**Implementation Prompt (copy/paste for an engineer or LLM)**
Implement a mobile-only offline downloads feature for the Android app.

Context:
- The app already resolves playable streams in `StreamRepository` and shows a stream picker in `DetailsScreen` / `DetailsViewModel`.
- Playback uses ExoPlayer in `PlayerScreen` / `PlayerViewModel`, and continue-watching data is persisted with `TraktRepository`.

Requirements:
- Add a `Download` button in the `Details` screen play-row (visible only on mobile/touch devices). When tapped, open the existing stream picker and start a download of the selected stream and its metadata (title, poster, subtitles).
- Persist downloads in a local DB and implement download workers (WorkManager + ExoPlayer download APIs or OkHttp for progressive downloads). Downloads must continue reliably across app restarts.
- Add a `Downloads` tab to the `Watchlist` screen with two sections: `Movies` and `Series`. Use existing media card UI. Clicking an item navigates to `Details`.
- For Series details: show only episodes that are downloaded (filter by downloads DB entries).
- Playback must prefer the downloaded local file when available so playback works offline (airplane mode).
- Provide delete functionality and progress/status indicators.

Acceptance criteria:
- Download button present on mobile devices and triggers the stream picker -> download flow.
- Downloaded items appear under `Watchlist -> Downloads` grouped by Movies/Series.
- Offline playback works without any internet connection.
- Downloads persist and can be deleted by the user.

Files to update: see the Relevant files section above.

---

**Clarifying questions (I’ll ask these interactively next)**
1. Target devices: confirm Android phones only or phones+tablets?
2. DRM: do we need to support DRM offline licenses (Widevine)?
3. Storage: prefer internal app storage (no extra permission) or external/shared storage?
4. Background downloads: should downloads continue if app is killed (foreground service / WorkManager)?
5. Quality & size: allow user to select quality (Low/Med/High) and show estimated size?
6. Subtitles/audio: include subtitles and extra audio tracks in downloads?
7. Series: should we support season-level "Download all" or only per-episode downloads?
8. Auto-cleanup: should we auto-delete old downloads (policy) or keep manual only?
