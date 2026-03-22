package com.arflix.tv.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CardLayoutModeTest {

    // ===== normalizeCardLayoutMode() tests =====

    @Test
    fun `normalizeCardLayoutMode returns Poster for poster value`() {
        val result = normalizeCardLayoutMode("Poster")
        assertThat(result).isEqualTo(CARD_LAYOUT_MODE_POSTER)
    }

    @Test
    fun `normalizeCardLayoutMode is case insensitive for poster`() {
        assertThat(normalizeCardLayoutMode("poster")).isEqualTo(CARD_LAYOUT_MODE_POSTER)
        assertThat(normalizeCardLayoutMode("POSTER")).isEqualTo(CARD_LAYOUT_MODE_POSTER)
        assertThat(normalizeCardLayoutMode("PoStEr")).isEqualTo(CARD_LAYOUT_MODE_POSTER)
    }

    @Test
    fun `normalizeCardLayoutMode trims whitespace`() {
        val result = normalizeCardLayoutMode("  Poster  ")
        assertThat(result).isEqualTo(CARD_LAYOUT_MODE_POSTER)
    }

    @Test
    fun `normalizeCardLayoutMode returns Landscape for landscape value`() {
        val result = normalizeCardLayoutMode("Landscape")
        assertThat(result).isEqualTo(CARD_LAYOUT_MODE_LANDSCAPE)
    }

    @Test
    fun `normalizeCardLayoutMode returns Poster for null`() {
        val result = normalizeCardLayoutMode(null)
        assertThat(result).isEqualTo(CARD_LAYOUT_MODE_POSTER)
    }

    @Test
    fun `normalizeCardLayoutMode returns Poster for empty string`() {
        val result = normalizeCardLayoutMode("")
        assertThat(result).isEqualTo(CARD_LAYOUT_MODE_POSTER)
    }

    @Test
    fun `normalizeCardLayoutMode returns Poster for unknown value`() {
        val result = normalizeCardLayoutMode("unknown")
        assertThat(result).isEqualTo(CARD_LAYOUT_MODE_POSTER)
    }

    @Test
    fun `normalizeCardLayoutMode returns Poster for whitespace-only`() {
        val result = normalizeCardLayoutMode("   ")
        assertThat(result).isEqualTo(CARD_LAYOUT_MODE_POSTER)
    }

    // ===== parseCardLayoutMode() tests =====

    @Test
    fun `parseCardLayoutMode returns POSTER for poster string`() {
        val result = parseCardLayoutMode("Poster")
        assertThat(result).isEqualTo(CardLayoutMode.POSTER)
    }

    @Test
    fun `parseCardLayoutMode returns LANDSCAPE for landscape string`() {
        val result = parseCardLayoutMode("Landscape")
        assertThat(result).isEqualTo(CardLayoutMode.LANDSCAPE)
    }

    @Test
    fun `parseCardLayoutMode returns POSTER for null`() {
        val result = parseCardLayoutMode(null)
        assertThat(result).isEqualTo(CardLayoutMode.POSTER)
    }

    @Test
    fun `parseCardLayoutMode returns POSTER for unknown`() {
        val result = parseCardLayoutMode("grid")
        assertThat(result).isEqualTo(CardLayoutMode.POSTER)
    }

    @Test
    fun `parseCardLayoutMode handles case insensitive poster`() {
        assertThat(parseCardLayoutMode("poster")).isEqualTo(CardLayoutMode.POSTER)
        assertThat(parseCardLayoutMode("POSTER")).isEqualTo(CardLayoutMode.POSTER)
    }

    @Test
    fun `parseCardLayoutMode handles whitespace`() {
        val result = parseCardLayoutMode("  poster  ")
        assertThat(result).isEqualTo(CardLayoutMode.POSTER)
    }

    // ===== CardLayoutMode enum tests =====

    @Test
    fun `CardLayoutMode has LANDSCAPE value`() {
        assertThat(CardLayoutMode.LANDSCAPE).isNotNull()
    }

    @Test
    fun `CardLayoutMode has POSTER value`() {
        assertThat(CardLayoutMode.POSTER).isNotNull()
    }

    @Test
    fun `CardLayoutMode values count is 2`() {
        assertThat(CardLayoutMode.values()).hasLength(2)
    }

    // ===== Constants tests =====

    @Test
    fun `CARD_LAYOUT_MODE_LANDSCAPE constant is correct`() {
        assertThat(CARD_LAYOUT_MODE_LANDSCAPE).isEqualTo("Landscape")
    }

    @Test
    fun `CARD_LAYOUT_MODE_POSTER constant is correct`() {
        assertThat(CARD_LAYOUT_MODE_POSTER).isEqualTo("Poster")
    }
}
