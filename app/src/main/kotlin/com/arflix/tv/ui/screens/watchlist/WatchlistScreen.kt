package com.arflix.tv.ui.screens.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.ui.components.AppTopBar
import com.arflix.tv.ui.components.AppTopBarContentTopInset
import com.arflix.tv.util.LocalDeviceType
import com.arflix.tv.ui.components.LoadingIndicator
import com.arflix.tv.ui.components.CardContent
import com.arflix.tv.ui.components.MediaCard
import com.arflix.tv.ui.components.SidebarItem
import com.arflix.tv.ui.components.Toast
import com.arflix.tv.ui.components.ToastType as ComponentToastType
import com.arflix.tv.ui.components.topBarFocusedItem
import com.arflix.tv.ui.components.topBarMaxIndex
import com.arflix.tv.ui.components.CardLayoutMode
import com.arflix.tv.ui.components.rememberCardLayoutMode
import com.arflix.tv.ui.theme.ArflixTypography
import com.arflix.tv.ui.theme.BackgroundDark
import com.arflix.tv.ui.theme.Pink
import com.arflix.tv.ui.theme.TextPrimary
import com.arflix.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import android.os.SystemClock

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: WatchlistViewModel = hiltViewModel(),
    currentProfile: com.arflix.tv.data.model.Profile? = null,
    onNavigateToDetails: (MediaType, Int) -> Unit = { _, _ -> },
    onNavigateToHome: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToMovies: () -> Unit = {},
    onNavigateToSeries: () -> Unit = {},
    onNavigateToTv: (String?, String?) -> Unit = { _, _ -> },
    onNavigateToSettings: () -> Unit = {},
    onSwitchProfile: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val cardLayoutMode = rememberCardLayoutMode()
    val usePosterCards = cardLayoutMode == CardLayoutMode.POSTER
    val cardWidth = if (usePosterCards) 140.dp else 200.dp
    
    val isTouchDevice = LocalDeviceType.current.isTouchDevice()
    var isSidebarFocused by remember { mutableStateOf(false) }
    val hasProfile = currentProfile != null
    val maxSidebarIndex = topBarMaxIndex(hasProfile)
    var sidebarFocusIndex by remember { mutableIntStateOf(if (hasProfile) 5 else 4) } // WATCHLIST
    val rootFocusRequester = remember { FocusRequester() }
    var focusedSectionIndex by remember { mutableIntStateOf(0) }
    var focusedItemIndex by remember { mutableIntStateOf(0) }
    var enterKeyDownTimeMs by remember { mutableLongStateOf(0L) }
    val longPressThresholdMs = 500L
    val lazyColumnState = rememberLazyListState()
    
    // Helper to detect IPTV items
    val isIptvItem: (com.arflix.tv.data.model.MediaItem) -> Boolean = { item: com.arflix.tv.data.model.MediaItem ->
        item.status?.startsWith("iptv:") == true
    }
    
    // Helper to extract IPTV channel ID from status field
    val getIptvChannelId: (com.arflix.tv.data.model.MediaItem) -> String? = { item: com.arflix.tv.data.model.MediaItem ->
        item.status?.removePrefix("iptv:")
    }
    
    // Get the sections that have content
    val sections = listOf(
        Pair("movies", uiState.movies),
        Pair("series", uiState.series),
        Pair("livetv", uiState.liveTv)
    ).filter { it.second.isNotEmpty() }
    
    // Helper to get focused item
    val getFocusedItem: () -> com.arflix.tv.data.model.MediaItem? = {
        if (focusedSectionIndex < sections.size && focusedItemIndex < sections[focusedSectionIndex].second.size) {
            sections[focusedSectionIndex].second[focusedItemIndex]
        } else null
    }

    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
    }

    // Reset focus to top when content becomes available or changes
    LaunchedEffect(sections.size, uiState.movies.size, uiState.series.size) {
        if (sections.isNotEmpty() && !isSidebarFocused) {
            // Reset to first section and first item when data changes
            if (focusedSectionIndex >= sections.size) {
                focusedSectionIndex = 0
                focusedItemIndex = 0
            }
        }
    }

    // Scroll to top when content loads or screen is first composed
    LaunchedEffect(sections.size) {
        if (sections.isNotEmpty()) {
            // Always scroll to top when sections are populated
            lazyColumnState.scrollToItem(0)
        }
    }

    // Auto-scroll LazyColumn when navigating between sections
    LaunchedEffect(focusedSectionIndex, sections.size) {
        if (!isSidebarFocused && sections.isNotEmpty() && focusedSectionIndex < sections.size) {
            lazyColumnState.animateScrollToItem(focusedSectionIndex)
        }
    }

    val totalItems = uiState.movies.size + uiState.series.size + uiState.liveTv.size
    LaunchedEffect(uiState.isLoading, totalItems) {
        if (!uiState.isLoading && totalItems == 0) {
            // Empty screen must always have a deterministic focus target.
            isSidebarFocused = true
            sidebarFocusIndex = if (hasProfile) 5 else SidebarItem.WATCHLIST.ordinal
        } else if (!uiState.isLoading && totalItems > 0 && !isSidebarFocused) {
            // Ensure first card can receive focus when content becomes available.
            delay(80)
            runCatching { rootFocusRequester.requestFocus() }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Back, Key.Escape -> {
                            if (isSidebarFocused) {
                                onBack()
                            } else {
                                isSidebarFocused = true
                            }
                            true
                        }
                        Key.DirectionLeft -> {
                            if (!isSidebarFocused) {
                                if (focusedItemIndex > 0) {
                                    focusedItemIndex--
                                    true
                                } else {
                                    isSidebarFocused = true
                                    true
                                }
                            } else {
                                if (sidebarFocusIndex > 0) {
                                    sidebarFocusIndex = (sidebarFocusIndex - 1).coerceIn(0, maxSidebarIndex)
                                }
                                true
                            }
                        }
                        Key.DirectionRight -> {
                            if (!isSidebarFocused) {
                                val currentSection = sections.getOrNull(focusedSectionIndex)
                                if (currentSection != null && focusedItemIndex < currentSection.second.size - 1) {
                                    focusedItemIndex++
                                    true
                                } else {
                                    false
                                }
                            } else {
                                if (sidebarFocusIndex < maxSidebarIndex) {
                                    sidebarFocusIndex = (sidebarFocusIndex + 1).coerceIn(0, maxSidebarIndex)
                                }
                                true
                            }
                        }
                        Key.DirectionUp -> {
                            if (isSidebarFocused) {
                                true
                            } else if (focusedSectionIndex > 0) {
                                focusedSectionIndex--
                                focusedItemIndex = 0
                                true
                            } else {
                                isSidebarFocused = true
                                true
                            }
                        }
                        Key.DirectionDown -> {
                            if (isSidebarFocused) {
                                if (totalItems > 0) {
                                    isSidebarFocused = false
                                    focusedSectionIndex = 0
                                    focusedItemIndex = 0
                                }
                                true
                            } else if (focusedSectionIndex < sections.size - 1) {
                                focusedSectionIndex++
                                focusedItemIndex = 0
                                true
                            } else {
                                false
                            }
                        }
                        Key.Enter, Key.DirectionCenter -> {
                            if (isSidebarFocused) {
                                if (hasProfile && sidebarFocusIndex == 0) {
                                    onSwitchProfile()
                                } else {
                                    when (topBarFocusedItem(sidebarFocusIndex, hasProfile)) {
                                        SidebarItem.SEARCH -> onNavigateToSearch()
                                        SidebarItem.HOME -> onNavigateToHome()
                                        SidebarItem.MOVIES -> onNavigateToMovies()
                                        SidebarItem.SERIES -> onNavigateToSeries()
                                        SidebarItem.WATCHLIST -> { }
                                        SidebarItem.TV -> onNavigateToTv(null, null)
                                        SidebarItem.SETTINGS -> onNavigateToSettings()
                                        null -> Unit
                                    }
                                }
                                true
                            } else {
                                // Record when Enter key was pressed (for long-press detection)
                                enterKeyDownTimeMs = SystemClock.elapsedRealtime()
                                true
                            }
                        }
                        else -> false
                    }
                } else if (event.type == KeyEventType.KeyUp) {
                    when (event.key) {
                        Key.Enter, Key.DirectionCenter -> {
                            if (!isSidebarFocused) {
                                // Check if Enter was held long enough for long-press
                                val holdDurationMs = SystemClock.elapsedRealtime() - enterKeyDownTimeMs
                                val isLongPress = holdDurationMs >= longPressThresholdMs
                                
                                val focusedItem = getFocusedItem()
                                if (focusedItem != null) {
                                    if (isLongPress) {
                                        // Long-press: show removal prompt
                                        // viewModel.removeFromWatchlist(focusedItem)
                                    } else {
                                        // Short-press: navigate to details or TV screen
                                        if (isIptvItem(focusedItem)) {
                                            val channelId = getIptvChannelId(focusedItem)
                                            onNavigateToTv(channelId, null)
                                        } else {
                                            onNavigateToDetails(focusedItem.mediaType, focusedItem.id)
                                        }
                                    }
                                }
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        if (!LocalDeviceType.current.isTouchDevice()) {
            AppTopBar(
                selectedItem = SidebarItem.WATCHLIST,
                isFocused = isSidebarFocused,
                focusedIndex = sidebarFocusIndex,
                profile = currentProfile
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!isTouchDevice) Modifier.padding(top = AppTopBarContentTopInset) else Modifier)
                .padding(start = 24.dp, top = 24.dp, end = 48.dp)
        ) {
                // Header with pink bookmark icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bookmark,
                        contentDescription = null,
                        tint = Pink,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "MY WATCHLIST",
                        style = ArflixTypography.sectionTitle,
                        color = TextPrimary
                    )
                }
                
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator(color = Pink, size = 64.dp)
                        }
                    }
                    totalItems == 0 -> {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Bookmark,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.size(80.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Your watchlist is empty",
                                    style = ArflixTypography.body,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Add movies and shows to watch later",
                                    style = ArflixTypography.caption,
                                    color = Color.White.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                    else -> {
                        // Two horizontal sections for Movies and Series
                        LazyColumn(
                            state = lazyColumnState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .focusable(),
                            contentPadding = PaddingValues(bottom = 48.dp),
                            verticalArrangement = Arrangement.spacedBy(32.dp),
                            userScrollEnabled = isTouchDevice
                        ) {
                            itemsIndexed(
                                items = sections,
                                key = { _, (type, _) -> type },
                                contentType = { _, _ -> "watchlist_section" }
                            ) { sectionIdx, (sectionType, items) ->
                                val title = when (sectionType) {
                                    "movies" -> "Movies"
                                    "series" -> "Series"
                                    "livetv" -> "Favorite TV"
                                    else -> sectionType.replaceFirstChar { it.uppercase() }
                                }
                                val isThisSectionFocused = focusedSectionIndex == sectionIdx && !isSidebarFocused
                                
                                WatchlistItemsSection(
                                    title = title,
                                    items = items,
                                    cardWidth = cardWidth,
                                    isLandscape = !usePosterCards,
                                    onItemClick = { item ->
                                        if (isIptvItem(item)) {
                                            // Navigate to TV screen with this channel
                                            val channelId = getIptvChannelId(item)
                                            onNavigateToTv(channelId, null)  // streamUrl will be looked up in TvScreen
                                        } else {
                                            // Navigate to details for regular items
                                            onNavigateToDetails(item.mediaType, item.id)
                                        }
                                    },
                                    // onItemLongPress = { viewModel.removeFromWatchlist(it) },
                                    focusedItemIndex = if (isThisSectionFocused) focusedItemIndex else -1,
                                    onItemFocused = { index -> 
                                        if (!isSidebarFocused && focusedSectionIndex == sectionIdx) {
                                            focusedItemIndex = index
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

        // Toast notification
        uiState.toastMessage?.let { message ->
            Toast(
                message = message,
                type = when (uiState.toastType) {
                    ToastType.SUCCESS -> ComponentToastType.SUCCESS
                    ToastType.ERROR -> ComponentToastType.ERROR
                    ToastType.INFO -> ComponentToastType.INFO
                },
                isVisible = true,
                onDismiss = { viewModel.dismissToast() }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun WatchlistItemsSection(
    title: String,
    items: List<com.arflix.tv.data.model.MediaItem>,
    cardWidth: Dp,
    isLandscape: Boolean,
    onItemClick: (com.arflix.tv.data.model.MediaItem) -> Unit,
    onItemLongPress: (com.arflix.tv.data.model.MediaItem) -> Unit = { },
    focusedItemIndex: Int = -1,
    onItemFocused: (Int) -> Unit = { }
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = ArflixTypography.sectionTitle,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        val lazyListState = rememberLazyListState()
        LazyRow(
            state = lazyListState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> "${item.mediaType.name}-${item.id}" },
                contentType = { _, item -> "${item.mediaType.name}_card" }
            ) { index, item ->
                val itemIsFocused = index == focusedItemIndex && focusedItemIndex >= 0
                MediaCard(
                    content = CardContent.Media(item),
                    width = cardWidth,
                    isLandscape = isLandscape,
                    isFocusedOverride = itemIsFocused,
                    enableSystemFocus = false,
                    onFocused = { onItemFocused(index) },
                    onClick = { onItemClick(item) },
                    onLongPress = { onItemLongPress(item) }
                )
            }
        }
    }
}


