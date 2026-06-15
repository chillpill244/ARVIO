package com.muvio.shared.ui.screens.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.muvio.shared.domain.MediaItem
import com.muvio.shared.ui.components.MediaCard
import com.muvio.shared.viewmodel.HomeViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onItemClick: (MediaItem) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Muvio", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            state.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = viewModel::retry) { Text("Retry") }
                    }
                }
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    state.heroItem?.let { hero ->
                        item {
                            HeroBanner(item = hero, onClick = { onItemClick(hero) })
                            Spacer(Modifier.height(24.dp))
                        }
                    }

                    items(state.categories) { category ->
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(category.items) { item ->
                                MediaCard(
                                    item = item,
                                    onClick = { onItemClick(item) },
                                    modifier = Modifier.width(120.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroBanner(item: MediaItem, onClick: () -> Unit) {
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
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            if (item.overview.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 2,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onClick) { Text("Watch Now") }
            }
        }
    }
}
