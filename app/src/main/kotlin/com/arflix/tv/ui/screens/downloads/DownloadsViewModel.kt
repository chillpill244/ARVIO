package com.arflix.tv.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arflix.tv.data.db.DownloadEntity
import com.arflix.tv.data.db.DownloadStatus
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.repository.DownloadsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsUiState(
    val isLoading: Boolean = true,
    val movieDownloads: List<DownloadEntity> = emptyList(),
    val seriesDownloads: List<DownloadEntity> = emptyList(), // grouped by tmdbId
    val toastMessage: String? = null
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadsRepository: DownloadsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        observeDownloads()
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            downloadsRepository.observeAllDownloads().collect { downloads ->
                val movies = downloads.filter { it.mediaType == MediaType.MOVIE.name }
                // For series: get unique by tmdbId; representative has fileSize = sum of all episodes
                val seriesGrouped = downloads
                    .filter { it.mediaType == MediaType.TV.name }
                    .groupBy { it.tmdbId }
                    .map { (_, episodes) ->
                        val totalSize = episodes.sumOf { it.fileSize }
                        episodes.first().copy(fileSize = totalSize)
                    }

                _uiState.value = DownloadsUiState(
                    isLoading = false,
                    movieDownloads = movies,
                    seriesDownloads = seriesGrouped
                )
            }
        }
    }

    fun deleteDownload(downloadId: Long) {
        viewModelScope.launch {
            try {
                downloadsRepository.deleteDownload(downloadId)
                _uiState.value = _uiState.value.copy(toastMessage = "Download removed")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(toastMessage = "Failed to delete")
            }
        }
    }

    fun retryDownload(downloadId: Long) {
        viewModelScope.launch {
            try {
                downloadsRepository.retryDownload(downloadId)
                _uiState.value = _uiState.value.copy(toastMessage = "Retrying download")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(toastMessage = "Retry failed")
            }
        }
    }

    fun cancelDownload(downloadId: Long) {
        viewModelScope.launch {
            try {
                downloadsRepository.cancelDownload(downloadId)
            } catch (_: Exception) { }
        }
    }

    fun pauseDownload(downloadId: Long) {
        viewModelScope.launch {
            try {
                downloadsRepository.pauseDownload(downloadId)
            } catch (_: Exception) { }
        }
    }

    fun resumeDownload(downloadId: Long) {
        viewModelScope.launch {
            try {
                downloadsRepository.resumeDownload(downloadId)
            } catch (_: Exception) { }
        }
    }

    fun dismissToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    suspend fun getDownloadedEpisodes(tmdbId: Int): List<DownloadEntity> {
        return downloadsRepository.getDownloadsForTmdb(tmdbId, MediaType.TV)
            .filter { it.status == DownloadStatus.COMPLETED }
            .sortedWith(compareBy({ it.season }, { it.episode }))
    }

    suspend fun getAllEpisodesForSeries(tmdbId: Int): List<DownloadEntity> {
        return downloadsRepository.getDownloadsForTmdb(tmdbId, MediaType.TV)
            .sortedWith(compareBy({ it.season }, { it.episode }))
    }

    /** Live flow of all download entities for a series, sorted by season/episode. */
    fun observeEpisodesForSeries(tmdbId: Int): Flow<List<DownloadEntity>> {
        return downloadsRepository.observeDownloadsForMedia(tmdbId, MediaType.TV)
            .map { list -> list.sortedWith(compareBy({ it.season }, { it.episode })) }
    }
}
