@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.arflix.tv.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.arflix.tv.data.model.IptvChannel
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.ui.skin.ArvioFocusableSurface
import com.arflix.tv.ui.skin.ArvioSkin
import com.arflix.tv.ui.skin.rememberArvioCardShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex

/**
 * Sealed class representing card content for unified display.
 * Supports both MediaItem and IptvChannel through a common interface.
 */
sealed class CardContent {
    abstract val id: Any  // Can be Int (MediaItem) or String (IptvChannel)
    abstract val title: String
    abstract val image: String?
    abstract val subtitle: String

    data class Media(val item: MediaItem) : CardContent() {
        override val id: Any = item.id
        override val title: String = item.title
        override val image: String? = item.image
        override val subtitle: String = item.subtitle
        val backdrop: String? = item.backdrop
        val isWatched: Boolean = item.isWatched
        val progress: Int = item.progress
        val isPlaceholder: Boolean = item.isPlaceholder
        val mediaType: MediaType = item.mediaType
    }

    data class Channel(val channel: IptvChannel) : CardContent() {
        override val id: Any = channel.id
        override val title: String = channel.name
        override val image: String? = channel.logo
        override val subtitle: String = ""  // Channels don't have subtitles
    }
}

/**
 * Media card component for rows/grids.
 * Arctic Fuse 2 style:
 * - Large landscape cards with solid pink/magenta focus border
 * - Transform-based focus (graphicsLayer) via `ArvioFocusableSurface`
 * - No layout size changes on focus (no width/height scaling)
 * - Uses `ArvioSkin` for consistent styling
 * - Supports both MediaItem and IptvChannel via CardContent sealed class
 */

