package com.arflix.tv.ui.screens.downloads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.arflix.tv.data.db.DownloadEntity
import com.arflix.tv.data.db.DownloadStatus
import com.arflix.tv.ui.theme.ArflixTypography
import com.arflix.tv.ui.theme.BackgroundDark

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DownloadedEpisodesScreen(
    tmdbId: Int,
    title: String,
    viewModel: DownloadsViewModel = hiltViewModel(),
    onPlayEpisode: (DownloadEntity) -> Unit,
    onBack: () -> Unit
) {
    // Observe live so any action (pause, delete, cancel, resume) reflects immediately
    val episodes by viewModel.observeEpisodesForSeries(tmdbId).collectAsState(initial = emptyList())
    val isLoading = false // Flow emits instantly from Room cache

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = title,
                        style = ArflixTypography.sectionTitle,
                        color = Color.White
                    )
                    Text(
                        text = "${episodes.count { it.status == DownloadStatus.COMPLETED }} downloaded · ${episodes.size} total",
                        style = ArflixTypography.caption,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (episodes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No episodes",
                        style = ArflixTypography.body,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 48.dp)
                ) {
                    items(episodes, key = { it.id }) { episode ->
                        DownloadedEpisodeRow(
                            episode = episode,
                            onPlay = { onPlayEpisode(episode) },
                            onDelete = { viewModel.deleteDownload(episode.id) },
                            onPause = { viewModel.pauseDownload(episode.id) },
                            onResume = { viewModel.resumeDownload(episode.id) },
                            onCancel = { viewModel.cancelDownload(episode.id) },
                            onRetry = { viewModel.retryDownload(episode.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DownloadedEpisodeRow(
    episode: DownloadEntity,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            containerColor = Color(0xFF1C1C1E),
            title = {
                androidx.compose.material3.Text(
                    text = episode.episodeTitle ?: "S${episode.season}:E${episode.episode}",
                    color = Color.White,
                    style = ArflixTypography.body.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (episode.status) {
                        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                            EpisodeMenuAction(Icons.Default.Pause, "Pause") { showMenu = false; onPause() }
                            EpisodeMenuAction(Icons.Default.Cancel, "Cancel") { showMenu = false; onCancel() }
                        }
                        DownloadStatus.PAUSED -> {
                            EpisodeMenuAction(Icons.Default.PlayArrow, "Resume") { showMenu = false; onResume() }
                            EpisodeMenuAction(Icons.Default.Cancel, "Cancel") { showMenu = false; onCancel() }
                        }
                        DownloadStatus.FAILED -> {
                            EpisodeMenuAction(Icons.Default.Refresh, "Retry") { showMenu = false; onRetry() }
                            EpisodeMenuAction(Icons.Default.Delete, "Delete") { showMenu = false; onDelete() }
                        }
                        else -> {
                            EpisodeMenuAction(Icons.Default.Delete, "Delete") { showMenu = false; onDelete() }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMenu = false }) {
                    androidx.compose.material3.Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .combinedClickable(
                onClick = { if (episode.status == DownloadStatus.COMPLETED) onPlay() },
                onLongClick = { showMenu = true }
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.DarkGray)
        ) {
            AsyncImage(
                model = episode.backdropPath ?: episode.posterPath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Play overlay
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "S${episode.season}:E${episode.episode}",
                style = ArflixTypography.body.copy(fontSize = 11.sp),
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = episode.episodeTitle ?: episode.title,
                style = ArflixTypography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            when (episode.status) {
                DownloadStatus.DOWNLOADING -> {
                    val progress = if (episode.fileSize > 0) {
                        episode.downloadedBytes.toFloat() / episode.fileSize.toFloat()
                    } else episode.progress / 100f
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFF4CAF50),
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (episode.fileSize > 0)
                            "${(progress * 100).toInt()}% · ${formatSize(episode.downloadedBytes)} / ${formatSize(episode.fileSize)}"
                        else "${episode.progress}%",
                        style = ArflixTypography.body.copy(fontSize = 10.sp),
                        color = Color(0xFF4CAF50)
                    )
                }
                DownloadStatus.QUEUED -> {
                    Text(
                        text = "Queued",
                        style = ArflixTypography.body.copy(fontSize = 10.sp),
                        color = Color(0xFFFFC107)
                    )
                }
                DownloadStatus.PAUSED -> {
                    val progress = if (episode.fileSize > 0) {
                        episode.downloadedBytes.toFloat() / episode.fileSize.toFloat()
                    } else episode.progress / 100f
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFFFFC107),
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Paused · ${(progress * 100).toInt()}%",
                        style = ArflixTypography.body.copy(fontSize = 10.sp),
                        color = Color(0xFFFFC107)
                    )
                }
                DownloadStatus.FAILED -> {
                    Text(
                        text = "Failed — long-press to retry",
                        style = ArflixTypography.body.copy(fontSize = 10.sp),
                        color = Color(0xFFFF5252)
                    )
                }
                DownloadStatus.COMPLETED -> {
                    if (episode.fileSize > 0) {
                        Text(
                            text = formatSize(episode.fileSize),
                            style = ArflixTypography.body.copy(fontSize = 10.sp),
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
                else -> {}
            }
        }

        // Context-sensitive right icon
        val (icon, iconTint) = when (episode.status) {
            DownloadStatus.PAUSED -> Icons.Default.PlayArrow to Color(0xFFFFC107)
            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> Icons.Default.Pause to Color(0xFF4CAF50)
            DownloadStatus.FAILED -> Icons.Default.Refresh to Color(0xFFFF5252)
            else -> Icons.Default.MoreVert to Color.White.copy(alpha = 0.4f)
        }
        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
            Icon(
                icon,
                contentDescription = "More options",
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeMenuAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(label, style = ArflixTypography.body.copy(fontSize = 14.sp), color = Color.White)
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.0f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
