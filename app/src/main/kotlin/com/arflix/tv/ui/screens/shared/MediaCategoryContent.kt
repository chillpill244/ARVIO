@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.arflix.tv.ui.screens.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.tween
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.ui.components.CardLayoutMode
import com.arflix.tv.ui.components.LoadingIndicator
import com.arflix.tv.ui.components.MediaCard
import com.arflix.tv.ui.components.MediaCategoryRail
import com.arflix.tv.ui.components.TopBarClock
import com.arflix.tv.ui.theme.ArflixTypography
import com.arflix.tv.ui.theme.BackgroundDark
import com.arflix.tv.ui.theme.TextPrimary
import com.arflix.tv.ui.theme.TextSecondary
import kotlin.math.abs

/**
 * Focus zones within the MediaCategoryContent
 */
enum class MediaCategoryFocusZone {
    SEARCH,       // Category search input
    CATEGORIES,   // Category list
    ITEM_SEARCH,  // In-category item search
    ITEMS         // Grid of media items
}

/**
 * UI State for MediaCategoryContent
 */
data class MediaCategoryUiState(
    val isLoading: Boolean = false,
    val categories: List<String> = emptyList(),
    val selectedCategoryIndex: Int = 0,
    val items: List<MediaItem> = emptyList(),
    val favoriteCategories: Set<String> = emptySet(),
    val categorySearchQuery: String = "",
    val itemSearchQuery: String = "",
    val error: String? = null
) {
    val selectedCategory: String get() = filteredAndSortedCategories.getOrNull(selectedCategoryIndex).orEmpty()

    val filteredAndSortedCategories: List<String>
        get() {
            val filtered = if (categorySearchQuery.isBlank()) {
                categories
            } else {
                categories.filter { it.contains(categorySearchQuery, ignoreCase = true) }
            }
            return filtered.sortedBy {
                if (favoriteCategories.contains(it)) 0 else 1
            }
        }

    val displayedItems: List<MediaItem>
        get() {
            val source = items.reversed()
            return if (itemSearchQuery.isBlank()) {
                source
            } else {
                source.filter { item ->
                    item.title.contains(itemSearchQuery, ignoreCase = true)
                }
            }
        }
}

/**
 * Shared composable for rendering Movies or Series category screens.
 * Supports category navigation, item search, "All Categories" view, and lazy loading.
 *
 * @param uiState Current UI state
 * @param isLandscape Whether to display items in landscape mode
 * @param onCategorySearchChange Callback when category search query changes
 * @param onItemSearchChange Callback when item search query changes
 * @param onSelectCategory Callback when a category is selected
 * @param onToggleFavorite Callback when a category favorite is toggled
 * @param onItemClick Callback when an item is clicked
 * @param onLoadAllCategories Callback to load all categories (lazy load)
 * @param onRefresh Callback to refresh data
 */
