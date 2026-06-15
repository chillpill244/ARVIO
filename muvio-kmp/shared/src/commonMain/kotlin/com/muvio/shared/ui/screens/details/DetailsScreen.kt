package com.muvio.shared.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.muvio.shared.domain.MediaItem
import com.muvio.shared.domain.MediaType
import com.muvio.shared.domain.StreamSource
import com.muvio.shared.ui.components.MediaCard
import com.muvio.shared.viewmodel.DetailsViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    tmdbId: Int,
    mediaTypeStr: String,
    onBack: () -> Unit,
    onPlayStream: (streamIndex: Int) -> Unit,
    viewModel: DetailsViewModel = koinViewModel(parameters = { parametersOf(tmdbId, mediaTypeStr) }),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading && state.item == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.error != null && state.item == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error ?: "Error", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = viewModel::retry) { Text("Retry") }
                    }
                }
            }

            state.item != null -> {
                val item = state.item!!
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 48.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    item {
                        BackdropWithMetadata(
                            item = item,
                            onPlayClick = {
                                if (state.streams.isNotEmpty()) {
                                    onPlayStream(0)
                                } else {
                                    viewModel.loadStreams()
                                }
                            },
                            isLoadingStreams = state.isLoadingStreams,
                            progress = if (item.progress > 0) item.progress / 100f else null,
                        )
                    }

                    item {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.genres.take(3).forEach { genre ->
                                Text(
                                    text = genre,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (item.overview.isNotEmpty()) {
                            Text(
                                text = item.overview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }

                    // Stream list
                    if (state.streams.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Sources",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        itemsIndexed(state.streams) { index, stream ->
                            StreamRow(
                                stream = stream,
                                onClick = { onPlayStream(index) },
                            )
                        }
                    }

                    // Season / episode selector (TV only)
                    if (mediaTypeStr == MediaType.TV.name && state.seasons.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Episodes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            // Season selector
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                itemsIndexed(state.seasons) { _, season ->
                                    FilterChip(
                                        selected = season.seasonNumber == state.selectedSeason,
                                        onClick = { viewModel.selectSeason(season.seasonNumber) },
                                        label = { Text("S${season.seasonNumber}") },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        itemsIndexed(state.episodes) { _, episode ->
                            EpisodeRow(
                                title = episode.name,
                                episodeNumber = episode.episodeNumber,
                                overview = episode.overview,
                                stillUrl = episode.stillPath,
                                onClick = {
                                    viewModel.loadEpisodeStreams(state.selectedSeason, episode.episodeNumber)
                                },
                            )
                        }
                    }

                    // Similar
                    if (state.similar.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "More Like This",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                itemsIndexed(state.similar) { _, similar ->
                                    MediaCard(
                                        item = similar,
                                        onClick = { /* navigate to details — handled by parent */ },
                                        modifier = Modifier.width(110.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackdropWithMetadata(
    item: MediaItem,
    onPlayClick: () -> Unit,
    isLoadingStreams: Boolean,
    progress: Float?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
    ) {
        AsyncImage(
            model = item.backdrop ?: item.image,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        startY = 0.45f,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.year.isNotEmpty()) {
                    Text(item.year, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                }
                if (item.tmdbRating.isNotEmpty()) {
                    Text(" · ★ ${item.tmdbRating}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onPlayClick,
                enabled = !isLoadingStreams,
            ) {
                if (isLoadingStreams) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (progress != null) "Resume" else "Play")
                }
            }
        }
        progress?.let {
            LinearProgressIndicator(
                progress = { it },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StreamRow(stream: StreamSource, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${stream.addonName} · ${stream.quality}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(
                stream.size.takeIf { it.isNotEmpty() },
                stream.description,
                stream.source.takeIf { it.isNotEmpty() },
            ).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    title: String,
    episodeNumber: Int,
    overview: String?,
    stillUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (stillUrl != null) {
                AsyncImage(
                    model = stillUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = "E$episodeNumber",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            overview?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
