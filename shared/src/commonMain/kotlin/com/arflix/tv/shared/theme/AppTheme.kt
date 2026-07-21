package com.arflix.tv.shared.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.arflix.tv.shared.skin.LocalAccentColorOverride
import com.arflix.tv.shared.skin.ProvideArvioSkin
import com.arflix.tv.shared.skin.accentColorFromName

/**
 * ARVIO Color scheme holder - Arctic Fuse 2 inspired
 */
data class ArvioColors(
    val arcticWhite: Color = ArcticWhite,
    val arcticWhite90: Color = ArcticWhite90,
    val arcticWhite70: Color = ArcticWhite70,
    val arcticWhite50: Color = ArcticWhite50,
    val arcticBlack: Color = ArcticBlack,
    val arcticGray: Color = ArcticGray,
    
    val cyan: Color = ArcticWhite,
    val cyanDark: Color = ArcticGray,
    val cyanGlow: Color = FocusGlow,
    val purple: Color = ArcticWhite,
    val purpleDark: Color = ArcticGray,
    val purpleGlow: Color = FocusGlow,
    val pink: Color = AccentWhite,
    val pinkDark: Color = ArcticGray,
    val pinkGlow: Color = FocusGlow,

    val backgroundDark: Color = BackgroundDark,
    val backgroundCard: Color = BackgroundCard,
    val backgroundElevated: Color = BackgroundElevated,
    val backgroundGlass: Color = BackgroundGlass,

    val textPrimary: Color = TextPrimary,
    val textSecondary: Color = TextSecondary,
    val textTertiary: Color = TextTertiary,

    val borderLight: Color = BorderLight,
    val borderGradient: Color = BorderGradient,

    val success: Color = SuccessGreen,
    val error: Color = ErrorRed,
    val warning: Color = WarningOrange,
    val info: Color = InfoBlue,

    val imdbYellow: Color = ImdbYellow,
    val accentRed: Color = AccentRed,

    val focusRing: Color = FocusRing,
    val focusGlow: Color = FocusGlow,

    val particleCyan: Color = ParticleCyan,
    val particlePurple: Color = ParticlePurple,
    val particlePink: Color = ParticlePink
)

val LocalArvioColors = staticCompositionLocalOf { ArvioColors() }
val LocalOledBlackBackground = staticCompositionLocalOf { false }

@Composable
fun appBackgroundDark(): Color {
    return if (LocalOledBlackBackground.current) Color.Black else BackgroundDark
}

val LocalArflixColors = LocalArvioColors

val ArvioTypography: Typography = Typography().let { base ->
        val fontFamily = JetBrainsSansFontFamily
        Typography(
            displayLarge = base.displayLarge.copy(fontFamily = fontFamily, fontWeight = FontWeight.Bold),
            displayMedium = base.displayMedium.copy(fontFamily = fontFamily, fontWeight = FontWeight.Bold),
            displaySmall = base.displaySmall.copy(fontFamily = fontFamily, fontWeight = FontWeight.Bold),
            headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold),
            headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold),
            headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold),
            titleLarge = base.titleLarge.copy(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold),
            titleMedium = base.titleMedium.copy(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold),
            titleSmall = base.titleSmall.copy(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold),
            bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily, fontWeight = FontWeight.Normal),
            bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily, fontWeight = FontWeight.Normal),
            bodySmall = base.bodySmall.copy(fontFamily = fontFamily, fontWeight = FontWeight.Normal),
            labelLarge = base.labelLarge.copy(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold),
            labelMedium = base.labelMedium.copy(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold),
            labelSmall = base.labelSmall.copy(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold),
        )
    }

@Composable
fun AppTheme(
    oledBlackBackground: Boolean = false,
    accentColorName: String? = null,
    content: @Composable () -> Unit
) {
    val backgroundDark = if (oledBlackBackground) Color.Black else BackgroundDark
    val accentColor = accentColorName?.let { accentColorFromName(it) }
    val colorScheme = darkColorScheme(
        primary = ArcticWhite,
        onPrimary = ArcticBlack,
        primaryContainer = ArcticGray,
        onPrimaryContainer = ArcticWhite,
        secondary = ArcticWhite70,
        onSecondary = ArcticBlack,
        secondaryContainer = ArcticGray,
        onSecondaryContainer = ArcticWhite,
        tertiary = AccentWhite,
        onTertiary = ArcticBlack,
        tertiaryContainer = ArcticGray,
        onTertiaryContainer = ArcticWhite,
        background = backgroundDark,
        onBackground = TextPrimary,
        surface = BackgroundCard,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceVariant,
        onSurfaceVariant = TextSecondary,
        error = ErrorRed,
        onError = ArcticWhite,
        errorContainer = ErrorRed,
        onErrorContainer = ArcticWhite,
        outline = BorderLight
    )

    val arvioColors = ArvioColors(backgroundDark = backgroundDark)

    CompositionLocalProvider(
        LocalArvioColors provides arvioColors,
        LocalOledBlackBackground provides oledBlackBackground,
        LocalAccentColorOverride provides accentColor
    ) {
        ProvideArvioSkin {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = ArvioTypography,
                content = content
            )
        }
    }
}
