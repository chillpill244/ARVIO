
@file:Suppress("UnsafeOptInUsageError")
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.arflix.tv.ui.screens.tv

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.arflix.tv.data.model.IptvChannel
import com.arflix.tv.data.model.IptvNowNext
import com.arflix.tv.data.model.IptvProgram
import com.arflix.tv.network.OkHttpProvider
import com.arflix.tv.ui.components.AppTopBar
import com.arflix.tv.ui.components.KeepScreenOn
import com.arflix.tv.ui.components.AppTopBarContentTopInset
import com.arflix.tv.util.LocalDeviceType
import com.arflix.tv.ui.components.SidebarItem
import com.arflix.tv.ui.components.topBarFocusedItem
import com.arflix.tv.ui.components.topBarMaxIndex
import com.arflix.tv.ui.theme.AccentGreen
import com.arflix.tv.ui.theme.ArflixTypography
import com.arflix.tv.ui.theme.BackgroundCard
import com.arflix.tv.ui.theme.BackgroundDark
import com.arflix.tv.ui.theme.Pink
import com.arflix.tv.ui.theme.TextPrimary
import com.arflix.tv.ui.theme.TextSecondary
import com.arflix.tv.ui.skin.ArvioFocusableSurface
import com.arflix.tv.ui.skin.ArvioSkin
import com.arflix.tv.ui.skin.arvioFocusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs


private object TvScreenRegexes {
    val NON_ALPHANUMERIC_REGEX = Regex("""[^a-z0-9]+""")
}