@Composable
fun MediaCategoryContent(
    uiState: MediaCategoryUiState,
    isLandscape: Boolean,
    onCategorySearchChange: (String) -> Unit,
    onItemSearchChange: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onRefresh: () -> Unit,
    onNavigateLeft: (() -> Unit)? = null,
    onNavigateUp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Number of items per row in the grid
    val itemsPerRow = 6

    var focusZone by remember { mutableStateOf(MediaCategoryFocusZone.CATEGORIES) }
    var categoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var itemIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastItemIndex by rememberSaveable { mutableIntStateOf(0) } // Track last item position when leaving ITEMS zone
    var categorySearchActive by remember { mutableStateOf(false) }
    var itemSearchActive by remember { mutableStateOf(false) }

    val categoriesListState = rememberLazyListState()
    val itemsGridState = rememberLazyGridState()
    val categorySearchFocusRequester = remember { FocusRequester() }
    val itemSearchFocusRequester = remember { FocusRequester() }

    val safeCategoryIndex = categoryIndex.coerceIn(0, (uiState.filteredAndSortedCategories.size - 1).coerceAtLeast(0))
    // Use ViewModel's confirmed selection — only changes when user presses Enter on a category
    val selectedCategory = uiState.selectedCategory
    val displayedItems = uiState.displayedItems

    // Track Enter long-press duration for CATEGORIES zone (long press = toggle favorite)
    var centerDownAtMs by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(focusZone) {
        when (focusZone) {
            MediaCategoryFocusZone.SEARCH -> categorySearchFocusRequester.requestFocus()
            MediaCategoryFocusZone.ITEM_SEARCH -> itemSearchFocusRequester.requestFocus()
            else -> {}
        }
    }

    LaunchedEffect(itemIndex, focusZone) {
        if (focusZone == MediaCategoryFocusZone.ITEMS && displayedItems.isNotEmpty()) {
            val distance = abs(itemsGridState.firstVisibleItemIndex - itemIndex)
            if (distance > 12) {
                itemsGridState.scrollToItem(itemIndex)
            } else {
                itemsGridState.animateScrollToItem(itemIndex)
            }
        }
    }

    // Ensure focus zones are valid
    LaunchedEffect(uiState.categories.size) {
        if (focusZone == MediaCategoryFocusZone.ITEMS && displayedItems.isEmpty()) {
            focusZone = MediaCategoryFocusZone.CATEGORIES
        }
        if (categoryIndex >= uiState.filteredAndSortedCategories.size) categoryIndex = 0
    }

    LaunchedEffect(safeCategoryIndex, focusZone) {
        if (focusZone == MediaCategoryFocusZone.CATEGORIES && uiState.filteredAndSortedCategories.isNotEmpty()) {
            categoriesListState.smoothScrollToItem(safeCategoryIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .focusable()
            .onPreviewKeyEvent { event ->
                // Handle Enter long-press for CATEGORIES zone:
                // short press = select category and load items, long press = toggle favorite
                if (focusZone == MediaCategoryFocusZone.CATEGORIES) {
                    val isSelect = event.key == Key.Enter || event.key == Key.DirectionCenter
                    if (isSelect) {
                        if (event.type == KeyEventType.KeyDown) {
                            if (centerDownAtMs == null) centerDownAtMs = android.os.SystemClock.elapsedRealtime()
                            return@onPreviewKeyEvent true
                        }
                        if (event.type == KeyEventType.KeyUp) {
                            val pressMs = centerDownAtMs?.let { android.os.SystemClock.elapsedRealtime() - it } ?: 0L
                            centerDownAtMs = null
                            val category = uiState.filteredAndSortedCategories.getOrNull(safeCategoryIndex)
                            if (category != null) {
                                if (pressMs >= 550L) {
                                    // Long press = toggle favorite
                                    onToggleFavorite(category)
                                } else {
                                    // Short press = select category and move to its items
                                    onSelectCategory(category)
                                    if (displayedItems.isNotEmpty()) {
                                        itemIndex = 0
                                        focusZone = MediaCategoryFocusZone.ITEMS
                                    }
                                }
                            }
                            return@onPreviewKeyEvent true
                        }
                    }
                }

                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                // Handle category SEARCH zone navigation
                if (focusZone == MediaCategoryFocusZone.SEARCH) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            // Navigate to AppTopBar (parent should handle this)
                            if (onNavigateUp != null) {
                                onNavigateUp()
                                return@onPreviewKeyEvent true
                            }
                            return@onPreviewKeyEvent false
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            categorySearchActive = true
                            return@onPreviewKeyEvent true
                        }
                        Key.DirectionDown -> {
                            if (uiState.filteredAndSortedCategories.isNotEmpty()) {
                                categoryIndex = 0
                                itemIndex = 0
                                categorySearchActive = false
                                focusZone = MediaCategoryFocusZone.CATEGORIES
                                // Do NOT call onSelectCategory here — user must press Enter to load items
                            }
                            return@onPreviewKeyEvent true
                        }
                        Key.DirectionRight -> {
                            if (displayedItems.isNotEmpty()) {
                                categorySearchActive = false
                                focusZone = MediaCategoryFocusZone.ITEM_SEARCH
                            }
                            return@onPreviewKeyEvent true
                        }
                        Key.DirectionLeft -> {
                            categorySearchActive = false
                            if (onNavigateLeft != null) {
                                onNavigateLeft()
                                return@onPreviewKeyEvent true
                            }
                            return@onPreviewKeyEvent false
                        }
                        Key.Back, Key.Escape -> {
                            // Close keyboard if active, otherwise let parent handle
                            if (categorySearchActive) {
                                categorySearchActive = false
                                return@onPreviewKeyEvent true
                            }
                            return@onPreviewKeyEvent false
                        }
                        else -> return@onPreviewKeyEvent false
                    }
                }

                // Handle item SEARCH zone navigation
                if (focusZone == MediaCategoryFocusZone.ITEM_SEARCH) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            // Navigate to AppTopBar (parent should handle this)
                            if (onNavigateUp != null) {
                                onNavigateUp()
                                return@onPreviewKeyEvent true
                            }
                            return@onPreviewKeyEvent false
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            itemSearchActive = true
                            return@onPreviewKeyEvent true
                        }
                        Key.DirectionDown -> {
                            if (displayedItems.isNotEmpty()) {
                                itemIndex = 0
                                itemSearchActive = false
                                focusZone = MediaCategoryFocusZone.ITEMS
                            }
                            return@onPreviewKeyEvent true
                        }
                        Key.DirectionLeft -> {
                            itemSearchActive = false
                            focusZone = MediaCategoryFocusZone.SEARCH
                            return@onPreviewKeyEvent true
                        }
                        else -> return@onPreviewKeyEvent false
                    }
                }

                // Handle other zones
                when (event.key) {
                    Key.Back, Key.Escape -> {
                        when (focusZone) {
                            MediaCategoryFocusZone.ITEMS -> {
                                focusZone = MediaCategoryFocusZone.CATEGORIES
                                return@onPreviewKeyEvent true
                            }
                            MediaCategoryFocusZone.CATEGORIES -> {
                                focusZone = MediaCategoryFocusZone.SEARCH
                                return@onPreviewKeyEvent true
                            }
                            else -> return@onPreviewKeyEvent false
                        }
                    }

                    Key.DirectionLeft -> {
                        when (focusZone) {
                            MediaCategoryFocusZone.ITEMS -> {
                                // Save current item index before leaving
                                lastItemIndex = itemIndex
                                if (itemIndex % itemsPerRow == 0) {
                                    // At leftmost item, move to categories
                                    focusZone = MediaCategoryFocusZone.CATEGORIES
                                    return@onPreviewKeyEvent true
                                } else {
                                    itemIndex--
                                    return@onPreviewKeyEvent true
                                }
                            }
                            MediaCategoryFocusZone.CATEGORIES -> {
                                if (onNavigateLeft != null) {
                                    onNavigateLeft()
                                    return@onPreviewKeyEvent true
                                }
                                return@onPreviewKeyEvent false
                            }
                            MediaCategoryFocusZone.SEARCH -> {
                                // Search zone - let parent handle sidebar navigation
                                return@onPreviewKeyEvent false
                            }
                            else -> return@onPreviewKeyEvent false
                        }
                    }

                    Key.DirectionRight -> {
                        when (focusZone) {
                            MediaCategoryFocusZone.ITEMS -> {
                                if (itemIndex < displayedItems.size - 1) itemIndex++
                                return@onPreviewKeyEvent true
                            }
                            MediaCategoryFocusZone.CATEGORIES -> {
                                if (displayedItems.isNotEmpty()) {
                                    // Restore last valid item index, or use 0 if none saved
                                    itemIndex = if (lastItemIndex < displayedItems.size) lastItemIndex else 0
                                    focusZone = MediaCategoryFocusZone.ITEMS
                                }
                                return@onPreviewKeyEvent true
                            }
                            else -> return@onPreviewKeyEvent false
                        }
                    }

                    Key.DirectionUp -> {
                        when (focusZone) {
                            MediaCategoryFocusZone.CATEGORIES -> {
                                // Only move highlight — do NOT load items (user must press Enter)
                                if (categoryIndex > 0) {
                                    categoryIndex--
                                } else {
                                    focusZone = MediaCategoryFocusZone.SEARCH
                                }
                                return@onPreviewKeyEvent true
                            }
                            MediaCategoryFocusZone.ITEMS -> {
                                if (itemIndex - itemsPerRow >= 0) {
                                    itemIndex -= itemsPerRow
                                } else {
                                    focusZone = MediaCategoryFocusZone.ITEM_SEARCH
                                }
                                return@onPreviewKeyEvent true
                            }
                            else -> return@onPreviewKeyEvent false
                        }
                    }

                    Key.DirectionDown -> {
                        when (focusZone) {
                            MediaCategoryFocusZone.CATEGORIES -> {
                                // Only move highlight — do NOT load items (user must press Enter)
                                if (categoryIndex < uiState.filteredAndSortedCategories.size - 1) {
                                    categoryIndex++
                                }
                                return@onPreviewKeyEvent true
                            }
                            MediaCategoryFocusZone.ITEMS -> {
                                if (itemIndex + itemsPerRow < displayedItems.size) itemIndex += itemsPerRow
                                return@onPreviewKeyEvent true
                            }
                            else -> return@onPreviewKeyEvent false
                        }
                    }

                    Key.Enter, Key.DirectionCenter -> {
                        // CATEGORIES Enter is handled by the long-press block above
                        when (focusZone) {
                            MediaCategoryFocusZone.ITEMS -> {
                                val item = displayedItems.getOrNull(itemIndex)
                                if (item != null) {
                                    onItemClick(item)
                                    return@onPreviewKeyEvent true
                                }
                            }
                            else -> return@onPreviewKeyEvent false
                        }
                        return@onPreviewKeyEvent false
                    }

                    Key.Menu -> {
                        // Menu key toggles favorite for focused category
                        if (focusZone == MediaCategoryFocusZone.CATEGORIES) {
                            if (uiState.filteredAndSortedCategories.isNotEmpty()) {
                                val category = uiState.filteredAndSortedCategories.getOrNull(safeCategoryIndex)
                                if (category != null) {
                                    onToggleFavorite(category)
                                }
                                return@onPreviewKeyEvent true
                            }
                        }
                        return@onPreviewKeyEvent false
                    }

                    else -> return@onPreviewKeyEvent false
                }
            }
    ) {
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Unable to load content",
                            style = ArflixTypography.sectionTitle,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.error ?: "",
                            style = ArflixTypography.body,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        androidx.tv.material3.Button(
                            onClick = onRefresh
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    // Left column: Categories (with integrated search bar) - collapses when navigating to items
                    val showCategoryRail = focusZone != MediaCategoryFocusZone.ITEMS
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showCategoryRail,
                        enter = androidx.compose.animation.expandHorizontally(
                            expandFrom = Alignment.Start,
                            animationSpec = androidx.compose.animation.core.tween(200)
                        ),
                        exit = androidx.compose.animation.shrinkHorizontally(
                            shrinkTowards = Alignment.Start,
                            animationSpec = androidx.compose.animation.core.tween(200)
                        )
                    ) {
                        MediaCategoryRail(
                            categories = uiState.filteredAndSortedCategories,
                            favoriteCategories = uiState.favoriteCategories,
                            focusedCategoryIndex = categoryIndex,
                            isFocused = focusZone == MediaCategoryFocusZone.CATEGORIES,
                            listState = categoriesListState,
                            searchQuery = uiState.categorySearchQuery,
                            onSearchChange = onCategorySearchChange,
                            searchFocusRequester = categorySearchFocusRequester,
                            isSearchFocused = focusZone == MediaCategoryFocusZone.SEARCH,
                            isSearchActive = categorySearchActive,
                            modifier = Modifier
                                .width(180.dp)
                                .fillMaxHeight()
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Right column: Item Search + Items Grid
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        // Item search input with item count - 75% width
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .padding(start = 10.dp, end = 10.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (focusZone == MediaCategoryFocusZone.ITEM_SEARCH) Color.White.copy(alpha = 0.12f)
                                        else Color.White.copy(alpha = 0.06f)
                                    )
                                    .then(
                                        if (focusZone == MediaCategoryFocusZone.ITEM_SEARCH) Modifier.border(
                                            width = 1.5.dp,
                                            color = Color.White.copy(alpha = 0.7f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) else Modifier.border(
                                            width = 0.5.dp,
                                            color = Color.White.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    )
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = uiState.itemSearchQuery,
                                    onValueChange = onItemSearchChange,
                                    textStyle = ArflixTypography.body.copy(color = Color.White),
                                    cursorBrush = SolidColor(Color.White),
                                    singleLine = true,
                                    readOnly = !itemSearchActive,
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(itemSearchFocusRequester),
                                    decorationBox = { innerTextField ->
                                        val hint = if (selectedCategory.isNotEmpty()) "Search in $selectedCategory" else "Search"
                                        if (uiState.itemSearchQuery.isEmpty()) {
                                            Text(hint, color = TextSecondary.copy(alpha = 0.5f), style = ArflixTypography.body)
                                        }
                                        innerTextField()
                                    }
                                )
                                if (uiState.itemSearchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onItemSearchChange("") },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Clear,
                                            contentDescription = "Clear search",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            // Item count display next to search box
                            Text(
                                text = "${displayedItems.size} items",
                                style = ArflixTypography.body.copy(fontSize = 16.sp),
                                color = TextSecondary.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 4.dp, end = 4.dp)
                            )
                        }

                        // Item count is now shown in category rail header when items are displayed

                        // Grid of items
                        if (displayedItems.isNotEmpty()) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(itemsPerRow),
                                state = itemsGridState,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(top = 10.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)
                            ) {
                                itemsIndexed(
                                    displayedItems,
                                    key = { _, item -> "${item.iptvMovieId ?: item.iptvSeriesId ?: item.id}_${item.title}" }
                                ) { index, item ->
                                    MediaCard(
                                        item = item,
                                        isLandscape = isLandscape,
                                        isFocusedOverride = focusZone == MediaCategoryFocusZone.ITEMS && index == itemIndex,
                                        enableSystemFocus = focusZone == MediaCategoryFocusZone.ITEMS,
                                        onFocused = { itemIndex = index },
                                        onClick = { onItemClick(item) },
                                        modifier = Modifier.width(128.dp)
                                    )
                                }
                            }
                        } else if (!uiState.isLoading && uiState.categories.isNotEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (uiState.itemSearchQuery.isNotEmpty()) {
                                        "No items match your search"
                                    } else {
                                        "No items in this category"
                                    },
                                    style = ArflixTypography.body,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

    }
}

private suspend fun LazyListState.smoothScrollToItem(index: Int) {
    val distance = abs(firstVisibleItemIndex - index)
    if (distance > 12) {
        scrollToItem(index)
    } else {
        animateScrollToItem(index)
    }
}
