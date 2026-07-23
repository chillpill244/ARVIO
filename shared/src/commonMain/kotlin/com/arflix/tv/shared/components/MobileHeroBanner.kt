package com.arflix.tv.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.text.font.FontWeight
import com.arflix.tv.shared.theme.JetBrainsSansFontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision

private val BannerShape = RoundedCornerShape(24.dp)
private val CardBorder = Color(0xFF2B2B2B)
private val ImdbYellow = Color(0xFFF5C518)
private val BottomScrim = Brush.verticalGradient(
    colorStops = arrayOf(
        0.00f to Color.Transparent,
        0.25f to Color.Transparent,
        0.48f to Color.Black.copy(alpha = 0.40f),
        0.66f to Color.Black.copy(alpha = 0.80f),
        0.82f to Color.Black.copy(alpha = 0.94f),
        1.00f to Color(0xCC000000)
    )
)

/**
 * Netflix-style immersive mobile hero banner.
 *
 * Displays a full-bleed poster image with a layered dark scrim, a large title,
 * genre tags, and a metadata row showing the release year and IMDb rating.
 * No action buttons.
 *
 * @param imageUrl  URL of the backdrop / poster image.
 * @param title     Primary title shown as the visual anchor.
 * @param genres    List of genre strings displayed as a bullet-separated row.
 * @param year      Release year string (e.g. "2024"). Pass empty to hide.
 * @param rating    IMDb rating string (e.g. "8.7"). Pass empty to hide.
 * @param modifier  Modifier applied to the card's outermost Box.
 */
@Composable
fun MobileHeroBanner(
    imageUrl: String,
    title: String,
    genres: List<String>,
    year: String = "",
    rating: String = "",
    logoUrl: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalPlatformContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .shadow(elevation = 8.dp, shape = BannerShape, clip = false)
            .clip(BannerShape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .border(width = 1.dp, color = CardBorder, shape = BannerShape)
    ) {
        // ── Layer 1: Full-bleed background image ────────────────────────────
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .precision(Precision.INEXACT)
                
                
                .build(),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // ── Layer 2: Bottom scrim spanning the lower 65% of the card ────────
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .align(Alignment.BottomCenter)
                .background(BottomScrim)
        )

        // ── Layer 3: Content overlay — pinned to bottom-center ───────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Element A — Logo image when available, otherwise large title text
            if (logoUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(logoUrl)
                        .precision(Precision.INEXACT)
                        
                        
                        .build(),
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
                    modifier = Modifier
                        .height(56.dp)
                        .fillMaxWidth()
                )
            } else {
                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = JetBrainsSansFontFamily,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.1).sp,
                    lineHeight = 42.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Element B — Genre tags with bullet separators
            BannerGenres(genres = genres)

            // Element C — Year and IMDb rating
            BannerMeta(year = year, rating = rating)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private sub-composables
// ─────────────────────────────────────────────────────────────────────────────

/** Year + IMDb rating row, matching the hero carousel metadata style. */
@Composable
private fun BannerMeta(year: String, rating: String) {
    val hasYear = year.isNotEmpty()
    val hasRating = rating.isNotEmpty()
    if (!hasYear && !hasRating) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (hasYear) {
            Text(
                text = year,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        if (hasRating) {
            // IMDb logo pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(ImdbYellow)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "IMDb",
                    color = Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.3.sp
                )
            }
            Text(
                text = rating,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** Bullet-separated genre string rendered in muted gray. */
@Composable
private fun BannerGenres(genres: List<String>) {
    if (genres.isEmpty()) return
    Text(
        text = genres.joinToString("  •  "),
        color = Color.LightGray,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Center,
        lineHeight = 17.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}
