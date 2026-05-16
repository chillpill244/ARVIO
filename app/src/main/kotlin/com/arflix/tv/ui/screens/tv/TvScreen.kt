
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayCircle
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
import com.arflix.tv.network.OkHttpProvider
import com.arflix.tv.ui.components.AppTopBar
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
import com.arflix.tv.util.Constants
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private enum class TvFocusZone {
    SIDEBAR,
    CONTENT_MENU,
    SEARCH,
    GROUPS,
    GUIDE
}

private enum class TvContentTab { TV, MOVIES, SERIES }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvScreen(
    viewModel: TvViewModel = hiltViewModel(),
    currentProfile: com.arflix.tv.data.model.Profile? = null,
    initialChannelId: String? = null,
    initialStreamUrl: String? = null,
    onNavigateToHome: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToMovies: () -> Unit = {},
    onNavigateToSeries: () -> Unit = {},
    onNavigateToWatchlist: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onSwitchProfile: () -> Unit = {},
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

    var focusZone by rememberSaveable { mutableStateOf(if (uiState.isConfigured) TvFocusZone.CONTENT_MENU else TvFocusZone.SIDEBAR) }
    var searchActive by remember { mutableStateOf(false) }
    val hasProfile = currentProfile != null
    val maxSidebarIndex = topBarMaxIndex(hasProfile)
    var sidebarFocusIndex by rememberSaveable { mutableIntStateOf(if (hasProfile) 4 else 3) }
    var contentMenuIndex by rememberSaveable { mutableIntStateOf(1) } // 0=Movies, 1=TV, 2=Series
    var groupIndex by rememberSaveable { mutableIntStateOf(0) }
    var channelIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var playingChannelId by rememberSaveable { mutableStateOf<String?>(null) }
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
    var isPiPMode by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = isMobile && isFullScreen || isPiPMode) {
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
    val contentTopPadding = (AppTopBarContentTopInset() - 14.dp).coerceAtLeast(52.dp)

    val groups by remember(uiState.snapshot.grouped, uiState.snapshot.favoriteGroups, uiState.snapshot.favoriteChannels) {
        derivedStateOf { uiState.groups() }
    }
    val safeGroupIndex = groupIndex.coerceIn(0, (groups.size - 1).coerceAtLeast(0))
    val selectedGroup = groups.getOrNull(safeGroupIndex).orEmpty()
    val channels = uiState.filteredChannels(selectedGroup)
    val safeChannelIndex = channelIndex.coerceIn(0, (channels.size - 1).coerceAtLeast(0))
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

    LaunchedEffect(groups.size) {
        if (groupIndex >= groups.size) groupIndex = 0
    }
    LaunchedEffect(uiState.isConfigured) {
        if (uiState.isConfigured && focusZone == TvFocusZone.SIDEBAR) {
            focusZone = TvFocusZone.CONTENT_MENU
        }
    }
    LaunchedEffect(channels.size) {
        if (channelIndex >= channels.size) channelIndex = 0
        if (selectedChannelId != null && uiState.snapshot.channels.none { it.id == selectedChannelId }) {
            selectedChannelId = null
        }
    }
    LaunchedEffect(safeGroupIndex, focusZone, groups.size) {
        if (focusZone == TvFocusZone.GROUPS && groups.isNotEmpty()) {
            smoothScrollTo(groupsListState, safeGroupIndex)
        }
    }
    LaunchedEffect(safeChannelIndex, focusZone, channels.size) {
        if (focusZone == TvFocusZone.GUIDE && channels.isNotEmpty()) {
            smoothScrollTo(channelsListState, safeChannelIndex)
        }
    }
    // Click-to-play model: Only update selectedChannelId for visual feedback, 
    // but don't auto-play. User must press Enter/OK to play.
    LaunchedEffect(safeChannelIndex, focusZone) {
        if (focusZone == TvFocusZone.GUIDE && channels.isNotEmpty()) {
            kotlinx.coroutines.delay(150L) // small debounce
            val focusedChannel = channels.getOrNull(safeChannelIndex)
            if (focusedChannel != null) {
                // Only update selectedChannelId for visual feedback, NEVER playingChannelId
                selectedChannelId = focusedChannel.id
                // DO NOT set playingChannelId here - only on explicit Enter key press
            }
        }
    }
    LaunchedEffect(uiState.isConfigured, uiState.isLoading, uiState.snapshot.channels.size, groups.size) {
        if (uiState.isConfigured && !uiState.isLoading && uiState.snapshot.channels.isEmpty()) {
            viewModel.refresh(force = true, showLoading = true)
        }
    }
    LaunchedEffect(groups, selectedGroup, channels.size) {
        if (selectedGroup == "My Favorites" && channels.isEmpty() && groups.size > 1 && groupIndex == 0) {
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
            .setUserAgent(Constants.CUSTOM_AGENT)
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

    // Handle PiP mode entry
    LaunchedEffect(isPiPMode) {
        if (isPiPMode && isMobile) {
            val activity = context as? android.app.Activity
            if (activity != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    val ratio = android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(android.util.Rational(16, 9))
                        .build()
                    activity.enterPictureInPictureMode(ratio)
                } catch (e: Exception) {
                    // Fallback for older devices or if PiP is not supported
                    isPiPMode = false
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

                            TvFocusZone.GUIDE -> channels.getOrNull(safeChannelIndex)?.let {
                                viewModel.toggleFavoriteChannel(it.id)
                                return@onPreviewKeyEvent true
                            }

                            TvFocusZone.SIDEBAR, TvFocusZone.CONTENT_MENU, TvFocusZone.SEARCH -> Unit
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

                        TvFocusZone.CONTENT_MENU -> {
                            when (contentMenuIndex) {
                                0 -> onNavigateToMovies()
                                1 -> focusZone = TvFocusZone.SEARCH // TV tab — enter EPG
                                2 -> onNavigateToSeries()
                            }
                            true
                        }

                        TvFocusZone.SEARCH -> {
                            searchActive = true
                            false
                        }
                        
                        TvFocusZone.GROUPS -> {
                            channelIndex = 0
                            focusZone = TvFocusZone.GUIDE
                            true
                        }

                        TvFocusZone.GUIDE -> {
                            channels.getOrNull(safeChannelIndex)?.let { channel ->
                                selectedChannelId = channel.id
                                userHasClickedPlay = true // User explicitly clicked
                                if (playingChannelId == channel.id) {
                                    // Already playing this channel - go fullscreen
                                    isFullScreen = true
                                } else {
                                    // First click - start playing in mini player
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
                                TvFocusZone.CONTENT_MENU -> onBack()
                                TvFocusZone.SEARCH -> {
                                    if (searchActive) {
                                        searchActive = false
                                    } else {
                                        focusZone = TvFocusZone.CONTENT_MENU
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
                                TvFocusZone.CONTENT_MENU -> Unit // nothing to the left
                                TvFocusZone.SEARCH -> { searchActive = false; focusZone = TvFocusZone.CONTENT_MENU }
                                TvFocusZone.GROUPS -> focusZone = TvFocusZone.SEARCH
                                TvFocusZone.GUIDE -> focusZone = TvFocusZone.GROUPS
                            }
                            true
                        }

                        Key.DirectionRight -> {
                            when (focusZone) {
                                TvFocusZone.SIDEBAR -> if (sidebarFocusIndex < maxSidebarIndex) {
                                    sidebarFocusIndex = (sidebarFocusIndex + 1).coerceIn(0, maxSidebarIndex)
                                }
                                TvFocusZone.CONTENT_MENU -> focusZone = TvFocusZone.SEARCH
                                TvFocusZone.SEARCH -> if (!searchActive && groups.isNotEmpty()) focusZone = TvFocusZone.GROUPS
                                TvFocusZone.GROUPS -> if (channels.isNotEmpty()) focusZone = TvFocusZone.GUIDE
                                TvFocusZone.GUIDE -> Unit
                            }
                            true
                        }

                        Key.DirectionUp -> {
                            when (focusZone) {
                                TvFocusZone.SIDEBAR -> Unit
                                TvFocusZone.CONTENT_MENU -> {
                                    if (contentMenuIndex > 0) contentMenuIndex--
                                    else focusZone = TvFocusZone.SIDEBAR
                                }
                                TvFocusZone.SEARCH -> if (!searchActive) focusZone = TvFocusZone.SIDEBAR
                                TvFocusZone.GROUPS -> if (groupIndex > 0) groupIndex-- else focusZone = TvFocusZone.SEARCH
                                TvFocusZone.GUIDE -> if (channelIndex > 0) channelIndex--
                            }
                            true
                        }

                        Key.DirectionDown -> {
                            when (focusZone) {
                                TvFocusZone.SIDEBAR -> focusZone = TvFocusZone.CONTENT_MENU
                                TvFocusZone.CONTENT_MENU -> {
                                    if (contentMenuIndex < 2) contentMenuIndex++
                                }
                                TvFocusZone.SEARCH -> if (groups.isNotEmpty()) {
                                    searchActive = false
                                    focusZone = TvFocusZone.GROUPS
                                    groupIndex = 0
                                }
                                TvFocusZone.GROUPS -> if (groupIndex < groups.size - 1) groupIndex++
                                TvFocusZone.GUIDE -> if (channelIndex < channels.size - 1) channelIndex++
                            }
                            true
                        }

                        Key.Menu, Key.Bookmark -> {
                            when (focusZone) {
                                TvFocusZone.GROUPS -> groups.getOrNull(safeGroupIndex)?.let {
                                    viewModel.toggleFavoriteGroup(it)
                                    true
                                } ?: false

                                TvFocusZone.GUIDE -> channels.getOrNull(safeChannelIndex)?.let {
                                    viewModel.toggleFavoriteChannel(it.id)
                                    true
                                } ?: false

                                TvFocusZone.SIDEBAR, TvFocusZone.CONTENT_MENU, TvFocusZone.SEARCH -> false
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
                    .padding(top = AppTopBarContentTopInset())
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                NotConfiguredPanel()
            }
        } else {
            // Immersive layout: seamless dark surface, no compartment borders
            Column(modifier = Modifier.fillMaxSize()) {
                // Mobile tab chips: Movies / TV / Series
                if (isMobile) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Movies", "TV", "Series").forEachIndexed { index, label ->
                            val isSelected = index == 1
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) Pink else Color.White.copy(alpha = 0.08f))
                                    .clickable {
                                        when (index) {
                                            0 -> onNavigateToMovies()
                                            2 -> onNavigateToSeries()
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = ArflixTypography.button,
                                    color = if (isSelected) Color.Black else TextSecondary
                                )
                            }
                        }
                    }
                }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = if (isMobile) 0.dp else contentTopPadding)
            ) {
                // Left content-type menu: Movies / TV / Series (TV only)
                if (!isMobile && !isFullScreen) {
                    ContentMenuPanel(
                        focusedIndex = contentMenuIndex,
                        isFocused = focusZone == TvFocusZone.CONTENT_MENU,
                        activeIndex = 1, // TV is at index 1 now (after Movies)
                        modifier = Modifier.fillMaxHeight()
                    )
                }

                // Mobile: full-screen groups list or channel view with swipe
                // TV: animated CategoryRail
                if (isMobile && showGroupsMobile) {
                    // Full-screen tappable groups list with search + favorites
                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = uiState.query,
                            onValueChange = { viewModel.setQuery(it) },
                            placeholder = { Text("Search groups…", style = ArflixTypography.body, color = TextSecondary) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary) },
                            trailingIcon = {
                                if (uiState.query.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setQuery("") }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = TextSecondary)
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Pink,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Pink
                            )
                        )
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            itemsIndexed(groups) { index, group ->
                                val isFavorite = uiState.snapshot.favoriteGroups.contains(group)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                groupIndex = index
                                                showGroupsMobile = false
                                            },
                                            onLongClick = { viewModel.toggleFavoriteGroup(group) }
                                        )
                                        .padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = group,
                                        style = ArflixTypography.body,
                                        color = if (isFavorite) Pink else TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { viewModel.toggleFavoriteGroup(group) }) {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                            contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                                            tint = if (isFavorite) Pink else TextSecondary
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
                        val showCategoryRail = focusZone != TvFocusZone.GUIDE || isFullScreen
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
                                groups = groups,
                                favoriteGroups = uiState.snapshot.favoriteGroups.toSet(),
                                focusedGroupIndex = safeGroupIndex,
                                isGroupsFocused = focusZone == TvFocusZone.GROUPS,
                                isSearchFocused = focusZone == TvFocusZone.SEARCH,
                                isSearchActive = searchActive,
                                listState = groupsListState,
                                searchQuery = uiState.query,
                                onSearchQueryChange = { viewModel.setQuery(it) },
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
                    // Top section: channel info/header above mini player on mobile, side-by-side on TV
                    if (isMobile) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.36f)
                                .background(BackgroundDark)
                        ) {
                            // Header: hamburger (mobile) + channel title or placeholder
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, top = 10.dp, bottom = 8.dp, end = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                               
                                    IconButton(
                                        onClick = { showGroupsMobile = true },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Categories", tint = Color.White)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                
                                if (playingChannel != null && !playingChannel.logo.isNullOrBlank()) {
                                    AsyncImage(
                                        model = playingChannel.logo,
                                        contentDescription = playingChannel.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Color.White.copy(alpha = 0.04f))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                if (playingChannel != null) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playingChannel.name,
                                            modifier = Modifier.fillMaxWidth(),
                                            style = ArflixTypography.cardTitle.copy(fontSize = 16.sp),
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = playingChannel.group,
                                            style = ArflixTypography.caption.copy(fontSize = 10.sp),
                                            color = Color.White.copy(alpha = 0.35f),
                                            maxLines = 1
                                        )
                                    }
                                } else {
                                    Text(
                                        "Select a channel",
                                        style = ArflixTypography.body.copy(fontSize = 14.sp),
                                        color = Color.White.copy(alpha = 0.2f)
                                    )
                                }
                            }

                            // Mini player below header (full width on mobile)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(top = 4.dp, bottom = 4.dp, end = 4.dp, start = 4.dp)
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.36f)
                                .background(BackgroundDark)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(0.45f)
                                    .fillMaxHeight()
                                    .padding(start = 14.dp, top = 10.dp, bottom = 8.dp, end = 10.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Header: hamburger + channel title or placeholder
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    if (playingChannel != null && !playingChannel.logo.isNullOrBlank()) {
                                        AsyncImage(
                                            model = playingChannel.logo,
                                            contentDescription = playingChannel.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(Color.White.copy(alpha = 0.04f))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    if (playingChannel != null) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = playingChannel.name,
                                                modifier = Modifier.fillMaxWidth(),
                                                style = ArflixTypography.cardTitle.copy(fontSize = 16.sp),
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = playingChannel.group,
                                                style = ArflixTypography.caption.copy(fontSize = 10.sp),
                                                color = Color.White.copy(alpha = 0.35f),
                                                maxLines = 1
                                            )
                                        }
                                    } else {
                                        Text(
                                            "Select a channel",
                                            style = ArflixTypography.body.copy(fontSize = 14.sp),
                                            color = Color.White.copy(alpha = 0.2f)
                                        )
                                    }
                                }
                            }

                            // Right: mini player (55% on TV)
                            Box(
                                modifier = Modifier
                                    .weight(0.55f)
                                    .fillMaxHeight()
                                    .padding(top = 4.dp, bottom = 4.dp, end = 4.dp)
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

                    // Channel guide - seamless below video
                    GuidePanel(
                        channels = channels,
                        isLoading = uiState.isLoading,
                        focusedChannelIndex = safeChannelIndex,
                        guideFocused = focusZone == TvFocusZone.GUIDE,
                        playingChannelId = playingChannelId,
                        favoriteChannels = uiState.snapshot.favoriteChannels.toSet(),
                        listState = channelsListState,
                        onChannelClick = { index ->
                            val channel = channels.getOrNull(index) ?: return@GuidePanel
                            channelIndex = index
                            focusZone = TvFocusZone.GUIDE
                            if (playingChannelId == channel.id) {
                                selectedChannelId = channel.id
                                isFullScreen = true
                            } else {
                                selectedChannelId = channel.id
                                playingChannelId = channel.id
                            }
                        },
                        modifier = Modifier.weight(0.64f)
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

                    // Channel info overlay (toggle with OK, auto-hides after 5s)
                    AnimatedVisibility(
                        visible = showFullscreenOverlay,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        FullscreenChannelOverlay(
                            channel = playingChannel,
                            isMobile = isMobile,
                            isPiPMode = isPiPMode,
                            onEnterPiP = { isPiPMode = true }
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
internal fun ContentMenuPanel(
    focusedIndex: Int,
    isFocused: Boolean,
    activeIndex: Int = 1, // 0=Movies, 1=TV, 2=Series (default TV)
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Pair(Icons.Outlined.Movie, "Movies"),
        Pair(Icons.Filled.LiveTv, "TV"),
        Pair(Icons.Outlined.PlayCircle, "Series")
    )
    Box(
        modifier = modifier
            .width(72.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.35f),
                        Color.Black.copy(alpha = 0.15f),
                        Color.Transparent
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            items.forEachIndexed { index, (icon, label) ->
                val isItemFocused = isFocused && index == focusedIndex
                val isActive = index == activeIndex
                val bgAlpha = when {
                    isItemFocused -> 0.22f
                    isActive -> 0.10f
                    else -> 0f
                }
                val iconAlpha = when {
                    isItemFocused -> 1f
                    isActive -> 0.92f
                    else -> 0.45f
                }
                val textAlpha = when {
                    isItemFocused -> 1f
                    isActive -> 0.85f
                    else -> 0.40f
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = bgAlpha))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = Color.White.copy(alpha = iconAlpha),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = textAlpha),
                        fontWeight = if (isItemFocused || isActive) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
                if (index < items.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryRail(
    groups: List<String>,
    favoriteGroups: Set<String>,
    focusedGroupIndex: Int,
    isGroupsFocused: Boolean,
    isSearchFocused: Boolean,
    isSearchActive: Boolean = false,
    listState: LazyListState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
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

        // Search bar for filtering categories (matches Movies/Series styling)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { androidx.compose.material3.Text("Search", color = TextSecondary.copy(alpha = 0.5f)) },
            textStyle = ArflixTypography.body.copy(color = Color.White.copy(alpha = 1f), fontSize = 12.sp),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(top = 0.dp, bottom = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .focusRequester(searchFocusRequester)
                .background(
                    if (isSearchFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent
                )
                .then(
                    if (isSearchFocused) Modifier.border(
                        width = 1.5.dp,
                        color = Color.White.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(2.dp)
                    ) else Modifier
                ),
            singleLine = true,
            readOnly = !isSearchActive,
            enabled = true,
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Clear search",
                            tint = TextSecondary
                        )
                    }
                }
            }
        )

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(groups, key = { _, group -> group }, contentType = { _, _ -> "category_group" }) { index, group ->
                GroupRailItem(
                    name = group,
                    isFocused = isGroupsFocused && index == focusedGroupIndex,
                    isFavorite = favoriteGroups.contains(group),
                    showMenu = showMenuForIndex == index,
                    onClick = {},
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
    name: String, isFocused: Boolean, isFavorite: Boolean,
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
            .background(if (isFocused) Color.White.copy(alpha = 0.07f) else Color.Transparent)
            .then(if (isFocused) Modifier.border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(6.dp)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFavorite) {
            Icon(Icons.Default.Star, null, tint = Color(0xFFF5C518).copy(alpha = 0.8f), modifier = Modifier.size(10.dp))
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(name, style = ArflixTypography.caption.copy(fontSize = 11.sp, fontWeight = if (isFocused) FontWeight.Medium else FontWeight.Normal, lineHeight = 14.sp),
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.4f), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun FullscreenChannelOverlay(
    channel: IptvChannel,
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
            // Top right: PiP button (mobile only) + Clock
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.spacedBy(if (isMobile) 8.dp else 12.dp)
            ) {
                if (isMobile && !isPiPMode) {
                    IconButton(
                        onClick = onEnterPiP,
                        modifier = Modifier
                            .size(if (isMobile) 36.dp else 44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPicture,
                            contentDescription = "Picture in Picture",
                            tint = Color.White,
                            modifier = Modifier.size(if (isMobile) 20.dp else 24.dp)
                        )
                    }
                }
                Text(
                    text = programTimeFormatter.format(java.time.LocalTime.now()),
                    style = ArflixTypography.sectionTitle.copy(fontSize = if (isMobile) 13.sp else 18.sp),
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        // Bottom scrim: LIVE badge + channel name
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
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
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GuidePanel(
    channels: List<IptvChannel>,
    isLoading: Boolean,
    focusedChannelIndex: Int,
    guideFocused: Boolean,
    playingChannelId: String?,
    favoriteChannels: Set<String>,
    listState: LazyListState,
    onChannelClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isMobile = LocalDeviceType.current.isTouchDevice()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 4.dp)
    ) {
        if (channels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (isLoading) "Loading channels..." else "No channels in this group",
                    style = ArflixTypography.body,
                    color = Color.White.copy(alpha = 0.3f)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    channels,
                    key = { _, ch -> ch.id },
                    contentType = { _, _ -> "guide_channel_row" }
                ) { index, channel ->
                    val focused = guideFocused && index == focusedChannelIndex
                    GuideChannelRow(
                        channel = channel,
                        isFocused = focused,
                        isPlaying = channel.id == playingChannelId,
                        isFavoriteChannel = favoriteChannels.contains(channel.id),
                        isMobile = isMobile,
                        onClick = { onChannelClick(index) }
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GuideChannelRow(
    channel: IptvChannel,
    isFocused: Boolean,
    isPlaying: Boolean,
    isFavoriteChannel: Boolean,
    isMobile: Boolean = false,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val rowBg = when {
        isFocused -> Color.White.copy(alpha = 0.05f)
        isPlaying -> AccentGreen.copy(alpha = 0.025f)
        else -> Color.Transparent
    }
    val borderColor = when {
        isFocused -> Color.White.copy(alpha = 0.5f)
        isPlaying -> AccentGreen.copy(alpha = 0.2f)
        else -> Color.Transparent
    }
    val primaryText = Color.White.copy(alpha = if (isFocused) 1f else 0.88f)
    val secondaryText = Color(0xFFA0A0A0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isMobile) 44.dp else 56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(rowBg)
            .then(
                if (isFocused || isPlaying) Modifier.border(
                    width = if (isFocused) 1.5.dp else 0.5.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(4.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(if (isMobile) 24.dp else 30.dp)) {
            if (!channel.logo.isNullOrBlank()) {
                val logoRequest = remember(channel.logo) {
                    ImageRequest.Builder(context)
                        .data(channel.logo)
                        .size(64, 64)
                        .precision(Precision.INEXACT)
                        .crossfade(false)
                        .allowHardware(true)
                        .build()
                }
                AsyncImage(
                    model = logoRequest,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(if (isMobile) 24.dp else 30.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(if (isMobile) 24.dp else 30.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.04f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(14.dp))
                }
            }
            if (isFavoriteChannel) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFF5C518).copy(alpha = 0.9f),
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.TopEnd)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channel.name,
                    style = ArflixTypography.cardTitle.copy(fontSize = if (isMobile) 10.sp else 11.sp),
                    color = primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isPlaying) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "LIVE",
                        style = ArflixTypography.caption.copy(fontSize = 7.sp, fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier
                            .background(AccentGreen.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            Text(
                text = channel.group,
                style = ArflixTypography.caption.copy(fontSize = if (isMobile) 8.sp else 8.sp),
                color = secondaryText.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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

private val programTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")