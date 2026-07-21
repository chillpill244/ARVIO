package com.arflix.tv.shared.skin

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arflix.tv.shared.theme.JetBrainsSansFontFamily

@Immutable
data class ArvioColorTokens(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val accent: Color,
    val focusOutline: Color,
    val focusGradientStart: Color,
    val focusGradientEnd: Color,
    val tealAccent: Color,
    val watchedGreen: Color,      // Green checkmark for watched items (Arctic Fuse 2 style)
    val inProgressGrey: Color,    // Grey clock for in-progress items
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
        fun defaults(): ArvioSkinTokens {
            // Nuvio standard easing: cubic-bezier(0.2, 0, 0, 1)
            val easeOut: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

            return ArvioSkinTokens(
                colors = ArvioColorTokens(
                    background = Color(0xFF0D0D0D),     // Nuvio background
                    surface = Color(0xFF1A1A1A),        // Nuvio backgroundElevated
                    surfaceRaised = Color(0xFF222222),  // Nuvio backgroundCard (White palette)
                    textPrimary = Color(0xFFF5F7F8),    // Nuvio textPrimary
                    textMuted = Color(0xFF969CA3),      // Nuvio textMuted
                    accent = Color(0xFFF5F5F5),         // Nuvio White palette secondary
                    focusOutline = Color(0xFFFFFFFF),   // Nuvio White palette focusRing
                    focusGradientStart = Color(0xFFFFFFFF),
                    focusGradientEnd = Color(0xFFFFFFFF),
                    tealAccent = Color(0xFF00D9B5),  // Teal checkmark color
                    watchedGreen = Color(0xFF66BB6A),  // Nuvio success
                    inProgressGrey = Color(0xFF969CA3),  // Nuvio neutral/textMuted
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
                    sm = 8.dp,
                    md = 12.dp,
                    lg = 16.dp,
                ),
                typography = ArvioTypographyTokens(
                    heroTitle = TextStyle(
                        fontFamily = JetBrainsSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 50.sp,
                        letterSpacing = (-1.6).sp,
                        lineHeight = 56.sp,
                    ),
                    sectionTitle = TextStyle(
                        fontFamily = JetBrainsSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        letterSpacing = (-0.6).sp,
                        lineHeight = 26.sp,
                    ),
                    cardTitle = TextStyle(
                        fontFamily = JetBrainsSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        letterSpacing = 0.sp,
                        lineHeight = 20.sp,
                    ),
                    body = TextStyle(
                        fontFamily = JetBrainsSansFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        letterSpacing = 0.sp,
                        lineHeight = 20.sp,
                    ),
                    caption = TextStyle(
                        fontFamily = JetBrainsSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 0.sp,
                        lineHeight = 14.sp,
                    ),
                    badge = TextStyle(
                        fontFamily = JetBrainsSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp,
                        letterSpacing = 0.8.sp,
                        lineHeight = 12.sp,
                    ),
                    button = TextStyle(
                        fontFamily = JetBrainsSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        letterSpacing = 0.sp,
                        lineHeight = 20.sp,
                    ),
                ),
                motion = ArvioMotionTokens(
                    focusDurationMillis = 150,    // Nuvio fast
                    focusEasing = easeOut,
                    screenTransitionMillis = 220, // Nuvio normal
                    heroFadeMillis = 220,         // Nuvio normal
                ),
                focus = ArvioFocusTokens(
                    scaleFocused = 1.05f,  // Noticeable scale for TV viewing distance
                    scalePressed = 0.97f,
                    durationMillis = 120,  // Smooth but responsive animations
                    easing = easeOut,
                    outlineWidth = 3.dp,   // Prominent white border
                    glowWidth = 0.dp,      // No glow for performance
                    glowAlpha = 0f,        // No glow
                    translationZFocused = 8.dp,  // Visible lift effect
                ),
            )
        }
    }
}

