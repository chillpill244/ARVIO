package com.arflix.tv.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.arflix.tv.data.model.Profile
import com.arflix.tv.ui.skin.ArvioSkin
import com.arflix.tv.ui.theme.AnimationConstants
import com.arflix.tv.ui.theme.TextSecondary

/**
 * Premium navigation sidebar with glass morphism design
 * Translucent frosted panel with luminous focus indicators
 */
enum class SidebarItem(val icon: ImageVector, val label: String) {
    SEARCH(Icons.Outlined.Search, "Search"),
    HOME(Icons.Outlined.Home, "Home"),
    WATCHLIST(Icons.Outlined.Bookmark, "Watchlist"),
    TV(Icons.Outlined.LiveTv, "Content"),
    SETTINGS(Icons.Outlined.Settings, "Settings")
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun Sidebar(
    selectedItem: SidebarItem = SidebarItem.HOME,
    isSidebarFocused: Boolean = false,
    focusedIndex: Int = 1,
    profile: Profile? = null,
    onProfileClick: () -> Unit = {},
    onItemSelected: (SidebarItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val centerItems = listOf(SidebarItem.SEARCH, SidebarItem.HOME, SidebarItem.WATCHLIST, SidebarItem.TV)
    val bottomItem = SidebarItem.SETTINGS
    val hasProfile = profile != null
    // With profile: index 0 = profile, 1..(centerItems.size) = center items, centerItems.size+1 = settings.
    // Without: 0..(centerItems.size-1) = center items, centerItems.size = settings.
    val centerFocusedIndex = if (hasProfile) focusedIndex - 1 else focusedIndex
    val settingsFocused = centerFocusedIndex == centerItems.size

    // Glass sidebar: translucent frosted panel with luminous right edge
    Box(
        modifier = modifier
            .width(56.dp)
            .fillMaxHeight()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.50f),
                        Color.Black.copy(alpha = 0.25f),
                        Color.Transparent
                    )
                )
            )
    ) {
        // Subtle luminous right edge
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(0.5.dp)
                .fillMaxHeight(0.7f)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile at top
            if (hasProfile) {
                Spacer(modifier = Modifier.height(4.dp))
                SidebarProfileAvatar(
                    profile = profile!!,
                    isFocused = isSidebarFocused && focusedIndex == 0
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Flexible space - pushes center group down
            Spacer(modifier = Modifier.weight(1f))

            // Center group: Search, Home, Watchlist, TV (vertically centered)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                centerItems.forEachIndexed { index, item ->
                    SidebarIcon(
                        item = item,
                        isSelected = item == selectedItem,
                        isFocused = isSidebarFocused && index == centerFocusedIndex,
                    )
                }
            }

            // Flexible space - pushes settings to bottom
            Spacer(modifier = Modifier.weight(1f))

            // Settings at bottom
            SidebarIcon(
                item = bottomItem,
                isSelected = bottomItem == selectedItem,
                isFocused = isSidebarFocused && settingsFocused,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SidebarProfileAvatar(
    profile: Profile,
    isFocused: Boolean
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.12f else 1f,
        animationSpec = tween(
            durationMillis = AnimationConstants.DURATION_FAST,
            easing = AnimationConstants.EaseOut
        ),
        label = "profile_scale"
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(
            durationMillis = AnimationConstants.DURATION_FAST,
            easing = AnimationConstants.EaseOut
        ),
        label = "profile_indicator"
    )
    Box(
        modifier = Modifier
            .width(42.dp)
            .height(34.dp),
        contentAlignment = Alignment.Center
    ) {
        if (indicatorAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(ArvioSkin.colors.focusOutline.copy(alpha = indicatorAlpha))
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    if (profile.avatarId > 0) {
                        val (c1, c2) = AvatarRegistry.gradientColors(profile.avatarId)
                        Brush.verticalGradient(listOf(c1, c2))
                    } else {
                        Brush.linearGradient(listOf(Color(profile.avatarColor), Color(profile.avatarColor)))
                    }
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center
        ) {
            if (profile.avatarId > 0) {
                AvatarIcon(
                    avatarId = profile.avatarId,
                    modifier = Modifier.size(30.dp).padding(3.dp)
                )
            } else {
                Text(
                    text = profile.name.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SidebarIcon(
    item: SidebarItem,
    isSelected: Boolean,
    isFocused: Boolean,
) {
    // Glass icon color — soft glow when focused
    val iconColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isSelected -> Color.White.copy(alpha = 0.55f)
            else -> Color.White.copy(alpha = 0.30f)
        },
        animationSpec = spring(
            dampingRatio = AnimationConstants.SPRING_DAMPING_FOCUS,
            stiffness = AnimationConstants.SPRING_STIFFNESS_FOCUS
        ),
        label = "icon_color"
    )

    // Subtle spring scale
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.10f else 1f,
        animationSpec = spring(
            dampingRatio = AnimationConstants.SPRING_DAMPING_FOCUS,
            stiffness = AnimationConstants.SPRING_STIFFNESS_FOCUS
        ),
        label = "icon_scale"
    )

    // Glass chip background
    val chipBackground by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White.copy(alpha = 0.14f)
            isSelected -> Color.White.copy(alpha = 0.06f)
            else -> Color.Transparent
        },
        animationSpec = tween(
            durationMillis = AnimationConstants.DURATION_FAST,
            easing = AnimationConstants.EaseOut
        ),
        label = "sidebar_chip_bg"
    )

    val indicatorAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = spring(
            dampingRatio = AnimationConstants.SPRING_DAMPING_FOCUS,
            stiffness = AnimationConstants.SPRING_STIFFNESS_FOCUS
        ),
        label = "sidebar_indicator_alpha"
    )

    Box(
        modifier = Modifier
            .width(42.dp)
            .height(34.dp),
        contentAlignment = Alignment.Center
    ) {
        // Luminous focus indicator line
        if (indicatorAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(2.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = indicatorAlpha * 0.9f))
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(chipBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = iconColor,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }
    }
}


