package com.arflix.tv.ui.screens.tv

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Text
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.Profile
import com.arflix.tv.ui.components.AppTopBarContentTopInset
import com.arflix.tv.ui.screens.movies.MoviesScreen
import com.arflix.tv.ui.screens.series.SeriesScreen
import com.arflix.tv.ui.screens.tv.shared.ContentHubTab
import com.arflix.tv.ui.screens.tv.shared.ContentMenuPanel
import com.arflix.tv.ui.theme.ArflixTypography
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
    var isTvImmersive by remember { mutableStateOf(false) }
    val isMobile = LocalDeviceType.current.isTouchDevice()

    // Hoisted so TvViewModel survives tab switches — prevents EPG from reloading on every TV tab visit
    val tvViewModel: TvViewModel = hiltViewModel()

    if (isMobile) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isTvImmersive) {
                MobileContentHubPill(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
            }
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    ContentHubTab.MOVIES -> MoviesScreen(
                        contentStartPadding = 0.dp,
                        currentProfile = currentProfile,
                        onNavigateToHome = onNavigateToHome,
                        onNavigateToSearch = onNavigateToSearch,
                        onNavigateToWatchlist = onNavigateToWatchlist,
                        onNavigateToTv = { _, _ -> selectedTab = ContentHubTab.TV },
                        onNavigateToSeries = { selectedTab = ContentHubTab.SERIES },
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToDetails = onNavigateToDetails,
                        onNavigateToContentMenu = {},
                        focusTopBar = focusTopBar,
                        onTopBarFocused = { focusTopBar = false },
                        onSwitchProfile = onSwitchProfile,
                        onBack = onBack,
                    )
                    ContentHubTab.TV -> TvScreen(
                        contentStartPadding = 0.dp,
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
                        onNavigateToContentMenu = {},
                        focusTopBar = focusTopBar,
                        onTopBarFocused = { focusTopBar = false },
                        onSwitchProfile = onSwitchProfile,
                        onFullscreenChanged = { immersive ->
                            isTvImmersive = immersive
                            onFullscreenChanged(immersive)
                        },
                        onBack = onBack,
                    )
                    else -> SeriesScreen(
                        contentStartPadding = 0.dp,
                        currentProfile = currentProfile,
                        onNavigateToHome = onNavigateToHome,
                        onNavigateToSearch = onNavigateToSearch,
                        onNavigateToWatchlist = onNavigateToWatchlist,
                        onNavigateToTv = { _, _ -> selectedTab = ContentHubTab.TV },
                        onNavigateToMovies = { selectedTab = ContentHubTab.MOVIES },
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToDetails = onNavigateToDetails,
                        onNavigateToContentMenu = {},
                        focusTopBar = focusTopBar,
                        onTopBarFocused = { focusTopBar = false },
                        onSwitchProfile = onSwitchProfile,
                        onBack = onBack,
                    )
                }
            }
        }
    } else {
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
                        Key.DirectionLeft -> true
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
            when (selectedTab) {
                ContentHubTab.MOVIES -> MoviesScreen(
                    contentStartPadding = 72.dp,
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
                    contentStartPadding = 72.dp,
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
                    contentStartPadding = 72.dp,
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

@Composable
private fun MobileContentHubPill(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(4.dp)
    ) {
        listOf("Movies", "TV", "Series").forEachIndexed { index, label ->
            val isSelected = selectedTab == index
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else Color.Transparent,
                animationSpec = tween(durationMillis = 180),
                label = "tabBg$index"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color(0xFF111111) else Color.White.copy(alpha = 0.45f),
                animationSpec = tween(durationMillis = 180),
                label = "tabText$index"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(bgColor)
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = ArflixTypography.label.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = textColor
                )
            }
        }
    }
}
