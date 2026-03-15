package com.arflix.tv.ui.screens.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arflix.tv.data.model.IptvChannel
import com.arflix.tv.data.model.IptvNowNext
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.repository.IptvRepository
import com.arflix.tv.data.repository.WatchlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val IPTV_STATUS_PREFIX = "iptv:"

enum class ToastType {
    SUCCESS, ERROR, INFO
}

data class WatchlistUiState(
    val isLoading: Boolean = true,
    val movies: List<MediaItem> = emptyList(),
    val series: List<MediaItem> = emptyList(),
    val liveTv: List<MediaItem> = emptyList(),
    val error: String? = null,
    // Toast
    val toastMessage: String? = null,
    val toastType: ToastType = ToastType.INFO
)

// Kept for backward compatibility in some places
val WatchlistUiState.items: List<MediaItem>
    get() = movies + series + liveTv

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val watchlistRepository: WatchlistRepository,
    private val iptvRepository: IptvRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchlistUiState())
    val uiState: StateFlow<WatchlistUiState> = _uiState.asStateFlow()

    init {
        // Show cached items instantly, then refresh in background
        loadWatchlistInstant()
        // Load favorite TV channels from IPTV
        loadFavoriteTvChannels()
        // Also observe the repository's StateFlow for live updates
        observeWatchlistChanges()
    }

    private fun observeWatchlistChanges() {
        viewModelScope.launch {
            watchlistRepository.watchlistItems.collect { items ->
                val (movies, series, _) = organizeItems(items)
                if (items.isNotEmpty() || (_uiState.value.movies.isEmpty() && _uiState.value.series.isEmpty() && _uiState.value.liveTv.isEmpty())) {
                    // Preserve liveTv from IPTV favorites (loaded separately)
                    _uiState.value = _uiState.value.copy(
                        movies = movies,
                        series = series,
                        // Don't overwrite liveTv - it comes from loadFavoriteTvChannels()
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun organizeItems(items: List<MediaItem>): Triple<List<MediaItem>, List<MediaItem>, List<MediaItem>> {
        val movies = items.filter { it.mediaType == MediaType.MOVIE }
        val series = items.filter { it.mediaType == MediaType.TV }  // MediaType.TV denotes series/shows
        val liveTv = emptyList<MediaItem>()  // Live TV channels are not shown in watchlist
        return Triple(movies, series, liveTv)
    }

    private fun loadWatchlistInstant() {
        viewModelScope.launch {
            // Show cached items INSTANTLY (no loading state if we have cache)
            val cachedItems = watchlistRepository.getCachedItems()
            val (movies, series, _) = organizeItems(cachedItems)
            if (cachedItems.isNotEmpty()) {
                _uiState.value = WatchlistUiState(
                    isLoading = false,
                    movies = movies,
                    series = series
                    // liveTv will be loaded by loadFavoriteTvChannels()
                )
            } else {
                // Only show loading if no cache
                _uiState.value = WatchlistUiState(isLoading = true)
            }

            // Fetch fresh data (will update via StateFlow)
            try {
                val items = watchlistRepository.getWatchlistItems()
                val (freshMovies, freshSeries, _) = organizeItems(items)
                // Preserve existing liveTv from IPTV favorites
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    movies = freshMovies,
                    series = freshSeries
                    // Don't overwrite liveTv
                )
            } catch (e: Exception) {
                // Keep showing cached items on error
                if (movies.isEmpty() && series.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val items = watchlistRepository.refreshWatchlistItems()
                val (movies, series, _) = organizeItems(items)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    movies = movies,
                    series = series
                    // Preserve liveTv from IPTV favorites
                )
                // Refresh IPTV favorites as well
                loadFavoriteTvChannels()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    toastMessage = "Failed to refresh",
                    toastType = ToastType.ERROR
                )
            }
        }
    }

    fun removeFromWatchlist(item: MediaItem) {
        viewModelScope.launch {
            try {
                // Check if this is an IPTV item
                val isIptvItem = item.status?.startsWith(IPTV_STATUS_PREFIX) == true
                
                if (isIptvItem) {
                    // For IPTV items, remove from favorites
                    val channelId = item.status?.removePrefix(IPTV_STATUS_PREFIX)
                    if (channelId != null) {
                        iptvRepository.toggleFavoriteChannel(channelId)
                        // Remove from local state
                        val updatedLiveTv = _uiState.value.liveTv.filter { it.id != item.id }
                        _uiState.value = _uiState.value.copy(
                            liveTv = updatedLiveTv,
                            toastMessage = "Removed from favorites",
                            toastType = ToastType.SUCCESS
                        )
                    }
                } else {
                    // Regular watchlist item
                    // Optimistic update - remove from local state immediately
                    val updatedMovies = _uiState.value.movies.filter { it.id != item.id }
                    val updatedSeries = _uiState.value.series.filter { it.id != item.id }
                    
                    _uiState.value = _uiState.value.copy(
                        movies = updatedMovies,
                        series = updatedSeries,
                        toastMessage = "Removed from watchlist",
                        toastType = ToastType.SUCCESS
                    )
                    // Then sync to backend
                    watchlistRepository.removeFromWatchlist(item.mediaType, item.id)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    toastMessage = "Failed to remove",
                    toastType = ToastType.ERROR
                )
            }
        }
    }

    fun dismissToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    private fun loadFavoriteTvChannels() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Load IPTV favorite channels
                val snapshot = iptvRepository.getMemoryCachedSnapshot()
                    ?: iptvRepository.getCachedSnapshotOrNull()
                    ?: return@launch
                
                val favoriteIds = snapshot.favoriteChannels.toHashSet()
                if (favoriteIds.isEmpty()) return@launch

                // Re-derive now/next from cached programs for accurate EPG
                val favoriteChannelIds = snapshot.channels
                    .filter { favoriteIds.contains(it.id) }
                    .map { it.id }
                    .toSet()
                iptvRepository.reDeriveCachedNowNext(favoriteChannelIds)
                
                // Re-read snapshot after re-derive
                val freshSnapshot = iptvRepository.getMemoryCachedSnapshot() ?: snapshot
                
                // Convert IPTV channels to MediaItems
                val tvItems = freshSnapshot.channels
                    .filter { favoriteIds.contains(it.id) }
                    .mapNotNull { channel ->
                        val epg = freshSnapshot.nowNext[channel.id]
                        iptvChannelToMediaItem(channel, epg)
                    }
                
                if (tvItems.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(liveTv = tvItems)
                }
            } catch (e: Exception) {
                // Silently fail - Live TV is optional in watchlist
            }
        }
    }

    private fun iptvChannelToMediaItem(channel: IptvChannel, nowNext: IptvNowNext?): MediaItem? {
        val epgText = when {
            nowNext?.now != null -> {
                val timeRange = formatProgramTime(nowNext.now)
                "Now: ${nowNext.now.title} ($timeRange)"
            }
            else -> "Live TV"
        }
        
        return MediaItem(
            id = channel.id.hashCode(),  // Convert string ID to int
            title = channel.name,
            overview = epgText,
            image = channel.logo.orEmpty(),
            backdrop = channel.logo,
            mediaType = MediaType.TV,
            badge = "LIVE",
            status = "$IPTV_STATUS_PREFIX${channel.id}",
            isOngoing = true
        )
    }

    private fun formatProgramTime(program: com.arflix.tv.data.model.IptvProgram): String {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val start = java.time.Instant.ofEpochMilli(program.startUtcMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(formatter)
        val end = java.time.Instant.ofEpochMilli(program.endUtcMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(formatter)
        return "$start-$end"
    }
}
