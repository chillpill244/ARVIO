package com.muvio.shared.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muvio.shared.player.PlayerEngine
import com.muvio.shared.player.PlayerEngineListener
import com.muvio.shared.util.toTimeString
import com.muvio.shared.viewmodel.PlayerViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * PlayerScreen owns the PlayerEngine lifecycle. Platform-specific rendering
 * (AndroidView for ExoPlayer, or Metal UIView for MPV on iOS) is handled via
 * PlatformPlayerView expect/actual in Phase 7. For now, a black surface with
 * playback controls is rendered — enough to wire and test the VM + engine.
 */
@Composable
fun PlayerScreen(
    tmdbId: Int,
    mediaTypeStr: String,
    streamIndex: Int,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    val engine = remember { PlayerEngine() }

    // Wire engine callbacks → VM
    DisposableEffect(engine) {
        engine.setListener(object : PlayerEngineListener {
            override fun onProgress(progressMs: Long, durationMs: Long) =
                viewModel.onPlaybackProgress(progressMs, durationMs)

            override fun onPlayStateChanged(isPlaying: Boolean) =
                viewModel.onPlayPause(isPlaying)

            override fun onBufferingChanged(isBuffering: Boolean) =
                viewModel.onBuffering(isBuffering)

            override fun onError(message: String) = viewModel.onError(message)
            override fun onEnded() = viewModel.onPlayPause(false)
        })
        onDispose {
            engine.setListener(null)
            engine.release()
        }
    }

    // Start playback when the selected stream is set
    LaunchedEffect(state.selectedStream) {
        val stream = state.selectedStream ?: return@LaunchedEffect
        engine.prepare(stream, state.progressMs)
        engine.play()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { viewModel.toggleControls() },
    ) {
        // Phase 7: replace this placeholder with PlatformPlayerView(engine)
        if (state.isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Overlay controls
        if (state.showControls) {
            PlayerControls(
                title = state.mediaItem?.title ?: "",
                episodeTitle = state.episodeTitle,
                progressMs = state.progressMs,
                durationMs = state.durationMs,
                isPlaying = state.isPlaying,
                error = state.error,
                onBack = {
                    engine.pause()
                    onBack()
                },
                onPlayPause = {
                    if (state.isPlaying) engine.pause() else engine.play()
                },
                onSeek = { fraction ->
                    val target = (fraction * state.durationMs).toLong()
                    engine.seekTo(target)
                },
            )
        }
    }
}

@Composable
private fun PlayerControls(
    title: String,
    episodeTitle: String?,
    progressMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    error: String?,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
    ) {
        // Top bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                episodeTitle?.let {
                    Text(it, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(16.dp),
            )
        }

        // Center play/pause
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(64.dp),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.weight(1f))

        // Bottom seek bar + time
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            val progress = if (durationMs > 0) progressMs.toFloat() / durationMs else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.3f),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(progressMs.toTimeString(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(durationMs.toTimeString(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// toTimeString is imported from com.muvio.shared.util.toTimeString
