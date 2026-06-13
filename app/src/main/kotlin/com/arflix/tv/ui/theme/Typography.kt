package com.arflix.tv.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Arflix typography - TV-optimized text styles (scaled for 1080p TV)
 */
object ArflixTypography {
    
    // Hero title (large display) - JetBrains Sans, Nuvio display style (Bold, tight tracking)
    val heroTitle = TextStyle(
        fontFamily = JetBrainsSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        letterSpacing = (-1.5).sp,
        lineHeight = 52.sp
    )

    // Section headers - JetBrains Sans, Nuvio headline style (SemiBold, tight tracking)
    val sectionTitle = TextStyle(
        fontFamily = JetBrainsSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = (-0.6).sp,
        lineHeight = 26.sp
    )

    // Card titles - JetBrains Sans, Nuvio title style (SemiBold, neutral tracking)
    val cardTitle = TextStyle(
        fontFamily = JetBrainsSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 0.sp,
        lineHeight = 20.sp
    )
    
    // Body text - JetBrains Sans, Nuvio bodyMd style
    val body = TextStyle(
        fontFamily = JetBrainsSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.sp,
        lineHeight = 20.sp
    )

    // Body large (for hero overview) - JetBrains Sans, Nuvio bodyLg style
    val bodyLarge = TextStyle(
        fontFamily = JetBrainsSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        letterSpacing = 0.sp,
        lineHeight = 24.sp
    )

    // Caption / small text - JetBrains Sans, Nuvio labelXs style
    val caption = TextStyle(
        fontFamily = JetBrainsSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.sp,
        lineHeight = 14.sp
    )

    // Label (metadata pills) - JetBrains Sans, Nuvio labelMedium style (wide tracking)
    val label = TextStyle(
        fontFamily = JetBrainsSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
        lineHeight = 16.sp
    )

    // Button text - JetBrains Sans, Nuvio labelLarge style
    val button = TextStyle(
        fontFamily = JetBrainsSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.sp,
        lineHeight = 20.sp
    )

    // Clock display - JetBrains Sans, Nuvio title style
    val clock = TextStyle(
        fontFamily = JetBrainsSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        letterSpacing = 0.sp,
        lineHeight = 30.sp
    )

    // Episode number badge - JetBrains Sans, Nuvio label tracking
    val badge = TextStyle(
        fontFamily = JetBrainsSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        letterSpacing = 0.8.sp,
        lineHeight = 12.sp
    )
}

