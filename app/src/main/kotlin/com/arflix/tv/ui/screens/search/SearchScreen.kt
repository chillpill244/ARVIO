package com.arflix.tv.ui.screens.search

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.arflix.tv.data.model.IptvChannel
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.ui.components.LoadingIndicator
import com.arflix.tv.ui.components.CardLayoutMode
import com.arflix.tv.ui.components.AppTopBar
import com.arflix.tv.ui.components.AppTopBarContentTopInset
import com.arflix.tv.ui.components.CardContent
import com.arflix.tv.ui.components.MediaCard
import com.arflix.tv.ui.components.SidebarItem
import com.arflix.tv.ui.components.topBarFocusedItem
import com.arflix.tv.ui.components.topBarMaxIndex
import com.arflix.tv.ui.components.rememberCardLayoutMode
import com.arflix.tv.ui.skin.ArvioSkin
import com.arflix.tv.ui.theme.ArflixTypography
import com.arflix.tv.ui.theme.BackgroundCard
import com.arflix.tv.ui.theme.BackgroundDark
import com.arflix.tv.ui.theme.Pink
import com.arflix.tv.ui.theme.TextPrimary
import com.arflix.tv.ui.theme.TextSecondary
import com.arflix.tv.util.LocalDeviceType

/**
 * Represents which result category is currently focused
 */
sealed class SearchResultCategory {
    object Movies : SearchResultCategory()
    object TvShows : SearchResultCategory()
    object LiveTv : SearchResultCategory()
}

