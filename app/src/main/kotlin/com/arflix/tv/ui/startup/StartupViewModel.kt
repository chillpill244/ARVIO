package com.arflix.tv.ui.startup

import androidx.lifecycle.ViewModel
import com.arflix.tv.data.repository.PreferenceStore
import com.arflix.tv.data.repository.PlatformEnvironment
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Precision
import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * StartupViewModel - Handles parallel loading during splash screen
 * Pre-loads all data needed for instant home screen display
 */
data class StartupState(
    val isLoading: Boolean = true,
    val isReady: Boolean = false,
    val loadingProgress: Float = 0f,
    val loadingMessage: String = "Starting...",
    val categories: List<Category> = emptyList(),
    val heroItem: MediaItem? = null,
    val heroLogoUrl: String? = null,
    val logoCache: Map<String, String> = emptyMap(),
    val isAuthenticated: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class StartupViewModel constructor(
    private val mediaRepository: MediaRepository,
    private val preferenceStore: PreferenceStore, private val platformEnvironment: PlatformEnvironment,) : ViewModel() {
    private val imageLoader: ImageLoader by lazy(LazyThreadSafetyMode.NONE) {
        platformEnvironment.imageLoader
    }

    private val networkDispatcher = Dispatchers.IO.limitedParallelism(8)
    private val heroLogoPreloadWidth = 300
    private val heroLogoPreloadHeight = 70
    private val heroBackdropPreloadWidth = 3840
    private val heroBackdropPreloadHeight = 2160

    private val _state = MutableStateFlow(StartupState())
    val state: StateFlow<StartupState> = _state.asStateFlow()

    init {
        startParallelLoading()
    }

    private fun startParallelLoading() {
        viewModelScope.launch {
            try {
                // App always opens on profile selection first, so defer heavy
                // home network preloading to HomeViewModel after profile is chosen.
                updateProgress(0.7f, "Preparing...")

                _state.value = _state.value.copy(
                    isLoading = false,
                    isReady = true,
                    categories = emptyList(),
                    heroItem = null,
                    isAuthenticated = false
                )

                updateProgress(1.0f, "Ready!")

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isReady = true,
                    error = e.message
                )
            }
        }
    }

    private fun updateProgress(progress: Float, message: String) {
        _state.value = _state.value.copy(
            loadingProgress = progress,
            loadingMessage = message
        )
    }

    private fun prefetchHeroAssets(heroItem: MediaItem?) {
        if (heroItem == null) return

        val backdropUrl = heroItem.backdrop ?: heroItem.image
        if (!backdropUrl.isNullOrBlank()) {
            val request = coil3.request.ImageRequest.Builder(platformEnvironment.coilContext)
                .data(backdropUrl)
                .size(heroBackdropPreloadWidth, heroBackdropPreloadHeight)
                .precision(Precision.INEXACT)
                
                .build()
            imageLoader.enqueue(request)
        }

        viewModelScope.launch(networkDispatcher) {
            try {
                val logoUrl = mediaRepository.getLogoUrl(heroItem.mediaType, heroItem.id)
                if (!logoUrl.isNullOrBlank()) {
                    val request = coil3.request.ImageRequest.Builder(platformEnvironment.coilContext)
                        .data(logoUrl)
                        .size(heroLogoPreloadWidth, heroLogoPreloadHeight)
                        .precision(Precision.INEXACT)
                        
                        .build()
                    imageLoader.enqueue(request)
                    val cacheKey = "${heroItem.mediaType}_${heroItem.id}"
                    val currentCache = _state.value.logoCache.toMutableMap()
                    currentCache[cacheKey] = logoUrl
                    _state.value = _state.value.copy(
                        heroLogoUrl = logoUrl,
                        logoCache = currentCache
                    )
                }
            } catch (e: Exception) {
                // Hero logo preload failed
            }
        }
    }
}

