package com.muvio.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.muvio.shared.domain.MediaItem
import com.muvio.shared.domain.MediaType

private val AccentTeal = Color(0xFF00C8A0)
private val SurfaceBg = Color(0xFF1A1A1A)
private val CardShapeCw = RoundedCornerShape(10.dp)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextMuted = Color(0xFF888888)

@Composable
fun ContinueWatchingCard(
    item: MediaItem,
    progress: Float = 0f,
    episodeInfo: String? = null,
    width: Dp = 200.dp,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.width(width).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(CardShapeCw)
                .background(SurfaceBg),
        ) {
            val imageUrl = item.backdrop?.takeIf { it.isNotBlank() } ?: item.image.takeIf { it.isNotBlank() }
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                            startY = 80f,
                        ),
                    ),
            )

            // Type badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(
                    text = if (item.mediaType == MediaType.TV) "TV" else "MOVIE",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(AccentTeal),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp),
                )
            }

            if (progress > 0f) {
                Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.2f)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxSize()
                            .background(AccentTeal),
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = item.title,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        val meta = episodeInfo ?: item.year.takeIf { it.isNotBlank() }
        if (meta != null) {
            Text(
                text = meta,
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}