private enum class TvFocusZone {
    SIDEBAR,
    SEARCH,
    GROUPS,
    GUIDE
}


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvScreen(
    viewModel: TvViewModel = hiltViewModel(),
    currentProfile: com.arflix.tv.data.model.Profile? = null,
    contentStartPadding: Dp = 0.dp,
    initialChannelId: String? = null,
    initialStreamUrl: String? = null,
    onNavigateToHome: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToMovies: () -> Unit = {},
    onNavigateToSeries: () -> Unit = {},
    onNavigateToWatchlist: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToContentMenu: () -> Unit = {},
    focusTopBar: Boolean = false,
    onTopBarFocused: () -> Unit = {},
    onSwitchProfile: () -> Unit = {},
    onFullscreenChanged: (Boolean) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isMobile = LocalDeviceType.current.isTouchDevice()

    // Keep screen on while live TV is active on all device types.
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var focusZone by rememberSaveable { mutableStateOf(if (uiState.isConfigured) TvFocusZone.GROUPS else TvFocusZone.SIDEBAR) }
    var searchActive by remember { mutableStateOf(false) }
    val hasProfile = currentProfile != null
    val maxSidebarIndex = topBarMaxIndex(hasProfile)
    var sidebarFocusIndex by rememberSaveable { mutableIntStateOf(if (hasProfile) 4 else 3) }
    var groupIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedGroupIndex by rememberSaveable { mutableIntStateOf(0) }
    var channelIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var playingChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    KeepScreenOn(active = playingChannelId != null)
    var showGroupContextMenu by remember { mutableStateOf(false) }
    // Track if user has explicitly clicked to play (prevents auto-play)
    var userHasClickedPlay by remember { mutableStateOf(false) }
    // When launched from Home with a stream URL, start in fullscreen immediately
    // to avoid a flash of the TV page channel list.
    var isFullScreen by rememberSaveable { mutableStateOf(initialStreamUrl != null) }
    var showFullscreenOverlay by remember { mutableStateOf(false) }
    var fullscreenOverlayTrigger by remember { mutableStateOf(0L) } // timestamp to reset auto-hide timer
    var centerDownAtMs by remember { mutableStateOf<Long?>(null) }
    var showGroupsMobile by rememberSaveable { mutableStateOf(true) }
    var channelGridQuery by remember { mutableStateOf("") }
    var categoryQuery by remember { mutableStateOf("") }
    var gridColumns by remember { mutableIntStateOf(5) }
    var isPiPMode by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(focusTopBar) {
        if (focusTopBar) {
            focusZone = TvFocusZone.SIDEBAR
            onTopBarFocused()
        }
    }

    LaunchedEffect(isFullScreen, isPiPMode) {
        onFullscreenChanged(isFullScreen || isPiPMode)
    }

    BackHandler(enabled = isFullScreen || isPiPMode) {
        // Exit PiP first if in PiP mode
        if (isPiPMode) {
            isPiPMode = false
            // Exit PiP mode gracefully - app will receive PiP lifecycle changes
        } else {
            // Always return to EPG guide first, regardless of how we got here
            isFullScreen = false
            showFullscreenOverlay = false
        }
    }

    val groupsListState = rememberLazyListState()
    val channelsListState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val contentTopPadding = (AppTopBarContentTopInset - 14.dp).coerceAtLeast(52.dp)

    val groups by remember(uiState.snapshot.grouped, uiState.snapshot.favoriteGroups, uiState.snapshot.favoriteChannels) {
        derivedStateOf { uiState.groups }
    }
    val filteredGroups = remember(groups, categoryQuery) {
        if (categoryQuery.isBlank()) groups
        else groups.filter { it.contains(categoryQuery.trim(), ignoreCase = true) }
    }
    val safeGroupIndex = groupIndex.coerceIn(0, (filteredGroups.size - 1).coerceAtLeast(0))
    val safeSelectedGroupIndex = selectedGroupIndex.coerceIn(0, (filteredGroups.size - 1).coerceAtLeast(0))
    val selectedGroup = filteredGroups.getOrNull(safeSelectedGroupIndex).orEmpty()
    val channels = uiState.channelsByGroup[selectedGroup].orEmpty()
    val gridFavorites = uiState.snapshot.favoriteChannels.toSet()
    val filteredChannels = remember(channels, channelGridQuery, gridFavorites) {
        val base = if (channelGridQuery.isBlank()) channels
        else channels.filter { it.name.contains(channelGridQuery.trim(), ignoreCase = true) }
        base.sortedByDescending { gridFavorites.contains(it.id) }
    }
    val safeChannelIndex = channelIndex.coerceIn(0, (filteredChannels.size - 1).coerceAtLeast(0))
    // playingChannel should ONLY use playingChannelId, NOT selectedChannelId
    // selectedChannelId is just for visual focus/highlighting
    val playingChannel = playingChannelId?.let { uiState.channelLookup[it] }

    // Auto-select channel when navigated from Home "Favorite TV" row.
    // If initialStreamUrl was provided, playback already started instantly —
    // this just updates selectedChannelId once the lookup is ready.
    LaunchedEffect(initialChannelId, uiState.snapshot.channels.size) {
        if (initialChannelId != null && uiState.snapshot.channels.isNotEmpty()) {
            val channel = uiState.channelLookup[initialChannelId]
            if (channel != null) {
                selectedChannelId = channel.id
                // Only auto-play if initialStreamUrl was provided (explicit navigation from Home)
                if (initialStreamUrl != null) {
                    userHasClickedPlay = true
                    if (playingChannelId != channel.id) {
                        playingChannelId = channel.id
                    }
                    isFullScreen = true
                }
            }
        }
    }

    LaunchedEffect(filteredGroups.size) {
        if (groupIndex >= filteredGroups.size) groupIndex = 0
        if (selectedGroupIndex >= filteredGroups.size) selectedGroupIndex = 0
    }
    LaunchedEffect(uiState.isConfigured) {
        if (uiState.isConfigured && focusZone == TvFocusZone.SIDEBAR) {
            focusZone = TvFocusZone.GROUPS
        }
    }
    LaunchedEffect(filteredChannels.size) {
        if (channelIndex >= filteredChannels.size) channelIndex = 0
        if (selectedChannelId != null && uiState.snapshot.channels.none { it.id == selectedChannelId }) {
            selectedChannelId = null
        }
    }
    LaunchedEffect(safeGroupIndex, safeSelectedGroupIndex, focusZone, filteredGroups.size) {
        if (filteredGroups.isNotEmpty()) {
            when (focusZone) {
                TvFocusZone.GROUPS -> smoothScrollTo(groupsListState, safeGroupIndex)
                TvFocusZone.GUIDE -> smoothScrollTo(groupsListState, safeSelectedGroupIndex)
                else -> {}
            }
        }
    }
    LaunchedEffect(safeChannelIndex, focusZone) {
        if (focusZone == TvFocusZone.GUIDE && filteredChannels.isNotEmpty()) {
            kotlinx.coroutines.delay(80L)
            filteredChannels.getOrNull(safeChannelIndex)?.let { selectedChannelId = it.id }
            gridState.animateScrollToItem(safeChannelIndex.coerceAtLeast(0))
        }
    }
    LaunchedEffect(uiState.isConfigured, uiState.isLoading, uiState.snapshot.channels.size, groups.size) {
        if (uiState.isConfigured && !uiState.isLoading && uiState.snapshot.channels.isEmpty()) {
            viewModel.refresh(force = true, showLoading = true)
        }
    }
    LaunchedEffect(groups, selectedGroup, channels.size) {
        if (selectedGroup == "My Favorites" && channels.isEmpty() && groups.size > 1 && selectedGroupIndex == 0) {
            selectedGroupIndex = 1
            groupIndex = 1
        }
    }

    // OkHttp with connection pooling for faster channel switching
    val iptvHttpClient = remember {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(8, 10, TimeUnit.MINUTES))
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .dns(OkHttpProvider.dns)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS) // 5 min — live streams should not timeout during normal playback
            .build()
    }
    val iptvDataSourceFactory = remember(iptvHttpClient) {
        OkHttpDataSource.Factory(iptvHttpClient)
            .setUserAgent(OkHttpProvider.userAgent)
    }
    // HLS factory with chunkless preparation (used when stream is detected as HLS)
    val iptvHlsFactory = remember(iptvDataSourceFactory) {
        HlsMediaSource.Factory(iptvDataSourceFactory)
            .setAllowChunklessPreparation(true)
    }
    // Default factory handles all formats (MPEG-TS, HLS, DASH, progressive, etc.)
    val iptvDefaultFactory = remember(iptvDataSourceFactory) {
        DefaultMediaSourceFactory(context)
            .setDataSourceFactory(iptvDataSourceFactory)
    }

    // Track whether ExoPlayer has been released to guard against post-dispose calls
    var isPlayerReleased by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                20_000,    // minBufferMs — keep a healthy safety buffer for live streams
                120_000,   // maxBufferMs — 2 min ahead to survive brief IPTV server hiccups
                1_000,     // bufferForPlaybackMs — fast initial start
                3_000      // bufferForPlaybackAfterRebufferMs — resume quickly after stall
            )
            .setTargetBufferBytes(80 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(true) // prioritize time buffer for live continuity
            .setBackBuffer(10_000, true)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(iptvDefaultFactory)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            }
    }

    var miniPlayerView by remember { mutableStateOf<PlayerView?>(null) }
    var fullPlayerView by remember { mutableStateOf<PlayerView?>(null) }

    // Keep an always-current reference to the playing channel's stream URL
    // so the error listener never captures a stale closure.
    val currentStreamUrl by rememberUpdatedState(playingChannel?.streamUrl)

    DisposableEffect(Unit) {
        onDispose {
            isPlayerReleased = true
            exoPlayer.release()
        }
    }

    // Pause playback when the activity goes to background, resume when it comes back
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> if (!isPiPMode) exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> if (playingChannelId != null && !isPiPMode) exoPlayer.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // PiP entry — called directly from the button so there's no async state hop
    val enterPiP: () -> Unit = remember(context) {
        {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val activity = context.findTvActivity()
                if (activity != null) {
                    try {
                        val ratio = android.app.PictureInPictureParams.Builder()
                            .setAspectRatio(android.util.Rational(16, 9))
                            .build()
                        activity.enterPictureInPictureMode(ratio)
                        isPiPMode = true
                    } catch (_: Exception) { }
                }
            }
        }
    }

    // Helper: prepare ExoPlayer with a stream URL (shared by normal play + error retry)
    fun prepareStream(stream: String) {
        if (isPlayerReleased) return
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        val mediaItem = MediaItem.Builder()
            .setUri(stream)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setMinPlaybackSpeed(1.0f)
                    .setMaxPlaybackSpeed(1.0f)
                    .setTargetOffsetMs(4_000)
                    .build()
            )
            .build()
        val streamLower = stream.lowercase()
        if (streamLower.contains(".m3u8") || streamLower.contains("/hls") || streamLower.contains("format=hls")) {
            exoPlayer.setMediaSource(iptvHlsFactory.createMediaSource(mediaItem))
        } else {
            exoPlayer.setMediaItem(mediaItem)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // Track the last stream URL prepared to avoid redundant prepareStream calls
    var lastPreparedStreamUrl by remember { mutableStateOf<String?>(null) }

    // Instant playback: if we have a stream URL from Home, start playing immediately
    // before the full channel list is loaded.
    LaunchedEffect(Unit) {
        if (initialStreamUrl != null && initialChannelId != null) {
            playingChannelId = initialChannelId
            userHasClickedPlay = true // Coming from Home with explicit selection
            isFullScreen = true
            lastPreparedStreamUrl = initialStreamUrl
            prepareStream(initialStreamUrl)
        }
    }

    LaunchedEffect(playingChannelId, playingChannel?.streamUrl) {
        var stream = playingChannel?.streamUrl ?: return@LaunchedEffect
        if (isPlayerReleased) return@LaunchedEffect
        // Resolve Stalker portal cmd to actual stream URL
        if (stream.startsWith("ffmpeg") || (stream.startsWith("/") && !stream.startsWith("//"))) {
            val stalker = viewModel.iptvRepository.cachedStalkerApi
            if (stalker != null) {
                val resolved = stalker.resolveStreamUrl(stream)
                if (resolved != null) stream = resolved else return@LaunchedEffect
            }
        }
        if (stream == lastPreparedStreamUrl) return@LaunchedEffect
        lastPreparedStreamUrl = stream
        prepareStream(stream)
    }

    // Ensure smooth player surface handoff between mini and fullscreen views
    LaunchedEffect(isFullScreen) {
        if (isPlayerReleased) return@LaunchedEffect
        // Small delay to ensure target view is fully created before attaching player
        kotlinx.coroutines.delay(50L)
        
        if (isFullScreen) {
            // Detach from mini first
            miniPlayerView?.player = null
            // Wait for fullscreen view to be ready, retry up to 200ms
            var attempts = 0
            while (fullPlayerView == null && attempts < 4) {
                kotlinx.coroutines.delay(50L)
                attempts++
            }
            fullPlayerView?.let { view ->
                view.post {
                    if (!isPlayerReleased) {
                        view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        view.player = exoPlayer
                        view.requestLayout()
                        view.invalidate()
                    }
                }
            }
        } else {
            // Detach from fullscreen first
            fullPlayerView?.player = null
            // Wait for mini view to be ready
            var attempts = 0
            while (miniPlayerView == null && attempts < 4) {
                kotlinx.coroutines.delay(50L)
                attempts++
            }
            miniPlayerView?.let { view ->
                view.post {
                    if (!isPlayerReleased) {
                        view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        view.player = exoPlayer
                        view.requestLayout()
                        view.invalidate()
                    }
                }
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (isPlayerReleased) return
                // Use the always-current stream URL (not a stale captured value)
                val stream = currentStreamUrl ?: return
                exoPlayer.clearMediaItems()
                val mediaItem = MediaItem.Builder()
                    .setUri(stream)
                    .setLiveConfiguration(
                        MediaItem.LiveConfiguration.Builder()
                            .setMinPlaybackSpeed(1.0f)
                            .setMaxPlaybackSpeed(1.0f)
                            .setTargetOffsetMs(4_000)
                            .build()
                    )
                    .build()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (isPlayerReleased) return
                // Force PlayerView to re-apply resize mode once real video
                // dimensions are known, preventing the initial stretched frame.
                miniPlayerView?.let { pv ->
                    pv.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                fullPlayerView?.let { pv ->
                    pv.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .focusable()
            .onPreviewKeyEvent { event ->
                // When context menu is open, let it handle all key events
                if (showGroupContextMenu) {
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) {
                        showGroupContextMenu = false
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }
                if (isFullScreen) {
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.Back, Key.Escape -> {
                                // Always return to EPG guide first, regardless of launch source
                                isFullScreen = false
                                showFullscreenOverlay = false
                                return@onPreviewKeyEvent true
                            }
                            Key.Enter, Key.DirectionCenter -> {
                                // If focused channel != playing channel, play it
                                // Otherwise, toggle EPG overlay
                                if (selectedChannelId != null && selectedChannelId != playingChannelId) {
                                    playingChannelId = selectedChannelId
                                    showFullscreenOverlay = true
                                    fullscreenOverlayTrigger = System.currentTimeMillis()
                                } else {
                                    // Toggle EPG info overlay
                                    showFullscreenOverlay = !showFullscreenOverlay
                                    if (showFullscreenOverlay) {
                                        fullscreenOverlayTrigger = System.currentTimeMillis()
                                    }
                                }
                                return@onPreviewKeyEvent true
                            }
                            Key.DirectionUp -> {
                                // Switch to next channel (up = next in list)
                                if (channels.isNotEmpty()) {
                                    val currentIdx = channels.indexOfFirst { it.id == playingChannelId }
                                    val nextIdx = if (currentIdx < 0) 0 else (currentIdx + 1) % channels.size
                                    val nextChannel = channels[nextIdx]
                                    channelIndex = nextIdx
                                    selectedChannelId = nextChannel.id
                                    // Don't auto-play - let user explicitly select with Enter
                                    // playingChannelId = nextChannel.id
                                    // Show overlay briefly on channel focus
                                    showFullscreenOverlay = true
                                    fullscreenOverlayTrigger = System.currentTimeMillis()
                                }
                                return@onPreviewKeyEvent true
                            }
                            Key.DirectionDown -> {
                                // Switch to previous channel (down = previous in list)
                                if (channels.isNotEmpty()) {
                                    val currentIdx = channels.indexOfFirst { it.id == playingChannelId }
                                    val prevIdx = if (currentIdx <= 0) channels.lastIndex else currentIdx - 1
                                    val prevChannel = channels[prevIdx]
                                    channelIndex = prevIdx
                                    selectedChannelId = prevChannel.id
                                    // Don't auto-play - let user explicitly select with Enter
                                    // playingChannelId = prevChannel.id
                                    // Show overlay briefly on channel focus
                                    showFullscreenOverlay = true
                                    fullscreenOverlayTrigger = System.currentTimeMillis()
                                }
                                return@onPreviewKeyEvent true
                            }
                            else -> return@onPreviewKeyEvent false
                        }
                    }
                    return@onPreviewKeyEvent false
                }

                val isSelect = event.key == Key.Enter || event.key == Key.DirectionCenter
                if (event.type == KeyEventType.KeyDown && isSelect) {
                    if (centerDownAtMs == null) centerDownAtMs = SystemClock.elapsedRealtime()
                    return@onPreviewKeyEvent true
                }
                if (event.type == KeyEventType.KeyUp && isSelect) {
                    val pressMs = centerDownAtMs?.let { SystemClock.elapsedRealtime() - it } ?: 0L
                    centerDownAtMs = null
                    if (pressMs >= 550L) {
                        when (focusZone) {
                            TvFocusZone.GROUPS -> groups.getOrNull(safeGroupIndex)?.let {
                                showGroupContextMenu = true
                                return@onPreviewKeyEvent true
                            }

                            TvFocusZone.GUIDE -> filteredChannels.getOrNull(safeChannelIndex)?.let {
                                viewModel.toggleFavoriteChannel(it.id)
                                return@onPreviewKeyEvent true
                            }

                            TvFocusZone.SIDEBAR, TvFocusZone.SEARCH -> Unit
                        }
                    }

                    when (focusZone) {
                        TvFocusZone.SIDEBAR -> {
                            if (hasProfile && sidebarFocusIndex == 0) {
                                onSwitchProfile()
                            } else {
                                when (topBarFocusedItem(sidebarFocusIndex, hasProfile)) {
                                    SidebarItem.SEARCH -> onNavigateToSearch()
                                    SidebarItem.HOME -> onNavigateToHome()
                                    SidebarItem.WATCHLIST -> onNavigateToWatchlist()
                                    SidebarItem.TV -> Unit
                                    SidebarItem.SETTINGS -> onNavigateToSettings()
                                    else -> Unit
                                }
                            }
                            true
                        }

                        TvFocusZone.SEARCH -> {
                            searchActive = true
                            false
                        }
                        
                        TvFocusZone.GROUPS -> {
                            selectedGroupIndex = safeGroupIndex
                            channelIndex = 0
                            focusZone = TvFocusZone.GUIDE
                            true
                        }

                        TvFocusZone.GUIDE -> {
                            filteredChannels.getOrNull(safeChannelIndex)?.let { channel ->
                                selectedChannelId = channel.id
                                userHasClickedPlay = true
                                if (playingChannelId == channel.id) {
                                    isFullScreen = true
                                } else {
                                    playingChannelId = channel.id
                                }
                            }
                            true
                        }
                    }
                } else if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Back, Key.Escape -> {
                            when (focusZone) {
                                TvFocusZone.SIDEBAR -> onBack()
                                TvFocusZone.SEARCH -> {
                                    if (searchActive) {
                                        searchActive = false
                                    } else {
                                        onNavigateToContentMenu()
                                    }
                                }
                                TvFocusZone.GROUPS -> focusZone = TvFocusZone.SEARCH
                                TvFocusZone.GUIDE -> focusZone = TvFocusZone.GROUPS
                            }
                            true
                        }

                        Key.DirectionLeft -> {
                            when (focusZone) {
                                TvFocusZone.SIDEBAR -> if (sidebarFocusIndex > 0) {
                                    sidebarFocusIndex = (sidebarFocusIndex - 1).coerceIn(0, maxSidebarIndex)
                                }
                                TvFocusZone.SEARCH -> { searchActive = false; onNavigateToContentMenu() }
                                TvFocusZone.GROUPS -> onNavigateToContentMenu()
                                TvFocusZone.GUIDE -> {
                                    if (channelIndex % gridColumns > 0) channelIndex--
                                    else focusZone = TvFocusZone.GROUPS
                                }
                            }
                            true
                        }

                        Key.DirectionRight -> {
                            when (focusZone) {
                                TvFocusZone.SIDEBAR -> if (sidebarFocusIndex < maxSidebarIndex) {
                                    sidebarFocusIndex = (sidebarFocusIndex + 1).coerceIn(0, maxSidebarIndex)
                                }
                                TvFocusZone.SEARCH -> if (!searchActive && filteredChannels.isNotEmpty()) { channelIndex = 0; focusZone = TvFocusZone.GUIDE }
                                TvFocusZone.GROUPS -> if (filteredChannels.isNotEmpty()) { channelIndex = 0; focusZone = TvFocusZone.GUIDE }
                                TvFocusZone.GUIDE -> channelIndex = (channelIndex + 1).coerceAtMost((filteredChannels.size - 1).coerceAtLeast(0))
                            }
                            true
                        }

                        Key.DirectionUp -> {
                            when (focusZone) {
                                TvFocusZone.SIDEBAR -> Unit
                                TvFocusZone.SEARCH -> if (!searchActive) focusZone = TvFocusZone.SIDEBAR
                                TvFocusZone.GROUPS -> if (groupIndex > 0) groupIndex-- else focusZone = TvFocusZone.SEARCH
                                TvFocusZone.GUIDE -> channelIndex = (channelIndex - gridColumns).coerceAtLeast(0)
                            }
                            true
                        }

                        Key.DirectionDown -> {
                            when (focusZone) {
                                TvFocusZone.SIDEBAR -> focusZone = TvFocusZone.SEARCH
                                TvFocusZone.SEARCH -> if (filteredGroups.isNotEmpty()) {
                                    searchActive = false
                                    focusZone = TvFocusZone.GROUPS
                                    groupIndex = 0
                                }
                                TvFocusZone.GROUPS -> if (groupIndex < filteredGroups.size - 1) groupIndex++
                                TvFocusZone.GUIDE -> channelIndex = (channelIndex + gridColumns).coerceAtMost((filteredChannels.size - 1).coerceAtLeast(0))
                            }
                            true
                        }

                        Key.Menu, Key.Bookmark -> {
                            when (focusZone) {
                                TvFocusZone.GROUPS -> groups.getOrNull(safeGroupIndex)?.let {
                                    viewModel.toggleFavoriteGroup(it)
                                    true
                                } ?: false

                                TvFocusZone.GUIDE -> filteredChannels.getOrNull(safeChannelIndex)?.let {
                                    viewModel.toggleFavoriteChannel(it.id)
                                    true
                                } ?: false

                                TvFocusZone.SIDEBAR, TvFocusZone.SEARCH -> false
                            }
                        }

                        Key.Enter, Key.DirectionCenter -> true
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        if (!LocalDeviceType.current.isTouchDevice()) {
            AppTopBar(
                selectedItem = SidebarItem.TV,
                isFocused = focusZone == TvFocusZone.SIDEBAR,
                focusedIndex = sidebarFocusIndex,
                profile = currentProfile
            )
        }

        if (!uiState.isConfigured) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = AppTopBarContentTopInset)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                NotConfiguredPanel()
            }
        } else {
            // Immersive layout: seamless dark surface, no compartment borders
            Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = if (isMobile) 0.dp else contentTopPadding, start = if (isMobile) 0.dp else contentStartPadding)
            ) {
                // Mobile: full-screen groups list or channel view with swipe
                // TV: animated CategoryRail
                if (isMobile && showGroupsMobile) {
                    // Full-screen tappable groups list with search + favorites
                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = categoryQuery,
                            onValueChange = { categoryQuery = it },
                            placeholder = { androidx.compose.material3.Text("Search categories", color = TextSecondary.copy(alpha = 0.5f)) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary) },
                            trailingIcon = {
                                if (categoryQuery.isNotEmpty()) {
                                    IconButton(onClick = { categoryQuery = "" }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = TextSecondary)
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White.copy(alpha = 0.7f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                cursorColor = Color.White,
                                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                                focusedLeadingIconColor = TextSecondary,
                                unfocusedLeadingIconColor = TextSecondary,
                                focusedTrailingIconColor = TextSecondary,
                                unfocusedTrailingIconColor = TextSecondary,
                                focusedPlaceholderColor = TextSecondary.copy(alpha = 0.5f),
                                unfocusedPlaceholderColor = TextSecondary.copy(alpha = 0.5f),
                            )
                        )
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            itemsIndexed(filteredGroups) { index, group ->
                                val isFavorite = uiState.snapshot.favoriteGroups.contains(group)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                groupIndex = index
                                                selectedGroupIndex = index
                                                showGroupsMobile = false
                                                channelGridQuery = ""
                                            },
                                            onLongClick = { viewModel.toggleFavoriteGroup(group) }
                                        )
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = group,
                                        style = ArflixTypography.body,
                                        color = if (isFavorite) Pink else TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.toggleFavoriteGroup(group) },
                                        modifier = Modifier.width(36.dp).height(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                            contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                                            tint = if (isFavorite) Pink else TextSecondary.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                                androidx.compose.material3.Divider(color = Color.White.copy(alpha = 0.06f))
                            }
                        }
                    }
                } else {
                    // TV: AnimatedVisibility CategoryRail
                    if (!isMobile) {
                        val showCategoryRail = !isFullScreen
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showCategoryRail,
                            enter = androidx.compose.animation.expandHorizontally(
                                expandFrom = Alignment.Start,
                                animationSpec = androidx.compose.animation.core.tween(200)
                            ),
                            exit = androidx.compose.animation.shrinkHorizontally(
                                shrinkTowards = Alignment.Start,
                                animationSpec = androidx.compose.animation.core.tween(200)
                            )
                        ) {
                            Row {
                            CategoryRail(
                                groups = filteredGroups,
                                favoriteGroups = uiState.snapshot.favoriteGroups.toSet(),
                                focusedGroupIndex = safeGroupIndex,
                                selectedGroupIndex = safeSelectedGroupIndex,
                                isGroupsFocused = focusZone == TvFocusZone.GROUPS,
                                isSearchFocused = focusZone == TvFocusZone.SEARCH,
                                isSearchActive = searchActive,
                                listState = groupsListState,
                                searchQuery = categoryQuery,
                                onSearchQueryChange = { categoryQuery = it },
                                onGroupClick = { index ->
                                    selectedGroupIndex = index
                                    groupIndex = index
                                    channelIndex = 0
                                    focusZone = TvFocusZone.GUIDE
                                },
                                onGroupLongPress = { index ->
                                    groupIndex = index
                                    showGroupContextMenu = true
                                },
                                showMenuForIndex = if (showGroupContextMenu) safeGroupIndex else -1,
                                onDismissMenu = { showGroupContextMenu = false },
                                onToggleFavorite = { viewModel.toggleFavoriteGroup(it) },
                                onToggleHidden = { viewModel.toggleHiddenGroup(it) },
                                onMoveUp = { viewModel.moveGroupUp(it) },
                                onMoveDown = { viewModel.moveGroupDown(it) },
                                modifier = Modifier
                                    .width(214.dp)
                                    .fillMaxHeight()
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            }
                        }
                    }

                // Main content: EPG info + player top, guide bottom
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(
                            if (isMobile) Modifier.pointerInput(Unit) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    if (dragAmount > 40f) showGroupsMobile = true
                                }
                            } else Modifier
                        )
                ) {
                    // Top section: EPG info/header + mini player (TV only)
                    val epgSlice = playingChannel?.id?.let { uiState.snapshot.nowNext[it] }
                    if (!isMobile) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.70f)
                                .background(BackgroundDark),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: channel info
                            Column(
                                modifier = Modifier
                                    .weight(0.38f)
                                    .fillMaxHeight()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (playingChannel != null && !playingChannel.logo.isNullOrBlank()) {
                                    AsyncImage(
                                        model = playingChannel.logo,
                                        contentDescription = playingChannel.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.White.copy(alpha = 0.04f))
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                if (playingChannel != null) {
                                    Text(
                                        text = playingChannel.name,
                                        style = ArflixTypography.cardTitle.copy(fontSize = 16.sp),
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = playingChannel.group,
                                        style = ArflixTypography.caption.copy(fontSize = 10.sp),
                                        color = Color.White.copy(alpha = 0.35f),
                                        maxLines = 1
                                    )
                                } else {
                                    Text(
                                        "Select a channel",
                                        style = ArflixTypography.body.copy(fontSize = 14.sp),
                                        color = Color.White.copy(alpha = 0.2f)
                                    )
                                }
                            }

                            // Right: mini player
                            Box(
                                modifier = Modifier
                                    .weight(0.62f)
                                    .fillMaxHeight()
                                    .padding(top = 6.dp, bottom = 6.dp, end = 8.dp, start = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0A0A0A))
                                    .clickable {
                                        if (playingChannel != null) {
                                            selectedChannelId = playingChannel.id
                                            isFullScreen = true
                                        }
                                    }
                            ) {
                                if (playingChannel != null && !isFullScreen) {
                                    AndroidView(
                                        factory = { ctx ->
                                            PlayerView(ctx).apply {
                                                miniPlayerView = this
                                                player = null
                                                useController = false
                                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                setKeepContentOnPlayerReset(true)
                                                setShutterBackgroundColor(0xFF0A0A0A.toInt())
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                        update = { playerView ->
                                            miniPlayerView = playerView
                                            if (!isFullScreen) {
                                                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                if (playerView.player !== exoPlayer) playerView.player = exoPlayer
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.72f)
                                .background(BackgroundDark)
                        ) {
                            // Full-width row: hamburger + search
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { showGroupsMobile = true },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Categories",
                                        tint = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                                OutlinedTextField(
                                    value = channelGridQuery,
                                    onValueChange = { channelGridQuery = it },
                                    placeholder = { androidx.compose.material3.Text("Search channels in $selectedGroup", color = TextSecondary.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    trailingIcon = {
                                        if (channelGridQuery.isNotEmpty()) {
                                            IconButton(onClick = { channelGridQuery = "" }) {
                                                Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = TextSecondary)
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color.White.copy(alpha = 0.7f),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        cursorColor = Color.White,
                                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                                        unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                                        focusedTrailingIconColor = TextSecondary,
                                        unfocusedTrailingIconColor = TextSecondary,
                                        focusedPlaceholderColor = TextSecondary.copy(alpha = 0.5f),
                                        unfocusedPlaceholderColor = TextSecondary.copy(alpha = 0.5f),
                                    )
                                )
                            }
                            if (!isMobile) {
                                    // If a channel is playing, show NOW/NEXT/LATER info
                                    if (playingChannel != null) {
                                        Spacer(modifier = Modifier.height(10.dp))

                                        // NOW
                                        val nowProg = epgSlice?.now
                                        if (nowProg != null) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "NOW",
                                                    style = ArflixTypography.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                    color = Color.Black,
                                                    modifier = Modifier
                                                        .background(AccentGreen, RoundedCornerShape(3.dp))
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "${formatProgramTime(nowProg.startUtcMillis)} - ${formatProgramTime(nowProg.endUtcMillis)}",
                                                    style = ArflixTypography.caption.copy(fontSize = 10.sp),
                                                    color = Color.White.copy(alpha = 0.5f)
                                                )
                                            }
                                            Text(
                                                text = nowProg.title,
                                                style = ArflixTypography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                                                color = Color.White.copy(alpha = 0.9f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        // NEXT
                                        val nextProg = epgSlice?.next
                                        if (nextProg != null) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "NEXT",
                                                    style = ArflixTypography.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                    color = Color.White.copy(alpha = 0.7f),
                                                    modifier = Modifier
                                                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(3.dp))
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = formatProgramTime(nextProg.startUtcMillis),
                                                    style = ArflixTypography.caption.copy(fontSize = 10.sp),
                                                    color = Color.White.copy(alpha = 0.4f)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = nextProg.title,
                                                    style = ArflixTypography.caption.copy(fontSize = 11.sp),
                                                    color = Color.White.copy(alpha = 0.55f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        // LATER
                                        val laterProg = epgSlice?.later
                                        if (laterProg != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "LATER",
                                                    style = ArflixTypography.caption.copy(fontSize = 9.sp),
                                                    color = Color.White.copy(alpha = 0.4f),
                                                    modifier = Modifier
                                                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(3.dp))
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = formatProgramTime(laterProg.startUtcMillis),
                                                    style = ArflixTypography.caption.copy(fontSize = 10.sp),
                                                    color = Color.White.copy(alpha = 0.3f)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = laterProg.title,
                                                    style = ArflixTypography.caption.copy(fontSize = 11.sp),
                                                    color = Color.White.copy(alpha = 0.35f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }

                            // Full-width mini player
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0A0A0A))
                                    .clickable {
                                        if (playingChannel != null) {
                                            selectedChannelId = playingChannel.id
                                            isFullScreen = true
                                        }
                                    }
                            ) {
                                if (playingChannel != null && !isFullScreen) {
                                    AndroidView(
                                        factory = { ctx ->
                                            PlayerView(ctx).apply {
                                                miniPlayerView = this
                                                player = null
                                                useController = false
                                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                setKeepContentOnPlayerReset(true)
                                                setShutterBackgroundColor(0xFF0A0A0A.toInt())
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                        update = { playerView ->
                                            miniPlayerView = playerView
                                            if (!isFullScreen) {
                                                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                if (playerView.player !== exoPlayer) playerView.player = exoPlayer
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Channel list: grid for all devices
                    ChannelGrid(
                        channels = filteredChannels,
                        isLoading = uiState.isLoading,
                        playingChannelId = playingChannelId,
                        favoriteChannels = uiState.snapshot.favoriteChannels.toSet(),
                        isMobile = isMobile,
                        focusedIndex = safeChannelIndex,
                        isFocusZoneActive = focusZone == TvFocusZone.GUIDE && !isMobile,
                        gridState = gridState,
                        onColumnsChanged = { gridColumns = it },
                        onChannelLongClick = { channel -> viewModel.toggleFavoriteChannel(channel.id) },
                        onChannelClick = { index ->
                            val channel = filteredChannels.getOrNull(index) ?: return@ChannelGrid
                            channelIndex = index
                            if (playingChannelId == channel.id) {
                                selectedChannelId = channel.id
                                isFullScreen = true
                            } else {
                                selectedChannelId = channel.id
                                playingChannelId = channel.id
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                } // end else (not showGroupsMobile on mobile / CategoryRail+content on TV)
            } // end Row
            } // end Column (mobile wrapper)
        }

        if (isFullScreen) {
            // Show black screen immediately when fullscreen is active.
            // Player and EPG overlay only render once playingChannel resolves.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable {
                        // Tap toggles EPG info overlay (mirrors D-pad Enter behavior)
                        showFullscreenOverlay = !showFullscreenOverlay
                        if (showFullscreenOverlay) {
                            fullscreenOverlayTrigger = System.currentTimeMillis()
                        }
                    }
            ) {
                if (playingChannel != null) {
                    val fsNowNext = uiState.snapshot.nowNext[playingChannel.id]
                    val fsNow = fsNowNext?.now
                    val fsNext = fsNowNext?.next

                    // Auto-hide overlay after 5 seconds
                    LaunchedEffect(fullscreenOverlayTrigger, showFullscreenOverlay) {
                        if (showFullscreenOverlay && fullscreenOverlayTrigger > 0L) {
                            kotlinx.coroutines.delay(5000L)
                            showFullscreenOverlay = false
                        }
                    }

                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                fullPlayerView = this
                                player = null
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setKeepContentOnPlayerReset(true)
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { playerView ->
                            fullPlayerView = playerView
                            // Always ensure player is attached when this view is active
                            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            if (playerView.player !== exoPlayer) {
                                // Attach player directly in update to ensure immediate rendering
                                playerView.player = exoPlayer
                            }
                        }
                    )

                    // Premium EPG overlay (toggle with OK, auto-hides after 5s)
                    AnimatedVisibility(
                        visible = showFullscreenOverlay,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        FullscreenEpgOverlay(
                            channel = playingChannel,
                            nowProgram = fsNow,
                            nextProgram = fsNext,
                            isMobile = isMobile,
                            isPiPMode = isPiPMode,
                            onEnterPiP = enterPiP
                        )
                    }
                }
            }
        }

        uiState.error?.let { err ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .background(Color(0xFF4A1D1D), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFB91C1C), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(text = err, style = ArflixTypography.caption, color = Color(0xFFFECACA))
            }
        }
    }
}

private suspend fun smoothScrollTo(state: LazyListState, targetIndex: Int) {
    val safe = targetIndex.coerceAtLeast(0)
    val firstVisible = state.firstVisibleItemIndex
    val lastVisible = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: firstVisible
    val outsideViewport = safe < firstVisible || safe > lastVisible
    val distance = abs(firstVisible - safe)
    if (safe == 0 || outsideViewport || distance > 12) {
        state.scrollToItem(safe)
    } else {
        state.animateScrollToItem(safe)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryRail(
    groups: List<String>,
    favoriteGroups: Set<String>,
    focusedGroupIndex: Int,
    selectedGroupIndex: Int = -1,
    isGroupsFocused: Boolean,
    isSearchFocused: Boolean,
    isSearchActive: Boolean = false,
    listState: LazyListState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onGroupClick: (Int) -> Unit = {},
    onGroupLongPress: (Int) -> Unit = {},
    showMenuForIndex: Int = -1,
    onDismissMenu: () -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
    onToggleHidden: (String) -> Unit = {},
    onMoveUp: (String) -> Unit = {},
    onMoveDown: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.045f), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        val searchFocusRequester = remember { FocusRequester() }

        LaunchedEffect(isSearchFocused) {
            if (isSearchFocused) {
                searchFocusRequester.requestFocus()
            }
        }

        Text(
            text = "Categories",
            style = ArflixTypography.caption.copy(fontSize = 11.sp, letterSpacing = 0.7.sp),
            color = TextSecondary.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 6.dp, bottom = 4.dp, top = 2.dp)
        )

        // Search bar — same styling as MediaCategoryRail in Movies/Series
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(bottom = 3.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (isSearchFocused) Color.White.copy(alpha = 0.12f)
                    else Color.White.copy(alpha = 0.06f)
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
                .focusRequester(searchFocusRequester)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                textStyle = ArflixTypography.body.copy(color = Color.White, fontSize = 12.sp),
                cursorBrush = SolidColor(Color.White),
                singleLine = true,
                readOnly = !isSearchActive,
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            "Search",
                            color = TextSecondary.copy(alpha = 0.5f),
                            style = ArflixTypography.body.copy(fontSize = 12.sp)
                        )
                    }
                    innerTextField()
                }
            )
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { onSearchQueryChange("") },
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
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(groups, key = { _, group -> group }, contentType = { _, _ -> "category_group" }) { index, group ->
                GroupRailItem(
                    name = group,
                    isFocused = isGroupsFocused && index == focusedGroupIndex,
                    isSelected = index == selectedGroupIndex,
                    isFavorite = favoriteGroups.contains(group),
                    showMenu = showMenuForIndex == index,
                    onClick = { onGroupClick(index) },
                    onLongPress = { onGroupLongPress(index) },
                    onDismissMenu = onDismissMenu,
                    onToggleFavorite = { onToggleFavorite(group) },
                    onToggleHidden = { onToggleHidden(group) },
                    onMoveUp = { onMoveUp(group) },
                    onMoveDown = { onMoveDown(group) }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun GroupRailItem(
    name: String, isFocused: Boolean, isSelected: Boolean = false, isFavorite: Boolean,
    showMenu: Boolean = false,
    onClick: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onDismissMenu: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onToggleHidden: () -> Unit = {},
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {}
) {
    Box {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isFocused -> Color.White.copy(alpha = 0.07f)
                    isSelected -> Color.White.copy(alpha = 0.05f)
                    else -> Color.Transparent
                }
            )
            .then(
                when {
                    isFocused -> Modifier.border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    isSelected -> Modifier.border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                    else -> Modifier
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFavorite) {
            Icon(Icons.Default.Star, null, tint = Color(0xFFF5C518).copy(alpha = 0.8f), modifier = Modifier.size(10.dp))
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(name, style = ArflixTypography.caption.copy(fontSize = 11.sp, fontWeight = if (isFocused || isSelected) FontWeight.Medium else FontWeight.Normal, lineHeight = 14.sp),
            color = if (isFocused) Color.White else if (isSelected) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.4f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (showMenu) {
        DropdownMenu(
            expanded = true,
            onDismissRequest = onDismissMenu,
            modifier = Modifier.background(Color.Black.copy(alpha = 0.95f))
        ) {
            FocusableMenuItem(if (isFavorite) "Unfavorite" else "Favorite", if (isFavorite) Icons.Default.StarOutline else Icons.Default.Star, Color(0xFFF5C518)) { onDismissMenu(); onToggleFavorite() }
            FocusableMenuItem("Hide", Icons.Default.VisibilityOff) { onDismissMenu(); onToggleHidden() }
            FocusableMenuItem("Move Up", Icons.Default.KeyboardArrowUp) { onDismissMenu(); onMoveUp() }
            FocusableMenuItem("Move Down", Icons.Default.KeyboardArrowDown) { onDismissMenu(); onMoveDown() }
        }
    }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FullscreenEpgOverlay(
    channel: IptvChannel,
    nowProgram: IptvProgram?,
    nextProgram: IptvProgram?,
    isMobile: Boolean = false,
    isPiPMode: Boolean = false,
    onEnterPiP: () -> Unit = {}
) {
    val topScrimBrush = remember {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Black.copy(alpha = 0.8f),
                0.7f to Color.Black.copy(alpha = 0.4f),
                1.0f to Color.Transparent
            )
        )
    }
    val bottomScrimBrush = remember {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.3f to Color.Black.copy(alpha = 0.4f),
                1.0f to Color.Black.copy(alpha = 0.85f)
            )
        )
    }
    Box(modifier = Modifier.fillMaxSize()) {
        // Top scrim: channel info
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(topScrimBrush)
                .padding(
                    start = if (isMobile) 16.dp else 32.dp,
                    end = if (isMobile) 16.dp else 32.dp,
                    top = if (isMobile) 16.dp else 24.dp,
                    bottom = if (isMobile) 24.dp else 40.dp
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                if (!channel.logo.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(if (isMobile) 36.dp else 48.dp)
                            .clip(RoundedCornerShape(if (isMobile) 6.dp else 8.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                    )
                    Spacer(modifier = Modifier.width(if (isMobile) 10.dp else 14.dp))
                }
                Column {
                    Text(
                        text = channel.name,
                        style = ArflixTypography.sectionTitle.copy(fontSize = if (isMobile) 14.sp else 22.sp),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = channel.group,
                        style = ArflixTypography.caption.copy(fontSize = if (isMobile) 11.sp else 12.sp),
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1
                    )
                }
            }
            // Top right: Clock
            Text(
                text = programTimeFormatter.format(java.time.LocalTime.now()),
                style = ArflixTypography.sectionTitle.copy(fontSize = if (isMobile) 13.sp else 18.sp),
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }

        // Bottom scrim: NOW / NEXT program info
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(bottomScrimBrush)
                .padding(
                    start = if (isMobile) 16.dp else 32.dp,
                    end = if (isMobile) 16.dp else 32.dp,
                    top = if (isMobile) 24.dp else 40.dp,
                    bottom = if (isMobile) 18.dp else 28.dp
                )
        ) {
            // Bottom right: PiP button (mobile only)
            if (isMobile && !isPiPMode) {
                IconButton(
                    onClick = onEnterPiP,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPicture,
                        contentDescription = "Picture in Picture",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                // NOW program
                if (nowProgram != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "NOW",
                            style = ArflixTypography.caption.copy(fontWeight = FontWeight.Bold, fontSize = if (isMobile) 10.sp else 12.sp),
                            color = Color.Black,
                            modifier = Modifier
                                .background(AccentGreen, RoundedCornerShape(4.dp))
                                .padding(horizontal = if (isMobile) 6.dp else 8.dp, vertical = if (isMobile) 2.dp else 3.dp)
                        )
                        Spacer(modifier = Modifier.width(if (isMobile) 8.dp else 10.dp))
                        Text(
                            text = "${formatProgramTime(nowProgram.startUtcMillis)} - ${formatProgramTime(nowProgram.endUtcMillis)}",
                            style = ArflixTypography.caption.copy(fontSize = if (isMobile) 11.sp else 14.sp),
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.height(if (isMobile) 3.dp else 4.dp))
                    Text(
                        text = nowProgram.title,
                        style = ArflixTypography.sectionTitle.copy(fontSize = if (isMobile) 14.sp else 20.sp),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Progress bar
                    val progDuration = (nowProgram.endUtcMillis - nowProgram.startUtcMillis).coerceAtLeast(1L)
                    val progElapsed = (System.currentTimeMillis() - nowProgram.startUtcMillis).coerceIn(0, progDuration)
                    val progFraction = (progElapsed.toFloat() / progDuration.toFloat()).coerceIn(0f, 1f)
                    Spacer(modifier = Modifier.height(if (isMobile) 6.dp else 8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (isMobile) 0.6f else 0.4f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progFraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(AccentGreen)
                        )
                    }
                    nowProgram.description?.let { desc ->
                        if (desc.isNotBlank()) {
                            Spacer(modifier = Modifier.height(if (isMobile) 4.dp else 6.dp))
                            Text(
                                text = desc,
                                style = ArflixTypography.caption.copy(fontSize = if (isMobile) 11.sp else 13.sp),
                                color = Color.White.copy(alpha = 0.55f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(if (isMobile) 0.85f else 0.6f)
                            )
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "LIVE",
                            style = ArflixTypography.caption.copy(fontWeight = FontWeight.Bold, fontSize = if (isMobile) 10.sp else 12.sp),
                            color = Color.Black,
                            modifier = Modifier
                                .background(AccentGreen, RoundedCornerShape(4.dp))
                                .padding(horizontal = if (isMobile) 6.dp else 8.dp, vertical = if (isMobile) 2.dp else 3.dp)
                        )
                        Spacer(modifier = Modifier.width(if (isMobile) 8.dp else 10.dp))
                        Text(
                            text = channel.name,
                            style = ArflixTypography.sectionTitle.copy(fontSize = if (isMobile) 14.sp else 20.sp),
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }
                // NEXT program
                if (nextProgram != null) {
                    Spacer(modifier = Modifier.height(if (isMobile) 8.dp else 12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "NEXT",
                            style = ArflixTypography.caption.copy(fontWeight = FontWeight.Bold, fontSize = if (isMobile) 9.sp else 11.sp),
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = if (isMobile) 6.dp else 8.dp, vertical = if (isMobile) 2.dp else 3.dp)
                        )
                        Spacer(modifier = Modifier.width(if (isMobile) 6.dp else 10.dp))
                        Text(
                            text = formatProgramTime(nextProgram.startUtcMillis),
                            style = ArflixTypography.caption.copy(fontSize = if (isMobile) 11.sp else 14.sp),
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(if (isMobile) 6.dp else 8.dp))
                        Text(
                            text = nextProgram.title,
                            style = ArflixTypography.body.copy(fontSize = if (isMobile) 12.sp else 16.sp),
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelGrid(
    channels: List<IptvChannel>,
    isLoading: Boolean,
    playingChannelId: String?,
    favoriteChannels: Set<String>,
    isMobile: Boolean = false,
    focusedIndex: Int = -1,
    isFocusZoneActive: Boolean = false,
    gridState: LazyGridState = rememberLazyGridState(),
    onColumnsChanged: (Int) -> Unit = {},
    onChannelClick: (Int) -> Unit = {},
    onChannelLongClick: (IptvChannel) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (channels.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (isLoading) "Loading channels..." else "No channels in this group",
                style = ArflixTypography.body,
                color = Color.White.copy(alpha = 0.3f)
            )
        }
        return
    }
    val cardMinSize = if (isMobile) 100.dp else 120.dp
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val cols = remember(maxWidth, cardMinSize) {
            (maxWidth / cardMinSize).toInt().coerceAtLeast(1)
        }
        LaunchedEffect(cols) { onColumnsChanged(cols) }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = cardMinSize),
            state = gridState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                start = 10.dp,
                end = 10.dp,
                top = if (isMobile) 8.dp else 20.dp,
                bottom = 8.dp
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            gridItemsIndexed(
                channels,
                key = { _, ch -> ch.id },
                contentType = { _, _ -> "channel_grid_card" }
            ) { index, channel ->
                val isPlaying = channel.id == playingChannelId
                val isFavorite = favoriteChannels.contains(channel.id)
                val cardShape = RoundedCornerShape(8.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .arvioFocusable(
                            shape = cardShape,
                            focusedScale = ArvioSkin.focus.scaleFocused,
                            pressedScale = ArvioSkin.focus.scalePressed,
                            outlineWidth = ArvioSkin.focus.outlineWidth,
                            glowWidth = ArvioSkin.focus.glowWidth,
                            glowAlpha = ArvioSkin.focus.glowAlpha,
                            outlineColor = ArvioSkin.colors.focusOutline,
                            isFocusedOverride = isFocusZoneActive && index == focusedIndex,
                            enableSystemFocus = true,
                            onClick = { onChannelClick(index) },
                            onLongClick = { onChannelLongClick(channel) },
                        )
                        .clip(cardShape)
                        .background(
                            if (isPlaying) AccentGreen.copy(alpha = 0.15f)
                            else ArvioSkin.colors.surface
                        )
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isFavorite) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = Pink.copy(alpha = 0.8f),
                            modifier = Modifier
                                .size(12.dp)
                                .align(Alignment.TopEnd)
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!channel.logo.isNullOrBlank()) {
                            AsyncImage(
                                model = channel.logo,
                                contentDescription = channel.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LiveTv,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Text(
                            text = channel.name,
                            style = ArflixTypography.caption.copy(fontSize = 11.sp),
                            color = if (isPlaying) AccentGreen else Color.White.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusableMenuItem(label: String, icon: ImageVector, iconTint: Color = Color.White.copy(alpha = 0.6f), onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    DropdownMenuItem(
        text = { Text(label, style = ArflixTypography.caption.copy(fontSize = 12.sp, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal), color = if (focused) Color.White else Color.White.copy(alpha = 0.8f)) },
        leadingIcon = { Icon(icon, null, tint = if (focused) iconTint else iconTint.copy(alpha = 0.5f), modifier = Modifier.size(16.dp)) },
        onClick = onClick,
        modifier = Modifier
            .height(40.dp)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)) else Modifier)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NotConfiguredPanel() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundCard, RoundedCornerShape(14.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.LiveTv,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("IPTV is not configured", style = ArflixTypography.sectionTitle, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Open Settings and add your M3U URL.",
                style = ArflixTypography.body,
                color = TextSecondary
            )
        }
    }
}

private tailrec fun android.content.Context.findTvActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findTvActivity()
    else -> null
}

private val programTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

private fun formatProgramTime(utcMillis: Long): String {
    return programTimeFormatter.format(Instant.ofEpochMilli(utcMillis).atZone(ZoneId.systemDefault()))
}