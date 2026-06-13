package com.arflix.tv.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ARVIO Color Palette
 * Arctic Fuse 2 Inspired - Minimal Dark Theme
 */

// ============================================
// MAIN COLORS (Nuvio textPrimary #F5F7F8)
// ============================================
val ArcticWhite = Color(0xFFF5F7F8)          // Nuvio textPrimary
val ArcticWhite90 = Color(0xE7F5F7F8)        // 90% opacity
val ArcticWhite70 = Color(0xB3F5F7F8)        // 70% opacity
val ArcticWhite50 = Color(0x80F5F7F8)        // 50% opacity
val ArcticWhite30 = Color(0x4DF5F7F8)        // 30% opacity
val ArcticWhite12 = Color(0x1FF5F7F8)        // 12% opacity

val ArcticBlack = Color(0xFF000000)          // Main background #000000
val ArcticBlack90 = Color(0xE7000000)        // 90% opacity
val ArcticBlack70 = Color(0xB3000000)        // 70% opacity
val ArcticBlack50 = Color(0x80000000)        // 50% opacity
val ArcticBlack30 = Color(0x4D000000)        // 30% opacity
val ArcticBlack12 = Color(0x1F000000)        // 12% opacity

val ArcticGray = Color(0xFF4D4D4D)           // Soft gray accent
val ArcticGrayLight = Color(0xFFB3B3B3)      // Logo/subtle elements

// ============================================
// ACCENT COLORS
// ============================================
val AccentWhite = Color(0xFFFFFFFF)          // Pure white for focus
val AccentYellow = Color(0xFFFFCD3C)         // Star ratings
val AccentGreen = Color(0xFF00D588)          // "New episode" badges

// Legacy aliases for compatibility
val PrimeBlue = ArcticWhite
val PrimeBlueDark = ArcticGray
val PrimeBlueLight = AccentWhite
val PrimeBlueGlow = Color(0x33FFFFFF)
val PrimeGreen = AccentGreen
val RankNumberColor = ArcticWhite70

val PurplePrimary = ArcticWhite
val PurpleLight = AccentWhite
val PurpleDark = ArcticGray
val PurpleDeep = ArcticBlack
val PurpleGlow = Color(0x33FFFFFF)
val PurpleSoft = ArcticWhite70

val Cyan = ArcticWhite
val CyanDark = ArcticGray
val CyanGlow = Color(0x33FFFFFF)

val Purple = ArcticWhite
val PurpleAccent = ArcticWhite

val Pink = AccentWhite
val PinkDark = ArcticGray
val PinkGlow = Color(0x33FFFFFF)

// Gradient combinations (minimal)
val GradientStart = Color(0xFF0D0D0D)
val GradientMiddle = Color(0xFF0D0D0D)
val GradientEnd = Color(0xFF0D0D0D)

// ============================================
// BACKGROUND COLORS (Nuvio palette)
// ============================================
val BackgroundDark = Color(0xFF0D0D0D)        // Nuvio background
val BackgroundCard = Color(0xFF1A1A1A)        // Nuvio backgroundElevated
val BackgroundElevated = Color(0xFF222222)    // Nuvio backgroundCard (White palette)
val BackgroundOverlay = BackgroundDark.copy(alpha = 0.90f)
val BackgroundGlass = BackgroundDark.copy(alpha = 0.60f)

// Gradient backgrounds
val BackgroundGradientStart = BackgroundDark
val BackgroundGradientCenter = BackgroundDark
val BackgroundGradientMiddle = BackgroundDark
val BackgroundGradientEnd = BackgroundDark

// ============================================
// SURFACE COLORS
// ============================================
val SurfaceDark = BackgroundDark
val SurfaceVariant = Color(0xFF1A1A1A)
val SurfaceGlass = Color(0x4D000000)

// ============================================
// TEXT COLORS (Nuvio text tokens)
// ============================================
val TextPrimary = ArcticWhite                 // Nuvio textPrimary #F5F7F8
val TextSecondary = Color(0xFFB8BEC5)         // Nuvio textSecondary
val TextTertiary = Color(0xFF969CA3)          // Nuvio textMuted
val TextDisabled = ArcticWhite30              // 30% opacity

// ============================================
// BORDER COLORS (Nuvio border tokens)
// ============================================
val BorderLight = Color(0x8C252A2A)           // Nuvio borderSubtle (55%)
val BorderMedium = Color(0xFF252A2A)          // Nuvio borderDefault
val BorderGradient = Color(0xFF3A4040)        // Nuvio borderStrong

// ============================================
// STATUS COLORS (Nuvio status tokens)
// ============================================
val SuccessGreen = Color(0xFF66BB6A)          // Nuvio success
val ErrorRed = Color(0xFFE36A8A)              // Nuvio danger
val WarningOrange = Color(0xFFFFC857)         // Nuvio warning
val InfoBlue = Color(0xFF42A5F5)              // Nuvio info
val OngoingBlue = Color(0xFF42A5F5)           // Nuvio info

// ============================================
// SPECIAL COLORS
// ============================================
val ImdbYellow = AccentYellow                 // Star ratings
val AccentRed = Color(0xFFE53935)

// ============================================
// FOCUS & GLOW STATES (Kodi Inspired)
// ============================================
val KodiMagenta = Color(0xFFFC1C8E)           // Pink focus indicator
val KodiPurple = Color(0xFFB64BFF)            // Purple card border
val FocusRing = AccentWhite                   // Arctic Fuse 2 default: white focus
val FocusGlow = AccentWhite.copy(alpha = 0.20f)
val FocusShadowColor = Color(0x40000000)
val FocusGradientStart = AccentWhite
val FocusGradientEnd = ArcticWhite90

// ============================================
// PARTICLE/EFFECT COLORS
// ============================================
val ParticleCyan = ArcticWhite30
val ParticlePurple = ArcticWhite12
val ParticlePink = ArcticWhite30
val ParticlePurpleLight = ArcticWhite50
val ParticlePurpleDark = ArcticBlack50

// ============================================
// LEGACY ALIASES
// ============================================
val ArvioAccent = ArcticWhite
val ArvioPurple = ArcticBlack
val ArvioLight = ArcticWhite70

