package com.arflix.tv.ui.screens.details

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import com.arflix.tv.data.repository.PreferenceStore
import com.arflix.tv.data.repository.PlatformEnvironment
import androidx.lifecycle.viewModelScope
import com.arflix.tv.data.model.CastMember
import com.arflix.tv.data.model.Episode
import com.arflix.tv.data.model.IptvEpisodeInfo
import com.arflix.tv.data.model.IptvSeriesFullInfo
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.model.StreamSource
import com.arflix.tv.data.repository.IptvRepository
import com.arflix.tv.data.repository.MediaRepository
import com.arflix.tv.shared.repository.TraktRepository
import com.arflix.tv.shared.repository.WatchlistRepository
import com.arflix.tv.util.settingsDataStore
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class IptvDetailsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val item: MediaItem? = null,
    val episodes: List<Episode> = emptyList(),
    val totalSeasons: Int = 1,
    val currentSeason: Int = 1,
    val availableSeasons: List<Int> = emptyList(),
    val genres: List<String> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val trailerKey: String? = null,
    val logoUrl: String? = null,
    val streams: List<StreamSource> = emptyList(),
    val isLoadingStreams: Boolean = false,
    val playSeason: Int? = null,
    val playEpisode: Int? = null,
    val playLabel: String? = null,
    val autoPlaySingleSource: Boolean = true,
    val autoPlayMinQuality: String = "Any",
    val isInWatchlist: Boolean = false
)

