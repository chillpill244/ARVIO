package com.arflix.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import com.arflix.tv.R
import com.arflix.tv.ui.theme.appBackgroundDark

data class BottomBarItem(
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String
)

val bottomBarItems = listOf(
    BottomBarItem(R.string.home, Icons.Rounded.Home, Icons.Outlined.Home, "home"),
    BottomBarItem(R.string.search, Icons.Rounded.Search, Icons.Outlined.Search, "search"),
    BottomBarItem(R.string.watchlist, Icons.Rounded.Archive, Icons.Outlined.Archive, "watchlist"),
    BottomBarItem(R.string.topbar_tv, Icons.Rounded.LiveTv, Icons.Outlined.LiveTv, "tv"),
    BottomBarItem(R.string.settings, Icons.Rounded.Settings, Icons.Outlined.Settings, "settings")
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    activeDownloadProgress: Float? = null,
    hasAnyDownloads: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Hairline glass edge separating the bar from content above.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.12f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(appBackgroundDark())
                // Subtle tint lifts the bar off the app's black background.
                .background(Color.White.copy(alpha = 0.06f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomBarItems.forEach { item ->
                val isWatchlist = item.route == "watchlist"
                val showAsDownloads = isWatchlist && hasAnyDownloads
                val isDownloading = showAsDownloads && activeDownloadProgress != null
                val navRoute = if (showAsDownloads) "watchlist?tab=1" else item.route
                val isSelected = currentRoute?.contains(item.route, ignoreCase = true) == true
                var isFocused by remember { mutableStateOf(false) }
                val label = when {
                    showAsDownloads -> "Downloads"
                    else -> stringResource(item.labelRes)
                }
                val iconTint = when {
                    isFocused || isSelected -> Color.White
                    else -> Color.White.copy(alpha = 0.55f)
                }
                val icon = when {
                    showAsDownloads && isSelected -> Icons.Rounded.Download
                    showAsDownloads -> Icons.Outlined.Download
                    isSelected -> item.selectedIcon
                    else -> item.unselectedIcon
                }
                // Search/download glyphs have more built-in padding than the rest;
                // scale them visually (no layout impact) to keep weight and alignment even.
                val iconScale = if (showAsDownloads || item.route == "search") 1.12f else 1f

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (isFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                        .focusable()
                        .onFocusChanged { isFocused = it.isFocused }
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                                onNavigate(navRoute)
                                true
                            } else false
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onNavigate(navRoute) }
                        .padding(vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Fixed slot so every item has identical height regardless of
                    // glyph scaling or the download progress ring.
                    Box(
                        modifier = Modifier.size(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDownloading) {
                            if (activeDownloadProgress!! > 0f) {
                                CircularProgressIndicator(
                                    progress = { activeDownloadProgress },
                                    modifier = Modifier.size(36.dp),
                                    color = iconTint,
                                    strokeWidth = 2.dp,
                                    trackColor = Color.White.copy(alpha = 0.15f)
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    color = iconTint,
                                    strokeWidth = 2.dp,
                                    trackColor = Color.White.copy(alpha = 0.15f)
                                )
                            }
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = iconTint,
                            modifier = Modifier
                                .size(28.dp)
                                .scale(iconScale)
                        )
                    }
                }
            }
        }
    }
}
