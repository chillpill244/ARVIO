package com.arflix.tv.ui.screens.details

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arflix.tv.data.model.CastMember
import com.arflix.tv.data.model.Episode
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.model.PersonDetails
import com.arflix.tv.data.model.Review
import com.arflix.tv.data.model.StreamSource
import com.arflix.tv.data.model.Subtitle
import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.repository.MediaRepository
import com.arflix.tv.data.repository.ProfileManager
import com.arflix.tv.data.repository.StreamRepository
import com.arflix.tv.data.repository.TraktRepository
import com.arflix.tv.data.repository.WatchHistoryRepository
import kotlinx.coroutines.coroutineScope
import com.arflix.tv.data.repository.WatchlistRepository
import com.arflix.tv.util.Constants
import com.arflix.tv.util.settingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailsUiState(
    val isLoading: Boolean = true,
    val item: MediaItem? = null,
    val imdbId: String? = null,  // Real IMDB ID for stream resolution
    val tvdbId: Int? = null,     // TVDB ID for Kitsu anime mapping
    val logoUrl: String? = null,
    val trailerKey: String? = null,
    val episodes: List<Episode> = emptyList(),
    val totalSeasons: Int = 1,
    val currentSeason: Int = 1,
    // For IPTV series with non-contiguous seasons (e.g., only S2, S3)
    val availableSeasons: List<Int> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val similar: List<MediaItem> = emptyList(),
    val similarLogoUrls: Map<String, String> = emptyMap(),
    val reviews: List<Review> = emptyList(),
    val error: String? = null,
    // Person modal
    val showPersonModal: Boolean = false,
    val selectedPerson: PersonDetails? = null,
    val isLoadingPerson: Boolean = false,
    // Streams
    val streams: List<StreamSource> = emptyList(),
    val subtitles: List<Subtitle> = emptyList(),
    val isLoadingStreams: Boolean = false,
    val hasStreamingAddons: Boolean = true,
    val isInWatchlist: Boolean = false,
    // Toast
    val toastMessage: String? = null,
    val toastType: ToastType = ToastType.INFO,
    // Genre names
    val genres: List<String> = emptyList(),
    val language: String? = null,
    // Budget (movies only)
    val budget: String? = null,
    // Show status
    val showStatus: String? = null,
    // Initial positions for Continue Watching navigation
    val initialEpisodeIndex: Int = 0,
    val initialSeasonIndex: Int = 0,
    // Season progress: Map<seasonNumber, Pair<watchedCount, totalCount>>
    val seasonProgress: Map<Int, Pair<Int, Int>> = emptyMap(),
    val playSeason: Int? = null,
    val playEpisode: Int? = null,
    val playLabel: String? = null,
    val playPositionMs: Long? = null,
    val autoPlaySingleSource: Boolean = true,
    val autoPlayMinQuality: String = "Any",
    // IPTV Series matching for instant next episode (TASK_17)
    val iptvSeriesMatches: List<com.arflix.tv.data.model.IptvSeriesMatch> = emptyList(),
    val iptvSeriesContext: com.arflix.tv.data.model.IptvSeriesContext? = null,
    val isLoadingIptvMatches: Boolean = false
)

private data class PlayTarget(
    val season: Int? = null,
    val episode: Int? = null,
    val label: String,
    val positionMs: Long? = null
)

private data class SeasonProgressResult(
    val progress: Map<Int, Pair<Int, Int>>,
    val hasWatched: Boolean,
    val nextUnwatched: Pair<Int, Int>?
)

private data class ResumeInfo(
    val season: Int? = null,
    val episode: Int? = null,
    val label: String,
    val positionMs: Long
)

// TMDB Genre mappings
private val movieGenres = mapOf(
    28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy",
    80 to "Crime", 99 to "Documentary", 18 to "Drama", 10751 to "Family",
    14 to "Fantasy", 36 to "History", 27 to "Horror", 10402 to "Music",
    9648 to "Mystery", 10749 to "Romance", 878 to "Sci-Fi", 10770 to "TV Movie",
    53 to "Thriller", 10752 to "War", 37 to "Western"
)

private val tvGenres = mapOf(
    10759 to "Action & Adventure", 16 to "Animation", 35 to "Comedy",
    80 to "Crime", 99 to "Documentary", 18 to "Drama", 10751 to "Family",
    10762 to "Kids", 9648 to "Mystery", 10763 to "News", 10764 to "Reality",
    10765 to "Sci-Fi & Fantasy", 10766 to "Soap", 10767 to "Talk",
    10768 to "War & Politics", 37 to "Western"
)

private val languages = mapOf(
    "en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German",
    "it" to "Italian", "pt" to "Portuguese", "ja" to "Japanese", "ko" to "Korean",
    "zh" to "Chinese", "hi" to "Hindi", "ru" to "Russian", "ar" to "Arabic",
    "nl" to "Dutch", "sv" to "Swedish", "pl" to "Polish", "tr" to "Turkish",
    "th" to "Thai", "vi" to "Vietnamese", "id" to "Indonesian", "tl" to "Tagalog"
)

/**
 * Format budget number to human-readable string
 */
private fun formatBudget(budget: Long): String {
    return when {
        budget >= 1_000_000_000 -> "$${budget / 1_000_000_000.0}B"
        budget >= 1_000_000 -> "$${budget / 1_000_000}M"
        budget >= 1_000 -> "$${budget / 1_000}K"
        else -> "$$budget"
    }
}

enum class ToastType {
    SUCCESS, ERROR, INFO
}

