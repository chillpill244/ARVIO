package com.arflix.tv.ui.skin

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arflix.tv.ui.theme.InterFontFamily

@Immutable
data class ArvioColorTokens(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val glassSurface: Color,
    val glassSurfaceHover: Color,
    val glassBorder: Color,
    val glassBorderFocus: Color,
    val glassInnerHighlight: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val accent: Color,
    val focusOutline: Color,
    val focusGradientStart: Color,
    val focusGradientEnd: Color,
    val focusGlow: Color,
    val tealAccent: Color,
    val watchedGreen: Color,
    val inProgressGrey: Color,
)

@Immutable
data class ArvioSpacingTokens(
    val x1: Dp,
    val x2: Dp,
    val x3: Dp,
    val x4: Dp,
    val x6: Dp,
    val x8: Dp,
    val x12: Dp,
    val x16: Dp,
)

@Immutable
data class ArvioRadiusTokens(
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
)

@Immutable
data class ArvioTypographyTokens(
    val heroTitle: TextStyle,
    val sectionTitle: TextStyle,
    val cardTitle: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
    val badge: TextStyle,
    val button: TextStyle,
)

@Immutable
data class ArvioMotionTokens(
    val focusDurationMillis: Int,
    val focusEasing: Easing,
    val screenTransitionMillis: Int,
    val heroFadeMillis: Int,
    val springDampingFocus: Float,
    val springStiffnessFocus: Float,
    val springDampingGentle: Float,
    val springStiffnessGentle: Float,
)

@Immutable
data class ArvioFocusTokens(
    val scaleFocused: Float,
    val scalePressed: Float,
    val durationMillis: Int,
    val easing: Easing,
    val outlineWidth: Dp,
    val glowWidth: Dp,
    val glowAlpha: Float,
    val translationZFocused: Dp,
)

@Immutable
data class ArvioSkinTokens(
    val colors: ArvioColorTokens,
    val spacing: ArvioSpacingTokens,
    val radius: ArvioRadiusTokens,
    val typography: ArvioTypographyTokens,
    val motion: ArvioMotionTokens,
    val focus: ArvioFocusTokens,
) {
    companion object {
        /**
         * Apple Glass design tokens — translucent depth, luminous edges,
         * ultra-smooth spring physics.
         */
        fun defaults(): ArvioSkinTokens {
            // Apple-style ease-out: fast start, gentle deceleration
            val easeOut: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

            return ArvioSkinTokens(
                colors = ArvioColorTokens(
                    background = Color(0xFF000000),
                    surface = Color(0xFF111114),
                    surfaceRaised = Color(0xFF1C1C1E),
                    glassSurface = Color(0x26FFFFFF),         // 15% white frosted glass
                    glassSurfaceHover = Color(0x33FFFFFF),    // 20% white — hovered glass
                    glassBorder = Color(0x26FFFFFF),           // 15% white luminous edge
                    glassBorderFocus = Color(0x66FFFFFF),      // 40% white — focus edge
                    glassInnerHighlight = Color(0x0DFFFFFF),   // 5% specular highlight
                    textPrimary = Color(0xFFF5F5F7),
                    textMuted = Color(0xB3F5F5F7),
                    accent = Color(0xFFF5F5F7),
                    focusOutline = Color(0xCCFFFFFF),         // 80% white — softer than pure white
                    focusGradientStart = Color(0xFFFFFFFF),
                    focusGradientEnd = Color(0xB3FFFFFF),
                    focusGlow = Color(0x33FFFFFF),             // 20% white ambient glow
                    tealAccent = Color(0xFF64D2FF),           // Apple system teal
                    watchedGreen = Color(0xFF30D158),          // Apple system green
                    inProgressGrey = Color(0xFF636366),        // Apple system gray 3
                ),
                spacing = ArvioSpacingTokens(
                    x1 = 4.dp,
                    x2 = 8.dp,
                    x3 = 12.dp,
                    x4 = 16.dp,
                    x6 = 24.dp,
                    x8 = 32.dp,
                    x12 = 48.dp,
                    x16 = 64.dp,
                ),
                radius = ArvioRadiusTokens(
                    sm = 10.dp,   // Slightly larger for glass panels
                    md = 14.dp,
                    lg = 20.dp,
                    xl = 28.dp,   // Extra-large for modals / sheets
                ),
                typography = ArvioTypographyTokens(
                    heroTitle = TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,       // Bold instead of Black for elegance
                        fontSize = 48.sp,
                        letterSpacing = (-0.5).sp,
                        lineHeight = 54.sp,
                    ),
                    sectionTitle = TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        letterSpacing = 0.2.sp,
                        lineHeight = 26.sp,
                    ),
                    cardTitle = TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,     // Lighter weight for glass aesthetic
                        fontSize = 15.sp,
                        letterSpacing = 0.sp,
                        lineHeight = 20.sp,
                    ),
                    body = TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        letterSpacing = 0.sp,
                        lineHeight = 20.sp,
                    ),
                    caption = TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        letterSpacing = 0.3.sp,
                        lineHeight = 14.sp,
                    ),
                    badge = TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp,
                        letterSpacing = 0.4.sp,
                        lineHeight = 12.sp,
                    ),
                    button = TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        letterSpacing = 0.2.sp,
                        lineHeight = 20.sp,
                    ),
                ),
                motion = ArvioMotionTokens(
                    focusDurationMillis = 180,             // Slightly longer for silky smooth
                    focusEasing = easeOut,
                    screenTransitionMillis = 200,
                    heroFadeMillis = 280,                  // Slower dissolve for cinematic feel
                    springDampingFocus = 0.82f,            // Less bounce — refined Apple feel
                    springStiffnessFocus = 350f,           // Lower stiffness — fluid motion
                    springDampingGentle = 0.88f,
                    springStiffnessGentle = 250f,
                ),
                focus = ArvioFocusTokens(
                    scaleFocused = 1.04f,                  // Subtle scale — glass panels don't "jump"
                    scalePressed = 0.98f,
                    durationMillis = 180,
                    easing = easeOut,
                    outlineWidth = 1.5.dp,                 // Thin luminous border, not thick ring
                    glowWidth = 12.dp,                     // Soft ambient glow around focused items
                    glowAlpha = 0.15f,                     // Subtle glow
                    translationZFocused = 6.dp,
                ),
            )
        }
    }
}

