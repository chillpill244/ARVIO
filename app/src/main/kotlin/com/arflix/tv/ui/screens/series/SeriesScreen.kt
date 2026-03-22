@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.arflix.tv.ui.screens.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.Profile
import com.arflix.tv.ui.components.CardLayoutMode
import com.arflix.tv.ui.components.Sidebar
import com.arflix.tv.ui.components.SidebarItem
import com.arflix.tv.ui.components.TopBarClock
import com.arflix.tv.ui.components.rememberCardLayoutMode
import com.arflix.tv.ui.screens.shared.MediaCategoryContent
import com.arflix.tv.ui.theme.BackgroundDark

private enum class SeriesFocusZone {
    SIDEBAR,
    CONTENT
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeriesScreen(
    viewModel: SeriesViewModel = hiltViewModel(),
    currentProfile: Profile? = null,
    onNavigateToHome: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToWatchlist: () -> Unit = {},
    onNavigateToTv: (channelId: String?, streamUrl: String?) -> Unit = { _, _ -> },
    onNavigateToMovies: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDetails: (MediaItem) -> Unit = {},
    onSwitchProfile: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val cardLayoutMode = rememberCardLayoutMode()
    val isLandscape = cardLayoutMode == CardLayoutMode.LANDSCAPE
    
    var focusZone by remember { mutableStateOf(SeriesFocusZone.CONTENT) }
    val hasProfile = currentProfile != null
    val maxSidebarIndex = if (hasProfile) SidebarItem.entries.size else SidebarItem.entries.size - 1
    var sidebarFocusIndex by remember { mutableIntStateOf((if (hasProfile) 1 else 0) + SidebarItem.SERIES.ordinal) }
    
    var isLoadingSeriesInfo by rememberSaveable { mutableStateOf(false) }
    var selectedSeriesForNavigation by rememberSaveable { mutableStateOf<MediaItem?>(null) }

    LaunchedEffect(selectedSeriesForNavigation) {
        if (selectedSeriesForNavigation != null) {
            isLoadingSeriesInfo = true
            val enhancedItem = viewModel.getSeriesDetailsWithTmdbId(selectedSeriesForNavigation!!)
            if (enhancedItem != null) {
                onNavigateToDetails(enhancedItem)
            }
            isLoadingSeriesInfo = false
            selectedSeriesForNavigation = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .onKeyEvent { event ->
                // Handle bubbling phase - catch events not consumed by children
                if (event.type != KeyEventType.KeyDown) {
                    return@onKeyEvent false
                }
                
                if (event.key == Key.DirectionLeft && focusZone == SeriesFocusZone.CONTENT) {
                    // MediaCategoryContent didn't consume it (from CATEGORIES or SEARCH zones)
                    focusZone = SeriesFocusZone.SIDEBAR
                    return@onKeyEvent true
                }
                
                return@onKeyEvent false
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                when (event.key) {
                    Key.DirectionLeft -> {
                        // In SIDEBAR: consume the event (no further left navigation)
                        if (focusZone == SeriesFocusZone.SIDEBAR) {
                            return@onPreviewKeyEvent true
                        }
                        // In CONTENT: let MediaCategoryContent handle it
                        return@onPreviewKeyEvent false
                    }

                    Key.DirectionRight -> {
                        if (focusZone == SeriesFocusZone.SIDEBAR) {
                            focusZone = SeriesFocusZone.CONTENT
                            return@onPreviewKeyEvent true
                        }
                        return@onPreviewKeyEvent false
                    }

                    Key.DirectionUp -> {
                        if (focusZone == SeriesFocusZone.SIDEBAR && sidebarFocusIndex > 0) {
                            sidebarFocusIndex--
                            return@onPreviewKeyEvent true
                        }
                        return@onPreviewKeyEvent false
                    }

                    Key.DirectionDown -> {
                        if (focusZone == SeriesFocusZone.SIDEBAR && sidebarFocusIndex < maxSidebarIndex) {
                            sidebarFocusIndex++
                            return@onPreviewKeyEvent true
                        }
                        return@onPreviewKeyEvent false
                    }

                    Key.Enter, Key.DirectionCenter -> {
                        if (focusZone == SeriesFocusZone.SIDEBAR) {
                            if (hasProfile && sidebarFocusIndex == 0) {
                                onSwitchProfile()
                            } else {
                                val itemIndex = if (hasProfile) sidebarFocusIndex - 1 else sidebarFocusIndex
                                when (SidebarItem.entries.getOrNull(itemIndex)) {
                                    SidebarItem.SEARCH -> onNavigateToSearch()
                                    SidebarItem.HOME -> onNavigateToHome()
                                    SidebarItem.MOVIES -> onNavigateToMovies()
                                    SidebarItem.SERIES -> {} // Already on series
                                    SidebarItem.WATCHLIST -> onNavigateToWatchlist()
                                    SidebarItem.TV -> onNavigateToTv(null, null)
                                    SidebarItem.SETTINGS -> onNavigateToSettings()
                                    else -> {}
                                }
                            }
                            return@onPreviewKeyEvent true
                        }
                        return@onPreviewKeyEvent false
                    }

                    Key.Back, Key.Escape -> {
                        if (focusZone != SeriesFocusZone.SIDEBAR) {
                            focusZone = SeriesFocusZone.SIDEBAR
                            return@onPreviewKeyEvent true
                        }
                        return@onPreviewKeyEvent false
                    }

                    else -> return@onPreviewKeyEvent false
                }
            }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Sidebar(
                selectedItem = SidebarItem.SERIES,
                isSidebarFocused = focusZone == SeriesFocusZone.SIDEBAR,
                focusedIndex = sidebarFocusIndex,
                profile = currentProfile,
                onProfileClick = onSwitchProfile
            )

            MediaCategoryContent(
                uiState = uiState,
                isLandscape = isLandscape,
                onCategorySearchChange = { viewModel.setCategorySearchQuery(it) },
                onItemSearchChange = { viewModel.setItemSearchQuery(it) },
                onSelectCategory = { viewModel.selectCategory(it) },
                onToggleFavorite = { viewModel.toggleFavoriteCategory(it) },
                onItemClick = { item ->
                    if (!isLoadingSeriesInfo) {
                        selectedSeriesForNavigation = item
                    }
                },
                onRefresh = { viewModel.refresh() },
                onNavigateLeft = { focusZone = SeriesFocusZone.SIDEBAR }
            )
        }

        TopBarClock(modifier = Modifier.align(Alignment.TopEnd))
    }
}
