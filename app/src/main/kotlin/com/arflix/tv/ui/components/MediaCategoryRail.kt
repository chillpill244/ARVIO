@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.arflix.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.arflix.tv.ui.theme.ArflixTypography
import com.arflix.tv.ui.theme.TextSecondary

/**
 * Reusable category rail component for Movies, Series, and TV screens.
 * Displays a vertical list of categories with favorite star toggle.
 * Includes search box at the top, before the first category.
 */
@Composable
fun MediaCategoryRail(
    categories: List<String>,
    favoriteCategories: Set<String>,
    focusedCategoryIndex: Int,
    isFocused: Boolean,
    listState: LazyListState,
    searchQuery: String = "",
    onSearchChange: (String) -> Unit = {},
    searchFocusRequester: FocusRequester? = null,
    isSearchFocused: Boolean = false,
    isSearchActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        // Categories header
        Text(
            text = "Categories",
            style = ArflixTypography.caption.copy(fontSize = 11.sp, letterSpacing = 0.7.sp),
            color = TextSecondary.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 6.dp, bottom = 4.dp, top = 2.dp)
        )

        // Search box below Categories header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(bottom = 3.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (isSearchFocused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f)
                )
                .then(
                    if (isSearchFocused) Modifier.border(
                        width = 1.5.dp,
                        color = Color.White.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp)
                    ) else Modifier.border(
                        width = 0.5.dp,
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    )
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                textStyle = ArflixTypography.body.copy(color = Color.White, fontSize = 12.sp),
                cursorBrush = SolidColor(Color.White),
                singleLine = true,
                readOnly = !isSearchActive,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (searchFocusRequester != null) {
                            Modifier.focusRequester(searchFocusRequester)
                        } else {
                            Modifier
                        }
                    ),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text("Search", color = TextSecondary.copy(alpha = 0.5f), style = ArflixTypography.body.copy(fontSize = 12.sp))
                    }
                    innerTextField()
                }
            )
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { onSearchChange("") },
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

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Category items
            itemsIndexed(
                categories,
                key = { _, category -> category },
                contentType = { _, _ -> "media_category" }
            ) { index, category ->
                MediaCategoryRailItem(
                    name = category,
                    isFocused = isFocused && index == focusedCategoryIndex,
                    isFavorite = favoriteCategories.contains(category)
                )
            }
        }
    }
}

@Composable
private fun MediaCategoryRailItem(name: String, isFocused: Boolean, isFavorite: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isFocused) Color.White.copy(alpha = 0.07f) else Color.Transparent
            )
            .then(
                if (isFocused) Modifier.border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(6.dp)
                ) else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFavorite) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFF5C518).copy(alpha = 0.8f),
                modifier = Modifier.size(10.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(
            text = name,
            style = ArflixTypography.caption.copy(fontSize = 11.sp, fontWeight = if (isFocused) FontWeight.Medium else FontWeight.Normal, lineHeight = 14.sp),
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.4f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = if (isFocused) Modifier.basicMarquee() else Modifier
        )
    }
}
