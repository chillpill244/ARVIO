@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.arflix.tv.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.arflix.tv.ui.theme.ArflixTypography
import kotlinx.coroutines.delay

/**
 * Next Episode button that appears in the last minute of an episode or during end credits (via IntroDB).
 * Positioned on the right side of the screen, similar to Netflix.
 */
@Composable
fun NextEpisodeButton(
    hasNextEpisode: Boolean,
    isInLastMinute: Boolean,
    controlsVisible: Boolean,
    seasonNumber: Int?,
    nextEpisodeNumber: Int?,
    onPlayNext: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val shouldShow = hasNextEpisode && isInLastMinute
    var autoHidden by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    // Reset auto-hide when visibility changes
    LaunchedEffect(shouldShow) {
        if (shouldShow) {
            autoHidden = false
        }
    }

    // Auto-hide after 15s
    LaunchedEffect(shouldShow, autoHidden) {
        if (shouldShow && !autoHidden) {
            delay(15_000)
            autoHidden = true
        }
    }

    // If user brings up controls, let it reappear
    LaunchedEffect(controlsVisible) {
        if (controlsVisible && autoHidden && shouldShow) {
            autoHidden = false
        }
    }

    val isVisible = shouldShow && (!autoHidden || controlsVisible)

    val shape = RoundedCornerShape(20.dp)
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, label = "next_episode_scale")

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 0.88f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.92f),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.Enter, Key.DirectionCenter -> {
                                onPlayNext()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
                .focusable()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onPlayNext() }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(
                    if (isFocused) Color.White else Color.White.copy(alpha = 0.1f),
                    shape
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.2f),
                    shape = shape
                )
                .padding(horizontal = 20.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            val label = if (seasonNumber != null && nextEpisodeNumber != null) {
                "Next Episode: S$seasonNumber E$nextEpisodeNumber"
            } else {
                "Next Episode"
            }
            
            Text(
                text = label,
                style = ArflixTypography.body.copy(
                    fontSize = 14.sp,
                    fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (isFocused) Color.Black else Color.White
            )
        }
    }
}