// Shared gradient overlay for all cards - avoids per-card Brush allocation.
// Uses pre-computed ARGB values for BackgroundDark (0x0D0D14).
private val sharedCardOverlayBrush = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        Color.Transparent,
        Color(0x8C_0D0D14),  // ~55% alpha
        Color(0xD9_0D0D14),  // ~85% alpha
    )
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaCard(
    content: CardContent,
    width: Dp = 280.dp,  // Arctic Fuse 2: larger cards by default
    isLandscape: Boolean = true,
    logoImageUrl: String? = null,
    showProgress: Boolean = false,
    isFocusedOverride: Boolean = false,
    enableSystemFocus: Boolean = true,
    onFocused: () -> Unit = {},
    onClick: () -> Unit = {},
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Extract MediaItem for type-specific features
    val mediaItem = (content as? CardContent.Media)?.item
    
    // If this is a placeholder card, show skeleton only
    if (mediaItem?.isPlaceholder == true) {
        PlaceholderCard(
            width = width,
            isLandscape = isLandscape,
            modifier = modifier
        )
        return
    }

    var isFocused by remember { mutableStateOf(false) }
    val visualFocused = isFocusedOverride || isFocused

    val aspectRatio = if (isLandscape) 16f / 9f else 2f / 3f
    // Plex-like behavior: landscape cards should prefer wide artwork/backdrops.
    // Poster art is portrait and looks cropped in 16:9 cards, so only use it as fallback.
    val imageUrl = if (isLandscape) {
        (mediaItem?.backdrop ?: content.image)
    } else {
        content.image
    }
    val shape = rememberArvioCardShape(ArvioSkin.radius.md)

    val showFocusOutline = visualFocused
    val jumpBorderWidth = if (showFocusOutline) 3.dp else 0.dp

    val context = LocalContext.current
    val density = LocalDensity.current
    val overlayBrush = sharedCardOverlayBrush
    // Performance: Removed context/density from keys - they're stable CompositionLocals
    val imageRequest = remember(imageUrl, width, aspectRatio) {
        val widthPx = with(density) { width.roundToPx() }
        val heightPx = (widthPx / aspectRatio).toInt().coerceAtLeast(1)
        ImageRequest.Builder(context)
            .data(imageUrl)
            .size(widthPx, heightPx)
            .precision(Precision.INEXACT)
            .allowHardware(true)
            .crossfade(false)  // No crossfade: avoids 2x overdraw per card during scroll
            .build()
    }
    // Performance: Removed context/density from keys
    val logoRequest = remember(logoImageUrl) {
        val logoWidthPx = with(density) { 220.dp.roundToPx() }.coerceAtLeast(1)
        val logoHeightPx = with(density) { 64.dp.roundToPx() }.coerceAtLeast(1)
        if (logoImageUrl.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(logoImageUrl)
                .size(logoWidthPx, logoHeightPx)
                .precision(Precision.INEXACT)
                .allowHardware(true)
                .crossfade(false)
                .build()
        }
    }

    Column(
        modifier = modifier
            .width(width)
            .zIndex(if (visualFocused) 1f else 0f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown()
                    val longPress = awaitLongPressOrCancellation(firstDown.id)
                    if (longPress != null) {
                        onLongPress()
                    }
                }
            }
    ) {
        ArvioFocusableSurface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio),
            shape = shape,
            backgroundColor = ArvioSkin.colors.surface,
            outlineColor = ArvioSkin.colors.focusOutline,
            outlineWidth = jumpBorderWidth,
            focusedScale = 1.0f,
            pressedScale = 1f,
            focusedTransformOriginX = 0f,
            enableSystemFocus = enableSystemFocus,
            isFocusedOverride = isFocusedOverride,
            onClick = onClick,
            onFocusChanged = {
                isFocused = it
                if (it) onFocused()
            },
        ) { _ ->
            Box(modifier = Modifier.fillMaxSize()) {
                // Glass UI background for missing images
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.05f),
                                    Color.White.copy(alpha = 0.08f),
                                    Color.White.copy(alpha = 0.05f)
                                )
                            )
                        )
                )
                // Performance: Removed SkeletonBox with infinite shimmer animation
                // The surface background color provides a placeholder while image loads
                AsyncImage(
                    model = imageRequest,
                    contentDescription = content.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(overlayBrush)
                )

                // Official logo/art overlay centered on landscape cards.
                // Only show for MediaItem (mediaItem is not null)
                if (isLandscape && logoRequest != null && mediaItem != null) {
                    AsyncImage(
                        model = logoRequest,
                        contentDescription = "${content.title} logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.62f)
                            .height(56.dp)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }

                // Subtle green watched badge - only for MediaItem
                if (mediaItem?.isWatched == true) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 6.dp, end = 6.dp)
                            .size(14.dp)
                            .background(
                                color = ArvioSkin.colors.watchedGreen.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = ArvioSkin.colors.watchedGreen,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = ArvioSkin.colors.watchedGreen,
                            modifier = Modifier.size(8.dp)
                        )
                    }
                }

                // Subtle progress bar for Continue Watching - only for MediaItem
                if (showProgress && mediaItem?.isWatched == false && mediaItem?.progress in 1..94) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.26f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(mediaItem.progress / 100f)
                                .fillMaxSize()
                                .background(Color.White.copy(alpha = 0.92f))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(ArvioSkin.spacing.x2))

        Text(
            text = content.title,
            style = ArvioSkin.typography.cardTitle,
            color = if (visualFocused) {
                ArvioSkin.colors.textPrimary
            } else {
                ArvioSkin.colors.textPrimary.copy(alpha = 0.85f)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (visualFocused) Modifier.basicMarquee() else Modifier,
        )

        // Arctic Fuse 2 style: Show media type with genre-like format
        // For channels, show empty or use a default text
        val subtitle = remember(content.subtitle, mediaItem?.mediaType) {
            content.subtitle.ifBlank {
                when (mediaItem?.mediaType) {
                    MediaType.TV -> "Drama / TV Series"
                    MediaType.MOVIE -> "Action / Movie"
                    else -> "Live TV"
                }
            }
        }
        Text(
            text = subtitle,
            style = ArvioSkin.typography.caption,
            color = ArvioSkin.colors.textMuted.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Placeholder card shown while Continue Watching data loads.
 * Displays a skeleton animation to indicate loading state.
 */
@Composable
private fun PlaceholderCard(
    width: Dp,
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    val aspectRatio = if (isLandscape) 16f / 9f else 2f / 3f
    val shape = rememberArvioCardShape(ArvioSkin.radius.md)

    Column(
        modifier = modifier.width(width)
    ) {
        // Card skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(shape)
        ) {
            SkeletonBox(
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(ArvioSkin.spacing.x2))

        // Title skeleton
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(14.dp)
                .clip(rememberArvioCardShape(ArvioSkin.radius.sm))
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Subtitle skeleton
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(12.dp)
                .clip(rememberArvioCardShape(ArvioSkin.radius.sm))
        )
    }
}

/**
 * Poster-style media card (portrait orientation).
 * Phase 5: Added proper image sizing and shimmer placeholder.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PosterCard(
    content: CardContent,
    width: Dp = 140.dp,
    isFocusedOverride: Boolean = false,
    enableSystemFocus: Boolean = true,
    onFocused: () -> Unit = {},
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    useWhiteBorder: Boolean = true,
) {
    var isFocused by remember { mutableStateOf(false) }
    val visualFocused = isFocusedOverride || isFocused

    val shape = rememberArvioCardShape(ArvioSkin.radius.md)
    val outlineColor = if (useWhiteBorder) ArvioSkin.colors.focusOutline else ArvioSkin.colors.accent

    val context = LocalContext.current
    val density = LocalDensity.current
    val aspectRatio = 2f / 3f
    // Performance: Removed context/density from keys
    val imageRequest = remember(content.image, width) {
        val widthPx = with(density) { width.roundToPx() }
        val heightPx = (widthPx / aspectRatio).toInt().coerceAtLeast(1)
        ImageRequest.Builder(context)
            .data(content.image)
            .size(widthPx, heightPx)
            .precision(Precision.INEXACT)
            .allowHardware(true)
            .crossfade(false)  // No crossfade: avoids 2x overdraw per card during scroll
            .build()
    }

    Column(modifier = modifier.width(width)) {
        ArvioFocusableSurface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio),
            shape = shape,
            backgroundColor = ArvioSkin.colors.surface,
            outlineColor = outlineColor,
            enableSystemFocus = enableSystemFocus,
            isFocusedOverride = isFocusedOverride,
            onClick = onClick,
            onFocusChanged = {
                isFocused = it
                if (it) onFocused()
            },
        ) { _ ->
            // Performance: Removed SkeletonBox with infinite shimmer animation
            AsyncImage(
                model = imageRequest,
                contentDescription = content.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(ArvioSkin.colors.surface),
            )
        }

        // Only show title and year when focused
        if (visualFocused) {
            Spacer(modifier = Modifier.height(ArvioSkin.spacing.x1))

            Text(
                text = content.title,
                style = ArvioSkin.typography.caption,
                color = ArvioSkin.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Show year for MediaItem only
            val mediaItem = (content as? CardContent.Media)?.item
            if (mediaItem?.year?.isNotBlank() == true) {
                Text(
                    text = mediaItem.year,
                    style = ArvioSkin.typography.caption,
                    color = ArvioSkin.colors.textMuted.copy(alpha = 0.65f),
                )
            }
        }
    }
}

/**
 * Convenience wrapper for usage at call sites - use CardContent.Media and Channel directly
 */
