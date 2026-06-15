package com.muvio.shared.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muvio.shared.domain.*
import com.muvio.shared.repository.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailsUiState(
    val isLoading: Boolean = true,
    val item: MediaItem? = null,
    val imdbId: String? = null,
    val tvdbId: Int? = null,
    val cast: List<CastMember> = emptyList(),
    val similar: List<MediaItem> = emptyList(),
    val trailerKey: String? = null,
    val clearLogoUrl: String? = null,
    val seasons: List<SeasonInfo> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val selectedSeason: Int = 1,
    val reviews: List<Review> = emptyList(),
    val streams: List<StreamSource> = emptyList(),
    val isLoadingStreams: Boolean = false,
    val genres: List<String> = emptyList(),
    val error: String? = null,
)

class DetailsViewModel(
    private val tmdbId: Int,
    private val mediaType: MediaType,
    private val mediaRepo: MediaRepository,
    private val watchHistory: WatchHistoryRepository,
    private val addonRepo: AddonRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    init {
        loadDetails()
    }

    private fun loadDetails() {
        viewModelScope.launch {
            _uiState.value = DetailsUiState(isLoading = true)
            try {
                when (mediaType) {
                    MediaType.MOVIE -> loadMovieDetails()
                    MediaType.TV -> loadTvDetails()
                }
            } catch (e: Exception) {
                _uiState.value = DetailsUiState(isLoading = false, error = e.message ?: "Failed to load")
            }
        }
    }

    private suspend fun loadMovieDetails() {
        val detailsDeferred = viewModelScope.async { mediaRepo.getMovieDetails(tmdbId) }
        val reviewsDeferred = viewModelScope.async {
            runCatching { mediaRepo.getReviews("movie", tmdbId) }.getOrDefault(emptyList())
        }
        val details = detailsDeferred.await()
        val reviews = reviewsDeferred.await()
        val progress = watchHistory.getProgress(MediaType.MOVIE, tmdbId)
        _uiState.value = DetailsUiState(
            isLoading = false,
            item = details.item.copy(progress = progress?.percent ?: 0, isWatched = progress?.isFinished ?: false),
            imdbId = details.imdbId,
            cast = details.cast,
            similar = details.similar,
            trailerKey = details.trailerKey,
            clearLogoUrl = details.clearLogoUrl,
            reviews = reviews,
            genres = details.genres,
        )
    }

    private suspend fun loadTvDetails() {
        val details = mediaRepo.getTvDetails(tmdbId)
        val firstSeason = details.seasons.firstOrNull()?.seasonNumber ?: 1
        val episodes = if (details.seasons.isNotEmpty()) {
            runCatching { mediaRepo.getEpisodes(tmdbId, firstSeason) }.getOrDefault(emptyList())
        } else emptyList()
        val progress = watchHistory.getProgress(MediaType.TV, tmdbId)
        _uiState.value = DetailsUiState(
            isLoading = false,
            item = details.item.copy(progress = progress?.percent ?: 0, isWatched = progress?.isFinished ?: false),
            imdbId = details.imdbId,
            tvdbId = details.tvdbId,
            cast = details.cast,
            similar = details.similar,
            trailerKey = details.trailerKey,
            clearLogoUrl = details.clearLogoUrl,
            seasons = details.seasons,
            episodes = episodes,
            selectedSeason = firstSeason,
            genres = details.genres,
        )
    }

    fun selectSeason(seasonNumber: Int) {
        _uiState.value = _uiState.value.copy(selectedSeason = seasonNumber, isLoading = true)
        viewModelScope.launch {
            val episodes = runCatching { mediaRepo.getEpisodes(tmdbId, seasonNumber) }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(episodes = episodes, isLoading = false)
        }
    }

    fun loadStreams() {
        val imdbId = _uiState.value.imdbId ?: return
        _uiState.value = _uiState.value.copy(isLoadingStreams = true, streams = emptyList())
        viewModelScope.launch {
            val streams = runCatching {
                when (mediaType) {
                    MediaType.MOVIE -> addonRepo.resolveMovieStreams(imdbId)
                    MediaType.TV -> {
                        val s = _uiState.value.selectedSeason
                        val e = _uiState.value.episodes.firstOrNull()?.episodeNumber ?: 1
                        addonRepo.resolveEpisodeStreams(imdbId, s, e)
                    }
                }
            }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(streams = streams, isLoadingStreams = false)
        }
    }

    fun loadEpisodeStreams(season: Int, episode: Int) {
        val imdbId = _uiState.value.imdbId ?: return
        _uiState.value = _uiState.value.copy(isLoadingStreams = true, streams = emptyList())
        viewModelScope.launch {
            val streams = runCatching {
                addonRepo.resolveEpisodeStreams(imdbId, season, episode)
            }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(streams = streams, isLoadingStreams = false)
        }
    }

    fun retry() = loadDetails()
}
