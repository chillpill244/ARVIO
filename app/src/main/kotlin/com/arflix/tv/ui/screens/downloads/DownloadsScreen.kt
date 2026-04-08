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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.ui.theme.ArflixTypography

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DownloadsTab(
    viewModel: DownloadsViewModel = hiltViewModel(),
    onMoviePlay: (DownloadEntity) -> Unit,
    onSeriesClick: (tmdbId: Int, title: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    if (uiState.movieDownloads.isEmpty() && uiState.seriesDownloads.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "No downloads yet",
                    style = ArflixTypography.body,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (uiState.movieDownloads.isNotEmpty()) {
            item {
                Text(
                    "Movies",
                    style = ArflixTypography.sectionTitle,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(uiState.movieDownloads, key = { it.id }) { download ->
                        DownloadCard(
                            download = download,
                            isSeries = false,
                            onClick = { onMoviePlay(download) },
                            onDelete = { viewModel.deleteDownload(download.id) },
                            onRetry = { viewModel.retryDownload(download.id) },
                            onCancel = { viewModel.cancelDownload(download.id) },
                            onPause = { viewModel.pauseDownload(download.id) },
                            onResume = { viewModel.resumeDownload(download.id) }
                        )
                    }
                }
            }
        }

        if (uiState.seriesDownloads.isNotEmpty()) {
            item {
                Text(
                    "Series",
                    style = ArflixTypography.sectionTitle,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(uiState.seriesDownloads, key = { it.id }) { download ->
                        DownloadCard(
                            download = download,
                            isSeries = true,
                            onClick = { onSeriesClick(download.tmdbId, download.title) },
                            onDelete = { viewModel.deleteDownload(download.id) },
                            onRetry = { viewModel.retryDownload(download.id) },
                            onCancel = { viewModel.cancelDownload(download.id) },
                            onPause = { viewModel.pauseDownload(download.id) },
                            onResume = { viewModel.resumeDownload(download.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DownloadCard(
    download: DownloadEntity,
    isSeries: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    val cardWidth = 140.dp
    val cardHeight = 200.dp
    var showMenu by remember { mutableStateOf(false) }

    // Long-press context menu
    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            containerColor = Color(0xFF1C1C1E),
            title = {
                androidx.compose.material3.Text(
                    text = download.title,
                    color = Color.White,
                    style = ArflixTypography.body.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (download.status) {
                        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                            MenuAction(Icons.Default.Pause, "Pause") { showMenu = false; onPause() }
                            MenuAction(Icons.Default.Cancel, "Cancel") { showMenu = false; onCancel() }
                        }
                        DownloadStatus.PAUSED -> {
                            MenuAction(Icons.Default.PlayArrow, "Resume") { showMenu = false; onResume() }
                            MenuAction(Icons.Default.Cancel, "Cancel") { showMenu = false; onCancel() }
                        }
                        DownloadStatus.FAILED -> {
                            MenuAction(Icons.Default.Refresh, "Retry") { showMenu = false; onRetry() }
                            MenuAction(Icons.Default.Delete, "Delete") { showMenu = false; onDelete() }
                        }
                        DownloadStatus.COMPLETED -> {
                            MenuAction(Icons.Default.Delete, "Delete") { showMenu = false; onDelete() }
                        }
                        else -> {
                            MenuAction(Icons.Default.Delete, "Delete") { showMenu = false; onDelete() }
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

    Column(
        modifier = Modifier
            .width(cardWidth)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
    ) {
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        ) {
            AsyncImage(
                model = download.posterPath,
                contentDescription = download.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Status overlay
            when (download.status) {
                DownloadStatus.DOWNLOADING -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(4.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = download.progress / 100f,
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF4CAF50),
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
                DownloadStatus.QUEUED -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }
                }
                DownloadStatus.PAUSED -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Pause,
                            contentDescription = "Paused",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                DownloadStatus.FAILED -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Failed",
                            tint = Color.Red,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                DownloadStatus.COMPLETED -> {
                    if (!isSeries) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = download.title,
            style = ArflixTypography.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (download.mediaType == MediaType.TV.name && download.fileSize > 0) {
            Text(
                text = formatFileSize(download.fileSize),
                style = ArflixTypography.body.copy(fontSize = 10.sp),
                color = Color.White.copy(alpha = 0.6f)
            )
        }

        val statusText = when (download.status) {
            DownloadStatus.COMPLETED -> if (download.mediaType == MediaType.TV.name) "" else formatFileSize(download.fileSize)
            DownloadStatus.DOWNLOADING -> "${download.progress}%"
            DownloadStatus.QUEUED -> "Queued"
            DownloadStatus.FAILED -> "Failed"
            DownloadStatus.PAUSED -> "Paused"
            else -> ""
        }
        if (statusText.isNotEmpty()) {
            Text(
                text = statusText,
                style = ArflixTypography.body.copy(fontSize = 10.sp),
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MenuAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
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

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.0f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
