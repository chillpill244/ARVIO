package com.muvio.shared.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muvio.shared.domain.MediaItem
import com.muvio.shared.ui.components.MediaCategoryRail
import com.muvio.shared.ui.components.MobileHeroBanner
import com.muvio.shared.viewmodel.HomeViewModel
import org.koin.compose.viewmodel.koinViewModel

private val BgDark = Color(0xFF0A0A0A)
private val AccentTeal = Color(0xFF00C8A0)

@Composable
fun HomeScreen(
    onItemClick: (MediaItem) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
    ) {
        when {
            state.isLoading && state.categories.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentTeal)
                }
            }

            state.error != null && state.categories.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = state.error ?: "Failed to load",
                        color = Color(0xFFCF6679),
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = viewModel::retry,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentTeal, contentColor = Color.Black),
                    ) { Text("Retry") }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    state.heroItem?.let { hero ->
                        item(key = "hero") {
                            MobileHeroBanner(
                                item = hero,
                                onPlayClick = { onItemClick(hero) },
                                onInfoClick = { onItemClick(hero) },
                            )
                        }
                    }

                    items(state.categories, key = { it.id }) { category ->
                        MediaCategoryRail(
                            title = category.title,
                            items = category.items,
                            onItemClick = onItemClick,
                        )
                    }

                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}
