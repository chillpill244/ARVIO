@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.arflix.tv.ui.screens.tv.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

/** 0 = Movies, 1 = TV, 2 = Series */
object ContentHubTab {
    const val MOVIES = 0
    const val TV = 1
    const val SERIES = 2
}

private data class TabDef(val icon: ImageVector, val label: String)

private val TABS = listOf(
    TabDef(Icons.Outlined.Movie, "Movies"),
    TabDef(Icons.Filled.LiveTv, "TV"),
    TabDef(Icons.Outlined.PlayCircle, "Series"),
)

@Composable
fun ContentMenuPanel(
    focusedIndex: Int,
    isFocused: Boolean,
    activeIndex: Int = ContentHubTab.TV,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(72.dp)
            .fillMaxHeight()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        TABS.forEachIndexed { index, tab ->
            val isItemFocused = isFocused && index == focusedIndex
            val isActive = index == activeIndex
            val alpha = when {
                isItemFocused -> 1f
                isActive -> 0.55f
                else -> 0.38f
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = Color.White.copy(alpha = alpha),
                    modifier = Modifier.size(22.dp),
                )
                if (isItemFocused) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = alpha),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(width = 16.dp, height = 2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(
                    when {
                        isItemFocused && isActive -> Color.White
                        isActive -> Color.White.copy(alpha = 0.55f)
                        else -> Color.Transparent
                    }
                ),
                )
            }
            if (index < TABS.size - 1) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