/**
 * Search screen with centered search bar and separate Movies/TV Shows rows
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    currentProfile: com.arflix.tv.data.model.Profile? = null,
    onNavigateToDetails: (MediaType, Int) -> Unit = { _, _ -> },
    onNavigateToPlayer: (MediaType, Int, Int?, Int?, String?, String?, String?, String?, Long?) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onNavigateToHome: () -> Unit = {},
    onNavigateToMovies: () -> Unit = {},
    onNavigateToSeries: () -> Unit = {},
    onNavigateToWatchlist: () -> Unit = {},
    onNavigateToTv: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onSwitchProfile: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val usePosterCards = rememberCardLayoutMode() == CardLayoutMode.POSTER
    val configuration = LocalConfiguration.current
    val isCompactHeight = configuration.screenHeightDp <= 780
    val isTouchDevice = LocalDeviceType.current.isTouchDevice()
    val searchBarWidth = if (isTouchDevice) {
        (configuration.screenWidthDp.dp * 0.9f).coerceIn(280.dp, 760.dp)
    } else {
        (configuration.screenWidthDp.dp * 0.56f).coerceIn(500.dp, 760.dp)
    }

    var focusZone by remember { mutableStateOf(FocusZone.SEARCH_INPUT) }
    val hasProfile = currentProfile != null
    val maxSidebarIndex = topBarMaxIndex(hasProfile)
    var sidebarFocusIndex by remember { mutableIntStateOf(if (hasProfile) 1 else 0) } // SEARCH
    var focusedCategory by remember { mutableStateOf<SearchResultCategory>(SearchResultCategory.Movies) }
    var movieItemIndex by remember { mutableIntStateOf(0) }
    var tvItemIndex by remember { mutableIntStateOf(0) }
    var livetvItemIndex by remember { mutableIntStateOf(0) }
    var isSearchInputFocused by remember { mutableStateOf(false) }
    var suppressSelectUntilMs by remember { mutableLongStateOf(0L) }
    
    // Helper to get visible/non-empty categories in order
    val visibleCategories = remember(uiState.movieResults.isEmpty(), uiState.tvResults.isEmpty(), uiState.livetvResults.isEmpty()) {
        listOfNotNull(
            SearchResultCategory.Movies.takeIf { uiState.movieResults.isNotEmpty() },
            SearchResultCategory.TvShows.takeIf { uiState.tvResults.isNotEmpty() },
            SearchResultCategory.LiveTv.takeIf { uiState.livetvResults.isNotEmpty() }
        )
    }
    
    // Ensure focused category is always valid
    LaunchedEffect(visibleCategories) {
        if (visibleCategories.isNotEmpty() && focusedCategory !in visibleCategories) {
            focusedCategory = visibleCategories.first()
        }
    }
    
    // Helper to navigate to next visible category
    fun tryNavigateToNextCategory(direction: Int): Boolean {
        if (visibleCategories.isEmpty()) return false
        val currentIndex = visibleCategories.indexOf(focusedCategory).takeIf { it >= 0 } ?: 0
        val nextIndex = (currentIndex + direction).coerceIn(-1, visibleCategories.size)
        return if (nextIndex in visibleCategories.indices) {
            focusedCategory = visibleCategories[nextIndex]
            true
        } else {
            false
        }
    }
    
    // Helper to get item index for a category
    fun getItemIndexForCategory(): Int = when (focusedCategory) {
        SearchResultCategory.Movies -> movieItemIndex
        SearchResultCategory.TvShows -> tvItemIndex
        SearchResultCategory.LiveTv -> livetvItemIndex
    }
    
    // Helper to update item index for a category
    fun updateItemIndexForCategory(value: Int) {
        when (focusedCategory) {
            SearchResultCategory.Movies -> movieItemIndex = value
            SearchResultCategory.TvShows -> tvItemIndex = value
            SearchResultCategory.LiveTv -> livetvItemIndex = value
        }
    }

    val searchFocusRequester = remember { FocusRequester() }
    val resultsFocusRequester = remember { FocusRequester() }
    val movieRowState = rememberTvLazyListState()
    val tvRowState = rememberTvLazyListState()
    val livetvRowState = rememberTvLazyListState()
    val resultsScrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-focus search input on launch
    LaunchedEffect(Unit) {
        searchFocusRequester.requestFocus()
        suppressSelectUntilMs = SystemClock.elapsedRealtime() + 300L
    }

    LaunchedEffect(uiState.movieResults) {
        if (movieItemIndex >= uiState.movieResults.size) {
            movieItemIndex = 0
        }
        if (uiState.movieResults.isNotEmpty()) {
            movieRowState.scrollToItem(0)
        }
    }

    LaunchedEffect(uiState.tvResults) {
        if (tvItemIndex >= uiState.tvResults.size) {
            tvItemIndex = 0
        }
        if (uiState.tvResults.isNotEmpty()) {
            tvRowState.scrollToItem(0)
        }
    }

    LaunchedEffect(uiState.livetvResults) {
        if (livetvItemIndex >= uiState.livetvResults.size) {
            livetvItemIndex = 0
        }
        if (uiState.livetvResults.isNotEmpty()) {
            livetvRowState.scrollToItem(0)
        }
    }

    // Auto-scroll results container when navigating between rows
    LaunchedEffect(focusZone, focusedCategory) {
        if (focusZone == FocusZone.RESULTS) {
            // Calculate scroll distance based on which categories are visible
            val categoryIndex = visibleCategories.indexOf(focusedCategory)
            val scrollDistance = (categoryIndex * 300).coerceAtLeast(0)  // ~300dp per row
            if (scrollDistance > 0) {
                resultsScrollState.animateScrollTo(scrollDistance)
            } else {
                resultsScrollState.animateScrollTo(0)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .let { modifier ->
                if (!isTouchDevice) {
                    modifier.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.Back, Key.Escape -> {
                                    when (focusZone) {
                                        FocusZone.SIDEBAR -> onBack()
                                        FocusZone.SEARCH_INPUT -> {
                                            focusZone = FocusZone.SIDEBAR
                                        }
                                        FocusZone.RESULTS -> {
                                            focusZone = FocusZone.SEARCH_INPUT
                                            searchFocusRequester.requestFocus()
                                        }
                                    }
                                    true
                                }
                                Key.DirectionLeft -> {
                                    when (focusZone) {
                                        FocusZone.SEARCH_INPUT -> {
                                            focusZone = FocusZone.SIDEBAR
                                            true
                                        }
                                        FocusZone.RESULTS -> {
                                            val currentIndex = getItemIndexForCategory()
                                            if (currentIndex == 0) {
                                                focusZone = FocusZone.SIDEBAR
                                            } else {
                                                updateItemIndexForCategory(currentIndex - 1)
                                            }
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                Key.DirectionRight -> {
                                    when (focusZone) {
                                        FocusZone.SIDEBAR -> {
                                            focusZone = FocusZone.SEARCH_INPUT
                                            searchFocusRequester.requestFocus()
                                            true
                                        }
                                        FocusZone.RESULTS -> {
                                            val maxIndex = when (focusedCategory) {
                                                SearchResultCategory.Movies -> uiState.movieResults.size - 1
                                                SearchResultCategory.TvShows -> uiState.tvResults.size - 1
                                                SearchResultCategory.LiveTv -> uiState.livetvResults.size - 1
                                            }
                                            val currentIndex = getItemIndexForCategory()
                                            if (currentIndex < maxIndex) {
                                                updateItemIndexForCategory(currentIndex + 1)
                                            }
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                Key.DirectionUp -> {
                                    when (focusZone) {
                                        FocusZone.SIDEBAR -> if (sidebarFocusIndex > 0) {
                                            sidebarFocusIndex = (sidebarFocusIndex - 1).coerceIn(0, maxSidebarIndex)
                                        }
                                        FocusZone.RESULTS -> {
                                            if (!tryNavigateToNextCategory(-1)) {
                                                focusZone = FocusZone.SEARCH_INPUT
                                                searchFocusRequester.requestFocus()
                                            }
                                        }
                                        else -> {}
                                    }
                                    true
                                }
                                Key.DirectionDown -> {
                                    when (focusZone) {
                                        FocusZone.SIDEBAR -> if (sidebarFocusIndex < maxSidebarIndex) {
                                            sidebarFocusIndex = (sidebarFocusIndex + 1).coerceIn(0, maxSidebarIndex)
                                        }
                                        FocusZone.SEARCH_INPUT -> {
                                            if (visibleCategories.isNotEmpty()) {
                                                focusZone = FocusZone.RESULTS
                                                focusedCategory = visibleCategories.first()
                                                resultsFocusRequester.requestFocus()
                                            }
                                        }
                                        FocusZone.RESULTS -> {
                                            tryNavigateToNextCategory(1)
                                        }
                                    }
                                    true
                                }
                                Key.Enter, Key.DirectionCenter -> {
                                    if (SystemClock.elapsedRealtime() < suppressSelectUntilMs) {
                                        return@onPreviewKeyEvent true
                                    }
                                    when (focusZone) {
                                        FocusZone.SIDEBAR -> {
                                            if (hasProfile && sidebarFocusIndex == 0) {
                                                onSwitchProfile()
                                            } else {
                                                val itemIndex = if (hasProfile) sidebarFocusIndex - 1 else sidebarFocusIndex
                                                when (SidebarItem.entries[itemIndex]) {
                                                    SidebarItem.SEARCH -> { /* Already here */ }
                                                    SidebarItem.HOME -> onNavigateToHome()
                                                    SidebarItem.MOVIES -> onNavigateToMovies()
                                                    SidebarItem.SERIES -> onNavigateToSeries()
                                                    SidebarItem.WATCHLIST -> onNavigateToWatchlist()
                                                    SidebarItem.TV -> onNavigateToTv()
                                                    SidebarItem.SETTINGS -> onNavigateToSettings()
                                                }
                                            }
                                            true
                                        }
                                        FocusZone.SEARCH_INPUT -> {
                                            // Let the TextField handle center/enter so users can
                                            // re-enter edit mode instead of forcing a re-search.
                                            false
                                        }
                                        FocusZone.RESULTS -> {
                                            when (focusedCategory) {
                                                SearchResultCategory.Movies -> {
                                                    val item = uiState.movieResults.getOrNull(movieItemIndex)
                                                    if (item != null) {
                                                        onNavigateToDetails(item.mediaType, item.id)
                                                    }
                                                }
                                                SearchResultCategory.TvShows -> {
                                                    val item = uiState.tvResults.getOrNull(tvItemIndex)
                                                    if (item != null) {
                                                        onNavigateToDetails(item.mediaType, item.id)
                                                    }
                                                }
                                                SearchResultCategory.LiveTv -> {
                                                    // Live TV: Direct navigation to player, bypass details screen
                                                    val channel = uiState.livetvResults.getOrNull(livetvItemIndex)
                                                    if (channel != null) {
                                                        val uniqueId = channel.streamUrl?.hashCode() ?: channel.id.hashCode()
                                                        onNavigateToPlayer(
                                                            MediaType.LIVE_TV,
                                                            uniqueId.coerceIn(Int.MIN_VALUE + 1, Int.MAX_VALUE),
                                                            null,
                                                            null,
                                                            null,
                                                            channel.streamUrl,
                                                            null,
                                                            null,
                                                            0L
                                                        )
                                                    }
                                                }
                                            }
                                            true
                                        }
                                    }
                                }
                                else -> false
                            }
                        } else false
                    }
                } else {
                    modifier
                }
            }
    ) {
        if (!LocalDeviceType.current.isTouchDevice()) {
            AppTopBar(
                selectedItem = SidebarItem.SEARCH,
                isFocused = focusZone == FocusZone.SIDEBAR,
                focusedIndex = sidebarFocusIndex,
                profile = currentProfile
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = AppTopBarContentTopInset)
                .padding(
                    horizontal = if (isCompactHeight) 40.dp else 48.dp,
                    vertical = if (isCompactHeight) 4.dp else 8.dp
                )
        ) {
                // Centered Search Bar at Top
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (isCompactHeight) 4.dp else 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isTouchDevice) {
                        // Mobile: standard OutlinedTextField that opens soft keyboard on tap
                        OutlinedTextField(
                            value = uiState.query,
                            onValueChange = { viewModel.updateQuery(it) },
                            placeholder = {
                                Text(
                                    text = "Search movies and TV shows...",
                                    style = ArflixTypography.body,
                                    color = TextSecondary
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = if (isSearchInputFocused) Pink else TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            textStyle = ArflixTypography.body.copy(color = TextPrimary),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                viewModel.search()
                                keyboardController?.hide()
                            }),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = Color.White,
                                focusedContainerColor = BackgroundCard,
                                unfocusedContainerColor = BackgroundCard,
                                focusedIndicatorColor = Color.White,
                                unfocusedIndicatorColor = Color.White.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .width(searchBarWidth)
                                .focusRequester(searchFocusRequester)
                                .onFocusChanged { state ->
                                    isSearchInputFocused = state.isFocused
                                }
                        )
                    } else {
                        // TV: BasicTextField with D-pad focus zone navigation
                        Row(
                            modifier = Modifier
                                .width(searchBarWidth)
                                .background(BackgroundCard, RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSearchInputFocused) 2.dp else 1.dp,
                                    color = if (isSearchInputFocused) Color.White else Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = if (isSearchInputFocused) Pink else TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            BasicTextField(
                                value = uiState.query,
                                onValueChange = { viewModel.updateQuery(it) },
                                textStyle = ArflixTypography.body.copy(color = TextPrimary),
                                cursorBrush = SolidColor(Color.White),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    viewModel.search()
                                    keyboardController?.hide()
                                }),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(searchFocusRequester)
                                    .onFocusChanged { state ->
                                        isSearchInputFocused = state.isFocused
                                        if (state.isFocused) {
                                            focusZone = FocusZone.SEARCH_INPUT
                                        }
                                    },
                                decorationBox = { innerTextField ->
                                    if (uiState.query.isEmpty()) {
                                        Text(
                                            text = "Search movies and TV shows...",
                                            style = ArflixTypography.body,
                                            color = TextSecondary
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }
                }

                // Results Area
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator(color = Pink, size = 64.dp)
                    }
                } else if (uiState.movieResults.isEmpty() && uiState.tvResults.isEmpty() && uiState.livetvResults.isEmpty() && uiState.query.isNotEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No results found for \"${uiState.query}\"",
                            style = ArflixTypography.body,
                            color = TextSecondary
                        )
                    }
                } else {
                    // Results Container with Vertical Scrolling
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(resultsScrollState)
                            .focusRequester(resultsFocusRequester)
                    ) {
                        // Movies Row
                        if (uiState.movieResults.isNotEmpty()) {
                            SearchResultRow(
                                title = "Movies",
                                items = uiState.movieResults,
                                cardLogoUrls = uiState.cardLogoUrls,
                                usePosterCards = usePosterCards,
                                rowState = movieRowState,
                                isCurrentRow = focusZone == FocusZone.RESULTS && focusedCategory == SearchResultCategory.Movies,
                                focusedItemIndex = movieItemIndex,
                                isTouchDevice = isTouchDevice,
                                onItemClick = {  }
                            )
                            Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 8.dp))
                        }

                        // TV Shows Row
                        if (uiState.tvResults.isNotEmpty()) {
                            SearchResultRow(
                                title = "TV Shows",
                                items = uiState.tvResults,
                                cardLogoUrls = uiState.cardLogoUrls,
                                usePosterCards = usePosterCards,
                                rowState = tvRowState,
                                isCurrentRow = focusZone == FocusZone.RESULTS && focusedCategory == SearchResultCategory.TvShows,
                                focusedItemIndex = tvItemIndex,
                                isTouchDevice = isTouchDevice,
                                onItemClick = {  }
                            )
                            Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 8.dp))
                        }

                        // Live TV Row
                        if (uiState.livetvResults.isNotEmpty()) {
                            SearchResultRow(
                                title = "Live TV",
                                channels = uiState.livetvResults,
                                usePosterCards = usePosterCards,
                                rowState = livetvRowState,
                                isCurrentRow = focusZone == FocusZone.RESULTS && focusedCategory == SearchResultCategory.LiveTv,
                                focusedItemIndex = livetvItemIndex,
                                onItemClick = { channel ->
                                    val uniqueId = channel.streamUrl?.hashCode() ?: channel.id.hashCode()
                                    onNavigateToPlayer(
                                        MediaType.LIVE_TV,
                                        uniqueId.coerceIn(Int.MIN_VALUE + 1, Int.MAX_VALUE),
                                        null,
                                        null,
                                        null,
                                        channel.streamUrl,
                                        null,
                                        null,
                                        0L
                                    )
                                }
                            )
                        }
                    }
                }
            }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchResultRow(
    title: String,
    items: List<MediaItem>,
    cardLogoUrls: Map<String, String>,
    usePosterCards: Boolean,
    rowState: androidx.tv.foundation.lazy.list.TvLazyListState,
    isCurrentRow: Boolean,
    focusedItemIndex: Int,
    isTouchDevice: Boolean = false,
    onItemClick: (MediaItem) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp
    val isCompactHeight = screenHeight <= 780
    val itemWidth = when {
        usePosterCards && screenHeight <= 600 -> 82.dp
        usePosterCards && screenHeight <= 700 -> 96.dp
        usePosterCards -> 108.dp
        !usePosterCards && screenHeight <= 600 -> 182.dp
        !usePosterCards && screenHeight <= 700 -> 204.dp
        else -> 228.dp
    }
    val itemSpacing = 16.dp
    val rowStartPadding = if (isCompactHeight) 12.dp else 16.dp
    val rowEndPadding = if (isCompactHeight) 120.dp else 160.dp
    var lastScrollIndex by remember { mutableIntStateOf(-1) }

    // D-pad scroll management — only needed for TV
    if (!isTouchDevice) {
        LaunchedEffect(isCurrentRow) {
            if (!isCurrentRow) {
                lastScrollIndex = -1
            }
        }
        LaunchedEffect(isCurrentRow, focusedItemIndex, items.size) {
            if (!isCurrentRow || items.isEmpty()) return@LaunchedEffect
            val targetIndex = focusedItemIndex.coerceIn(0, items.lastIndex)
            if (lastScrollIndex == targetIndex) return@LaunchedEffect
            val currentFirst = rowState.firstVisibleItemIndex
            val currentLast = rowState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: currentFirst
            val outsideViewport = targetIndex < currentFirst || targetIndex > currentLast
            val jumpDistance = kotlin.math.abs(targetIndex - currentFirst)
            if (targetIndex == 0 || outsideViewport || jumpDistance > 1 || lastScrollIndex == -1) {
                rowState.scrollToItem(index = targetIndex, scrollOffset = 0)
            } else {
                rowState.animateScrollToItem(index = targetIndex, scrollOffset = 0)
            }
            lastScrollIndex = targetIndex
        }
    }

    Column(
        modifier = Modifier.padding(bottom = 0.dp)
    ) {
        // Row Title
        Text(
            text = "$title (${items.size})",
            style = ArvioSkin.typography.sectionTitle,
            color = ArvioSkin.colors.textPrimary,
            modifier = Modifier.padding(start = rowStartPadding, bottom = if (isCompactHeight) 4.dp else 5.dp)
        )

        // Horizontal Card Row
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            if (isTouchDevice) {
                // Mobile: standard LazyRow with touch scrolling and clickable cards
                val touchRowState = rememberLazyListState()
                LazyRow(
                    state = touchRowState,
                    contentPadding = PaddingValues(
                        start = rowStartPadding,
                        end = rowEndPadding,
                        top = 0.dp,
                        bottom = if (isCompactHeight) 6.dp else 8.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(itemSpacing)
                ) {
                    items(
                        count = items.size,
                        key = { index -> items[index].id }
                    ) { index ->
                        val item = items[index]
                        val mediaTypeLabel = when (item.mediaType) {
                            MediaType.TV -> "TV Show"
                            MediaType.MOVIE -> "Movie"
                            MediaType.LIVE_TV -> "Live TV"
                        }
                        val yearValue = item.year.ifBlank { item.releaseDate?.take(4).orEmpty() }
                        val yearDisplay = if (yearValue.isNotBlank()) " | $yearValue" else ""
                        val displayItem = item.copy(
                            subtitle = "$mediaTypeLabel$yearDisplay"
                        )
                        MediaCard(
                            content = CardContent.Media(displayItem),
                            width = itemWidth,
                            isLandscape = !usePosterCards,
                            logoImageUrl = cardLogoUrls["${item.mediaType}_${item.id}"],
                            showProgress = false,
                            titleMaxLines = 2,
                            subtitleMaxLines = 1,
                            isFocusedOverride = false,
                            enableSystemFocus = false,
                            onFocused = { },
                            onClick = { onItemClick(item) },
                            modifier = Modifier.clickable { onItemClick(item) }
                        )
                    }
                }
            } else {
                // TV: TvLazyRow with D-pad focus management
                TvLazyRow(
                    state = rowState,
                    contentPadding = PaddingValues(
                        start = rowStartPadding,
                        end = rowEndPadding,
                        top = 0.dp,
                        bottom = if (isCompactHeight) 6.dp else 8.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                    pivotOffsets = androidx.tv.foundation.PivotOffsets(
                        parentFraction = 0.0f,
                        childFraction = 0.0f
                    )
                ) {
                    itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                        val mediaTypeLabel = when (item.mediaType) {
                            MediaType.TV -> "TV Show"
                            MediaType.MOVIE -> "Movie"
                            MediaType.LIVE_TV -> "Live TV"
                        }
                        val yearValue = item.year.ifBlank { item.releaseDate?.take(4).orEmpty() }
                        val yearDisplay = if (yearValue.isNotBlank()) " | $yearValue" else ""
                        val displayItem = item.copy(
                            subtitle = "$mediaTypeLabel$yearDisplay"
                        )
                        MediaCard(
                            content = CardContent.Media(displayItem),
                            width = itemWidth,
                            isLandscape = !usePosterCards,
                            logoImageUrl = cardLogoUrls["${item.mediaType}_${item.id}"],
                            showProgress = false,
                            titleMaxLines = 2,
                            subtitleMaxLines = 1,
                            isFocusedOverride = isCurrentRow && index == focusedItemIndex,
                            enableSystemFocus = false,
                            onFocused = { },
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * SearchResultRow overload for Live TV Channels
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchResultRow(
    title: String,
    channels: List<IptvChannel>,
    usePosterCards: Boolean,
    rowState: androidx.tv.foundation.lazy.list.TvLazyListState,
    isCurrentRow: Boolean,
    focusedItemIndex: Int,
    onItemClick: (IptvChannel) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp
    val isCompactHeight = screenHeight <= 780
    val itemWidth = 102.dp
    val itemHeight = 102.dp
    val itemSpacing = 16.dp
    val rowStartPadding = if (isCompactHeight) 12.dp else 16.dp
    val rowEndPadding = if (isCompactHeight) 120.dp else 160.dp
    var lastScrollIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(isCurrentRow) {
        if (!isCurrentRow) {
            lastScrollIndex = -1
        }
    }

    LaunchedEffect(isCurrentRow, focusedItemIndex, channels.size) {
        if (!isCurrentRow || channels.isEmpty()) return@LaunchedEffect
        val targetIndex = focusedItemIndex.coerceIn(0, channels.lastIndex)
        if (lastScrollIndex == targetIndex) return@LaunchedEffect
        rowState.animateScrollToItem(index = targetIndex, scrollOffset = 0)
        lastScrollIndex = targetIndex
    }

    Column {
        // Row Title
        Text(
            text = "$title (${channels.size})",
            style = ArvioSkin.typography.sectionTitle,
            color = ArvioSkin.colors.textPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Horizontal Channel Row
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            TvLazyRow(
                state = rowState,
                contentPadding = PaddingValues(
                    start = rowStartPadding,
                    end = rowEndPadding,
                    top = if (isCompactHeight) 10.dp else 16.dp,
                    bottom = if (isCompactHeight) 24.dp else 28.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                pivotOffsets = androidx.tv.foundation.PivotOffsets(
                    parentFraction = 0.0f,
                    childFraction = 0.0f
                )
            ) {
                itemsIndexed(channels, key = { _, it -> it.id }) { index, channel ->
                    MediaCard(
                        content = CardContent.Channel(channel),
                        width = itemWidth,
                        isLandscape = !usePosterCards,
                        isFocusedOverride = isCurrentRow && index == focusedItemIndex,
                        enableSystemFocus = false,
                        onFocused = { },
                        onClick = { onItemClick(channel) }
                    )
                }
            }
        }
    }
}

private enum class FocusZone {
    SIDEBAR, SEARCH_INPUT, RESULTS
}
