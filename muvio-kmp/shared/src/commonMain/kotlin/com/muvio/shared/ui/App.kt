package com.muvio.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.muvio.shared.ui.components.AppBottomBar
import com.muvio.shared.ui.navigation.DetailsRoute
import com.muvio.shared.ui.navigation.HomeRoute
import com.muvio.shared.ui.navigation.PlayerRoute
import com.muvio.shared.ui.navigation.SearchRoute
import com.muvio.shared.ui.navigation.SettingsRoute
import com.muvio.shared.ui.screens.details.DetailsScreen
import com.muvio.shared.ui.screens.home.HomeScreen
import com.muvio.shared.ui.screens.player.PlayerScreen
import com.muvio.shared.ui.screens.search.SearchScreen
import com.muvio.shared.ui.screens.settings.SettingsScreen
import com.muvio.shared.ui.theme.AppTheme

private val bottomBarRoutes = setOf(
    HomeRoute::class.qualifiedName,
    SearchRoute::class.qualifiedName,
    SettingsRoute::class.qualifiedName,
)

@Composable
fun App() {
    AppTheme {
        val navController = rememberNavController()
        val navBackStack by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStack?.destination?.route

        val showBottomBar = bottomBarRoutes.any { currentRoute?.contains(it ?: "") == true }

        Scaffold(
            containerColor = Color(0xFF0A0A0A),
            contentWindowInsets = WindowInsets(0.dp),
            bottomBar = {
                AnimatedVisibility(
                    visible = showBottomBar,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                ) {
                    AppBottomBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(HomeRoute) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        onItemClick = { item ->
                            navController.navigate(DetailsRoute(item.id, item.mediaType.name))
                        },
                    )
                }

                composable<SearchRoute> {
                    SearchScreen(
                        onItemClick = { item ->
                            navController.navigate(DetailsRoute(item.id, item.mediaType.name))
                        },
                    )
                }

                composable<DetailsRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<DetailsRoute>()
                    DetailsScreen(
                        tmdbId = route.tmdbId,
                        mediaTypeStr = route.mediaType,
                        onBack = { navController.popBackStack() },
                        onPlayStream = { streamIdx ->
                            navController.navigate(PlayerRoute(route.tmdbId, route.mediaType, streamIdx))
                        },
                    )
                }

                composable<PlayerRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<PlayerRoute>()
                    PlayerScreen(
                        tmdbId = route.tmdbId,
                        mediaTypeStr = route.mediaType,
                        streamIndex = route.streamIndex,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable<SettingsRoute> {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
