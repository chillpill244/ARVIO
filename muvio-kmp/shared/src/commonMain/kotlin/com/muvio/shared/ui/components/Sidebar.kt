package com.muvio.shared.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.muvio.shared.ui.navigation.HomeRoute
import com.muvio.shared.ui.navigation.SearchRoute
import com.muvio.shared.ui.navigation.SettingsRoute

private val AccentTeal = Color(0xFF00C8A0)

private data class SidebarTab(
    val route: Any,
    val icon: ImageVector,
    val label: String,
)

private val sidebarTabs = listOf(
    SidebarTab(HomeRoute, Icons.Outlined.Home, "Home"),
    SidebarTab(SearchRoute, Icons.Outlined.Search, "Search"),
    SidebarTab(SettingsRoute, Icons.Outlined.Settings, "Settings"),
)

/**
 * Vertical icon-only navigation sidebar for tablet/desktop layouts.
 * Shows Home, Search at center and Settings pinned at the bottom.
 */
@Composable
fun Sidebar(
    currentRoute: String?,
    onNavigate: (Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(56.dp)
            .fillMaxHeight()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.5f),
                        Color.Black.copy(alpha = 0.2f),
                        Color.Transparent,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            sidebarTabs.dropLast(1).forEach { tab ->
                val selected = currentRoute?.contains(tab.route::class.qualifiedName ?: "") == true
                SidebarIconButton(tab = tab, isSelected = selected, onNavigate = onNavigate)
                Spacer(Modifier.size(20.dp))
            }

            Spacer(Modifier.weight(1f))

            // Settings at bottom
            sidebarTabs.last().let { tab ->
                val selected = currentRoute?.contains(tab.route::class.qualifiedName ?: "") == true
                SidebarIconButton(tab = tab, isSelected = selected, onNavigate = onNavigate)
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun SidebarIconButton(
    tab: SidebarTab,
    isSelected: Boolean,
    onNavigate: (Any) -> Unit,
) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) AccentTeal.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(150),
        label = "sidebar_bg",
    )
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) AccentTeal else Color(0xFF666666),
        animationSpec = tween(150),
        label = "sidebar_icon",
    )

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable { onNavigate(tab.route) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = iconColor,
            modifier = Modifier.size(22.dp),
        )
    }
}
