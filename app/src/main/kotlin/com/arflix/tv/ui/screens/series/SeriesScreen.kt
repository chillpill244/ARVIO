@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.arflix.tv.ui.screens.series

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.Profile
import com.arflix.tv.ui.components.AppTopBar
import com.arflix.tv.ui.components.AppTopBarContentTopInset
import com.arflix.tv.ui.components.CardLayoutMode
import com.arflix.tv.ui.components.LoadingIndicator
import com.arflix.tv.ui.components.MediaCard
import com.arflix.tv.ui.components.SidebarItem
import com.arflix.tv.ui.components.rememberCardLayoutMode
import com.arflix.tv.ui.components.topBarFocusedItem
import com.arflix.tv.ui.components.topBarMaxIndex
import com.arflix.tv.ui.components.topBarSelectedIndex
import com.arflix.tv.ui.screens.shared.MediaCategoryContent
import com.arflix.tv.ui.theme.BackgroundDark
import com.arflix.tv.ui.theme.Pink
import com.arflix.tv.ui.theme.TextPrimary
import com.arflix.tv.ui.theme.TextSecondary
import com.arflix.tv.util.LocalDeviceType

private enum class SeriesFocusZone {
    TOP_BAR,
    CONTENT
}


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeriesScreen(
    viewModel: SeriesViewModel = hiltViewModel(),
    currentProfile: Profile? = null,
    contentStartPadding: Dp = 0.dp,
    onNavigateToHome: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToWatchlist: () -> Unit = {},
    onNavigateToTv: (channelId: String?, streamUrl: String?) -> Unit = { _, _ -> },
    onNavigateToMovies: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDetails: (MediaItem) -> Unit = {},
    onNavigateToContentMenu: () -> Unit = {},
    focusTopBar: Boolean = false,
    onTopBarFocused: () -> Unit = {},
    onSwitchProfile: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val cardLayoutMode = rememberCardLayoutMode()
    val isLandscape = cardLayoutMode == CardLayoutMode.LANDSCAPE
    val hasProfile = currentProfile != null

    var focusZone by rememberSaveable { mutableStateOf(SeriesFocusZone.CONTENT) }
    var sidebarFocusIndex by rememberSaveable { mutableIntStateOf(topBarSelectedIndex(SidebarItem.TV, hasProfile)) }
    val maxSidebarIndex = remember(hasProfile) { topBarMaxIndex(hasProfile) }

    var isLoadingSeriesInfo by rememberSaveable { mutableStateOf(false) }
    var selectedSeriesForNavigation by rememberSaveable { mutableStateOf<MediaItem?>(null) }

    LaunchedEffect(focusTopBar) {
        if (focusTopBar) {
            focusZone = SeriesFocusZone.TOP_BAR
            sidebarFocusIndex = topBarSelectedIndex(SidebarItem.TV, hasProfile)
            onTopBarFocused()
        }
    }

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
                    onNavigateToContentMenu()
                    return@onKeyEvent true
                }

                return@onKeyEvent false
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                // Handle top bar navigation first
                if (focusZone == SeriesFocusZone.TOP_BAR) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (sidebarFocusIndex > 0) sidebarFocusIndex--
                            return@onPreviewKeyEvent true
                        }
                        Key.DirectionRight -> {
                            if (sidebarFocusIndex < maxSidebarIndex) sidebarFocusIndex++
                            return@onPreviewKeyEvent true
                        }
                        Key.DirectionDown -> {
                            focusZone = SeriesFocusZone.CONTENT
                            return@onPreviewKeyEvent true
                        }
                        Key.DirectionUp -> return@onPreviewKeyEvent true
                        Key.Enter, Key.DirectionCenter -> {
                            when (topBarFocusedItem(sidebarFocusIndex, hasProfile)) {
                                SidebarItem.HOME -> onNavigateToHome()
                                SidebarItem.SEARCH -> onNavigateToSearch()
                                SidebarItem.WATCHLIST -> onNavigateToWatchlist()
                                SidebarItem.TV -> onNavigateToTv(null, null)
                                SidebarItem.SETTINGS -> onNavigateToSettings()
                                null -> onSwitchProfile()
                                else -> Unit
                            }
                            return@onPreviewKeyEvent true
                        }
                        Key.Back, Key.Escape -> {
                            focusZone = SeriesFocusZone.CONTENT
                            return@onPreviewKeyEvent true
                        }
                        else -> return@onPreviewKeyEvent false
                    }
                }

                when (event.key) {
                    Key.DirectionLeft -> {
                        when (focusZone) {
                            SeriesFocusZone.TOP_BAR -> return@onPreviewKeyEvent true
                            SeriesFocusZone.CONTENT -> return@onPreviewKeyEvent false
                        }
                    }

                    Key.DirectionRight -> {
                        when (focusZone) {
                            SeriesFocusZone.TOP_BAR -> return@onPreviewKeyEvent true
                            SeriesFocusZone.CONTENT -> return@onPreviewKeyEvent false
                        }
                    }

                    Key.DirectionUp -> {
                        when (focusZone) {
                            SeriesFocusZone.TOP_BAR -> return@onPreviewKeyEvent true
                            SeriesFocusZone.CONTENT -> return@onPreviewKeyEvent false
                        }
                    }

                    Key.DirectionDown -> {
                        when (focusZone) {
                            SeriesFocusZone.TOP_BAR -> return@onPreviewKeyEvent true
                            SeriesFocusZone.CONTENT -> return@onPreviewKeyEvent false
                        }
                    }

                    Key.Enter, Key.DirectionCenter -> {
                        when (focusZone) {
                            SeriesFocusZone.TOP_BAR -> return@onPreviewKeyEvent true
                            SeriesFocusZone.CONTENT -> return@onPreviewKeyEvent false
                        }
                    }

                    Key.Back, Key.Escape -> {
                        when (focusZone) {
                            SeriesFocusZone.TOP_BAR -> return@onPreviewKeyEvent false
                            SeriesFocusZone.CONTENT -> {
                                onNavigateToContentMenu()
                                return@onPreviewKeyEvent true
                            }
                        }
                    }

                    else -> return@onPreviewKeyEvent false
                }
            }
    ) {
        if (LocalDeviceType.current.isTouchDevice()) {
            MobileSeriesLayout(
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
                onNavigateToMovies = onNavigateToMovies,
                onNavigateToTv = { onNavigateToTv(null, null) }
            )
        } else {
            AppTopBar(
                selectedItem = SidebarItem.TV,
                isFocused = focusZone == SeriesFocusZone.TOP_BAR,
                focusedIndex = sidebarFocusIndex,
                profile = currentProfile
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = (AppTopBarContentTopInset - 14.dp), start = contentStartPadding)
            ) {
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
                    onNavigateLeft = { onNavigateToContentMenu() },
                    onNavigateUp = {
                        focusZone = SeriesFocusZone.TOP_BAR
                        sidebarFocusIndex = topBarSelectedIndex(SidebarItem.TV, hasProfile)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MobileSeriesLayout(
    uiState: com.arflix.tv.ui.screens.shared.MediaCategoryUiState,
    isLandscape: Boolean,
    onCategorySearchChange: (String) -> Unit,
    onItemSearchChange: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onRefresh: () -> Unit,
    onNavigateToMovies: () -> Unit,
    onNavigateToTv: () -> Unit
) {
    var showCategories by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Top tab chips: Movies / TV / Series
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Movies", "TV", "Series").forEachIndexed { index, label ->
                val isSelected = index == 2
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) Pink else Color.White.copy(alpha = 0.08f))
                        .clickable {
                            when (index) {
                                0 -> onNavigateToMovies()
                                1 -> onNavigateToTv()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label,
                        style = com.arflix.tv.ui.theme.ArflixTypography.button,
                        color = if (isSelected) Color.Black else TextSecondary
                    )
                }
            }
        }

        // Content area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (showCategories) {
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                } else {
                    // Category list with search
                    Column(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
                        // Search bar
                        OutlinedTextField(
                            value = uiState.categorySearchQuery,
                            onValueChange = onCategorySearchChange,
                            placeholder = { androidx.compose.material3.Text("Search categories", color = TextSecondary.copy(alpha = 0.5f)) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary) },
                            trailingIcon = {
                                if (uiState.categorySearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onCategorySearchChange("") }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = TextSecondary)
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White.copy(alpha = 0.7f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                cursorColor = Color.White,
                                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                                focusedLeadingIconColor = TextSecondary,
                                unfocusedLeadingIconColor = TextSecondary,
                                focusedTrailingIconColor = TextSecondary,
                                unfocusedTrailingIconColor = TextSecondary,
                                focusedPlaceholderColor = TextSecondary.copy(alpha = 0.5f),
                                unfocusedPlaceholderColor = TextSecondary.copy(alpha = 0.5f),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(uiState.filteredAndSortedCategories) { category ->
                                val isFav = uiState.favoriteCategories.contains(category)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                onSelectCategory(category)
                                                showCategories = false
                                            },
                                            onLongClick = { onToggleFavorite(category) }
                                        )
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = category,
                                        style = com.arflix.tv.ui.theme.ArflixTypography.body,
                                        color = if (isFav) Pink else TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { onToggleFavorite(category) },
                                        modifier = Modifier.width(36.dp).height(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFav) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                            contentDescription = if (isFav) "Unfavorite" else "Favorite",
                                            tint = if (isFav) Pink else TextSecondary.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                                androidx.compose.material3.Divider(
                                    color = Color.White.copy(alpha = 0.06f)
                                )
                            }
                        }
                    }
                }
            } else {
                // Items grid with search bar + swipe-right to show categories
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                if (dragAmount > 40f) showCategories = true
                            }
                        }
                ) {
                    // Hamburger + item search bar row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showCategories = true }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "Show categories",
                                tint = TextSecondary
                            )
                        }
                        OutlinedTextField(
                            value = uiState.itemSearchQuery,
                            onValueChange = onItemSearchChange,
                            placeholder = { androidx.compose.material3.Text("Search in ${uiState.selectedCategory}", color = TextSecondary.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary) },
                            trailingIcon = {
                                if (uiState.itemSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onItemSearchChange("") }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = TextSecondary)
                                    }
                                }
                            },
                            singleLine = true,
                            maxLines = 1,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White.copy(alpha = 0.7f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                cursorColor = Color.White,
                                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                                focusedLeadingIconColor = TextSecondary,
                                unfocusedLeadingIconColor = TextSecondary,
                                focusedTrailingIconColor = TextSecondary,
                                unfocusedTrailingIconColor = TextSecondary,
                                focusedPlaceholderColor = TextSecondary.copy(alpha = 0.5f),
                                unfocusedPlaceholderColor = TextSecondary.copy(alpha = 0.5f),
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (uiState.isLoading) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                    } else if (uiState.displayedItems.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (uiState.itemSearchQuery.isNotEmpty()) "No items match your search" else "No items in this category",
                                style = com.arflix.tv.ui.theme.ArflixTypography.body,
                                color = TextSecondary
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(top = 32.dp, bottom = 4.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)
                        ) {
                            items(uiState.displayedItems, key = { "${it.iptvSeriesId ?: it.id}_${it.title}" }) { item ->
                                MediaCard(
                                    item = item,
                                    isLandscape = isLandscape,
                                    onClick = { onItemClick(item) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
