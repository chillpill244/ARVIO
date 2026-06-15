package com.muvio.shared.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muvio.shared.domain.MediaItem
import com.muvio.shared.repository.MediaRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<MediaItem> = emptyList(),
    val movieResults: List<MediaItem> = emptyList(),
    val tvResults: List<MediaItem> = emptyList(),
    val error: String? = null,
)

class SearchViewModel(private val mediaRepo: MediaRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query, error = null)
        if (query.length < 2) {
            _uiState.value = _uiState.value.copy(results = emptyList(), movieResults = emptyList(), tvResults = emptyList())
            return
        }
        search(query)
    }

    fun search(query: String = _uiState.value.query) {
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val multiDeferred = async { mediaRepo.searchMulti(query) }
                val moviesDeferred = async { runCatching { mediaRepo.searchMovies(query) }.getOrDefault(emptyList()) }
                val tvDeferred = async { runCatching { mediaRepo.searchTv(query) }.getOrDefault(emptyList()) }

                val results = multiDeferred.await()
                val movies = moviesDeferred.await()
                val tv = tvDeferred.await()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    results = results,
                    movieResults = movies,
                    tvResults = tv,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Search failed")
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = SearchUiState()
    }
}
