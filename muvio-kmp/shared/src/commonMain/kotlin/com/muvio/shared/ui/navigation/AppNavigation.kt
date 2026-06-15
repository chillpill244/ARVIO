package com.muvio.shared.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.muvio.shared.ui.screens.details.DetailsScreen
import com.muvio.shared.ui.screens.home.HomeScreen
import com.muvio.shared.ui.screens.player.PlayerScreen
import com.muvio.shared.ui.screens.search.SearchScreen
import com.muvio.shared.ui.screens.settings.SettingsScreen
import kotlinx.serialization.Serializable

// ── Type-safe route objects ───────────────────────────────────────────────────

@Serializable
object HomeRoute

@Serializable
object SearchRoute

@Serializable
object SettingsRoute

@Serializable
data class DetailsRoute(val tmdbId: Int, val mediaType: String)

@Serializable
data class PlayerRoute(val tmdbId: Int, val mediaType: String, val streamIndex: Int = 0)

// ── NavHost ───────────────────────────────────────────────────────────────────

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = HomeRoute) {

        composable<HomeRoute> {
            HomeScreen(
                onItemClick = { item ->
                    navController.navigate(DetailsRoute(item.id, item.mediaType.name))
                },
                onSearchClick = { navController.navigate(SearchRoute) },
                onSettingsClick = { navController.navigate(SettingsRoute) },
            )
        }

        composable<SearchRoute> {
            SearchScreen(
                onItemClick = { item ->
                    navController.navigate(DetailsRoute(item.id, item.mediaType.name))
                },
                onBack = { navController.popBackStack() },
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
