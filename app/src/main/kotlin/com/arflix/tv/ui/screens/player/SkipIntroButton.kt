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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.arflix.tv.data.repository.SkipInterval
import com.arflix.tv.shared.theme.ArflixTypography
import kotlinx.coroutines.delay

/** Interval types that mark end credits / outro / ED segments. */
fun SkipInterval.isOutro(): Boolean = type in setOf("outro", "ed", "mixed-ed")

/**
 * In-player skip button (TV remote friendly).
 * Appears during active skip intervals (intro/recap/outro/OP/ED).
 * During outro intervals a "Next Episode" button is shown alongside when
 * [showNextEpisode] is set.
 */
@Composable
fun SkipIntroButton(
    interval: SkipInterval?,
    dismissed: Boolean,
    controlsVisible: Boolean,
    onSkip: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    showNextEpisode: Boolean = false,
    onNextEpisode: () -> Unit = {}
) {
    val shouldShow = interval != null && !dismissed
    var autoHidden by remember { mutableStateOf(false) }
    val nextEpisodeFocusRequester = remember { FocusRequester() }

    // Reset auto-hide when interval changes
    LaunchedEffect(interval?.startMs, interval?.endMs, interval?.type) {
        autoHidden = false
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
        if (controlsVisible && autoHidden && interval != null && !dismissed) {
            autoHidden = false
        }
    }

    val isVisible = shouldShow && (!autoHidden || controlsVisible)

    // When visible while controls are hidden, take focus so Enter skips immediately.
    LaunchedEffect(isVisible) {
        if (isVisible && !controlsVisible) {
            delay(160)
            runCatching { focusRequester.requestFocus() }
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 0.88f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.92f),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SkipPillButton(
                text = skipLabel(interval?.type),
                focusRequester = focusRequester,
                onClick = onSkip,
                onRightKey = if (showNextEpisode) {
                    { runCatching { nextEpisodeFocusRequester.requestFocus() } }
                } else null
            )
            if (showNextEpisode) {
                SkipPillButton(
                    text = "Next Episode",
                    focusRequester = nextEpisodeFocusRequester,
                    onClick = onNextEpisode,
                    onLeftKey = { runCatching { focusRequester.requestFocus() } }
                )
            }
        }
    }
}

@Composable
private fun SkipPillButton(
    text: String,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onLeftKey: (() -> Unit)? = null,
    onRightKey: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp) // match PlayerTextButtonFocusable
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, label = "skip_scale")

    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Enter, Key.DirectionCenter -> {
                            onClick()
                            true
                        }
                        Key.DirectionLeft -> {
                            if (onLeftKey != null) { onLeftKey(); true } else false
                        }
                        Key.DirectionRight -> {
                            if (onRightKey != null) { onRightKey(); true } else false
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
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
        Text(
            text = text,
            style = ArflixTypography.body.copy(
                fontSize = 14.sp,
                fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (isFocused) Color.Black else Color.White
        )
    }
}

private fun skipLabel(type: String?): String = when (type) {
    "op", "mixed-op", "intro" -> "Skip Intro"
    "recap" -> "Skip Recap"
    "ed", "mixed-ed", "outro" -> "Skip Outro"
    else -> "Skip"
}
