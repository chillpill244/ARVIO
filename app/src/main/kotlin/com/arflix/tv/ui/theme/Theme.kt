package com.arflix.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.Typography
import com.arflix.tv.shared.skin.LocalAccentColorOverride
import com.arflix.tv.shared.skin.ProvideArvioSkin
import com.arflix.tv.shared.skin.accentColorFromName
import com.arflix.tv.shared.theme.LocalOledBlackBackground
import com.arflix.tv.shared.theme.LocalArvioColors
import com.arflix.tv.shared.theme.ArvioColors
import com.arflix.tv.shared.theme.BackgroundDark
import com.arflix.tv.shared.theme.ArcticWhite
import com.arflix.tv.shared.theme.ArcticBlack
import com.arflix.tv.shared.theme.ArcticGray
import com.arflix.tv.shared.theme.ArcticWhite70
import com.arflix.tv.shared.theme.AccentWhite
import com.arflix.tv.shared.theme.TextPrimary
import com.arflix.tv.shared.theme.BackgroundCard
import com.arflix.tv.shared.theme.SurfaceVariant
import com.arflix.tv.shared.theme.TextSecondary
import com.arflix.tv.shared.theme.ErrorRed
import com.arflix.tv.shared.theme.BorderLight
import com.arflix.tv.shared.theme.JetBrainsSansFontFamily
import androidx.compose.ui.text.font.FontWeight

// Keep legacy aliases for compatibility
val LocalArflixColors = LocalArvioColors



private val ArvioTvTypography: Typography = Typography().let { base ->
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ArflixTvTheme(
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
        border = BorderLight
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
                typography = ArvioTvTypography,
                content = content
            )
        }
    }
}
