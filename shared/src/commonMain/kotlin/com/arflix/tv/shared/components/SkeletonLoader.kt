package com.arflix.tv.shared.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items


/**
 * Skeleton card for media items (poster style)
 */
@Composable
fun SkeletonPosterCard(
    width: Dp = 140.dp,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.width(width)) {
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp),
            shape = RoundedCornerShape(4.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        SkeletonBox(
            modifier = Modifier
                .width(80.dp)
                .height(12.dp),
            shape = RoundedCornerShape(4.dp)
        )
    }
}

/**
 * Skeleton card for media items (landscape style)
 */
@Composable
fun SkeletonMediaCard(
    width: Dp = 220.dp,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.width(width)) {
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp),
            shape = RoundedCornerShape(4.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        SkeletonBox(
            modifier = Modifier
                .width(60.dp)
                .height(10.dp),
            shape = RoundedCornerShape(4.dp)
        )
    }
}

/**
 * Skeleton for cast member
 */
@Composable
fun SkeletonCastCard(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(100.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        SkeletonBox(
            modifier = Modifier.size(80.dp),
            shape = RoundedCornerShape(40.dp) // Circle
        )
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonBox(
            modifier = Modifier
                .width(70.dp)
                .height(12.dp),
            shape = RoundedCornerShape(4.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        SkeletonBox(
            modifier = Modifier
                .width(50.dp)
                .height(10.dp),
            shape = RoundedCornerShape(4.dp)
        )
    }
}

/**
 * Skeleton for episode card
 */
@Composable
fun SkeletonEpisodeCard(
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.width(220.dp)) {
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp),
            shape = RoundedCornerShape(4.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        SkeletonBox(
            modifier = Modifier
                .width(80.dp)
                .height(10.dp),
            shape = RoundedCornerShape(4.dp)
        )
    }
}

/**
 * Skeleton row for home screen category
 */
@Composable
fun SkeletonCategoryRow(
    cardCount: Int = 6,
    cardType: SkeletonCardType = SkeletonCardType.POSTER,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Title skeleton
        SkeletonBox(
            modifier = Modifier
                .width(150.dp)
                .height(20.dp),
            shape = RoundedCornerShape(4.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Cards row
        LazyRow(
            contentPadding = PaddingValues(end = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(cardCount) {
                when (cardType) {
                    SkeletonCardType.POSTER -> SkeletonPosterCard()
                    SkeletonCardType.MEDIA -> SkeletonMediaCard()
                    SkeletonCardType.EPISODE -> SkeletonEpisodeCard()
                    SkeletonCardType.CAST -> SkeletonCastCard()
                }
            }
        }
    }
}

/**
 * Skeleton for details page hero section
 */
@Composable
fun SkeletonDetailsHero(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp)
    ) {
        // Logo/Title
        SkeletonBox(
            modifier = Modifier
                .width(300.dp)
                .height(60.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Metadata pills
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) {
                SkeletonBox(
                    modifier = Modifier
                        .width(80.dp)
                        .height(28.dp),
                    shape = RoundedCornerShape(6.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Overview
        SkeletonBox(
            modifier = Modifier
                .width(500.dp)
                .height(60.dp),
            shape = RoundedCornerShape(4.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(5) {
                SkeletonBox(
                    modifier = Modifier
                        .width(100.dp)
                        .height(40.dp),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
    }
}

/**
 * Full details page skeleton
 */
@Composable
fun SkeletonDetailsPage(
    isTV: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(start = 24.dp)) {
        SkeletonDetailsHero()
        Spacer(modifier = Modifier.height(32.dp))

        if (isTV) {
            // Episodes section
            SkeletonCategoryRow(cardCount = 6, cardType = SkeletonCardType.EPISODE)
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Cast section
        SkeletonCategoryRow(cardCount = 8, cardType = SkeletonCardType.CAST)
        Spacer(modifier = Modifier.height(32.dp))

        // Similar section
        SkeletonCategoryRow(cardCount = 6, cardType = SkeletonCardType.POSTER)
    }
}

/**
 * Home page skeleton with multiple rows
 */
@Composable
fun SkeletonHomePage(
    rowCount: Int = 4,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(start = 24.dp, top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        repeat(rowCount) { index ->
            SkeletonCategoryRow(
                cardType = if (index == 0) SkeletonCardType.MEDIA else SkeletonCardType.POSTER
            )
        }
    }
}

enum class SkeletonCardType {
    POSTER, MEDIA, EPISODE, CAST
}
