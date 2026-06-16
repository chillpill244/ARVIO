package com.muvio.shared.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muvio.shared.domain.Category
import com.muvio.shared.domain.MediaItem
import com.muvio.shared.ui.components.MediaCategoryRail
import com.muvio.shared.viewmodel.SearchFilter
import com.muvio.shared.viewmodel.SearchViewModel
import org.koin.compose.viewmodel.koinViewModel

private val BgDark = Color(0xFF0A0A0A)
private val SurfaceDark = Color(0xFF1C1C1C)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF888888)
private val AccentTeal = Color(0xFF00C8A0)

@Composable
fun SearchScreen(
    onItemClick: (MediaItem) -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current

    val hasQuery = state.query.length >= 2
    val hasResults = state.movieResults.isNotEmpty() || state.tvResults.isNotEmpty()

    val searchCategories = remember(state.movieResults, state.tvResults, state.selectedFilter, hasQuery) {
        if (!hasQuery) return@remember emptyList<Category>()
        buildList {
            when (state.selectedFilter) {
                SearchFilter.ALL -> {
                    val all = interleave(state.movieResults, state.tvResults).take(20)
                    if (all.isNotEmpty()) add(Category("s_all", "Top Results", all))
                    if (state.movieResults.isNotEmpty()) add(Category("s_m", "Movies", state.movieResults))
                    if (state.tvResults.isNotEmpty()) add(Category("s_t", "TV Shows", state.tvResults))
                }
                SearchFilter.MOVIES -> {
                    if (state.movieResults.isNotEmpty()) add(Category("s_m", "Movies", state.movieResults))
                }
                SearchFilter.TV -> {
                    if (state.tvResults.isNotEmpty()) add(Category("s_t", "TV Shows", state.tvResults))
                }
                SearchFilter.ANIME -> {
                    val anime = state.tvResults.filter { it.genreIds.contains(16) || it.originalLanguage == "ja" }
                    val display = anime.ifEmpty { state.tvResults }
                    if (display.isNotEmpty()) add(Category("s_anime", "Anime", display))
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding(),
    ) {
        // Search bar
        TextField(
            value = state.query,
            onValueChange = viewModel::onQueryChanged,
            placeholder = {
                Text("Search movies & shows…", color = TextSecondary, fontSize = 15.sp)
            },
            singleLine = true,
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = if (state.query.isNotEmpty()) AccentTeal else TextSecondary,
                )
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearSearch) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); viewModel.search() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = AccentTeal,
                focusedIndicatorColor = AccentTeal,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )

        // Filter chips row (All / Movies / Shows / Anime)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
        ) {
            items(SearchFilter.entries) { filter ->
                val isSelected = state.selectedFilter == filter
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setFilter(filter) },
                    label = { Text(filter.label, fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.White.copy(alpha = 0.07f),
                        labelColor = TextSecondary,
                        selectedContainerColor = AccentTeal.copy(alpha = 0.15f),
                        selectedLabelColor = AccentTeal,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = Color.White.copy(alpha = 0.15f),
                        selectedBorderColor = AccentTeal.copy(alpha = 0.5f),
                    ),
                )
            }
        }

        // Content area
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentTeal)
                }
            }

            hasQuery && hasResults -> {
                CategoryRowsContent(categories = searchCategories, onItemClick = onItemClick)
            }

            hasQuery && !hasResults && !state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No results for \"${state.query}\"",
                        color = TextSecondary,
                        fontSize = 15.sp,
                    )
                }
            }

            state.isDiscoverLoading && state.discoverCategories.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentTeal)
                }
            }

            state.discoverCategories.isNotEmpty() -> {
                CategoryRowsContent(categories = state.discoverCategories, onItemClick = onItemClick)
            }

            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Search for movies and TV shows",
                        color = TextSecondary,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRowsContent(
    categories: List<Category>,
    onItemClick: (MediaItem) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(categories, key = { it.id }) { category ->
            MediaCategoryRail(
                title = category.title,
                items = category.items,
                onItemClick = onItemClick,
                cardWidth = 110.dp,
            )
        }
    }
}

private fun interleave(movies: List<MediaItem>, shows: List<MediaItem>): List<MediaItem> {
    val result = ArrayList<MediaItem>(movies.size + shows.size)
    val maxSize = maxOf(movies.size, shows.size)
    for (i in 0 until maxSize) {
        if (i < movies.size) result.add(movies[i])
        if (i < shows.size) result.add(shows[i])
    }
    return result.distinctBy { "${it.mediaType}_${it.id}" }
}
