package com.arflix.tv.ui.screens.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.hilt.navigation.compose.hiltViewModel
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.Profile
import com.arflix.tv.ui.components.AppTopBarContentTopInset
import com.arflix.tv.ui.screens.movies.MoviesScreen
import com.arflix.tv.ui.screens.series.SeriesScreen
import com.arflix.tv.ui.screens.tv.shared.ContentHubTab
import com.arflix.tv.ui.screens.tv.shared.ContentMenuPanel
import com.arflix.tv.util.LocalDeviceType

@Composable
fun ContentHubScreen(
    currentProfile: Profile? = null,
    initialChannelId: String? = null,
    initialStreamUrl: String? = null,
    onNavigateToHome: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToWatchlist: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDetails: (MediaItem) -> Unit = {},
    onSwitchProfile: () -> Unit = {},
    onFullscreenChanged: (Boolean) -> Unit = {},
    onBack: () -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(ContentHubTab.TV) }
    var contentMenuIndex by rememberSaveable { mutableIntStateOf(ContentHubTab.TV) }
    var isContentMenuFocused by rememberSaveable { mutableStateOf(false) }
    var focusTopBar by remember { mutableStateOf(false) }
    val isMobile = LocalDeviceType.current.isTouchDevice()

    // Hoisted so TvViewModel survives tab switches — prevents EPG from reloading on every TV tab visit
    val tvViewModel: TvViewModel = hiltViewModel()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (!isContentMenuFocused) return@onPreviewKeyEvent false

                when (event.key) {
                    Key.DirectionUp -> {
                        if (contentMenuIndex > 0) contentMenuIndex--
                        else { isContentMenuFocused = false; focusTopBar = true }
                        true
                    }
                    Key.DirectionDown -> {
                        if (contentMenuIndex < 2) contentMenuIndex++
                        true
                    }
                    Key.DirectionRight -> {
                        isContentMenuFocused = false
                        true
                    }
                    Key.DirectionLeft -> true // nothing to the left, consume
                    Key.Enter, Key.DirectionCenter -> {
                        selectedTab = contentMenuIndex
                        isContentMenuFocused = false
                        true
                    }
                    Key.Back, Key.Escape -> {
                        isContentMenuFocused = false
                        onBack()
                        true
                    }
                    else -> false
                }
            }
    ) {
        val panelWidth = if (!isMobile) 72.dp else 0.dp

        when (selectedTab) {
            ContentHubTab.MOVIES -> MoviesScreen(
                contentStartPadding = panelWidth,
                currentProfile = currentProfile,
                onNavigateToHome = onNavigateToHome,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToWatchlist = onNavigateToWatchlist,
                onNavigateToTv = { _, _ -> selectedTab = ContentHubTab.TV },
                onNavigateToSeries = { selectedTab = ContentHubTab.SERIES },
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToDetails = onNavigateToDetails,
                onNavigateToContentMenu = { contentMenuIndex = ContentHubTab.MOVIES; isContentMenuFocused = true },
                focusTopBar = focusTopBar,
                onTopBarFocused = { focusTopBar = false },
                onSwitchProfile = onSwitchProfile,
                onBack = onBack,
            )

            ContentHubTab.TV -> TvScreen(
                contentStartPadding = panelWidth,
                viewModel = tvViewModel,
                currentProfile = currentProfile,
                initialChannelId = initialChannelId,
                initialStreamUrl = initialStreamUrl,
                onNavigateToMovies = { selectedTab = ContentHubTab.MOVIES },
                onNavigateToSeries = { selectedTab = ContentHubTab.SERIES },
                onNavigateToHome = onNavigateToHome,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToWatchlist = onNavigateToWatchlist,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToContentMenu = { contentMenuIndex = ContentHubTab.TV; isContentMenuFocused = true },
                focusTopBar = focusTopBar,
                onTopBarFocused = { focusTopBar = false },
                onSwitchProfile = onSwitchProfile,
                onFullscreenChanged = onFullscreenChanged,
                onBack = onBack,
            )

            else -> SeriesScreen(
                contentStartPadding = panelWidth,
                currentProfile = currentProfile,
                onNavigateToHome = onNavigateToHome,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToWatchlist = onNavigateToWatchlist,
                onNavigateToTv = { _, _ -> selectedTab = ContentHubTab.TV },
                onNavigateToMovies = { selectedTab = ContentHubTab.MOVIES },
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToDetails = onNavigateToDetails,
                onNavigateToContentMenu = { contentMenuIndex = ContentHubTab.SERIES; isContentMenuFocused = true },
                focusTopBar = focusTopBar,
                onTopBarFocused = { focusTopBar = false },
                onSwitchProfile = onSwitchProfile,
                onBack = onBack,
            )
        }

        if (!isMobile) {
            ContentMenuPanel(
                focusedIndex = contentMenuIndex,
                isFocused = isContentMenuFocused,
                activeIndex = selectedTab,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(top = (AppTopBarContentTopInset - 14.dp)),
            )
        }
    }
}
