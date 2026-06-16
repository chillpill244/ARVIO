package com.muvio.shared.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muvio.shared.domain.Category
import com.muvio.shared.domain.MediaItem
import com.muvio.shared.repository.MediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SearchFilter(val label: String) {
    ALL("All"),
    MOVIES("Movies"),
    TV("Shows"),
    ANIME("Anime"),
}

data class SearchUiState(
    val query: String = "",
    val selectedFilter: SearchFilter = SearchFilter.ALL,
    val isLoading: Boolean = false,
    val movieResults: List<MediaItem> = emptyList(),
    val tvResults: List<MediaItem> = emptyList(),
    val discoverCategories: List<Category> = emptyList(),
    val isDiscoverLoading: Boolean = false,
    val error: String? = null,
)

class SearchViewModel(private val mediaRepo: MediaRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var discoverJob: Job? = null

    init {
        loadDiscover(SearchFilter.ALL)
    }

    fun setFilter(filter: SearchFilter) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
        if (_uiState.value.query.isEmpty()) {
            loadDiscover(filter)
        }
    }

    private fun loadDiscover(filter: SearchFilter) {
        discoverJob?.cancel()
        discoverJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDiscoverLoading = true)
            try {
                val categories = coroutineScope {
                    when (filter) {
                        SearchFilter.ALL -> {
                            val moviesD = async { runCatching { mediaRepo.getTrendingMovies() }.getOrDefault(emptyList()) }
                            val tvD = async { runCatching { mediaRepo.getTrendingTv() }.getOrDefault(emptyList()) }
                            buildList {
                                val m = moviesD.await(); val t = tvD.await()
                                if (m.isNotEmpty()) add(Category("trending_movies", "Trending Movies", m))
                                if (t.isNotEmpty()) add(Category("trending_tv", "Trending TV", t))
                            }
                        }
                        SearchFilter.MOVIES -> {
                            val popularD = async { runCatching { mediaRepo.discoverMovies() }.getOrDefault(emptyList()) }
                            val topD = async {
                                runCatching { mediaRepo.discoverMovies(sortBy = "vote_average.desc", minVoteCount = 1000) }.getOrDefault(emptyList())
                            }
                            buildList {
                                val p = popularD.await(); val t = topD.await()
                                if (p.isNotEmpty()) add(Category("popular_movies", "Popular Movies", p))
                                if (t.isNotEmpty()) add(Category("top_rated_movies", "Top Rated", t))
                            }
                        }
                        SearchFilter.TV -> {
                            val popularD = async { runCatching { mediaRepo.discoverTv() }.getOrDefault(emptyList()) }
                            buildList {
                                val p = popularD.await()
                                if (p.isNotEmpty()) add(Category("popular_tv", "Popular Shows", p))
                            }
                        }
                        SearchFilter.ANIME -> {
                            val animeD = async { runCatching { mediaRepo.discoverTv(genres = "16") }.getOrDefault(emptyList()) }
                            buildList {
                                val a = animeD.await()
                                if (a.isNotEmpty()) add(Category("anime", "Anime", a))
                            }
                        }
                    }
                }
                _uiState.value = _uiState.value.copy(discoverCategories = categories, isDiscoverLoading = false)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isDiscoverLoading = false)
            }
        }
    }

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query, error = null)
        if (query.length < 2) {
            _uiState.value = _uiState.value.copy(movieResults = emptyList(), tvResults = emptyList(), isLoading = false)
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
                coroutineScope {
                    val moviesD = async { runCatching { mediaRepo.searchMovies(query) }.getOrDefault(emptyList()) }
                    val tvD = async { runCatching { mediaRepo.searchTv(query) }.getOrDefault(emptyList()) }
                    val movies = moviesD.await()
                    val tv = tvD.await()
                    _uiState.value = _uiState.value.copy(isLoading = false, movieResults = movies, tvResults = tv)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Search failed")
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            query = "",
            movieResults = emptyList(),
            tvResults = emptyList(),
            isLoading = false,
            error = null,
        )
    }
}
