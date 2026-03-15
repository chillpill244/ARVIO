package com.arflix.tv.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.model.Profile
import com.arflix.tv.data.repository.AuthState
import com.arflix.tv.data.repository.MediaRepository
import com.arflix.tv.ui.screens.details.DetailsScreen
import com.arflix.tv.ui.screens.home.HomeScreen
import com.arflix.tv.ui.screens.login.LoginScreen
import com.arflix.tv.ui.screens.movies.MoviesScreen
import com.arflix.tv.ui.screens.player.PlayerScreen
import com.arflix.tv.ui.screens.search.SearchScreen
import com.arflix.tv.ui.screens.series.SeriesScreen
import com.arflix.tv.ui.screens.settings.SettingsScreen
import com.arflix.tv.ui.screens.tv.TvScreen
import com.arflix.tv.ui.screens.watchlist.WatchlistScreen
import com.arflix.tv.ui.screens.profile.ProfileSelectionScreen

/**
 * Navigation destinations
 */
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object Search : Screen("search")
    object Movies : Screen("movies")
    object Series : Screen("series")
    object Watchlist : Screen("watchlist")
    object Tv : Screen("tv?channelId={channelId}&streamUrl={streamUrl}") {
        fun createRoute(channelId: String? = null, streamUrl: String? = null): String {
            if (channelId == null) return "tv"
            val enc = java.net.URLEncoder.encode(channelId, "UTF-8")
            val streamEnc = streamUrl?.let { java.net.URLEncoder.encode(it, "UTF-8") }
            return if (streamEnc != null) "tv?channelId=$enc&streamUrl=$streamEnc" else "tv?channelId=$enc"
        }
    }
    object Settings : Screen("settings")
    object ProfileSelection : Screen("profile_selection")
    
    object Details : Screen("details/{mediaType}/{mediaId}?initialSeason={initialSeason}&initialEpisode={initialEpisode}") {
        fun createRoute(
            mediaType: MediaType,
            mediaId: Int,
            initialSeason: Int? = null,
            initialEpisode: Int? = null
        ): String {
            val base = "details/${mediaType.name.lowercase()}/$mediaId"
            val params = mutableListOf<String>()
            initialSeason?.let { params.add("initialSeason=$it") }
            initialEpisode?.let { params.add("initialEpisode=$it") }
            return if (params.isNotEmpty()) "$base?${params.joinToString("&")}" else base
        }
    }
    
    object Player : Screen("player/{mediaType}/{mediaId}?seasonNumber={seasonNumber}&episodeNumber={episodeNumber}&imdbId={imdbId}&streamUrl={streamUrl}&preferredAddonId={preferredAddonId}&preferredSourceName={preferredSourceName}&startPositionMs={startPositionMs}") {
        fun createRoute(
            mediaType: MediaType,
            mediaId: Int,
            seasonNumber: Int? = null,
            episodeNumber: Int? = null,
            imdbId: String? = null,
            streamUrl: String? = null,
            preferredAddonId: String? = null,
            preferredSourceName: String? = null,
            startPositionMs: Long? = null
        ): String {
            val base = "player/${mediaType.name.lowercase()}/$mediaId"
            val params = mutableListOf<String>()
            seasonNumber?.let { params.add("seasonNumber=$it") }
            episodeNumber?.let { params.add("episodeNumber=$it") }
            imdbId?.let { params.add("imdbId=${java.net.URLEncoder.encode(it, "UTF-8")}") }
            streamUrl?.let { params.add("streamUrl=${java.net.URLEncoder.encode(it, "UTF-8")}") }
            preferredAddonId?.let { params.add("preferredAddonId=${java.net.URLEncoder.encode(it, "UTF-8")}") }
            preferredSourceName?.let { params.add("preferredSourceName=${java.net.URLEncoder.encode(it, "UTF-8")}") }
            startPositionMs?.let { params.add("startPositionMs=$it") }
            return if (params.isNotEmpty()) "$base?${params.joinToString("&")}" else base
        }
    }
}

