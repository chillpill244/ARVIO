@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.arflix.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.arflix.tv.ui.theme.BackgroundDark

/**
 * Frosted glass background effect component.
 * Creates a modern glass-morphism aesthetic with layered transparency and blur.
 * Perfect for SettingsScreen and other elevated surfaces.
 */
@Composable
fun FrostedGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        BackgroundDark,
                        BackgroundDark.copy(alpha = 0.95f)
                    )
                )
            )
    ) {
        // Frosted glass overlay with subtle blur
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = Color.White.copy(alpha = 0.03f),
                    shape = RoundedCornerShape(0.dp)
                )
        )

        // Content with subtle layered effect
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = Color.Black.copy(alpha = 0.15f)
                )
        ) {
            content()
        }
    }
}

/**
 * Frosted glass card component for individual settings items.
 * Provides elevation and glass effect for card containers.
 */
@Composable
fun FrostedGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                color = Color.White.copy(alpha = 0.04f),
                shape = RoundedCornerShape(12.dp)
            )
            .blur(radius = 0.5.dp)
    ) {
        // Inner glass layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.03f),
                            Color.White.copy(alpha = 0.01f)
                        )
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
        )

        content()
    }
}
