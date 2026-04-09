package com.arflix.tv.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring

/**
 * ARVIO Animation Constants — Apple Glass Motion Design
 * Ultra-smooth, fluid transitions with natural spring physics.
 * Inspired by Apple Vision Pro interaction patterns.
 */
object AnimationConstants {

    // ============================================
    // DURATION VALUES (Apple-style timing)
    // ============================================

    /** Fast micro-interactions (focus ring, color changes) */
    const val DURATION_FAST = 180

    /** Default transitions (scale, movement) */
    const val DURATION_NORMAL = 250

    /** Slower emphasis animations (hero changes, page transitions) */
    const val DURATION_EMPHASIS = 350

    /** Long decorative animations (Ken Burns, ambient effects) */
    const val DURATION_SLOW = 600

    /** Very long animations for background effects */
    const val DURATION_EXTRA_SLOW = 1200

    /** Ken Burns effect duration for hero backdrops */
    const val DURATION_KEN_BURNS = 20000

    /** Image crossfade duration */
    const val DURATION_IMAGE_CROSSFADE = 300

    /** Backdrop dissolve duration */
    const val DURATION_BACKDROP_DISSOLVE = 280

    // ============================================
    // STAGGER DELAYS
    // ============================================

    /** Delay between sequential card animations */
    const val STAGGER_CARD = 35

    /** Delay for section entrance animations */
    const val STAGGER_SECTION = 70

    // ============================================
    // SCALE VALUES (Subtle, refined for glass panels)
    // ============================================

    /** Default unfocused scale */
    const val SCALE_UNFOCUSED = 1.0f

    /** Focused card scale - subtle lift for glass panels */
    const val SCALE_FOCUSED = 1.04f

    /** Pressed/clicked scale */
    const val SCALE_PRESSED = 0.98f

    /** Hero logo pulsing scale */
    const val SCALE_PULSE_MIN = 1.0f
    const val SCALE_PULSE_MAX = 1.015f

    // ============================================
    // SPRING CONFIGURATIONS (Apple-style: fluid, minimal bounce)
    // ============================================

    /** Focus spring - smooth and refined, minimal bounce */
    const val SPRING_STIFFNESS_FOCUS = 350f
    const val SPRING_DAMPING_FOCUS = 0.82f

    /** Gentle spring for large movements */
    const val SPRING_STIFFNESS_GENTLE = 250f
    const val SPRING_DAMPING_GENTLE = 0.88f

    /** Tight spring for micro-interactions */
    const val SPRING_STIFFNESS_TIGHT = 450f
    const val SPRING_DAMPING_TIGHT = 0.9f

    /** Scroll spring for smooth deceleration */
    const val SPRING_STIFFNESS_SCROLL = 280f
    const val SPRING_DAMPING_SCROLL = 0.92f
    
    // ============================================
    // EASING CURVES (Apple-style: fast start, gentle landing)
    // ============================================
    
    /** Apple-style ease out — fast departure, soft arrival */
    val EaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    
    /** Apple-style fast out slow in */
    val FastOutSlowIn = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    
    /** Ease in out - symmetric, elegant */
    val EaseInOut = CubicBezierEasing(0.45f, 0.05f, 0.55f, 0.95f)
    
    /** Sharp ease - for quick snappy movements */
    val Sharp = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    
    /** Decelerate - for elements coming to rest */
    val Decelerate = CubicBezierEasing(0.0f, 0.0f, 0.1f, 1.0f)

    /** Smooth decelerate - for scroll stop */
    val SmoothDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    // ============================================
    // SHADOW & ELEVATION
    // ============================================

    /** Unfocused card elevation */
    const val ELEVATION_CARD_UNFOCUSED = 2

    /** Focused card elevation — subtle lift */
    const val ELEVATION_CARD_FOCUSED = 24

    /** Modal/overlay elevation */
    const val ELEVATION_MODAL = 40
    
    // ============================================
    // BORDER & GLOW (Glass design)
    // ============================================
    
    /** Focus ring width — thin luminous border */
    const val BORDER_FOCUS_WIDTH = 1.5f
    
    /** Glow blur radius for focus effect */
    const val GLOW_RADIUS_FOCUS = 12
    
    /** Ambient glow radius */
    const val GLOW_RADIUS_AMBIENT = 6
}
