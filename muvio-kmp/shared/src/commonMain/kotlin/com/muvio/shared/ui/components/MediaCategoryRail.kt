package com.muvio.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muvio.shared.domain.MediaItem

private val TextPrimary = Color(0xFFFFFFFF)

@Composable
fun MediaCategoryRail(
    title: String,
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 124.dp,
) {
    if (items.isEmpty()) return
    Column(modifier = modifier.padding(top = 24.dp)) {
        Text(
            text = title,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { "${it.mediaType}_${it.id}" }) { item ->
                MediaCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    modifier = Modifier.width(cardWidth),
                )
            }
        }
    }
}
