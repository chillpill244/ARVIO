package com.muvio.shared.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muvio.shared.domain.MediaItem
import com.muvio.shared.domain.MediaType
import com.muvio.shared.domain.StreamSource
import com.muvio.shared.domain.Subtitle
import com.muvio.shared.repository.WatchHistoryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val mediaItem: MediaItem? = null,
    val selectedStream: StreamSource? = null,
    val subtitles: List<Subtitle> = emptyList(),
    val selectedSubtitle: Subtitle? = null,
    val progressMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val showControls: Boolean = true,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val error: String? = null,
)

class PlayerViewModel(
    private val watchHistory: WatchHistoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var progressSaveJob: Job? = null

    fun setStream(
        mediaItem: MediaItem,
        stream: StreamSource,
        initialProgressMs: Long = 0L,
        season: Int? = null,
        episode: Int? = null,
        episodeTitle: String? = null,
    ) {
        _uiState.value = PlayerUiState(
            mediaItem = mediaItem,
            selectedStream = stream,
            subtitles = stream.subtitles,
            progressMs = initialProgressMs,
            season = season,
            episode = episode,
            episodeTitle = episodeTitle,
        )
        startPeriodicSave()
    }

    fun onPlaybackProgress(progressMs: Long, durationMs: Long) {
        _uiState.value = _uiState.value.copy(progressMs = progressMs, durationMs = durationMs)
    }

    fun onPlayPause(isPlaying: Boolean) {
        _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
        if (!isPlaying) saveProgressNow()
    }

    fun onBuffering(isBuffering: Boolean) {
        _uiState.value = _uiState.value.copy(isBuffering = isBuffering)
    }

    fun toggleControls() {
        _uiState.value = _uiState.value.copy(showControls = !_uiState.value.showControls)
    }

    fun selectSubtitle(subtitle: Subtitle?) {
        _uiState.value = _uiState.value.copy(selectedSubtitle = subtitle)
    }

    fun onError(message: String) {
        _uiState.value = _uiState.value.copy(error = message, isPlaying = false, isBuffering = false)
    }

    private fun startPeriodicSave() {
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(10_000L)
                if (_uiState.value.isPlaying) saveProgressNow()
            }
        }
    }

    private fun saveProgressNow() {
        val state = _uiState.value
        val item = state.mediaItem ?: return
        if (state.progressMs <= 0L) return
        viewModelScope.launch {
            watchHistory.saveProgress(
                mediaType = item.mediaType,
                tmdbId = item.id,
                progressMs = state.progressMs,
                durationMs = state.durationMs,
                season = state.season,
                episode = state.episode,
                episodeTitle = state.episodeTitle,
            )
        }
    }

    override fun onCleared() {
        saveProgressNow()
        super.onCleared()
    }
}
