package com.arflix.tv.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ARVIO Color Palette
 * Apple Glass Design — translucent depth, luminous borders, frosted surfaces
 */

// ============================================
// ARCTIC FUSE 2 MAIN COLORS
// ============================================
val ArcticWhite = Color(0xFFF5F5F7)          // Apple-style near-white foreground
val ArcticWhite90 = Color(0xE7F5F5F7)        // 90% opacity
val ArcticWhite70 = Color(0xB3F5F5F7)        // 70% opacity
val ArcticWhite50 = Color(0x80F5F5F7)        // 50% opacity
val ArcticWhite30 = Color(0x4DF5F5F7)        // 30% opacity
val ArcticWhite12 = Color(0x1FF5F5F7)        // 12% opacity

val ArcticBlack = Color(0xFF000000)          // Main background #000000
val ArcticBlack90 = Color(0xE7000000)        // 90% opacity
val ArcticBlack70 = Color(0xB3000000)        // 70% opacity
val ArcticBlack50 = Color(0x80000000)        // 50% opacity
val ArcticBlack30 = Color(0x4D000000)        // 30% opacity
val ArcticBlack12 = Color(0x1F000000)        // 12% opacity

val ArcticGray = Color(0xFF48484A)           // Apple system gray
val ArcticGrayLight = Color(0xFFAEAEB2)      // Apple secondary label

// ============================================
// ACCENT COLORS
// ============================================
val AccentWhite = Color(0xFFFFFFFF)          // Pure white for focus
val AccentYellow = Color(0xFFFFD60A)         // Apple system yellow
val AccentGreen = Color(0xFF30D158)          // Apple system green

// ============================================
// GLASS / FROSTED SURFACE COLORS
// ============================================
val GlassSurfaceThin = Color(0x1AFFFFFF)     // 10% white — ultra-thin glass
val GlassSurfaceRegular = Color(0x26FFFFFF)  // 15% white — standard glass panel
val GlassSurfaceThick = Color(0x33FFFFFF)    // 20% white — elevated glass
val GlassSurfaceUltra = Color(0x40FFFFFF)    // 25% white — prominent glass (modals)
val GlassBorderLight = Color(0x26FFFFFF)     // 15% white luminous edge
val GlassBorderMedium = Color(0x40FFFFFF)    // 25% white — stronger edge on focus
val GlassBorderFocus = Color(0x66FFFFFF)     // 40% white — focused glass edge
val GlassInnerLight = Color(0x0DFFFFFF)      // 5% white — inner specular highlight

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
val GradientStart = Color(0xFF08090A)
val GradientMiddle = Color(0xFF08090A)
val GradientEnd = Color(0xFF08090A)

// ============================================
// BACKGROUND COLORS (App Background)
// ============================================
val BackgroundDark = Color(0xFF08090A)        // #08090A
val BackgroundCard = Color(0xFF111114)        // Slightly elevated, warmer
val BackgroundElevated = Color(0xFF1C1C1E)   // Apple elevated surface
val BackgroundOverlay = Color(0xE5080808)     // 90% dark overlay
val BackgroundGlass = Color(0x99080808)       // 60% — frosted dark glass

// Gradient backgrounds
val BackgroundGradientStart = BackgroundDark
val BackgroundGradientCenter = BackgroundDark
val BackgroundGradientMiddle = BackgroundDark
val BackgroundGradientEnd = BackgroundDark

// ============================================
// SURFACE COLORS
// ============================================
val SurfaceDark = BackgroundDark
val SurfaceVariant = Color(0xFF111114)
val SurfaceGlass = GlassSurfaceRegular

// ============================================
// TEXT COLORS (Apple-style near-white)
// ============================================
val TextPrimary = ArcticWhite                 // #F5F5F7
val TextSecondary = ArcticWhite70             // 70% opacity
val TextTertiary = ArcticWhite50              // 50% opacity
val TextDisabled = ArcticWhite30              // 30% opacity

// ============================================
// BORDER COLORS
// ============================================
val BorderLight = GlassBorderLight            // Glass luminous edge
val BorderMedium = GlassBorderMedium          // Stronger glass edge
val BorderGradient = GlassBorderFocus         // Focus-level edge

// ============================================
// STATUS COLORS (Apple system colors)
// ============================================
val SuccessGreen = AccentGreen
val ErrorRed = Color(0xFFFF453A)             // Apple system red
val WarningOrange = Color(0xFFFF9F0A)        // Apple system orange
val InfoBlue = Color(0xFF0A84FF)             // Apple system blue
val OngoingBlue = Color(0xFF0A84FF)

// ============================================
// SPECIAL COLORS
// ============================================
val ImdbYellow = AccentYellow                 // Star ratings
val AccentRed = Color(0xFFFF453A)

// ============================================
// FOCUS & GLOW STATES (Glass Design)
// ============================================
val KodiMagenta = Color(0xFFFC1C8E)           // Pink focus indicator
val KodiPurple = Color(0xFFB64BFF)            // Purple card border
val FocusRing = AccentWhite                   // Pure white focus
val FocusGlow = Color(0x33FFFFFF)             // 20% white glow
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

