package com.arflix.tv.ui.screens.downloads

import android.widget.Toast
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.db.DownloadEntity
import com.arflix.tv.data.model.StreamSource
import com.arflix.tv.data.model.Subtitle
import com.arflix.tv.data.repository.DownloadsRepository
import com.arflix.tv.data.repository.MediaRepository
import com.arflix.tv.data.repository.ProfileManager
import com.arflix.tv.data.repository.StreamRepository
import com.arflix.tv.util.Constants
import com.arflix.tv.util.HlsDownloadSelection
import com.arflix.tv.util.profilesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: DownloadsRepository,
    private val mediaRepository: MediaRepository,
    private val streamRepository: StreamRepository,
    private val tmdbApi: TmdbApi,
    private val profileManager: ProfileManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class UiState(
        val movieDownloads: List<DownloadEntity> = emptyList(),
        val seriesDownloads: Map<Int, List<DownloadEntity>> = emptyMap(),
        val seriesMetadata: Map<Int, DownloadEntity> = emptyMap(),
        // Stream selection sheet state
        val showDownloadSheet: Boolean = false,
        val nextEpisodeSeason: Int? = null,
        val nextEpisodeNumber: Int? = null,
        val nextEpisodeTitle: String? = null,
        val nextEpisodePoster: String? = null,
        val nextEpisodeBackdrop: String? = null,
        val activeTmdbId: Int? = null,
        val streams: List<StreamSource> = emptyList(),
        val subtitles: List<Subtitle> = emptyList(),
        val isLoadingStreams: Boolean = false,
        val preferredSubtitleLang: String = "",
        val secondarySubtitleLang: String = ""
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = context.profilesDataStore.data.first()
            val preferredSubtitleLang = prefs[profileManager.profileStringKey("default_subtitle")]?.trim().orEmpty()
            val secondarySubtitleLang = prefs[profileManager.profileStringKey("secondary_subtitle")]?.trim().orEmpty()
            _uiState.update { 
                it.copy(
                    preferredSubtitleLang = preferredSubtitleLang,
                    secondarySubtitleLang = secondarySubtitleLang
                ) 
            }
            repository.observeAllDownloads().collect { all ->
                val movies = all.filter { it.mediaType == "movie" }
                val tvAll = all.filter { it.mediaType == "tv" }
                val seriesGroups = tvAll.groupBy { it.tmdbId }
                val seriesMeta = seriesGroups.mapValues { (_, eps) ->
                    eps.maxByOrNull { it.createdAt } ?: eps.first()
                }
                _uiState.update {
                    it.copy(
                        movieDownloads = movies,
                        seriesDownloads = seriesGroups,
                        seriesMetadata = seriesMeta
                    )
                }
            }
        }
    }

    fun prepareDownloadNextEpisode(tmdbId: Int) {
        val seriesDownloads = _uiState.value.seriesDownloads[tmdbId] ?: return
        // Find latest episode downloaded
        val latestDownloaded = seriesDownloads.maxWithOrNull(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }))
        val currentSeason = latestDownloaded?.season ?: 1
        val currentEpisode = latestDownloaded?.episode ?: 0
        val showTitle = latestDownloaded?.title ?: "Unknown Show"
        val showPoster = latestDownloaded?.posterPath
        val showBackdrop = latestDownloaded?.backdropPath

        _uiState.update {
            it.copy(
                showDownloadSheet = true,
                isLoadingStreams = true,
                streams = emptyList(),
                subtitles = emptyList(),
                activeTmdbId = tmdbId,
                nextEpisodeTitle = "Loading...",
                nextEpisodeSeason = currentSeason,
                nextEpisodeNumber = currentEpisode + 1
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Find next episode info
                val seasonEpisodes = mediaRepository.getSeasonEpisodes(tmdbId, currentSeason)
                var nextSeason = currentSeason
                var nextEpisodeNumber = currentEpisode + 1
                var nextEpisode = seasonEpisodes.find { it.episodeNumber == nextEpisodeNumber }

                if (nextEpisode == null) {
                    nextSeason = currentSeason + 1
                    val nextSeasonEpisodes = mediaRepository.getSeasonEpisodes(tmdbId, nextSeason)
                    if (nextSeasonEpisodes.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "No next episode available", Toast.LENGTH_SHORT).show()
                            dismissDownloadSheet()
                        }
                        return@launch
                    }
                    nextEpisodeNumber = nextSeasonEpisodes.firstOrNull()?.episodeNumber ?: 1
                    nextEpisode = nextSeasonEpisodes.firstOrNull()
                }

                if (nextEpisode == null) {
                    withContext(Dispatchers.Main) {
                        dismissDownloadSheet()
                    }
                    return@launch
                }

                val epTitle = nextEpisode.name

                _uiState.update {
                    it.copy(
                        nextEpisodeSeason = nextSeason,
                        nextEpisodeNumber = nextEpisodeNumber,
                        nextEpisodeTitle = epTitle,
                        nextEpisodePoster = showPoster,
                        nextEpisodeBackdrop = showBackdrop
                    )
                }

                // Resolve IMDB ID and streams
                val ids = runCatching { tmdbApi.getTvExternalIds(tmdbId, Constants.TMDB_API_KEY) }.getOrNull()
                val imdbId = ids?.imdbId
                val tvdbId = ids?.tvdbId

                val allStreams = mutableListOf<StreamSource>()
                val allSubtitles = mutableListOf<Subtitle>()

                val stremioJob = async {
                    if (!imdbId.isNullOrBlank()) {
                        streamRepository.resolveEpisodeStreams(
                            imdbId = imdbId,
                            season = nextSeason,
                            episode = nextEpisodeNumber,
                            tmdbId = tmdbId,
                            title = showTitle
                        )
                    } else null
                }

                val vodJob = async {
                    streamRepository.resolveEpisodeVodSources(
                        imdbId = imdbId,
                        season = nextSeason,
                        episode = nextEpisodeNumber,
                        title = showTitle,
                        tmdbId = tmdbId,
                        tvdbId = tvdbId,
                        timeoutMs = 15000L
                    ).filter { !it.url.isNullOrBlank() }
                }

                val homeServerJob = async {
                    streamRepository.resolveEpisodeHomeServerSources(
                        imdbId = imdbId,
                        season = nextSeason,
                        episode = nextEpisodeNumber,
                        title = showTitle,
                        tmdbId = tmdbId,
                        tvdbId = tvdbId,
                        timeoutMs = 15000L
                    ).filter { !it.url.isNullOrBlank() }
                }

                val stremioResult = stremioJob.await()
                val vodSources = vodJob.await()
                val homeServerSources = homeServerJob.await()
                
                if (stremioResult != null) {
                    allStreams.addAll(stremioResult.streams)
                    allSubtitles.addAll(stremioResult.subtitles)
                }
                allStreams.addAll(vodSources)
                allStreams.addAll(homeServerSources)

                val mergedStreams = allStreams.distinctBy { "${it.url?.trim().orEmpty()}|${it.source}" }

                _uiState.update {
                    it.copy(
                        streams = mergedStreams,
                        subtitles = allSubtitles,
                        isLoadingStreams = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingStreams = false) }
            }
        }
    }

    fun dismissDownloadSheet() {
        _uiState.update {
            it.copy(
                showDownloadSheet = false,
                activeTmdbId = null,
                streams = emptyList(),
                subtitles = emptyList()
            )
        }
    }

    fun enqueueNextEpisodeDownload(
        stream: StreamSource,
        subtitle: Subtitle?,
        hlsSelection: HlsDownloadSelection?
    ) {
        val state = _uiState.value
        val tmdbId = state.activeTmdbId ?: return
        val showTitle = state.seriesMetadata[tmdbId]?.title ?: "Unknown Show"

        viewModelScope.launch {
            repository.enqueueDownload(
                tmdbId = tmdbId,
                mediaType = "tv",
                season = state.nextEpisodeSeason,
                episode = state.nextEpisodeNumber,
                title = showTitle,
                episodeTitle = state.nextEpisodeTitle,
                posterPath = state.nextEpisodePoster,
                backdropPath = state.nextEpisodeBackdrop,
                streamUrl = stream.url ?: return@launch,
                addonId = stream.addonId,
                addonName = stream.addonName,
                quality = hlsSelection?.qualityLabel ?: stream.quality,
                subtitleUrl = subtitle?.url,
                subtitleLang = subtitle?.lang,
                headers = stream.behaviorHints?.proxyHeaders?.request
                    ?.filterKeys { it.isNotBlank() }
                    ?.takeIf { it.isNotEmpty() },
                downloadType = if (hlsSelection != null) "HLS" else "FILE",
                streamKeys = hlsSelection?.let {
                    com.arflix.tv.util.HlsDownloadUtil.serializeStreamKeys(it.streamKeys)
                }
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Next episode queued for download", Toast.LENGTH_SHORT).show()
                dismissDownloadSheet()
            }
        }
    }

    fun pause(id: Long) = viewModelScope.launch { repository.pauseDownload(id) }
    fun resume(id: Long) = viewModelScope.launch { repository.resumeDownload(id) }
    fun cancel(id: Long) = viewModelScope.launch { repository.cancelDownload(id) }
    fun delete(id: Long) = viewModelScope.launch { repository.deleteDownload(id) }
    fun retry(id: Long) = viewModelScope.launch { repository.retryDownload(id) }
    fun deleteAllForSeries(tmdbId: Int) = viewModelScope.launch { repository.deleteAllForSeries(tmdbId) }
}
