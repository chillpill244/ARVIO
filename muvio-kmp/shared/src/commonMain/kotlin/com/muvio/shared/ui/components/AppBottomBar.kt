package com.muvio.shared.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.muvio.shared.ui.navigation.HomeRoute
import com.muvio.shared.ui.navigation.SearchRoute
import com.muvio.shared.ui.navigation.SettingsRoute

private val AccentTeal = Color(0xFF00C8A0)

private data class BottomTab(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val bottomTabs = listOf(
    BottomTab(HomeRoute, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomTab(SearchRoute, "Search", Icons.Filled.Search, Icons.Outlined.Search),
    BottomTab(SettingsRoute, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun AppBottomBar(
    currentRoute: String?,
    onNavigate: (Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        containerColor = Color(0xFF111111),
        tonalElevation = 0.dp,
        windowInsets = WindowInsets.navigationBars,
        modifier = modifier,
    ) {
        bottomTabs.forEach { tab ->
            val routeName = tab.route::class.qualifiedName ?: ""
            val selected = currentRoute?.contains(routeName) == true
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onNavigate(tab.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label,
                    )
                },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AccentTeal,
                    selectedTextColor = AccentTeal,
                    unselectedIconColor = Color(0xFF888888),
                    unselectedTextColor = Color(0xFF888888),
                    indicatorColor = AccentTeal.copy(alpha = 0.15f),
                ),
            )
        }
    }
}
