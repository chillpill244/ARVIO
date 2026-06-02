package com.arflix.tv.ui.screens.details

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.model.Profile
import com.arflix.tv.navigation.Screen
import com.arflix.tv.ui.components.AppTopBar
import com.arflix.tv.ui.components.SidebarItem
import com.arflix.tv.ui.components.SkeletonDetailsPage
import com.arflix.tv.ui.components.TrailerPlayer
import com.arflix.tv.ui.components.topBarFocusedItem
import com.arflix.tv.ui.components.topBarMaxIndex
import com.arflix.tv.ui.theme.appBackgroundDark
import com.arflix.tv.util.LocalDeviceType
import kotlinx.coroutines.launch

@Composable
fun IptvDetailsScreen(
    iptvId: Int,
    mediaType: MediaType,
    initialSeason: Int? = null,
    initialEpisode: Int? = null,
    viewModel: IptvDetailsViewModel = hiltViewModel(),
    currentProfile: Profile? = null,
    onNavigateToPlayer: (MediaType, Int, Int?, Int?, String?, String?, String?, String?, Long?) -> Unit,
    onNavigateToHome: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToWatchlist: () -> Unit = {},
    onNavigateToTv: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onSwitchProfile: () -> Unit = {},
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isMobile = LocalDeviceType.current.isTouchDevice()
    val scope = rememberCoroutineScope()

    var focusedSection by remember { mutableStateOf(FocusSection.BUTTONS) }
    var buttonIndex by remember { mutableIntStateOf(0) }
    var episodeIndex by rememberSaveable { mutableIntStateOf(0) }
    var seasonIndex by rememberSaveable { mutableIntStateOf(0) }
    var castIndex by remember { mutableIntStateOf(0) }
    var suppressSelectUntilMs by remember { mutableLongStateOf(0L) }
    var isSidebarFocused by remember { mutableStateOf(false) }
    var showTrailerPlayer by remember { mutableStateOf(false) }
    var seasonSelectDownAtMs by remember { mutableLongStateOf(0L) }

    val hasProfile = currentProfile != null
    val maxSidebarIndex = topBarMaxIndex(hasProfile)
    var sidebarFocusIndex by remember { mutableIntStateOf(if (hasProfile) 2 else 1) }

    LaunchedEffect(iptvId, mediaType) {
        focusedSection = FocusSection.BUTTONS
        buttonIndex = 0
        episodeIndex = 0
        seasonIndex = 0
        viewModel.loadDetails(iptvId, mediaType, initialSeason, initialEpisode)
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        suppressSelectUntilMs = SystemClock.elapsedRealtime() + 150L
    }

    LaunchedEffect(uiState.episodes.size, uiState.totalSeasons) {
        if (episodeIndex >= uiState.episodes.size) {
            episodeIndex = (uiState.episodes.size - 1).coerceAtLeast(0)
        }
        if (seasonIndex >= uiState.totalSeasons) {
            seasonIndex = (uiState.totalSeasons - 1).coerceAtLeast(0)
        }
    }

    fun playEpisode(epIdx: Int) {
        val ep = uiState.episodes.getOrNull(epIdx) ?: return
        val tmdbId = uiState.item?.id ?: 0
        scope.launch {
            val url = viewModel.resolveEpisodeStreamUrl(ep.id)
            if (!url.isNullOrBlank()) {
                onNavigateToPlayer(mediaType, tmdbId, ep.seasonNumber, ep.episodeNumber, null, url, null, null, null)
            }
        }
    }

    fun playVod() {
        val tmdbId = uiState.item?.id ?: 0
        val stream = uiState.streams.firstOrNull()
        if (!stream?.url.isNullOrBlank()) {
            onNavigateToPlayer(mediaType, tmdbId, null, null, null, stream?.url, null, null, null)
        }
    }

    val hasValidTmdb = (uiState.item?.id ?: 0) != 0
    // Ordered indices of visible action buttons (Sources is never shown for IPTV)
    val activeButtonIndices = buildList {
        add(0) // Play
        if (uiState.trailerKey != null) add(2) // Trailer
        if (hasValidTmdb) { add(3); add(4) } // Watched, Watchlist
    }

    val keyModifier = Modifier.onKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) {
            when (event.key) {
                Key.Back -> {
                    if (showTrailerPlayer) { showTrailerPlayer = false; true }
                    else { onBack(); true }
                }
                Key.DirectionLeft -> {
                    if (isSidebarFocused) {
                        if (sidebarFocusIndex > 0) sidebarFocusIndex--
                        true
                    } else {
                        when (focusedSection) {
                            FocusSection.BUTTONS -> {
                                val pos = activeButtonIndices.indexOf(buttonIndex)
                                if (pos <= 0) isSidebarFocused = true
                                else buttonIndex = activeButtonIndices[pos - 1]
                            }
                            FocusSection.EPISODES -> if (episodeIndex > 0) episodeIndex-- else isSidebarFocused = true
                            FocusSection.SEASONS -> if (seasonIndex > 0) seasonIndex-- else isSidebarFocused = true
                            FocusSection.CAST -> if (castIndex > 0) castIndex-- else isSidebarFocused = true
                            else -> isSidebarFocused = true
                        }
                        true
                    }
                }
                Key.DirectionRight -> {
                    if (isSidebarFocused) {
                        if (sidebarFocusIndex < maxSidebarIndex) sidebarFocusIndex++
                        true
                    } else {
                        when (focusedSection) {
                            FocusSection.BUTTONS -> {
                                val pos = activeButtonIndices.indexOf(buttonIndex)
                                if (pos < activeButtonIndices.size - 1) buttonIndex = activeButtonIndices[pos + 1]
                            }
                            FocusSection.EPISODES -> if (episodeIndex < uiState.episodes.size - 1) episodeIndex++
                            FocusSection.SEASONS -> if (seasonIndex < uiState.totalSeasons - 1) seasonIndex++
                            FocusSection.CAST -> if (castIndex < uiState.cast.size - 1) castIndex++
                            else -> {}
                        }
                        true
                    }
                }
                Key.DirectionUp -> {
                    if (isSidebarFocused) {
                        true
                    } else {
                        val isTV = mediaType == MediaType.TV
                        val hasEpisodes = uiState.episodes.isNotEmpty()
                        val hasSeasons = uiState.totalSeasons > 1
                        val hasCast = uiState.cast.isNotEmpty()
                        focusedSection = when (focusedSection) {
                            FocusSection.BUTTONS -> {
                                isSidebarFocused = true
                                FocusSection.BUTTONS
                            }
                            FocusSection.SEASONS -> FocusSection.BUTTONS
                            FocusSection.EPISODES -> {
                                if (isTV && hasSeasons) FocusSection.SEASONS else FocusSection.BUTTONS
                            }
                            FocusSection.CAST -> {
                                when {
                                    isTV && hasEpisodes -> FocusSection.EPISODES
                                    isTV && hasSeasons -> FocusSection.SEASONS
                                    else -> FocusSection.BUTTONS
                                }
                            }
                            else -> FocusSection.BUTTONS
                        }
                        true
                    }
                }
                Key.DirectionDown -> {
                    if (isSidebarFocused) {
                        isSidebarFocused = false
                        true
                    } else {
                        val isTV = mediaType == MediaType.TV
                        val hasEpisodes = uiState.episodes.isNotEmpty()
                        val hasSeasons = uiState.totalSeasons > 1
                        val hasCast = uiState.cast.isNotEmpty()
                        focusedSection = when (focusedSection) {
                            FocusSection.BUTTONS -> when {
                                isTV && hasSeasons -> FocusSection.SEASONS
                                isTV && hasEpisodes -> FocusSection.EPISODES
                                hasCast -> FocusSection.CAST
                                else -> FocusSection.BUTTONS
                            }
                            FocusSection.SEASONS -> when {
                                hasEpisodes -> FocusSection.EPISODES
                                hasCast -> FocusSection.CAST
                                else -> FocusSection.SEASONS
                            }
                            FocusSection.EPISODES -> when {
                                hasCast -> FocusSection.CAST
                                else -> FocusSection.EPISODES
                            }
                            FocusSection.CAST -> FocusSection.CAST
                            else -> focusedSection
                        }
                        true
                    }
                }
                Key.Enter, Key.DirectionCenter -> {
                    if (SystemClock.elapsedRealtime() < suppressSelectUntilMs) return@onKeyEvent false
                    if (isSidebarFocused) {
                        if (hasProfile && sidebarFocusIndex == 0) {
                            onSwitchProfile()
                        } else {
                            when (topBarFocusedItem(sidebarFocusIndex, hasProfile)) {
                                SidebarItem.HOME -> onNavigateToHome()
                                SidebarItem.SEARCH -> onNavigateToSearch()
                                SidebarItem.WATCHLIST -> onNavigateToWatchlist()
                                SidebarItem.TV -> onNavigateToTv()
                                SidebarItem.SETTINGS -> onNavigateToSettings()
                                else -> {}
                            }
                        }
                        return@onKeyEvent true
                    }
                    when (focusedSection) {
                        FocusSection.BUTTONS -> when (buttonIndex) {
                            0 -> {
                                if (mediaType == MediaType.TV) playEpisode(episodeIndex) else playVod()
                                true
                            }
                            2 -> {
                                uiState.trailerKey?.let { showTrailerPlayer = true }
                                true
                            }
                            3 -> { viewModel.toggleWatched(episodeIndex); true }
                            4 -> { viewModel.toggleWatchlist(); true }
                            else -> false
                        }
                        FocusSection.EPISODES -> {
                            seasonSelectDownAtMs = SystemClock.elapsedRealtime()
                            playEpisode(episodeIndex)
                            true
                        }
                        FocusSection.SEASONS -> {
                            seasonSelectDownAtMs = SystemClock.elapsedRealtime()
                            true
                        }
                        else -> false
                    }
                }
                else -> false
            }
        } else if (event.type == KeyEventType.KeyUp &&
            (event.key == Key.Enter || event.key == Key.DirectionCenter)
        ) {
            if (!isSidebarFocused && focusedSection == FocusSection.SEASONS && seasonSelectDownAtMs > 0L) {
                val heldMs = SystemClock.elapsedRealtime() - seasonSelectDownAtMs
                seasonSelectDownAtMs = 0L
                if (heldMs < 900L) {
                    episodeIndex = 0
                    val actualSeason = uiState.availableSeasons.getOrElse(seasonIndex) { seasonIndex + 1 }
                    viewModel.loadSeason(actualSeason)
                }
                true
            } else {
                seasonSelectDownAtMs = 0L
                false
            }
        } else {
            false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appBackgroundDark())
            .focusRequester(focusRequester)
            .focusable()
            .then(keyModifier)
    ) {
        if (uiState.item == null) {
            SkeletonDetailsPage(
                isTV = mediaType == MediaType.TV,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            uiState.item?.let { item ->
                DetailsContent(
                    item = item,
                    logoUrl = uiState.logoUrl,
                    episodes = uiState.episodes,
                    totalSeasons = uiState.totalSeasons,
                    currentSeason = uiState.currentSeason,
                    cast = uiState.cast,
                    reviews = emptyList(),
                    similar = emptyList(),
                    similarLogoUrls = emptyMap(),
                    focusedSection = focusedSection,
                    buttonIndex = buttonIndex,
                    episodeIndex = episodeIndex,
                    seasonIndex = seasonIndex,
                    castIndex = castIndex,
                    reviewIndex = 0,
                    similarIndex = 0,
                    isInWatchlist = uiState.isInWatchlist,
                    genres = uiState.genres,
                    budget = null,
                    seasonProgress = emptyMap(),
                    playLabel = uiState.playLabel,
                    hasTrailer = uiState.trailerKey != null,
                    contentHasFocus = !isSidebarFocused,
                    isMobile = isMobile,
                    showSources = false,
                    showWatchlistAndWatched = hasValidTmdb,
                    onBack = onBack,
                    onButtonClick = { idx ->
                        when (idx) {
                            0 -> if (mediaType == MediaType.TV) playEpisode(episodeIndex) else playVod()
                            2 -> uiState.trailerKey?.let { showTrailerPlayer = true }
                            3 -> viewModel.toggleWatched(episodeIndex)
                            4 -> viewModel.toggleWatchlist()
                        }
                    },
                    onSeasonClick = { idx ->
                        seasonIndex = idx
                        episodeIndex = 0
                        val actualSeason = uiState.availableSeasons.getOrElse(idx) { idx + 1 }
                        viewModel.loadSeason(actualSeason)
                    },
                    onEpisodeClick = { idx ->
                        episodeIndex = idx
                        playEpisode(idx)
                    },
                    onCastClick = {},
                    onSimilarClick = {},
                    onCollectionClick = {}
                )
            }
        }

        if (!LocalDeviceType.current.isTouchDevice()) {
            AppTopBar(
                selectedItem = SidebarItem.HOME,
                isFocused = isSidebarFocused,
                focusedIndex = sidebarFocusIndex,
                profile = currentProfile
            )
        }

        if (showTrailerPlayer && uiState.trailerKey != null) {
            BackHandler { showTrailerPlayer = false }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .zIndex(50f)
                    .clickable { showTrailerPlayer = false }
            ) {
                TrailerPlayer(
                    youtubeKey = uiState.trailerKey!!,
                    modifier = Modifier.fillMaxSize(),
                    delayMs = 0L,
                    volume = 1f
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 16.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { showTrailerPlayer = false },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