/**
 * Main navigation graph
 */
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Login.route,
    preloadedCategories: List<Category> = emptyList(),
    preloadedHeroItem: MediaItem? = null,
    preloadedHeroLogoUrl: String? = null,
    preloadedLogoCache: Map<String, String> = emptyMap(),
    currentProfile: Profile? = null,
    onSwitchProfile: () -> Unit = {},
    onExitApp: () -> Unit = {},
    mediaRepository: MediaRepository? = null
) {
    val navigateTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(Screen.Home.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val navigateHome: () -> Unit = {
        // Navigate to Home clearing the entire back stack above it.
        // Uses navigate() instead of popBackStack() because popBackStack can
        // silently fail if Home is not found, and restoreState on other
        // navigateTopLevel calls can bring back stale Details pages.
        navController.navigate(Screen.Home.route) {
            popUpTo(Screen.Home.route) { inclusive = true; saveState = false }
            launchSingleTop = true
            restoreState = false
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Login screen
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        // Home screen
        composable(Screen.Home.route) {
            HomeScreen(
                preloadedCategories = preloadedCategories,
                preloadedHeroItem = preloadedHeroItem,
                preloadedHeroLogoUrl = preloadedHeroLogoUrl,
                preloadedLogoCache = preloadedLogoCache,
                currentProfile = currentProfile,
                onNavigateToDetails = { mediaType, mediaId, initialSeason, initialEpisode ->
                    navController.navigate(Screen.Details.createRoute(mediaType, mediaId, initialSeason, initialEpisode))
                },
                onNavigateToSearch = {
                    navigateTopLevel(Screen.Search.route)
                },
                onNavigateToMovies = {
                    navigateTopLevel(Screen.Movies.route)
                },
                onNavigateToSeries = {
                    navigateTopLevel(Screen.Series.route)
                },
                onNavigateToWatchlist = {
                    navigateTopLevel(Screen.Watchlist.route)
                },
                onNavigateToTv = { channelId, streamUrl ->
                    navigateTopLevel(Screen.Tv.createRoute(channelId, streamUrl))
                },
                onNavigateToSettings = {
                    navigateTopLevel(Screen.Settings.route)
                },
                onSwitchProfile = {
                    onSwitchProfile()
                    navController.navigate(Screen.ProfileSelection.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onExitApp = onExitApp
            )
        }
        
        // Search screen
        composable(Screen.Search.route) {
            SearchScreen(
                currentProfile = currentProfile,
                onNavigateToDetails = { mediaType, mediaId ->
                    navController.navigate(Screen.Details.createRoute(mediaType, mediaId))
                },
                onNavigateToPlayer = { type, id, season, episode, imdbId, url, preferredAddonId, preferredSourceName, startPositionMs ->
                    navController.navigate(
                        Screen.Player.createRoute(
                            mediaType = type,
                            mediaId = id,
                            seasonNumber = season,
                            episodeNumber = episode,
                            imdbId = imdbId,
                            streamUrl = url,
                            preferredAddonId = preferredAddonId,
                            preferredSourceName = preferredSourceName,
                            startPositionMs = startPositionMs
                        )
                    )
                },
                onNavigateToHome = { navigateHome() },
                onNavigateToMovies = { navigateTopLevel(Screen.Movies.route) },
                onNavigateToSeries = { navigateTopLevel(Screen.Series.route) },
                onNavigateToWatchlist = { navigateTopLevel(Screen.Watchlist.route) },
                onNavigateToTv = { navigateTopLevel(Screen.Tv.createRoute()) },
                onNavigateToSettings = { navigateTopLevel(Screen.Settings.route) },
                onSwitchProfile = {
                    onSwitchProfile()
                    navController.navigate(Screen.ProfileSelection.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Watchlist screen
        composable(Screen.Watchlist.route) {
            WatchlistScreen(
                currentProfile = currentProfile,
                onNavigateToDetails = { mediaType, mediaId ->
                    navController.navigate(Screen.Details.createRoute(mediaType, mediaId))
                },
                onNavigateToHome = { navigateHome() },
                onNavigateToSearch = { navigateTopLevel(Screen.Search.route) },
                onNavigateToMovies = { navigateTopLevel(Screen.Movies.route) },
                onNavigateToSeries = { navigateTopLevel(Screen.Series.route) },
                onNavigateToTv = { channelId, streamUrl ->
                    navigateTopLevel(Screen.Tv.createRoute(channelId, streamUrl))
                },
                onNavigateToSettings = { navigateTopLevel(Screen.Settings.route) },
                onSwitchProfile = {
                    onSwitchProfile()
                    navController.navigate(Screen.ProfileSelection.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Movies screen
        composable(Screen.Movies.route) {
            com.arflix.tv.ui.screens.movies.MoviesScreen(
                currentProfile = currentProfile,
                onNavigateToHome = { navigateHome() },
                onNavigateToSearch = { navigateTopLevel(Screen.Search.route) },
                onNavigateToWatchlist = { navigateTopLevel(Screen.Watchlist.route) },
                onNavigateToTv = { channelId, streamUrl ->
                    navigateTopLevel(Screen.Tv.createRoute(channelId, streamUrl))
                },
                onNavigateToSeries = { navigateTopLevel(Screen.Series.route) },
                onNavigateToSettings = { navigateTopLevel(Screen.Settings.route) },
                onNavigateToDetails = { item ->
                    // Cache the item (with vodStreams if available) so DetailsViewModel can retrieve it
                    mediaRepository?.cacheItem(item)
                    navController.navigate(Screen.Details.createRoute(item.mediaType, item.id))
                },
                onSwitchProfile = {
                    onSwitchProfile()
                    navController.navigate(Screen.ProfileSelection.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Series screen
        composable(Screen.Series.route) {
            com.arflix.tv.ui.screens.series.SeriesScreen(
                currentProfile = currentProfile,
                onNavigateToHome = { navigateHome() },
                onNavigateToSearch = { navigateTopLevel(Screen.Search.route) },
                onNavigateToWatchlist = { navigateTopLevel(Screen.Watchlist.route) },
                onNavigateToTv = { channelId, streamUrl ->
                    navigateTopLevel(Screen.Tv.createRoute(channelId, streamUrl))
                },
                onNavigateToMovies = { navigateTopLevel(Screen.Movies.route) },
                onNavigateToSettings = { navigateTopLevel(Screen.Settings.route) },
                onNavigateToDetails = { item ->
                    // Cache the item (with vodStreams if available) so DetailsViewModel can retrieve it
                    mediaRepository?.cacheItem(item)
                    navController.navigate(Screen.Details.createRoute(item.mediaType, item.id))
                },
                onSwitchProfile = {
                    onSwitchProfile()
                    navController.navigate(Screen.ProfileSelection.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // TV screen
        composable(
            route = Screen.Tv.route,
            arguments = listOf(
                navArgument("channelId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("streamUrl") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val initialChannelId = backStackEntry.arguments?.getString("channelId")
            val initialStreamUrl = backStackEntry.arguments?.getString("streamUrl")
            TvScreen(
                currentProfile = currentProfile,
                initialChannelId = initialChannelId,
                initialStreamUrl = initialStreamUrl,
                onNavigateToHome = { navigateHome() },
                onNavigateToSearch = { navigateTopLevel(Screen.Search.route) },
                onNavigateToMovies = { navigateTopLevel(Screen.Movies.route) },
                onNavigateToSeries = { navigateTopLevel(Screen.Series.route) },
                onNavigateToWatchlist = { navigateTopLevel(Screen.Watchlist.route) },
                onNavigateToSettings = { navigateTopLevel(Screen.Settings.route) },
                onSwitchProfile = {
                    onSwitchProfile()
                    navController.navigate(Screen.ProfileSelection.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Settings screen
        composable(Screen.Settings.route) {
            SettingsScreen(
                currentProfile = currentProfile,
                onNavigateToHome = { navigateHome() },
                onNavigateToSearch = { navigateTopLevel(Screen.Search.route) },
                onNavigateToMovies = { navigateTopLevel(Screen.Movies.route) },
                onNavigateToSeries = { navigateTopLevel(Screen.Series.route) },
                onNavigateToTv = { navigateTopLevel(Screen.Tv.createRoute()) },
                onNavigateToWatchlist = { navigateTopLevel(Screen.Watchlist.route) },
                onSwitchProfile = {
                    onSwitchProfile()
                    navController.navigate(Screen.ProfileSelection.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Profile selection screen
        composable(Screen.ProfileSelection.route) {
            ProfileSelectionScreen(
                onProfileSelected = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.ProfileSelection.route) { inclusive = true }
                    }
                },
                onShowAddProfile = { /* Handled internally by ProfileSelectionScreen */ }
            )
        }

        // Details screen
        composable(
            route = Screen.Details.route,
            arguments = listOf(
                navArgument("mediaType") { type = NavType.StringType },
                navArgument("mediaId") { type = NavType.IntType },
                navArgument("initialSeason") {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("initialEpisode") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            val mediaTypeStr = backStackEntry.arguments?.getString("mediaType") ?: "movie"
            val mediaId = backStackEntry.arguments?.getInt("mediaId") ?: 0
            val initialSeason = backStackEntry.arguments?.getInt("initialSeason")?.takeIf { it >= 0 }
            val initialEpisode = backStackEntry.arguments?.getInt("initialEpisode")?.takeIf { it >= 0 }
            val mediaType = if (mediaTypeStr == "tv") MediaType.TV else MediaType.MOVIE

            DetailsScreen(
                mediaType = mediaType,
                mediaId = mediaId,
                initialSeason = initialSeason,
                initialEpisode = initialEpisode,
                currentProfile = currentProfile,
                onNavigateToPlayer = { type, id, season, episode, imdbId, url, preferredAddonId, preferredSourceName, startPositionMs ->
                    navController.navigate(
                        Screen.Player.createRoute(
                            mediaType = type,
                            mediaId = id,
                            seasonNumber = season,
                            episodeNumber = episode,
                            imdbId = imdbId,
                            streamUrl = url,
                            preferredAddonId = preferredAddonId,
                            preferredSourceName = preferredSourceName,
                            startPositionMs = startPositionMs
                        )
                    )
                },
                onNavigateToDetails = { type, id ->
                    navController.navigate(Screen.Details.createRoute(type, id))
                },
                onNavigateToHome = {
                    navigateHome()
                },
                onNavigateToSearch = {
                    navigateTopLevel(Screen.Search.route)
                },
                onNavigateToMovies = {
                    navigateTopLevel(Screen.Movies.route)
                },
                onNavigateToSeries = {
                    navigateTopLevel(Screen.Series.route)
                },
                onNavigateToTv = {
                    navigateTopLevel(Screen.Tv.createRoute())
                },
                onNavigateToWatchlist = {
                    navigateTopLevel(Screen.Watchlist.route)
                },
                onNavigateToSettings = {
                    navigateTopLevel(Screen.Settings.route)
                },
                onSwitchProfile = {
                    onSwitchProfile()
                    navController.navigate(Screen.ProfileSelection.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        
        // Player screen
        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("mediaType") { type = NavType.StringType },
                navArgument("mediaId") { type = NavType.IntType },
                navArgument("seasonNumber") { 
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("episodeNumber") { 
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("imdbId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("streamUrl") { 
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("preferredAddonId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("preferredSourceName") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("startPositionMs") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val mediaTypeStr = backStackEntry.arguments?.getString("mediaType") ?: "movie"
            val mediaId = backStackEntry.arguments?.getInt("mediaId") ?: 0
            val seasonNumber = backStackEntry.arguments?.getInt("seasonNumber")?.takeIf { it >= 0 }
            val episodeNumber = backStackEntry.arguments?.getInt("episodeNumber")?.takeIf { it >= 0 }
            val imdbId = backStackEntry.arguments?.getString("imdbId")?.takeIf { it.isNotBlank() }
            val streamUrl = backStackEntry.arguments?.getString("streamUrl")?.takeIf { it.isNotEmpty() }
            val preferredAddonId = backStackEntry.arguments?.getString("preferredAddonId")?.takeIf { it.isNotBlank() }
            val preferredSourceName = backStackEntry.arguments?.getString("preferredSourceName")?.takeIf { it.isNotBlank() }
            val startPositionMs = backStackEntry.arguments?.getLong("startPositionMs")?.takeIf { it >= 0L }
            val mediaType = when (mediaTypeStr) {
                "tv" -> MediaType.TV
                "live_tv" -> MediaType.LIVE_TV
                else -> MediaType.MOVIE
            }
            
            PlayerScreen(
                mediaType = mediaType,
                mediaId = mediaId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                imdbId = imdbId,
                streamUrl = streamUrl,
                preferredAddonId = preferredAddonId,
                preferredSourceName = preferredSourceName,
                startPositionMs = startPositionMs,
                onBack = { navController.popBackStack() },
                onPlayNext = { nextSeason, nextEpisode, nextPreferredAddonId, nextPreferredSourceName, nextStreamUrl ->
                    // Navigate to next episode
                    navController.navigate(
                        Screen.Player.createRoute(
                            mediaType = mediaType,
                            mediaId = mediaId,
                            seasonNumber = nextSeason,
                            episodeNumber = nextEpisode,
                            streamUrl = nextStreamUrl,
                            preferredAddonId = nextPreferredAddonId,
                            preferredSourceName = nextPreferredSourceName
                        )
                    ) {
                        popUpTo(Screen.Player.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
