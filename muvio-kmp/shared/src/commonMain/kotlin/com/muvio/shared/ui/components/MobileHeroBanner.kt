package com.muvio.shared.ui.components

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private val BgDark = Color(0xFF0A0A0A)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFAAAAAA)
private val AccentTeal = Color(0xFF00C8A0)

private val movieGenreMap = mapOf(
    28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy",
    80 to "Crime", 18 to "Drama", 14 to "Fantasy", 27 to "Horror",
    9648 to "Mystery", 10749 to "Romance", 878 to "Sci-Fi", 53 to "Thriller",
    10752 to "War", 37 to "Western",
)
private val tvGenreMap = mapOf(
    10759 to "Action & Adventure", 16 to "Animation", 35 to "Comedy",
    80 to "Crime", 18 to "Drama", 9648 to "Mystery",
    10765 to "Sci-Fi & Fantasy", 37 to "Western",
)

/**
 * Full-bleed hero banner for the home screen. Displays backdrop, gradient scrim,
 * metadata, and Play / More Info action buttons.
 */
@Composable
fun MobileHeroBanner(
    item: MediaItem,
    onPlayClick: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val genreText = remember(item.id, item.genreIds, item.mediaType) {
        val map = if (item.mediaType == MediaType.TV) tvGenreMap else movieGenreMap
        item.genreIds.mapNotNull { map[it] }.take(2).joinToString(" · ")
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 14f),
    ) {
        AsyncImage(
            model = item.backdrop ?: item.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.45f),
                            0.18f to Color.Transparent,
                            0.52f to Color.Black.copy(alpha = 0.35f),
                            0.78f to Color.Black.copy(alpha = 0.80f),
                            1.0f to BgDark,
                        ),
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "MUVIO",
                color = AccentTeal,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                letterSpacing = 3.sp,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Text(
                text = item.title,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                lineHeight = 30.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(6.dp))

            val metaParts = buildList {
                if (item.year.isNotEmpty()) add(item.year)
                if (genreText.isNotEmpty()) add(genreText)
                if (item.tmdbRating.isNotEmpty()) add("★ ${item.tmdbRating}")
            }
            if (metaParts.isNotEmpty()) {
                Text(
                    text = metaParts.joinToString("  ·  "),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (item.overview.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = item.overview,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPlayClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TextPrimary,
                        contentColor = Color.Black,
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Play", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }

                OutlinedButton(
                    onClick = onInfoClick,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("More Info", fontSize = 15.sp)
                }
            }
        }
    }
}
