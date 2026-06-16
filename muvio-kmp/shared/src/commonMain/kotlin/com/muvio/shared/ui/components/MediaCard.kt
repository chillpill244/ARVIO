package com.muvio.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.muvio.shared.domain.MediaItem
import com.muvio.shared.domain.MediaType

private val CardPlaceholderBg = Color(0xFF1E1E1E)
private val AccentTeal = Color(0xFF00C8A0)
private val TextSecondary = Color(0xFF888888)

@Composable
fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
) {
    val aspectRatio = if (isLandscape) 16f / 9f else 2f / 3f
    val cardShape = RoundedCornerShape(8.dp)

    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(cardShape)
                .background(CardPlaceholderBg),
        ) {
            AsyncImage(
                model = item.image,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Gradient scrim: transparent at top, dark at bottom for title readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.50f to Color.Transparent,
                                0.72f to Color.Black.copy(alpha = 0.55f),
                                1.0f to Color.Black.copy(alpha = 0.88f),
                            ),
                        ),
                    ),
            )

            // Title at the bottom of the image
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )

            // Playback progress bar
            if (item.progress > 0) {
                LinearProgressIndicator(
                    progress = { item.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.BottomCenter),
                    color = AccentTeal,
                    trackColor = Color.White.copy(alpha = 0.2f),
                )
            }
        }

        // Subtitle below the image: type · year
        val typeLabel = when (item.mediaType) {
            MediaType.MOVIE -> "Movie"
            MediaType.TV -> "Series"
        }
        val year = item.year.takeIf { it.isNotEmpty() }
        val subtitleText = if (year != null) "$typeLabel · $year" else typeLabel
        Text(
            text = subtitleText,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 3.dp),
        )
    }
}
