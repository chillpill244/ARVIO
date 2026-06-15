package com.muvio.shared.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muvio.shared.domain.Category
import com.muvio.shared.domain.MediaItem
import com.muvio.shared.repository.MediaRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val heroItem: MediaItem? = null,
    val error: String? = null,
)

class HomeViewModel(private val mediaRepo: MediaRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = HomeUiState(isLoading = true)
            try {
                val trendingMoviesDeferred = async { mediaRepo.getTrendingMovies() }
                val trendingTvDeferred = async { mediaRepo.getTrendingTv() }
                val topRatedMoviesDeferred = async {
                    runCatching {
                        mediaRepo.discoverMovies(sortBy = "vote_average.desc", minVoteCount = 1000)
                    }.getOrDefault(emptyList())
                }
                val popularTvDeferred = async {
                    runCatching { mediaRepo.discoverTv() }.getOrDefault(emptyList())
                }

                val trendingMovies = trendingMoviesDeferred.await()
                val trendingTv = trendingTvDeferred.await()
                val topRatedMovies = topRatedMoviesDeferred.await()
                val popularTv = popularTvDeferred.await()

                val categories = buildList {
                    if (trendingMovies.isNotEmpty())
                        add(Category("trending_movies", "Trending Movies", trendingMovies))
                    if (trendingTv.isNotEmpty())
                        add(Category("trending_tv", "Trending TV", trendingTv))
                    if (topRatedMovies.isNotEmpty())
                        add(Category("top_rated_movies", "Top Rated Movies", topRatedMovies))
                    if (popularTv.isNotEmpty())
                        add(Category("popular_tv", "Popular TV Shows", popularTv))
                }

                _uiState.value = HomeUiState(
                    isLoading = false,
                    categories = categories,
                    heroItem = trendingMovies.firstOrNull() ?: trendingTv.firstOrNull(),
                )
            } catch (e: Exception) {
                _uiState.value = HomeUiState(isLoading = false, error = e.message ?: "Failed to load")
            }
        }
    }

    fun retry() = load()
}