@HiltViewModel
class DetailsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val profileManager: ProfileManager,
    private val traktRepository: TraktRepository,
    private val streamRepository: StreamRepository,
    private val tmdbApi: TmdbApi,
    private val watchHistoryRepository: WatchHistoryRepository,
    private val watchlistRepository: WatchlistRepository,
    private val iptvRepository: com.arflix.tv.data.repository.IptvRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private var currentMediaType: MediaType = MediaType.MOVIE
    private var currentMediaId: Int = 0
    private var vodAppendJob: kotlinx.coroutines.Job? = null
    private var loadStreamsJob: kotlinx.coroutines.Job? = null
    private var loadStreamsRequestId: Long = 0L
    /** Set to true after loadDetails() child coroutines finish populating episodes/seasons. */
    @Volatile private var initialLoadComplete = false
    private fun autoPlaySingleSourceKey() = profileManager.profileBooleanKey("auto_play_single_source")
    private fun autoPlayMinQualityKey() = profileManager.profileStringKey("auto_play_min_quality")

    private fun isBlankRating(value: String): Boolean {
        return value.isBlank() || value == "0.0" || value == "0"
    }

    private fun normalizeAutoPlayMinQuality(raw: String?): String {
        return when (raw?.trim()?.lowercase()) {
            "any" -> "Any"
            "720p", "hd" -> "720p"
            "1080p", "fullhd", "fhd" -> "1080p"
            "4k", "2160p", "uhd" -> "4K"
            else -> "Any"
        }
    }

    private fun mergeItem(primary: MediaItem, fallback: MediaItem?): MediaItem {
        if (fallback == null) return primary
        return primary.copy(
            title = primary.title.ifBlank { fallback.title },
            subtitle = primary.subtitle.ifBlank { fallback.subtitle },
            overview = primary.overview.ifBlank { fallback.overview },
            year = primary.year.ifBlank { fallback.year },
            releaseDate = primary.releaseDate ?: fallback.releaseDate,
            rating = primary.rating.ifBlank { fallback.rating },
            duration = primary.duration.ifBlank { fallback.duration },
            imdbRating = if (isBlankRating(primary.imdbRating)) fallback.imdbRating else primary.imdbRating,
            tmdbRating = if (isBlankRating(primary.tmdbRating)) fallback.tmdbRating else primary.tmdbRating,
            image = primary.image.ifBlank { fallback.image },
            backdrop = primary.backdrop ?: fallback.backdrop,
            genreIds = if (primary.genreIds.isEmpty()) fallback.genreIds else primary.genreIds,
            originalLanguage = primary.originalLanguage ?: fallback.originalLanguage,
            isOngoing = primary.isOngoing || fallback.isOngoing,
            totalEpisodes = primary.totalEpisodes ?: fallback.totalEpisodes,
            watchedEpisodes = primary.watchedEpisodes ?: fallback.watchedEpisodes,
            budget = primary.budget ?: fallback.budget,
            revenue = primary.revenue ?: fallback.revenue,
            status = primary.status ?: fallback.status,
            imdbId = primary.imdbId ?: fallback.imdbId,
            vodStreams = primary.vodStreams ?: fallback.vodStreams,
            iptvSeriesId = primary.iptvSeriesId ?: fallback.iptvSeriesId
        )
    }

    fun loadDetails(mediaType: MediaType, mediaId: Int, initialSeason: Int? = null, initialEpisode: Int? = null) {
        currentMediaType = mediaType
        currentMediaId = mediaId
        initialLoadComplete = false
        vodAppendJob?.cancel()

        viewModelScope.launch {
            try {
                val prefs = context.settingsDataStore.data.first()
                val autoPlaySingleSource = prefs[autoPlaySingleSourceKey()] ?: true
                val autoPlayMinQuality = normalizeAutoPlayMinQuality(prefs[autoPlayMinQualityKey()])
                val previousState = _uiState.value
                val previousMatches = previousState.item?.id == mediaId &&
                    previousState.item?.mediaType == mediaType
                val seasonToLoad = initialSeason ?: 1
                val previousItem = _uiState.value.item?.takeIf {
                    it.id == mediaId && it.mediaType == mediaType
                }
                val cachedItem = mediaRepository.getCachedItem(mediaType, mediaId)
                val initialItem = cachedItem ?: previousItem
                val cachedTotalSeasons = if (mediaType == MediaType.TV) {
                    initialItem?.totalEpisodes?.coerceAtLeast(1) ?: 1
                } else {
                    1
                }

                // Check if we have iptvSeriesId - if so, load from IPTV directly
                val iptvSeriesId = initialItem?.iptvSeriesId?.toIntOrNull()
                if (iptvSeriesId != null && mediaType == MediaType.TV) {
                    loadDetailsFromIptv(
                        iptvSeriesId = iptvSeriesId,
                        initialItem = initialItem,
                        initialSeason = initialSeason,
                        initialEpisode = initialEpisode,
                        autoPlaySingleSource = autoPlaySingleSource,
                        autoPlayMinQuality = autoPlayMinQuality
                    )
                    return@launch
                }

                // Check if we have iptvMovieId - if so, load from VOD directly
                val iptvMovieId = initialItem?.iptvMovieId?.toIntOrNull()
                if (iptvMovieId != null && mediaType == MediaType.MOVIE) {
                    loadDetailsFromVod(
                        iptvMovieId = iptvMovieId,
                        initialItem = initialItem,
                        autoPlaySingleSource = autoPlaySingleSource,
                        autoPlayMinQuality = autoPlayMinQuality
                    )
                    return@launch
                }

                _uiState.value = DetailsUiState(
                    isLoading = initialItem == null,
                    item = initialItem,
                    currentSeason = seasonToLoad,
                    totalSeasons = cachedTotalSeasons,
                    playSeason = initialSeason,
                    playEpisode = initialEpisode,
                    autoPlaySingleSource = autoPlaySingleSource,
                    autoPlayMinQuality = autoPlayMinQuality
                )

                val itemDeferred = async {
                    if (mediaType == MediaType.TV) {
                        mediaRepository.getTvDetails(mediaId)
                    } else {
                        mediaRepository.getMovieDetails(mediaId)
                    }
                }
                // Load supporting data in parallel
                val logoDeferred = async { mediaRepository.getLogoUrl(mediaType, mediaId) }
                val trailerDeferred = async { mediaRepository.getTrailerKey(mediaType, mediaId) }
                val castDeferred = async { mediaRepository.getCast(mediaType, mediaId) }
                val similarDeferred = async { mediaRepository.getSimilar(mediaType, mediaId) }
                val watchlistDeferred = async { watchlistRepository.isInWatchlist(mediaType, mediaId) }
                val reviewsDeferred = async { mediaRepository.getReviews(mediaType, mediaId) }

                // Fetch real IMDB ID and TVDB ID from TMDB external_ids endpoint
                val externalIdsDeferred = async { resolveExternalIds(mediaType, mediaId) }
                val resumeDeferred = async { fetchResumeInfo(mediaId, mediaType) }

                // For TV shows, also load episodes
                val episodesDeferred = if (mediaType == MediaType.TV) {
                    async { mediaRepository.getSeasonEpisodes(mediaId, seasonToLoad) }
                } else null

                // For TV shows, fetch season progress (watched/total per season)
                val seasonProgressDeferred = if (mediaType == MediaType.TV) {
                    async { fetchSeasonProgress(mediaId) }
                } else null

                val item = runCatching { itemDeferred.await() }.getOrNull() ?: initialItem
                if (item == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load details"
                    )
                    return@launch
                }
                val mergedItem = mergeItem(item, initialItem)

                // Get total seasons for TV shows (stored in totalEpisodes field)
                val totalSeasons = if (mediaType == MediaType.TV) {
                    mergedItem.totalEpisodes?.coerceAtLeast(1) ?: 1
                } else 1

                // Map genre IDs to names
                val genreMap = if (mediaType == MediaType.TV) tvGenres else movieGenres
                val genreNames = mergedItem.genreIds.mapNotNull { genreMap[it] }.take(4)

                // Get language name
                val languageName = mergedItem.originalLanguage?.let { languages[it] ?: it.uppercase() }

                // Format budget for movies
                val budgetDisplay = if (mediaType == MediaType.MOVIE && mergedItem.budget != null && mergedItem.budget > 0) {
                    formatBudget(mergedItem.budget)
                } else null

                // Get show status
                val showStatus = if (mediaType == MediaType.TV) mergedItem.status else null

                // Initialize watched cache (works for both Trakt and non-Trakt Cloud profiles)
                traktRepository.initializeWatchedCache()

                // Check if item is watched (for movies, check Trakt; for TV, check if started)
                val isWatched = if (mediaType == MediaType.MOVIE) {
                    traktRepository.isMovieWatched(mediaId)
                } else {
                    // For TV shows, check if any episode is watched
                    traktRepository.hasWatchedEpisodes(mediaId)
                }
                val itemWithWatchedStatus = mergedItem.copy(isWatched = isWatched)

                // Compute resume info and play target BEFORE setting baseState
                // to avoid race condition where baseState overwrites play target from launch blocks
                val resumeInfo = runCatching { resumeDeferred.await() }.getOrNull()
                val seasonProgressResultForPlayTarget = runCatching { seasonProgressDeferred?.await() }.getOrNull()
                val playTarget = buildPlayTarget(mediaType, seasonProgressResultForPlayTarget, resumeInfo)

                val baseState = _uiState.value.copy(
                    isLoading = false,
                    item = itemWithWatchedStatus,
                    totalSeasons = totalSeasons,
                    currentSeason = seasonToLoad,
                    genres = genreNames,
                    language = languageName,
                    budget = budgetDisplay,
                    showStatus = showStatus,
                    // Set play target immediately to avoid override by async launch blocks
                    playSeason = playTarget?.season ?: _uiState.value.playSeason,
                    playEpisode = playTarget?.episode ?: _uiState.value.playEpisode,
                    playLabel = playTarget?.label ?: _uiState.value.playLabel,
                    playPositionMs = playTarget?.positionMs ?: _uiState.value.playPositionMs
                )
                
                // If vodStreams are pre-populated in the item, use them immediately
                // This avoids redundant stream fetching
                val stateWithStreams = if (!itemWithWatchedStatus.vodStreams.isNullOrEmpty() && 
                    mediaType == MediaType.MOVIE) {
                    baseState.copy(streams = itemWithWatchedStatus.vodStreams)
                } else {
                    baseState
                }
                
                _uiState.value = stateWithStreams

                val requestMediaId = mediaId
                val requestMediaType = mediaType
                fun isCurrentRequest(): Boolean {
                    return currentMediaId == requestMediaId && currentMediaType == requestMediaType
                }
                fun updateState(block: (DetailsUiState) -> DetailsUiState) {
                    if (!isCurrentRequest()) return
                    _uiState.value = block(_uiState.value)
                }

                // Calculate initial season index (0-based)
                val initialSeasonIndex = (seasonToLoad - 1).coerceAtLeast(0)
                updateState { it.copy(initialSeasonIndex = initialSeasonIndex) }

                launch {
                    val externalIds = runCatching { externalIdsDeferred.await() }.getOrNull()
                    val imdbId = externalIds?.imdbId
                    val tvdbId = externalIds?.tvdbId
                    if (!imdbId.isNullOrBlank()) {
                        mediaRepository.cacheImdbId(mediaType, mediaId, imdbId)
                        updateState { state -> state.copy(imdbId = imdbId, tvdbId = tvdbId) }
                    } else if (tvdbId != null) {
                        updateState { state -> state.copy(tvdbId = tvdbId) }
                    }
                }

                launch {
                    val logoUrl = runCatching { logoDeferred.await() }.getOrNull()
                    if (logoUrl != null) {
                        updateState { state -> state.copy(logoUrl = logoUrl) }
                    }
                }

                launch {
                    val trailerKey = runCatching { trailerDeferred.await() }.getOrNull()
                    if (trailerKey != null) {
                        updateState { state -> state.copy(trailerKey = trailerKey) }
                    }
                }

                launch {
                    val cast = runCatching { castDeferred.await() }.getOrNull()
                    if (!cast.isNullOrEmpty()) {
                        updateState { state -> state.copy(cast = cast) }
                    }
                }

                launch {
                    val similar = runCatching { similarDeferred.await() }.getOrNull()
                    if (!similar.isNullOrEmpty()) {
                        val logos = similar.take(20).map { item ->
                            async {
                                val key = "${item.mediaType}_${item.id}"
                                val logo = runCatching {
                                    mediaRepository.getLogoUrl(item.mediaType, item.id)
                                }.getOrNull()
                                if (logo.isNullOrBlank()) null else key to logo
                            }
                        }.mapNotNull { runCatching { it.await() }.getOrNull() }.toMap()
                        updateState { state ->
                            state.copy(
                                similar = similar,
                                similarLogoUrls = logos
                            )
                        }
                    }
                }

                launch {
                    val reviews = runCatching { reviewsDeferred.await() }.getOrNull()
                    if (!reviews.isNullOrEmpty()) {
                        updateState { state -> state.copy(reviews = reviews) }
                    }
                }

                launch {
                    val episodes = runCatching { episodesDeferred?.await() }.getOrNull()
                    if (!episodes.isNullOrEmpty()) {
                        // Decorate episodes with watched status from cache
                        val watchedKeys = runCatching {
                            traktRepository.getWatchedEpisodesForShow(mediaId)
                        }.getOrDefault(emptySet())
                        val decoratedEpisodes = if (watchedKeys.isNotEmpty()) {
                            episodes.map { ep ->
                                val key = "show_tmdb:$mediaId:${ep.seasonNumber}:${ep.episodeNumber}"
                                if (watchedKeys.contains(key)) ep.copy(isWatched = true) else ep
                            }
                        } else episodes

                        val initialEpisodeIndex = if (initialEpisode != null) {
                            decoratedEpisodes.indexOfFirst { it.episodeNumber == initialEpisode }.coerceAtLeast(0)
                        } else 0
                        updateState { state ->
                            state.copy(
                                episodes = decoratedEpisodes,
                                initialEpisodeIndex = initialEpisodeIndex
                            )
                        }
                    }
                    initialLoadComplete = true
                }

                launch {
                    val isInWatchlist = runCatching { watchlistDeferred.await() }.getOrDefault(false)
                    updateState { state -> state.copy(isInWatchlist = isInWatchlist) }
                }

                launch {
                    val seasonProgressResult = runCatching { seasonProgressDeferred?.await() }.getOrNull()
                    val seasonProgress = seasonProgressResult?.progress ?: emptyMap()
                    val resolvedTotalSeasons = if (mediaType == MediaType.TV) {
                        maxOf(baseState.totalSeasons, seasonProgress.keys.maxOrNull() ?: 0, 1)
                    } else {
                        baseState.totalSeasons
                    }
                    updateState { state ->
                        state.copy(
                            seasonProgress = seasonProgress,
                            totalSeasons = resolvedTotalSeasons
                        )
                    }
                }

                if (mediaType == MediaType.TV) {
                    launch {
                        val titleForPrefetch = baseState.item?.title.orEmpty().ifBlank { mergedItem.title }
                        if (titleForPrefetch.isBlank()) {
                            return@launch
                        }
                        // Start immediately with TMDB/title so resolver can warm caches ASAP.
                        streamRepository.prefetchSeriesVodInfo(
                            imdbId = null,
                            title = titleForPrefetch,
                            tmdbId = mediaId
                        )
                        val externalIds = runCatching { externalIdsDeferred.await() }.getOrNull()
                        streamRepository.prefetchSeriesVodInfo(
                            imdbId = externalIds?.imdbId,
                            title = titleForPrefetch,
                            tmdbId = mediaId
                        )
                        val prefetchResumeInfo = runCatching { resumeDeferred.await() }.getOrNull()
                        val loadedEpisodes = runCatching { episodesDeferred?.await() }.getOrNull().orEmpty()
                        val targetSeason = initialSeason
                            ?: prefetchResumeInfo?.season
                            ?: loadedEpisodes.firstOrNull()?.seasonNumber
                            ?: seasonToLoad
                        val targetEpisode = initialEpisode
                            ?: prefetchResumeInfo?.episode
                            ?: loadedEpisodes.firstOrNull()?.episodeNumber
                            ?: 1
                        streamRepository.prefetchEpisodeVod(
                            imdbId = externalIds?.imdbId,
                            season = targetSeason,
                            episode = targetEpisode,
                            title = titleForPrefetch,
                            tmdbId = mediaId
                        )
                    }

                    // TASK_17: Load IPTV series matches for instant next episode
                    // Pass iptvSeriesId if available (from Series page navigation)
                    launch {
                        loadIptvSeriesMatches(
                            tmdbId = mediaId,
                            title = mergedItem.title,
                            iptvSeriesId = mergedItem.iptvSeriesId
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    /**
     * Load series details from IPTV provider directly using iptvSeriesId.
     * This bypasses TMDB and uses get_series_info API to populate the UI.
     */
    private suspend fun loadDetailsFromIptv(
        iptvSeriesId: Int,
        initialItem: MediaItem,
        initialSeason: Int?,
        initialEpisode: Int?,
        autoPlaySingleSource: Boolean,
        autoPlayMinQuality: String
    ) {
        _uiState.value = DetailsUiState(
            isLoading = true,
            item = initialItem,
            playSeason = initialSeason,
            playEpisode = initialEpisode,
            autoPlaySingleSource = autoPlaySingleSource,
            autoPlayMinQuality = autoPlayMinQuality
        )

        try {
            // Fetch full series info from IPTV provider
            val seriesInfo = iptvRepository.getSeriesFullInfo(iptvSeriesId)
            
            if (seriesInfo == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load series info from IPTV provider"
                )
                return
            }

            // Check if MediaItem already has TMDB data (enriched by SeriesViewModel)
            val hasTmdbData = initialItem.id != 0
            val tmdbId = if (hasTmdbData) initialItem.id else 0

            // If no TMDB data was pre-fetched, we won't search for it here
            // User explicitly navigated with IPTV-only data
            val (tmdbCast, trailerKey, tmdbGenres) = if (hasTmdbData) {
                try {
                    Log.d("DetailsViewModel", "Using pre-fetched TMDB ID: $tmdbId, fetching additional metadata")
                    
                    // Fetch cast with profile photos
                    val credits = runCatching {
                        tmdbApi.getCredits("tv", tmdbId, com.arflix.tv.util.Constants.TMDB_API_KEY)
                    }.getOrNull()
                    
                    val cast = credits?.cast?.take(15)?.map { tmdbCast ->
                        CastMember(
                            id = tmdbCast.id,
                            name = tmdbCast.name,
                            character = tmdbCast.character ?: "",
                            profilePath = tmdbCast.profilePath?.let { Constants.IMAGE_BASE + it }
                        )
                    } ?: emptyList()
                    
                    // Fetch trailer and genres from TV details
                    val tvDetails = runCatching {
                        tmdbApi.getTvDetails(tmdbId, com.arflix.tv.util.Constants.TMDB_API_KEY)
                    }.getOrNull()
                    
                    val videos = runCatching {
                        tmdbApi.getVideos("tv", tmdbId, com.arflix.tv.util.Constants.TMDB_API_KEY)
                    }.getOrNull()
                    
                    val trailer = videos?.results?.firstOrNull { video ->
                        video.site.equals("YouTube", ignoreCase = true) && 
                        video.type.equals("Trailer", ignoreCase = true)
                    }?.key
                    
                    val genres = tvDetails?.genres?.map { it.name } ?: emptyList()
                    
                    Triple(cast, trailer, genres)
                } catch (e: Exception) {
                    Log.w("DetailsViewModel", "Failed to fetch additional TMDB metadata", e)
                    Triple(emptyList(), null, emptyList())
                }
            } else {
                Log.d("DetailsViewModel", "No TMDB data available, using IPTV-only data for: ${seriesInfo.name}")
                Triple(emptyList(), null, emptyList())
            }

            // Update currentMediaId for continue watching (0 if no TMDB match)
            currentMediaId = tmdbId

            // Build MediaItem: Use pre-fetched TMDB data from initialItem when available,
            // otherwise use IPTV data from seriesInfo
            val mediaItem = if (hasTmdbData) {
                // Use TMDB-enriched data from SeriesViewModel
                Log.d("DetailsViewModel", "Using TMDB-enriched MediaItem from SeriesViewModel")
                initialItem.copy(
                    // Only update fields that come from IPTV
                    totalEpisodes = seriesInfo.seasons.size,
                    iptvSeriesId = iptvSeriesId.toString()
                )
            } else {
                // No TMDB data, use pure IPTV data
                Log.d("DetailsViewModel", "Using pure IPTV data from get_series_info")
                MediaItem(
                    id = 0,
                    title = seriesInfo.name,
                    overview = seriesInfo.plot ?: "",
                    mediaType = MediaType.TV,
                    image = seriesInfo.coverUrl ?: initialItem.image,
                    backdrop = seriesInfo.backdropUrl ?: initialItem.backdrop,
                    tmdbRating = seriesInfo.rating ?: "",
                    releaseDate = seriesInfo.releaseDate,
                    year = seriesInfo.releaseDate?.take(4) ?: "",
                    totalEpisodes = seriesInfo.seasons.size,
                    iptvSeriesId = iptvSeriesId.toString()
                )
            }

            // Parse genres from IPTV (TMDB genres already fetched above if hasTmdbData)
            val genreList = if (!hasTmdbData) {
                seriesInfo.genre?.split(",")?.map { it.trim() } ?: emptyList()
            } else {
                tmdbGenres
            }
            
            Log.d("DetailsViewModel", "Genres loaded (hasTmdbData=$hasTmdbData): $genreList")

            // Use cast from TMDB if available (includes photos), otherwise parse from IPTV
            val castList = if (tmdbCast.isNotEmpty()) {
                tmdbCast
            } else {
                seriesInfo.cast?.split(",")?.mapIndexed { index, name ->
                    CastMember(
                        id = index,
                        name = name.trim(),
                        character = "",
                        profilePath = null
                    )
                } ?: emptyList()
            }

            // Extract available seasons from episodes (handles non-contiguous seasons)
            val availableSeasons = seriesInfo.episodes
                .map { it.seasonNumber }
                .distinct()
                .sorted()

            // Determine which season to load (first available if requested doesn't exist)
            val seasonToLoad = when {
                initialSeason != null && availableSeasons.contains(initialSeason) -> initialSeason
                availableSeasons.isNotEmpty() -> availableSeasons.first()
                else -> 1
            }

            // Convert IPTV episodes to Episode model
            val episodesForSeason = seriesInfo.episodes
                .filter { it.seasonNumber == seasonToLoad }
                .map { ep ->
                    Episode(
                        id = ep.streamId,
                        episodeNumber = ep.episodeNumber,
                        seasonNumber = ep.seasonNumber,
                        name = ep.title.replace(Regex(".*S\\d{1,2}E\\d{1,2}\\s*-?\\s*"), "").ifBlank { "Episode ${ep.episodeNumber}" },
                        overview = ep.plot ?: "",
                        stillPath = ep.stillPath,
                        voteAverage = ep.rating,
                        runtime = ep.duration?.let { parseDurationToMinutes(it) } ?: 0
                    )
                }

            // Store IPTV series context for instant playback
            val context = com.arflix.tv.data.model.IptvSeriesContext(
                seriesId = iptvSeriesId,
                seriesName = seriesInfo.name,
                cachedAtMs = System.currentTimeMillis()
            )

            // Store the binding (use tmdbId for continue watching)
            runCatching {
                iptvRepository.storeSeriesContext(
                    tmdbId = tmdbId,
                    seriesId = iptvSeriesId,
                    seriesName = seriesInfo.name
                )
            }

            // Determine play target
            val playLabel = if (initialEpisode != null && initialSeason != null) {
                "S${initialSeason}:E${initialEpisode}"
            } else if (episodesForSeason.isNotEmpty()) {
                "S${seasonToLoad}:E${episodesForSeason.first().episodeNumber}"
            } else {
                "Play"
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                item = mediaItem,
                totalSeasons = availableSeasons.size.coerceAtLeast(1),
                currentSeason = seasonToLoad,
                availableSeasons = availableSeasons,
                episodes = episodesForSeason,
                genres = genreList,
                cast = castList,
                trailerKey = trailerKey ?: seriesInfo.youtubeTrailer,
                iptvSeriesContext = context,
                playSeason = initialSeason ?: seasonToLoad,
                playEpisode = initialEpisode ?: episodesForSeason.firstOrNull()?.episodeNumber,
                playLabel = playLabel
            )

            // If TMDB data was loaded, fetch logo and similar items from TMDB
            if (hasTmdbData) {
                viewModelScope.launch {
                    try {
                        val logoUrl = mediaRepository.getLogoUrl(MediaType.TV, tmdbId)
                        Log.d("DetailsViewModel", "Loaded logo for TMDB ID $tmdbId: $logoUrl")
                        if (logoUrl != null) {
                            _uiState.value = _uiState.value.copy(logoUrl = logoUrl)
                        }
                    } catch (e: Exception) {
                        Log.w("DetailsViewModel", "Failed to load logo", e)
                    }
                }
                
                viewModelScope.launch {
                    try {
                        val similar = mediaRepository.getSimilar(MediaType.TV, tmdbId)
                        Log.d("DetailsViewModel", "Loaded ${similar.size} similar items for TMDB ID $tmdbId")
                        if (!similar.isNullOrEmpty()) {
                            val logos = similar.take(20).mapNotNull { item ->
                                val logoUrl = runCatching {
                                    mediaRepository.getLogoUrl(item.mediaType, item.id)
                                }.getOrNull()
                                if (!logoUrl.isNullOrBlank()) {
                                    "${item.mediaType}_${item.id}" to logoUrl
                                } else {
                                    null
                                }
                            }.toMap()
                            _uiState.value = _uiState.value.copy(
                                similar = similar,
                                similarLogoUrls = logos
                            )
                        }
                    } catch (e: Exception) {
                        Log.w("DetailsViewModel", "Failed to load similar items", e)
                    }
                }
            }

            initialLoadComplete = true
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = e.message ?: "Failed to load series info"
            )
        }
    }

    /**
     * Parse duration string like "01:23:45" or "58" to minutes.
     */
    private fun parseDurationToMinutes(duration: String): Int {
        return try {
            if (duration.contains(":")) {
                val parts = duration.split(":")
                when (parts.size) {
                    3 -> parts[0].toInt() * 60 + parts[1].toInt() // HH:MM:SS -> minutes
                    2 -> parts[0].toInt() // MM:SS -> just use minutes
                    else -> 0
                }
            } else {
                duration.toIntOrNull() ?: 0
            }
        } catch (e: Exception) {
            0
        }
    }

    fun loadSeason(seasonNumber: Int) {
        if (currentMediaType != MediaType.TV) return
        // Don't reload if already on this season
        if (_uiState.value.currentSeason == seasonNumber && _uiState.value.episodes.isNotEmpty()) return

        viewModelScope.launch {
            // Keep current episodes visible while loading new ones
            val currentEpisodes = _uiState.value.episodes
            
            // Check if we're using IPTV series data
            val iptvContext = _uiState.value.iptvSeriesContext
            if (iptvContext != null) {
                loadSeasonFromIptv(iptvContext.seriesId, seasonNumber)
                return@launch
            }

            try {
                val episodes = mediaRepository.getSeasonEpisodes(currentMediaId, seasonNumber)
                if (episodes.isNotEmpty()) {
                    // Decorate episodes with watched status from cache
                    val watchedKeys = runCatching {
                        traktRepository.getWatchedEpisodesForShow(currentMediaId)
                    }.getOrDefault(emptySet())
                    val decoratedEpisodes = if (watchedKeys.isNotEmpty()) {
                        episodes.map { ep ->
                            val key = "show_tmdb:$currentMediaId:${ep.seasonNumber}:${ep.episodeNumber}"
                            if (watchedKeys.contains(key)) ep.copy(isWatched = true) else ep
                        }
                    } else episodes

                    _uiState.value = _uiState.value.copy(
                        episodes = decoratedEpisodes,
                        currentSeason = seasonNumber
                    )
                } else {
                    // If no episodes returned, keep current and show error
                    _uiState.value = _uiState.value.copy(
                        toastMessage = "No episodes found for Season $seasonNumber",
                        toastType = ToastType.ERROR
                    )
                }
            } catch (e: Exception) {
                // On error, keep showing current episodes
                _uiState.value = _uiState.value.copy(
                    toastMessage = "Failed to load Season $seasonNumber",
                    toastType = ToastType.ERROR
                )
            }
        }
    }

    /**
     * Load VOD movie details from IPTV provider using iptvMovieId.
     * Used when navigating from Movies page to populate DetailsScreen with VOD + TMDB data.
     * If vodStreams are already present in initialItem (pre-fetched by MoviesViewModel),
     * skip VOD API call and use cached streams.
     */
    private suspend fun loadDetailsFromVod(
        iptvMovieId: Int,
        initialItem: MediaItem,
        autoPlaySingleSource: Boolean,
        autoPlayMinQuality: String
    ) {
        _uiState.value = DetailsUiState(
            isLoading = true,
            item = initialItem,
            autoPlaySingleSource = autoPlaySingleSource,
            autoPlayMinQuality = autoPlayMinQuality
        )

        try {
            // Check if vodStreams are already present (pre-fetched by MoviesViewModel)
            val hasPreFetchedStreams = !initialItem.vodStreams.isNullOrEmpty()
            
            // Check if we have TMDB data in initialItem (id != 0 means TMDB enrichment happened)
            val hasTmdbData = initialItem.id != 0
            
            if (hasTmdbData) {
                Log.d("DetailsViewModel", "Loading TMDB-enriched VOD movie: ${initialItem.title} with TMDB ID: ${initialItem.id}, pre-fetched streams: $hasPreFetchedStreams")
            } else {
                Log.d("DetailsViewModel", "No TMDB data available, loading VOD-only data")
            }

            // Fetch VOD streams only if not already available
            var vodStreams: List<com.arflix.tv.data.model.StreamSource> = initialItem.vodStreams ?: emptyList()
            
            if (!hasPreFetchedStreams) {
                Log.d("DetailsViewModel", "Fetching VOD info for movie ID: $iptvMovieId")
                // Fetch VOD movie info from IPTV provider
                val vodInfo = iptvRepository.getVodInfo(iptvMovieId)
                
                if (vodInfo == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load movie from VOD provider"
                    )
                    return
                }

                // Extract VOD stream from vodInfo
                val movieData = vodInfo.movieData
                
                if (movieData?.streamId != null && !movieData.extension.isNullOrBlank()) {
                    val streamUrl = iptvRepository.getVodStreamUrl(movieData.streamId, movieData.extension)
                    if (!streamUrl.isNullOrBlank()) {
                        vodStreams = listOf(
                            com.arflix.tv.data.model.StreamSource(
                                source = "IPTV VOD",
                                addonName = "IPTV",
                                addonId = "iptv_xtream_vod",
                                quality = "HD",
                                size = "",
                                url = streamUrl
                            )
                        )
                    }
                }
                
                // If no TMDB data, populate basic info from VOD
                if (!hasTmdbData && vodInfo.info != null) {
                    val updatedItem = initialItem.copy(
                        title = vodInfo.info.name ?: "",
                        overview = vodInfo.info.description ?: "",
                        image = vodInfo.info.coverBig ?: "",
                        backdrop = null,
                        tmdbRating = "0",
                        releaseDate = vodInfo.info.releasedate,
                        year = vodInfo.info.releasedate?.take(4) ?: "",
                        iptvMovieId = iptvMovieId.toString(),
                        vodStreams = vodStreams
                    )
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        item = updatedItem,
                        streams = vodStreams
                    )
                    return
                }
            }

            // Build media item from TMDB data with VOD stream
            val mediaItem = initialItem.copy(
                iptvMovieId = iptvMovieId.toString(),
                vodStreams = vodStreams
            )

            // Load TMDB supplementary data in parallel if TMDB ID is available
            var cast: List<CastMember> = emptyList()
            var similarItems: List<MediaItem> = emptyList()
            var logoImageUrl: String? = null
            
            if (hasTmdbData && initialItem.id != 0) {
                val tmdbId = initialItem.id
                
                coroutineScope {
                    val castDeferred = async { runCatching { mediaRepository.getCast(MediaType.MOVIE, tmdbId) }.getOrNull() ?: emptyList() }
                    val logoDeferred = async { runCatching { mediaRepository.getLogoUrl(MediaType.MOVIE, tmdbId) }.getOrNull() }
                    val similarDeferred = async { runCatching { mediaRepository.getSimilar(MediaType.MOVIE, tmdbId) }.getOrNull() ?: emptyList() }
                    
                    cast = castDeferred.await()
                    logoImageUrl = logoDeferred.await()
                    similarItems = similarDeferred.await()
                }
            }

            // Update UI with all loaded data
            val finalItem = logoImageUrl?.let { url ->
                if (url.isNotEmpty()) mediaItem.copy(image = url) else mediaItem
            } ?: mediaItem
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                item = finalItem,
                logoUrl = logoImageUrl,  // Set logo URL for display
                cast = cast,
                similar = similarItems,
                streams = vodStreams
            )

        } catch (e: Exception) {
            Log.e("DetailsViewModel", "Error loading VOD movie: ${e.message}", e)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Failed to load movie details"
            )
        }
    }

    /**
     * Load episodes for a season from IPTV provider.
     */
    private suspend fun loadSeasonFromIptv(seriesId: Int, seasonNumber: Int) {
        try {
            val seriesInfo = iptvRepository.getSeriesFullInfo(seriesId)
            if (seriesInfo == null) {
                _uiState.value = _uiState.value.copy(
                    toastMessage = "Failed to load season from IPTV",
                    toastType = ToastType.ERROR
                )
                return
            }

            val episodesForSeason = seriesInfo.episodes
                .filter { it.seasonNumber == seasonNumber }
                .map { ep ->
                    Episode(
                        id = ep.streamId,
                        episodeNumber = ep.episodeNumber,
                        seasonNumber = ep.seasonNumber,
                        name = ep.title.replace(Regex(".*S\\d{1,2}E\\d{1,2}\\s*-?\\s*"), "").ifBlank { "Episode ${ep.episodeNumber}" },
                        overview = ep.plot ?: "",
                        stillPath = ep.stillPath,
                        voteAverage = ep.rating,
                        runtime = ep.duration?.let { parseDurationToMinutes(it) } ?: 0
                    )
                }

            if (episodesForSeason.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    episodes = episodesForSeason,
                    currentSeason = seasonNumber
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    toastMessage = "No episodes found for Season $seasonNumber",
                    toastType = ToastType.ERROR
                )
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                toastMessage = "Failed to load Season $seasonNumber",
                toastType = ToastType.ERROR
            )
        }
    }

    fun toggleWatched(episodeIndex: Int? = null) {
        val currentItem = _uiState.value.item ?: return

        viewModelScope.launch {
            try {
                if (currentMediaType == MediaType.MOVIE) {
                    val newWatched = !currentItem.isWatched
                    if (newWatched) {
                        traktRepository.markMovieWatched(currentMediaId)
                    } else {
                        traktRepository.markMovieUnwatched(currentMediaId)
                    }
                    _uiState.value = _uiState.value.copy(
                        item = currentItem.copy(isWatched = newWatched),
                        toastMessage = if (newWatched) "Marked as watched" else "Marked as unwatched",
                        toastType = ToastType.SUCCESS
                    )
                } else {
                    val targetEpisode = _uiState.value.episodes.getOrNull(episodeIndex ?: 0)
                    if (targetEpisode == null) {
                        _uiState.value = _uiState.value.copy(
                            toastMessage = "No episode selected",
                            toastType = ToastType.ERROR
                        )
                        return@launch
                    }

                    val episodeWatched = !targetEpisode.isWatched
                    if (episodeWatched) {
                        traktRepository.markEpisodeWatched(
                            currentMediaId,
                            targetEpisode.seasonNumber,
                            targetEpisode.episodeNumber
                        )
                        watchHistoryRepository.removeFromHistory(
                            currentMediaId,
                            targetEpisode.seasonNumber,
                            targetEpisode.episodeNumber
                        )

                        // Save the NEXT episode to CW (local + cloud) so it appears on all devices
                        try {
                            val nextEp = targetEpisode.episodeNumber + 1
                            traktRepository.saveLocalContinueWatching(
                                mediaType = MediaType.TV,
                                tmdbId = currentMediaId,
                                title = currentItem.title,
                                posterPath = currentItem.image,
                                backdropPath = currentItem.backdrop,
                                season = targetEpisode.seasonNumber,
                                episode = nextEp,
                                episodeTitle = null,
                                progress = 3,
                                positionSeconds = 1L,
                                durationSeconds = 1L,
                                year = currentItem.year
                            )
                            watchHistoryRepository.saveProgress(
                                mediaType = MediaType.TV,
                                tmdbId = currentMediaId,
                                title = currentItem.title,
                                poster = currentItem.image,
                                backdrop = currentItem.backdrop,
                                season = targetEpisode.seasonNumber,
                                episode = nextEp,
                                episodeTitle = null,
                                progress = 0.01f,
                                duration = 1L,
                                position = 60L
                            )
                        } catch (_: Exception) {}
                    } else {
                        traktRepository.markEpisodeUnwatched(
                            currentMediaId,
                            targetEpisode.seasonNumber,
                            targetEpisode.episodeNumber
                        )
                    }

                    val updatedEpisodes = _uiState.value.episodes.map { ep ->
                        if (ep.seasonNumber == targetEpisode.seasonNumber &&
                            ep.episodeNumber == targetEpisode.episodeNumber
                        ) {
                            ep.copy(isWatched = episodeWatched)
                        } else {
                            ep
                        }
                    }

                    val anyWatched = updatedEpisodes.any { it.isWatched }
                    _uiState.value = _uiState.value.copy(
                        item = currentItem.copy(isWatched = anyWatched),
                        episodes = updatedEpisodes,
                        toastMessage = if (episodeWatched) {
                            "S${targetEpisode.seasonNumber}E${targetEpisode.episodeNumber} marked as watched"
                        } else {
                            "S${targetEpisode.seasonNumber}E${targetEpisode.episodeNumber} marked as unwatched"
                        },
                        toastType = ToastType.SUCCESS
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    toastMessage = "Failed to update watched status",
                    toastType = ToastType.ERROR
                )
            }
        }
    }

    fun toggleWatchlist() {
        val currentItem = _uiState.value.item ?: return
        val newInWatchlist = !_uiState.value.isInWatchlist

        viewModelScope.launch {
            try {
                if (newInWatchlist) {
                    // Pass the full MediaItem so it appears instantly in watchlist
                    watchlistRepository.addToWatchlist(currentMediaType, currentMediaId, currentItem)
                } else {
                    watchlistRepository.removeFromWatchlist(currentMediaType, currentMediaId)
                }

                _uiState.value = _uiState.value.copy(
                    isInWatchlist = newInWatchlist,
                    toastMessage = if (newInWatchlist) "Added to watchlist" else "Removed from watchlist",
                    toastType = ToastType.SUCCESS
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    toastMessage = "Failed to update watchlist",
                    toastType = ToastType.ERROR
                )
            }
        }
    }

    fun dismissToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    /**
     * Refresh watched badges and continue target when returning from Player.
     * Uses local caches/history first for near-instant UI updates.
     */
    fun refreshAfterPlayerReturn() {
        val tmdbId = currentMediaId
        if (tmdbId == 0) return
        // Don't run during initial load — would overwrite episodes/seasonProgress with empty data.
        // The isLoading guard alone is insufficient: cached items set isLoading=false immediately,
        // but episodes haven't been populated yet. Wait for the episodes coroutine to finish.
        if (_uiState.value.isLoading || !initialLoadComplete) return
        val mediaType = currentMediaType

        viewModelScope.launch {
            // Force-refresh watched episodes from backend (not just in-memory cache)
            // to pick up episodes marked watched during playback.
            val watchedKeys = if (mediaType == MediaType.TV) {
                runCatching { traktRepository.getWatchedEpisodesForShow(tmdbId) }
                    .getOrDefault(traktRepository.getWatchedEpisodesFromCache())
            } else {
                emptySet()
            }

            // Build all updates in one shot to avoid partial state writes
            val currentState = _uiState.value

            // 1. Update main item watched status
            val updatedItem = currentState.item?.let { currentItem ->
                val watched = if (mediaType == MediaType.MOVIE) {
                    traktRepository.isMovieWatched(tmdbId)
                } else {
                    traktRepository.hasWatchedEpisodes(tmdbId) ||
                        watchedKeys.any { it.startsWith("show_tmdb:$tmdbId:") }
                }
                currentItem.copy(isWatched = watched)
            }

            // 2. Update episode watched badges and season progress
            // Read the LATEST state for episodes — the snapshot captured above may be stale
            // if loadDetails() child coroutines populated episodes after we started.
            val latestForEpisodes = _uiState.value
            var updatedEpisodes = latestForEpisodes.episodes
            var updatedProgress = latestForEpisodes.seasonProgress
            if (mediaType == MediaType.TV && latestForEpisodes.episodes.isNotEmpty()) {
                val prefix = "show_tmdb:$tmdbId:"
                if (watchedKeys.any { it.startsWith(prefix) }) {
                    updatedEpisodes = latestForEpisodes.episodes.map { ep ->
                        val key = "show_tmdb:$tmdbId:${ep.seasonNumber}:${ep.episodeNumber}"
                        ep.copy(isWatched = ep.isWatched || watchedKeys.contains(key))
                    }
                    val season = latestForEpisodes.currentSeason
                    val progress = latestForEpisodes.seasonProgress.toMutableMap()
                    progress[season] = Pair(updatedEpisodes.count { it.isWatched }, updatedEpisodes.size)
                    updatedProgress = progress
                }
            }

            // 3. Re-derive play target: PRIORITY 1 = resume info, PRIORITY 2 = next unwatched
            // But PRESERVE existing play target if it already has a position (resume position).
            // Only override if we have NEW resume info or if no target is currently set.
            val currentHasPosition = (latestForEpisodes.playPositionMs ?: 0L) > 0
            val quickResume = fetchResumeInfoFromHistoryOnly(tmdbId, mediaType)
            
            val playTarget = if (quickResume != null) {
                // We have new resume info - use it (highest priority)
                buildPlayTarget(mediaType, null, quickResume)
            } else if (!currentHasPosition) {
                // No resume info and current target has no position - derive next unwatched
                if (mediaType == MediaType.TV) {
                    deriveNextUnwatchedPlayTarget(tmdbId, watchedKeys)
                } else {
                    null
                }
            } else {
                // Keep existing play target (it already has a resume position)
                null
            }

            // Read latest state to avoid overwriting concurrent updates (e.g. seasonProgress)
            val latestState = _uiState.value
            _uiState.value = latestState.copy(
                item = updatedItem ?: latestState.item,
                // Only overwrite episodes if we actually computed watched badges;
                // otherwise keep the latest (avoids blanking if episodes were populated concurrently)
                episodes = if (updatedEpisodes.isNotEmpty()) updatedEpisodes else latestState.episodes,
                // Only update seasonProgress if we actually computed new data; preserve existing otherwise
                seasonProgress = if (updatedProgress !== latestForEpisodes.seasonProgress) updatedProgress else latestState.seasonProgress,
                // Only update play target if we computed a new one; otherwise preserve existing
                playSeason = playTarget?.season ?: latestState.playSeason,
                playEpisode = playTarget?.episode ?: latestState.playEpisode,
                playLabel = playTarget?.label ?: latestState.playLabel,
                playPositionMs = playTarget?.positionMs ?: latestState.playPositionMs
            )
        }
    }

    /**
     * Find the next unwatched episode across all seasons to set the play button target.
     */
    private suspend fun deriveNextUnwatchedPlayTarget(tmdbId: Int, watchedKeys: Set<String>): PlayTarget? {
        return try {
            val tvDetails = tmdbApi.getTvDetails(tmdbId, Constants.TMDB_API_KEY)
            for (seasonNum in 1..tvDetails.numberOfSeasons) {
                val seasonDetails = runCatching {
                    tmdbApi.getTvSeason(tmdbId, seasonNum, Constants.TMDB_API_KEY)
                }.getOrNull() ?: continue
                val firstUnwatched = seasonDetails.episodes.firstOrNull { episode ->
                    val key = "show_tmdb:$tmdbId:$seasonNum:${episode.episodeNumber}"
                    !watchedKeys.contains(key)
                }
                if (firstUnwatched != null) {
                    return PlayTarget(
                        season = seasonNum,
                        episode = firstUnwatched.episodeNumber,
                        label = "Continue S${seasonNum}-E${firstUnwatched.episodeNumber}"
                    )
                }
            }
            // All episodes watched — offer restart
            PlayTarget(season = 1, episode = 1, label = "Start E1-S1")
        } catch (_: Exception) {
            null
        }
    }

    // ========== Person Modal ==========

    fun loadPerson(personId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                showPersonModal = true,
                isLoadingPerson = true,
                selectedPerson = null
            )

            try {
                val person = mediaRepository.getPersonDetails(personId)
                _uiState.value = _uiState.value.copy(
                    isLoadingPerson = false,
                    selectedPerson = person
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingPerson = false
                )
            }
        }
    }

    fun closePersonModal() {
        _uiState.value = _uiState.value.copy(
            showPersonModal = false,
            selectedPerson = null
        )
    }

    // ========== Stream Resolution ==========

    fun loadStreams(imdbId: String?, season: Int? = null, episode: Int? = null) {
        // Skip if streams are already loaded (e.g., pre-populated from VOD info)
        if (_uiState.value.streams.isNotEmpty()) {
            return
        }
        
        loadStreamsJob?.cancel()
        val requestId = ++loadStreamsRequestId
        val requestMediaType = currentMediaType
        val requestMediaId = currentMediaId

        // Set loading state SYNCHRONOUSLY before launching coroutine
        // This ensures StreamSelector shows loading indicator immediately
        _uiState.value = _uiState.value.copy(
            isLoadingStreams = true,
            streams = emptyList(),
            subtitles = emptyList()
        )

        loadStreamsJob = viewModelScope.launch {
            fun isCurrentRequest(): Boolean {
                return requestId == loadStreamsRequestId &&
                    currentMediaType == requestMediaType &&
                    currentMediaId == requestMediaId
            }
            if (!isCurrentRequest()) return@launch

            try {
                // Get current item's genre IDs and language for anime detection
                val item = _uiState.value.item
                val genreIds = item?.genreIds ?: emptyList()
                val originalLanguage = item?.originalLanguage
                // Start VOD append in background - runs parallel to addon stream fetch
                vodAppendJob?.cancel()
                vodAppendJob = viewModelScope.launch {
                    // VOD lookups use disk-cached catalogs (near-instant on warm starts).
                    // On rare true cold starts, catalog download can take 15-30s for large providers.
                    val vodTimeout = if (currentMediaType == MediaType.MOVIE) 60_000L else 120_000L
                    appendVodSourceInBackground(
                        imdbId = imdbId,
                        season = season,
                        episode = episode,
                        timeoutMs = vodTimeout,
                        requestId = requestId,
                        requestMediaType = requestMediaType,
                        requestMediaId = requestMediaId
                    )
                }

                val result = if (currentMediaType == MediaType.MOVIE) {
                    if (imdbId.isNullOrBlank()) {
                        com.arflix.tv.data.repository.StreamResult(emptyList(), emptyList())
                    } else {
                        streamRepository.resolveMovieStreams(
                            imdbId = imdbId,
                            title = item?.title.orEmpty(),
                            year = item?.year?.toIntOrNull()
                        )
                    }
                } else {
                    if (imdbId.isNullOrBlank()) {
                        com.arflix.tv.data.repository.StreamResult(emptyList(), emptyList())
                    } else {
                        streamRepository.resolveEpisodeStreams(
                            imdbId = imdbId,
                            season = season ?: 1,
                            episode = episode ?: 1,
                            tmdbId = currentMediaId,
                            tvdbId = _uiState.value.tvdbId,
                            genreIds = genreIds,
                            originalLanguage = originalLanguage,
                            title = item?.title ?: ""
                        )
                    }
                }


                val filteredStreams = result.streams.filter { stream ->
                    val u = stream.url?.trim().orEmpty()
                    u.isNotBlank() && !u.startsWith("magnet:", ignoreCase = true)
                }
                // Keep existing IPTV sources (both VOD and Series)
                val existingIptv = _uiState.value.streams.filter { 
                    it.addonId == "iptv_xtream_vod" || it.addonId == "iptv_xtream_series" 
                }
                val mergedStreams = (filteredStreams + existingIptv)
                    .distinctBy { "${it.url?.trim().orEmpty()}|${it.source}" }
                if (!isCurrentRequest()) return@launch
                val addonCount = streamRepository.installedAddons.first()
                    .count { it.isEnabled && it.type != com.arflix.tv.data.model.AddonType.SUBTITLE }
                _uiState.value = _uiState.value.copy(
                    streams = mergedStreams,
                    subtitles = result.subtitles,
                    hasStreamingAddons = addonCount > 0
                )
                _uiState.value = _uiState.value.copy(isLoadingStreams = false)
            } catch (e: Exception) {
                if (!isCurrentRequest()) return@launch
                _uiState.value = _uiState.value.copy(isLoadingStreams = false)
            }
        }
    }

    fun markEpisodeWatched(season: Int, episode: Int, watched: Boolean) {
        viewModelScope.launch {
            try {
                if (watched) {
                    traktRepository.markEpisodeWatched(currentMediaId, season, episode)
                    // Also remove from Supabase watch_history (removes from Continue Watching)
                    watchHistoryRepository.removeFromHistory(currentMediaId, season, episode)

                    // Save the NEXT episode to CW (local + cloud) so it appears on all devices
                    val item = _uiState.value.item
                    if (item != null) {
                        try {
                            val nextEp = episode + 1
                            traktRepository.saveLocalContinueWatching(
                                mediaType = MediaType.TV,
                                tmdbId = currentMediaId,
                                title = item.title,
                                posterPath = item.image,
                                backdropPath = item.backdrop,
                                season = season,
                                episode = nextEp,
                                episodeTitle = null,
                                progress = 3,
                                positionSeconds = 1L,
                                durationSeconds = 1L,
                                year = item.year
                            )
                            watchHistoryRepository.saveProgress(
                                mediaType = MediaType.TV,
                                tmdbId = currentMediaId,
                                title = item.title,
                                poster = item.image,
                                backdrop = item.backdrop,
                                season = season,
                                episode = nextEp,
                                episodeTitle = null,
                                progress = 0.01f,
                                duration = 1L,
                                position = 60L
                            )
                        } catch (_: Exception) {}
                    }
                } else {
                    traktRepository.markEpisodeUnwatched(currentMediaId, season, episode)
                }

                // Update local state
                val updatedEpisodes = _uiState.value.episodes.map { ep ->
                    if (ep.seasonNumber == season && ep.episodeNumber == episode) {
                        ep.copy(isWatched = watched)
                    } else ep
                }
                _uiState.value = _uiState.value.copy(episodes = updatedEpisodes)
            } catch (e: Exception) {
                // Failed silently
            }
        }
    }

    /**
     * Resolve real IMDB ID from TMDB using external_ids endpoint
     * This is required for addon stream resolution
     */
    /**
     * Fetch season progress for a TV show
     * Returns Map<seasonNumber, Pair<watchedCount, totalCount>>
     * Uses Trakt's show progress API which has accurate per-season data
     */
    private suspend fun fetchSeasonProgress(tmdbId: Int): SeasonProgressResult {
        return try {
            runCatching { traktRepository.initializeWatchedCache() }
            val cachedEpisodes = runCatching { traktRepository.getWatchedEpisodesFromCache() }.getOrDefault(emptySet())
            val cachedCountsBySeason = mutableMapOf<Int, Int>()
            val cachedKeysForShow = cachedEpisodes.filter { it.startsWith("show_tmdb:$tmdbId:") }.toSet()
            for (key in cachedKeysForShow) {
                val parts = key.split(":")
                val seasonNum = parts.getOrNull(2)?.toIntOrNull() ?: continue
                cachedCountsBySeason[seasonNum] = (cachedCountsBySeason[seasonNum] ?: 0) + 1
            }

            val watchedKeys = if (cachedKeysForShow.isNotEmpty()) {
                cachedKeysForShow
            } else {
                runCatching { traktRepository.getWatchedEpisodesForShow(tmdbId) }.getOrDefault(emptySet())
            }

            val tvDetails = tmdbApi.getTvDetails(tmdbId, Constants.TMDB_API_KEY)
            val numSeasons = tvDetails.numberOfSeasons

            val progressMap = mutableMapOf<Int, Pair<Int, Int>>()
            var nextUnwatched: Pair<Int, Int>? = null

            for (seasonNum in 1..numSeasons) {
                try {
                    val seasonDetails = tmdbApi.getTvSeason(tmdbId, seasonNum, Constants.TMDB_API_KEY)
                    val totalEpisodes = seasonDetails.episodes.size

                    val watchedCount = if (cachedCountsBySeason.isNotEmpty()) {
                        cachedCountsBySeason[seasonNum] ?: 0
                    } else {
                        watchedKeys.count { key ->
                            key.startsWith("show_tmdb:$tmdbId:$seasonNum:")
                        }
                    }
                    progressMap[seasonNum] = Pair(watchedCount, totalEpisodes)

                    if (nextUnwatched == null) {
                        val firstUnwatched = seasonDetails.episodes.firstOrNull { episode ->
                            val key = "show_tmdb:$tmdbId:$seasonNum:${episode.episodeNumber}"
                            !watchedKeys.contains(key)
                        }
                        if (firstUnwatched != null) {
                            nextUnwatched = Pair(seasonNum, firstUnwatched.episodeNumber)
                        }
                    }
                } catch (e: Exception) {
                    // Skip seasons we can't load
                }
            }

            SeasonProgressResult(
                progress = progressMap,
                hasWatched = watchedKeys.isNotEmpty(),
                nextUnwatched = nextUnwatched
            )
        } catch (e: Exception) {
            SeasonProgressResult(emptyMap(), false, null)
        }
    }

    private suspend fun fetchResumeInfo(tmdbId: Int, mediaType: MediaType): ResumeInfo? {
        return try {
            val entry = watchHistoryRepository.getLatestProgress(mediaType, tmdbId)
            val cloudResume = if (entry != null) {
                val resume = buildResumeFromProgress(
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    season = entry.season,
                    episode = entry.episode,
                    progress = entry.progress,
                    positionSeconds = entry.position_seconds,
                    durationSeconds = entry.duration_seconds
                )
                resume
            } else null

            val hasTrakt = runCatching { traktRepository.hasTrakt() }.getOrDefault(false)
            val localItem = runCatching {
                traktRepository.getLocalContinueWatchingEntry(
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    season = entry?.season,
                    episode = entry?.episode
                )
            }.getOrNull()
            val localFallbackItem = if (localItem == null) {
                runCatching {
                    traktRepository.getBestLocalContinueWatchingEntry(
                        mediaType = mediaType,
                        tmdbId = tmdbId
                    )
                }.getOrNull()
            } else {
                null
            }

            val cachedTraktItem = if (hasTrakt) {
                runCatching {
                    traktRepository.getCachedContinueWatching()
                        .firstOrNull { it.id == tmdbId && it.mediaType == mediaType && it.progress > 0 }
                }.getOrNull()
            } else {
                null
            }
            val fetchedTraktItem = if (hasTrakt && cachedTraktItem == null) {
                withTimeoutOrNull(4_000L) {
                    runCatching {
                        traktRepository.getContinueWatching()
                            .firstOrNull { it.id == tmdbId && it.mediaType == mediaType && it.progress > 0 }
                    }.getOrNull()
                }
            } else {
                null
            }

            val resumeCandidate = fetchedTraktItem ?: cachedTraktItem ?: localItem ?: localFallbackItem
            val localResume = if (resumeCandidate != null) {
                val resume = buildResumeFromProgress(
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    season = resumeCandidate.season,
                    episode = resumeCandidate.episode,
                    progress = resumeCandidate.progress / 100f,
                    positionSeconds = resumeCandidate.resumePositionSeconds,
                    durationSeconds = resumeCandidate.durationSeconds
                )
                resume
            } else null

            when {
                cloudResume == null -> localResume
                localResume == null -> cloudResume
                localResume.positionMs > cloudResume.positionMs -> localResume
                else -> cloudResume
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchResumeInfoFromHistoryOnly(tmdbId: Int, mediaType: MediaType): ResumeInfo? {
        return try {
            val entry = watchHistoryRepository.getLatestProgress(mediaType, tmdbId) ?: return null
            buildResumeFromProgress(
                mediaType = mediaType,
                tmdbId = tmdbId,
                season = entry.season,
                episode = entry.episode,
                progress = entry.progress,
                positionSeconds = entry.position_seconds,
                durationSeconds = entry.duration_seconds
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun buildResumeFromProgress(
        mediaType: MediaType,
        tmdbId: Int,
        season: Int?,
        episode: Int?,
        progress: Float,
        positionSeconds: Long,
        durationSeconds: Long
    ): ResumeInfo? {
        val normalizedDuration = if (durationSeconds > 86_400L) durationSeconds / 1000L else durationSeconds
        val normalizedPosition = if (positionSeconds > 86_400L) positionSeconds / 1000L else positionSeconds

        var seconds = when {
            normalizedPosition > 0 -> normalizedPosition
            normalizedDuration > 0 && progress > 0f -> (normalizedDuration * progress).toLong()
            else -> 0L
        }
        if (seconds <= 0L && progress > 0f) {
            val runtimeSeconds = resolveRuntimeSeconds(tmdbId, mediaType, season, episode)
            if (runtimeSeconds > 0L) {
                seconds = (runtimeSeconds * progress).toLong()
            }
        }
        if (seconds <= 0L) return null
        val timeLabel = formatResumeTime(seconds)
        if (timeLabel.isBlank()) return null

        return if (mediaType == MediaType.MOVIE) {
            ResumeInfo(
                label = "Continue at $timeLabel",
                positionMs = seconds * 1000L
            )
        } else {
            val s = season ?: return null
            val e = episode ?: return null
            ResumeInfo(
                season = s,
                episode = e,
                label = "Continue S${s} E${e} at $timeLabel",
                positionMs = seconds * 1000L
            )
        }
    }

    private suspend fun resolveRuntimeSeconds(
        tmdbId: Int,
        mediaType: MediaType,
        season: Int?,
        episode: Int?
    ): Long {
        return try {
            if (mediaType == MediaType.MOVIE) {
                val details = tmdbApi.getMovieDetails(tmdbId, Constants.TMDB_API_KEY)
                (details.runtime ?: 0) * 60L
            } else {
                val details = tmdbApi.getTvDetails(tmdbId, Constants.TMDB_API_KEY)
                val avgRuntime = details.episodeRunTime.firstOrNull() ?: 0
                if (avgRuntime > 0) {
                    avgRuntime * 60L
                } else {
                    val s = season ?: return 0L
                    val e = episode ?: return 0L
                    val seasonDetails = tmdbApi.getTvSeason(tmdbId, s, Constants.TMDB_API_KEY)
                    val episodeRuntime = seasonDetails.episodes.firstOrNull { it.episodeNumber == e }?.runtime
                        ?: seasonDetails.episodes.firstOrNull { it.runtime != null }?.runtime
                        ?: 0
                    episodeRuntime * 60L
                }
            }
        } catch (_: Exception) {
            0L
        }
    }

    private fun formatResumeTime(seconds: Long): String {
        val total = seconds.coerceAtLeast(0)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%d:%02d".format(minutes, secs)
        }
    }

    /**
     * Build the play button target for movies/TV shows.
     * 
     * PRIORITY ORDER (highest to lowest):
     * 1. resumeInfo from buildResumeFromProgress - if user has partially watched content,
     *    show "Continue at HH:MM:SS" with exact position
     * 2. Season progress - for TV shows, find next unwatched episode and show "Continue S1E2"
     * 3. Default - for brand new shows, show "Start E1-S1"
     * 
     * This ensures resume positions (with timestamps) always take priority over
     * simple "next episode" logic.
     */
    private fun buildPlayTarget(
        mediaType: MediaType,
        result: SeasonProgressResult?,
        resumeInfo: ResumeInfo?
    ): PlayTarget? {
        // PRIORITY 1: Resume info with timestamp (from buildResumeFromProgress)
        if (resumeInfo != null) {
            return PlayTarget(
                season = resumeInfo.season,
                episode = resumeInfo.episode,
                label = resumeInfo.label,
                positionMs = resumeInfo.positionMs
            )
        }
        
        // PRIORITY 2: Season progress (next unwatched episode)
        if (mediaType == MediaType.MOVIE) return null
        if (result == null) return null
        return if (!result.hasWatched) {
            PlayTarget(
                season = 1,
                episode = 1,
                label = "Start S1-E1"
            )
        } else {
            val next = result.nextUnwatched
            if (next != null) {
                PlayTarget(
                    season = next.first,
                    episode = next.second,
                    label = "Continue S${next.first}-E${next.second}"
                )
            } else {
                PlayTarget(
                    season = 1,
                    episode = 1,
                    label = "Start S1-E1"
                )
            }
        }
    }

    private data class ExternalIds(val imdbId: String?, val tvdbId: Int?)

    private suspend fun resolveExternalIds(mediaType: MediaType, mediaId: Int): ExternalIds {
        return try {
            val ids = when (mediaType) {
                MediaType.MOVIE -> tmdbApi.getMovieExternalIds(mediaId, Constants.TMDB_API_KEY)
                MediaType.TV -> tmdbApi.getTvExternalIds(mediaId, Constants.TMDB_API_KEY)
                MediaType.LIVE_TV -> return ExternalIds(null, null)
            }
            ExternalIds(imdbId = ids.imdbId, tvdbId = ids.tvdbId)
        } catch (_: Exception) {
            ExternalIds(null, null)
        }
    }

    private suspend fun appendVodSourceInBackground(
        imdbId: String?,
        season: Int?,
        episode: Int?,
        timeoutMs: Long,
        requestId: Long,
        requestMediaType: MediaType,
        requestMediaId: Int
    ) {
        if (requestId != loadStreamsRequestId ||
            currentMediaType != requestMediaType ||
            currentMediaId != requestMediaId
        ) {
            return
        }
        val currentStreams = _uiState.value.streams
        // Skip if IPTV sources already present (either VOD or Series)
        // if (currentStreams.any { it.addonId == "iptv_xtream_vod" || it.addonId == "iptv_xtream_series" }) {
        //     return
        // }
        val itemTitle = _uiState.value.item?.title.orEmpty()

        val vodList = if (currentMediaType == MediaType.MOVIE) {
            streamRepository.resolveMovieVodOnly(
                imdbId = imdbId,
                title = itemTitle,
                year = _uiState.value.item?.year?.toIntOrNull(),
                tmdbId = currentMediaId,
                timeoutMs = timeoutMs
            )
        } else {
            streamRepository.resolveEpisodeVodOnly(
                imdbId = imdbId,
                season = season ?: 1,
                episode = episode ?: 1,
                title = itemTitle,
                tmdbId = currentMediaId,
                timeoutMs = timeoutMs
            )
        }
        if (vodList.isEmpty()) {
            return
        }

        val validVodList = vodList.filter { !it.url.isNullOrBlank() }
        if (validVodList.isEmpty()) {
            return
        }

        val existingUrls = _uiState.value.streams.map { it.url }.toSet()
        val newVodList = validVodList.filter { it.url !in existingUrls }
        if (newVodList.isEmpty()) {
            return
        }
        if (requestId != loadStreamsRequestId ||
            currentMediaType != requestMediaType ||
            currentMediaId != requestMediaId
        ) {
            return
        }
        _uiState.value = _uiState.value.copy(
            streams = _uiState.value.streams + newVodList
        )
    }

    // ========== TASK_17: IPTV Series Matching ==========

    /**
     * Load IPTV series matches for a TV show.
     * Called when loading TV show details to enable instant next episode playback.
     * If iptvSeriesId is provided (from Series page navigation), uses it directly.
     */
    private suspend fun loadIptvSeriesMatches(
        tmdbId: Int,
        title: String,
        iptvSeriesId: String? = null
    ) {
        if (title.isBlank()) return

        // If we have an IPTV series ID directly (navigated from Series page), use it
        if (!iptvSeriesId.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(isLoadingIptvMatches = true)

            // Try to parse series ID
            val seriesIdInt = iptvSeriesId.toIntOrNull()
            if (seriesIdInt != null) {
                // Fetch and cache episodes directly
                val episodes = runCatching {
                    iptvRepository.fetchAndCacheSeriesEpisodes(seriesIdInt)
                }.getOrDefault(emptyList())

                if (episodes.isNotEmpty()) {
                    // Store the binding for future use
                    runCatching {
                        iptvRepository.storeSeriesContext(
                            tmdbId = tmdbId,
                            seriesId = seriesIdInt,
                            seriesName = title
                        )
                    }

                    val context = com.arflix.tv.data.model.IptvSeriesContext(
                        seriesId = seriesIdInt,
                        seriesName = title,
                        cachedAtMs = System.currentTimeMillis()
                    )

                    _uiState.value = _uiState.value.copy(
                        iptvSeriesContext = context,
                        isLoadingIptvMatches = false
                    )
                    return
                }
            }
            // Fall through to normal matching if direct ID didn't work
            _uiState.value = _uiState.value.copy(isLoadingIptvMatches = false)
        }

        // First check if we have a stored series context
        val storedContext = runCatching {
            iptvRepository.getStoredSeriesContext(tmdbId)
        }.getOrNull()

        if (storedContext != null) {
            // We have a previous binding - check if episodes are cached
            val hasCachedEpisodes = runCatching {
                iptvRepository.hasSeriesEpisodeCache(storedContext.seriesId)
            }.getOrDefault(false)

            _uiState.value = _uiState.value.copy(
                iptvSeriesContext = storedContext,
                isLoadingIptvMatches = !hasCachedEpisodes
            )

            // If episodes aren't cached, fetch them in background
            if (!hasCachedEpisodes) {
                runCatching {
                    iptvRepository.fetchAndCacheSeriesEpisodes(storedContext.seriesId)
                }
                if (currentMediaId == tmdbId) {
                    _uiState.value = _uiState.value.copy(isLoadingIptvMatches = false)
                }
            }
            return
        }

        // No stored context - find matches
        _uiState.value = _uiState.value.copy(isLoadingIptvMatches = true)

        val externalIds = runCatching {
            resolveExternalIds(MediaType.TV, tmdbId)
        }.getOrNull()

        val matches = runCatching {
            iptvRepository.findSeriesMatches(
                title = title,
                tmdbId = tmdbId,
                imdbId = externalIds?.imdbId
            )
        }.getOrDefault(emptyList())

        if (currentMediaId != tmdbId) return

        _uiState.value = _uiState.value.copy(
            iptvSeriesMatches = matches,
            isLoadingIptvMatches = false
        )

        // If we have a high-confidence match, pre-fetch its episodes
        val bestMatch = matches.firstOrNull()
        if (bestMatch != null && bestMatch.confidence >= 0.9f) {
            runCatching {
                iptvRepository.fetchAndCacheSeriesEpisodes(bestMatch.seriesId)
            }
        }
    }

    /**
     * Select an IPTV series match for playback.
     * This fetches and caches all episode info for the series.
     */
    fun selectIptvSeriesMatch(match: com.arflix.tv.data.model.IptvSeriesMatch) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingIptvMatches = true)

            val episodes = runCatching {
                iptvRepository.fetchAndCacheSeriesEpisodes(match.seriesId)
            }.getOrDefault(emptyList())

            if (episodes.isNotEmpty()) {
                // Store the binding for future use
                runCatching {
                    iptvRepository.storeSeriesContext(
                        tmdbId = currentMediaId,
                        seriesId = match.seriesId,
                        seriesName = match.seriesName
                    )
                }

                val context = com.arflix.tv.data.model.IptvSeriesContext(
                    seriesId = match.seriesId,
                    seriesName = match.seriesName,
                    cachedAtMs = System.currentTimeMillis()
                )

                _uiState.value = _uiState.value.copy(
                    iptvSeriesContext = context,
                    isLoadingIptvMatches = false,
                    toastMessage = "IPTV series linked: ${match.seriesName}",
                    toastType = ToastType.SUCCESS
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoadingIptvMatches = false,
                    toastMessage = "Failed to load episodes for ${match.seriesName}",
                    toastType = ToastType.ERROR
                )
            }
        }
    }

    /**
     * Build a direct episode URL from cached IPTV series info.
     * Returns null if not cached or episode not found.
     */
    suspend fun buildIptvEpisodeUrl(season: Int, episode: Int): String? {
        val context = _uiState.value.iptvSeriesContext ?: return null
        return runCatching {
            iptvRepository.buildEpisodeUrlFromCache(context.seriesId, season, episode)
        }.getOrNull()
    }
}