class IptvDetailsViewModel constructor(
    private val iptvRepository: IptvRepository,
    private val mediaRepository: MediaRepository,
    private val watchlistRepository: WatchlistRepository,
    private val traktRepository: TraktRepository,
    private val preferenceStore: PreferenceStore, private val platformEnvironment: PlatformEnvironment,) : ViewModel() {

    private val _uiState = MutableStateFlow(IptvDetailsUiState())
    val uiState: StateFlow<IptvDetailsUiState> = _uiState.asStateFlow()

    private var cachedSeriesInfo: IptvSeriesFullInfo? = null
    private var cachedEpisodeInfos: List<IptvEpisodeInfo> = emptyList()

    private fun autoPlaySingleSourceKey() = booleanPreferencesKey("auto_play_single_source")
    private fun autoPlayMinQualityKey() = stringPreferencesKey("auto_play_min_quality")

    fun loadDetails(
        iptvId: Int,
        mediaType: MediaType,
        initialSeason: Int? = null,
        initialEpisode: Int? = null
    ) {
        viewModelScope.launch {
            val prefs = preferenceStore.settings.data.first()
            val autoPlay = prefs[autoPlaySingleSourceKey()] ?: true
            val autoQuality = prefs[autoPlayMinQualityKey()] ?: "Any"
            when (mediaType) {
                MediaType.TV -> {
                    val cachedItem = iptvRepository.getCachedSeriesItem(iptvId)
                    val cachedGenres = iptvRepository.getCachedSeriesGenres(iptvId)
                    _uiState.value = IptvDetailsUiState(
                        isLoading = true,
                        item = cachedItem,
                        genres = cachedGenres,
                        autoPlaySingleSource = autoPlay,
                        autoPlayMinQuality = autoQuality
                    )
                    loadSeriesDetails(iptvId, initialSeason, initialEpisode)
                }
                MediaType.MOVIE -> {
                    val cachedItem = iptvRepository.getCachedVodItem(iptvId)
                    val cachedExt = iptvRepository.getCachedVodContainerExtension(iptvId) ?: "mp4"
                    val cachedStreamUrl = iptvRepository.getVodStreamUrl(iptvId, cachedExt)
                    val cachedStreams = if (!cachedStreamUrl.isNullOrBlank()) {
                        listOf(StreamSource(
                            source = cachedItem?.title ?: "IPTV",
                            addonName = "IPTV",
                            addonId = "iptv_xtream_vod",
                            quality = "HD",
                            size = "",
                            url = cachedStreamUrl
                        ))
                    } else emptyList()
                    _uiState.value = IptvDetailsUiState(
                        isLoading = true,
                        item = cachedItem,
                        streams = cachedStreams,
                        autoPlaySingleSource = autoPlay,
                        autoPlayMinQuality = autoQuality
                    )
                    loadVodDetails(iptvId)
                }
            }
        }
    }

    private suspend fun loadSeriesDetails(
        seriesId: Int,
        initialSeason: Int?,
        initialEpisode: Int?,
    ) {
        val info = iptvRepository.getSeriesFullInfo(seriesId)
        if (info == null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Could not load series from IPTV provider"
            )
            return
        }
        cachedSeriesInfo = info
        cachedEpisodeInfos = info.episodes

        // Always derive seasons from episodes — some providers return a mismatched or
        // empty seasons array while the episode objects carry the correct season numbers.
        val availableSeasons = info.episodes
            .map { it.seasonNumber }
            .distinct()
            .sorted()
            .ifEmpty { (1..maxOf(info.seasons.size, 1)).toList() }

        val seasonToLoad = when {
            initialSeason != null && availableSeasons.contains(initialSeason) -> initialSeason
            availableSeasons.isNotEmpty() -> availableSeasons.first()
            else -> 1
        }

        val episodesForSeason = buildEpisodeList(info, seasonToLoad)

        val existingItem = _uiState.value.item
        var tmdbId = existingItem?.id?.takeIf { it != 0 } ?: info.tmdbId
        if (tmdbId == 0 && info.name.isNotBlank()) {
            tmdbId = mediaRepository.resolveTitleToTmdbId(info.name, MediaType.TV) ?: 0
        }
        val mediaItem = MediaItem(
            id = tmdbId,
            title = info.name.takeIf { it.isNotBlank() } ?: existingItem?.title ?: "",
            overview = info.plot ?: "",
            mediaType = MediaType.TV,
            image = info.coverUrl?.takeIf { it.isNotBlank() } ?: existingItem?.image ?: "",
            backdrop = info.backdropUrl ?: existingItem?.backdrop,
            tmdbRating = info.rating ?: "",
            releaseDate = info.releaseDate,
            year = info.releaseDate?.take(4) ?: existingItem?.year ?: "",
            totalEpisodes = availableSeasons.size,
            iptvSeriesId = seriesId.toString()
        )

        val playLabel = when {
            initialSeason != null && initialEpisode != null -> "S${initialSeason}E${initialEpisode}"
            episodesForSeason.isNotEmpty() -> "S${seasonToLoad}E${episodesForSeason.first().episodeNumber}"
            else -> "Play"
        }

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            item = mediaItem,
            episodes = episodesForSeason,
            totalSeasons = availableSeasons.size.coerceAtLeast(1),
            currentSeason = seasonToLoad,
            availableSeasons = availableSeasons,
            genres = info.genre?.split(",")?.map { it.trim() }.orEmpty(),
            cast = info.cast?.split(",")?.mapIndexed { i, name ->
                CastMember(id = i, name = name.trim(), character = "", profilePath = null)
            }.orEmpty(),
            trailerKey = info.youtubeTrailer,
            playSeason = initialSeason ?: seasonToLoad,
            playEpisode = initialEpisode ?: episodesForSeason.firstOrNull()?.episodeNumber,
            playLabel = playLabel
        )

        // Enrich with TMDB metadata (cast photos, trailer, logo) if TMDB ID is known.
        // Never call TMDB for season or episode data — all episode data comes from IPTV.
        if (mediaItem.id != 0) {
            enrichWithTmdb(MediaType.TV, mediaItem.id)
        }
    }

    private suspend fun loadVodDetails(
        vodId: Int,
    ) {
        val vodInfo = iptvRepository.getVodInfo(vodId)
        if (vodInfo == null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Could not load movie from IPTV provider"
            )
            return
        }

        // vodId IS the streamId from the catalog — use it directly so we don't
        // depend on movie_data being present in the API response (some providers omit it).
        val extension = vodInfo.movieData?.extension?.ifBlank { null } ?: "mp4"
        val streamUrl = iptvRepository.getVodStreamUrl(vodId, extension)

        val streams = if (!streamUrl.isNullOrBlank()) {
            listOf(
                StreamSource(
                    source = vodInfo.info?.name ?: "IPTV",
                    addonName = "IPTV",
                    addonId = "iptv_xtream_vod",
                    quality = "HD",
                    size = "",
                    url = streamUrl
                )
            )
        } else {
            _uiState.value.streams  // keep cached stream built from catalog extension
        }

        val existingItem = _uiState.value.item
        val vodTitle = vodInfo.info?.name?.takeIf { it.isNotBlank() } ?: existingItem?.title ?: ""
        var tmdbId = existingItem?.id?.takeIf { it != 0 } ?: vodInfo.info?.tmdbId?.toIntOrNull() ?: 0
        if (tmdbId == 0 && vodTitle.isNotBlank()) {
            tmdbId = mediaRepository.resolveTitleToTmdbId(vodTitle, MediaType.MOVIE) ?: 0
        }
        val mediaItem = MediaItem(
            id = tmdbId,
            title = vodTitle,
            overview = vodInfo.info?.description ?: "",
            mediaType = MediaType.MOVIE,
            image = vodInfo.info?.coverBig?.takeIf { it.isNotBlank() } ?: existingItem?.image ?: "",
            tmdbRating = "0",
            releaseDate = vodInfo.info?.releasedate,
            year = vodInfo.info?.releasedate?.take(4) ?: existingItem?.year ?: "",
            iptvMovieId = vodId.toString()
        )

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            item = mediaItem,
            streams = streams,
            genres = vodInfo.info?.genre?.split(",")?.map { it.trim() }.orEmpty(),
            cast = vodInfo.info?.cast?.split(",")?.mapIndexed { i, name ->
                CastMember(id = i, name = name.trim(), character = "", profilePath = null)
            }.orEmpty()
        )

        // Enrich with TMDB metadata if TMDB ID is known.
        if (mediaItem.id != 0) {
            enrichWithTmdb(MediaType.MOVIE, mediaItem.id)
        }
    }

    private fun enrichWithTmdb(mediaType: MediaType, tmdbId: Int) {
        viewModelScope.launch {
            val logoDeferred = async {
                runCatching { mediaRepository.getLogoUrl(mediaType, tmdbId) }.getOrNull()
            }
            val castDeferred = async {
                runCatching { mediaRepository.getCast(mediaType, tmdbId) }.getOrNull()
            }
            val trailerDeferred = async {
                runCatching { mediaRepository.getTrailerKey(mediaType, tmdbId) }.getOrNull()
            }
            val detailsDeferred = async {
                runCatching { mediaRepository.getTmdbFullDetails(mediaType, tmdbId) }.getOrNull()
            }
            val watchlistDeferred = async {
                runCatching { watchlistRepository.isInWatchlist(mediaType, tmdbId) }.getOrDefault(false)
            }

            val logo = logoDeferred.await()
            val cast = castDeferred.await()
            val trailer = trailerDeferred.await()
            val tmdbResult = detailsDeferred.await()
            val inWatchlist = watchlistDeferred.await()
            val tmdbItem = tmdbResult?.first
            val tmdbGenres = tmdbResult?.second

            val currentItem = _uiState.value.item
            val updatedItem = if (tmdbItem != null && currentItem != null) {
                currentItem.copy(
                    title = tmdbItem.title.takeIf { it.isNotBlank() } ?: currentItem.title,
                    overview = tmdbItem.overview.takeIf { it.isNotBlank() } ?: currentItem.overview,
                    image = tmdbItem.image.takeIf { it.isNotBlank() } ?: currentItem.image,
                    backdrop = tmdbItem.backdrop ?: currentItem.backdrop,
                    tmdbRating = tmdbItem.tmdbRating,
                    imdbRating = tmdbItem.imdbRating,
                    year = tmdbItem.year.takeIf { it.isNotBlank() } ?: currentItem.year,
                    releaseDate = tmdbItem.releaseDate ?: currentItem.releaseDate,
                    duration = tmdbItem.duration.takeIf { it.isNotBlank() } ?: currentItem.duration,
                    status = tmdbItem.status ?: currentItem.status,
                    isOngoing = tmdbItem.isOngoing,
                    genreIds = tmdbItem.genreIds
                )
            } else currentItem

            _uiState.value = _uiState.value.copy(
                item = updatedItem ?: _uiState.value.item,
                logoUrl = logo ?: _uiState.value.logoUrl,
                cast = cast?.takeIf { it.isNotEmpty() } ?: _uiState.value.cast,
                trailerKey = trailer ?: _uiState.value.trailerKey,
                genres = tmdbGenres?.takeIf { it.isNotEmpty() } ?: _uiState.value.genres,
                isInWatchlist = inWatchlist
            )
        }
    }

    fun toggleWatchlist() {
        val currentItem = _uiState.value.item ?: return
        val tmdbId = currentItem.id.takeIf { it != 0 } ?: return
        val mediaType = currentItem.mediaType
        val newInWatchlist = !_uiState.value.isInWatchlist
        viewModelScope.launch {
            runCatching {
                val traktConnected = runCatching { traktRepository.hasTrakt() }.getOrDefault(false)
                if (newInWatchlist) {
                    if (traktConnected) traktRepository.addToWatchlist(mediaType, tmdbId)
                    watchlistRepository.addToWatchlist(mediaType, tmdbId, currentItem)
                } else {
                    if (traktConnected) traktRepository.removeFromWatchlist(mediaType, tmdbId)
                    watchlistRepository.removeFromWatchlist(mediaType, tmdbId)
                }
                _uiState.value = _uiState.value.copy(isInWatchlist = newInWatchlist)
            }
        }
    }

    fun toggleWatched(episodeIndex: Int? = null) {
        val currentItem = _uiState.value.item ?: return
        val tmdbId = currentItem.id.takeIf { it != 0 } ?: return
        val mediaType = currentItem.mediaType
        viewModelScope.launch {
            runCatching {
                val traktConnected = runCatching { traktRepository.hasTrakt() }.getOrDefault(false)
                if (mediaType == MediaType.MOVIE) {
                    val newWatched = !currentItem.isWatched
                    if (traktConnected) {
                        if (newWatched) traktRepository.markMovieWatched(tmdbId)
                        else traktRepository.markMovieUnwatched(tmdbId)
                    }
                    _uiState.value = _uiState.value.copy(item = currentItem.copy(isWatched = newWatched))
                } else {
                    val targetEp = _uiState.value.episodes.getOrNull(episodeIndex ?: 0) ?: return@runCatching
                    val newWatched = !targetEp.isWatched
                    if (traktConnected) {
                        if (newWatched) traktRepository.markEpisodeWatched(tmdbId, targetEp.seasonNumber, targetEp.episodeNumber)
                        else traktRepository.markEpisodeUnwatched(tmdbId, targetEp.seasonNumber, targetEp.episodeNumber)
                    }
                    _uiState.value = _uiState.value.copy(
                        episodes = _uiState.value.episodes.mapIndexed { i, ep ->
                            if (i == (episodeIndex ?: 0)) ep.copy(isWatched = newWatched) else ep
                        }
                    )
                }
            }
        }
    }

    fun loadSeason(seasonNumber: Int) {
        if (_uiState.value.currentSeason == seasonNumber && _uiState.value.episodes.isNotEmpty()) return
        val info = cachedSeriesInfo
        if (info != null) {
            val episodes = buildEpisodeList(info, seasonNumber)
            _uiState.value = _uiState.value.copy(currentSeason = seasonNumber, episodes = episodes)
        } else {
            val seriesId = _uiState.value.item?.iptvSeriesId?.toIntOrNull() ?: return
            viewModelScope.launch {
                val fetched = iptvRepository.getSeriesFullInfo(seriesId) ?: return@launch
                cachedSeriesInfo = fetched
                cachedEpisodeInfos = fetched.episodes
                val episodes = buildEpisodeList(fetched, seasonNumber)
                _uiState.value = _uiState.value.copy(currentSeason = seasonNumber, episodes = episodes)
            }
        }
    }

    private fun buildEpisodeList(info: IptvSeriesFullInfo, seasonNumber: Int): List<Episode> {
        return info.episodes
            .filter { it.seasonNumber == seasonNumber }
            .sortedBy { it.episodeNumber }
            .map { ep ->
                Episode(
                    id = ep.streamId,
                    episodeNumber = ep.episodeNumber,
                    seasonNumber = ep.seasonNumber,
                    name = ep.title
                        .replace(Regex(".*S\\d{1,2}E\\d{1,2}\\s*-?\\s*"), "")
                        .ifBlank { "Episode ${ep.episodeNumber}" },
                    overview = ep.plot ?: "",
                    stillPath = ep.stillPath,
                    voteAverage = ep.rating,
                    runtime = ep.duration?.let { d ->
                        d.split(":").let { parts ->
                            when (parts.size) {
                                3 -> (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
                                2 -> parts[0].toIntOrNull() ?: 0
                                else -> d.toIntOrNull() ?: 0
                            }
                        }
                    } ?: 0,
                    airDate = ep.releaseDate ?: ""
                )
            }
    }

    suspend fun resolveEpisodeStreamUrl(streamId: Int): String? {
        val ext = cachedEpisodeInfos
            .firstOrNull { it.streamId == streamId }
            ?.containerExtension
            ?.ifBlank { null }
            ?: "mkv"
        return iptvRepository.getEpisodeStreamUrl(streamId, ext)
    }
}
