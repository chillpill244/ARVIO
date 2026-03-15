package com.arflix.tv.ui.screens.shared

import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaCategoryUiStateTest {

    private val sampleMovie1 = MediaItem(
        id = 1,
        title = "The Matrix",
        mediaType = MediaType.MOVIE
    )

    private val sampleMovie2 = MediaItem(
        id = 2,
        title = "Inception",
        mediaType = MediaType.MOVIE
    )

    private val sampleMovie3 = MediaItem(
        id = 3,
        title = "The Dark Knight",
        mediaType = MediaType.MOVIE
    )

    // ===== filteredAndSortedCategories tests =====

    @Test
    fun `filteredAndSortedCategories returns all categories when no search query`() {
        val state = MediaCategoryUiState(
            categories = listOf("Action", "Comedy", "Drama")
        )

        val result = state.filteredAndSortedCategories

        assertThat(result).hasSize(3)
        assertThat(result).containsExactly("Action", "Comedy", "Drama")
    }

    @Test
    fun `filteredAndSortedCategories filters by categorySearchQuery`() {
        val state = MediaCategoryUiState(
            categories = listOf("Action", "Comedy", "Drama", "Romance"),
            categorySearchQuery = "om"
        )

        val result = state.filteredAndSortedCategories

        // Should include "Comedy" + "Romance"
        assertThat(result).containsExactly("Comedy", "Romance")
    }

    @Test
    fun `filteredAndSortedCategories sorts favorites before non-favorites`() {
        val state = MediaCategoryUiState(
            categories = listOf("Action", "Comedy", "Drama", "Romance"),
            favoriteCategories = setOf("Drama", "Romance")
        )

        val result = state.filteredAndSortedCategories

        // Favorites sorted to front
        assertThat(result.take(2)).containsExactly("Drama", "Romance")
        assertThat(result.drop(2)).containsExactly("Action", "Comedy")
    }

    @Test
    fun `filteredAndSortedCategories is case insensitive`() {
        val state = MediaCategoryUiState(
            categories = listOf("Action", "Comedy", "Drama"),
            categorySearchQuery = "DRAMA"
        )

        val result = state.filteredAndSortedCategories

        assertThat(result).containsExactly("Drama")
    }

    @Test
    fun `filteredAndSortedCategories filters AND sorts correctly`() {
        val state = MediaCategoryUiState(
            categories = listOf("Action Movies", "Action Series", "Comedy", "Drama"),
            favoriteCategories = setOf("Action Series"),
            categorySearchQuery = "action"
        )

        val result = state.filteredAndSortedCategories

        // Action Series is favorite, should come first
        assertThat(result.first()).isEqualTo("Action Series")
        assertThat(result).containsExactly("Action Series", "Action Movies")
    }

    @Test
    fun `filteredAndSortedCategories returns empty for no matches`() {
        val state = MediaCategoryUiState(
            categories = listOf("Action", "Comedy", "Drama"),
            categorySearchQuery = "xyz"
        )

        val result = state.filteredAndSortedCategories

        assertThat(result).isEmpty()
    }

    // ===== displayedItems tests =====

    @Test
    fun `displayedItems returns items when no search query`() {
        val state = MediaCategoryUiState(
            items = listOf(sampleMovie1, sampleMovie2, sampleMovie3),
            itemSearchQuery = ""
        )

        val result = state.displayedItems

        assertThat(result).hasSize(3)
        assertThat(result).containsExactly(sampleMovie1, sampleMovie2, sampleMovie3)
    }

    @Test
    fun `displayedItems filters items by title when itemSearchQuery is set`() {
        val state = MediaCategoryUiState(
            items = listOf(sampleMovie1, sampleMovie2, sampleMovie3),
            itemSearchQuery = "the"
        )

        val result = state.displayedItems

        assertThat(result).hasSize(2)
        assertThat(result).containsExactly(sampleMovie1, sampleMovie3)
    }

    @Test
    fun `displayedItems is case-insensitive`() {
        val state = MediaCategoryUiState(
            items = listOf(sampleMovie1, sampleMovie2, sampleMovie3),
            itemSearchQuery = "MATRIX"
        )

        val result = state.displayedItems

        assertThat(result).hasSize(1)
        assertThat(result.first()).isEqualTo(sampleMovie1)
    }

    @Test
    fun `displayedItems returns empty for no matches`() {
        val state = MediaCategoryUiState(
            items = listOf(sampleMovie1, sampleMovie2, sampleMovie3),
            itemSearchQuery = "xyz"
        )

        val result = state.displayedItems

        assertThat(result).isEmpty()
    }

    @Test
    fun `displayedItems with blank search returns all`() {
        val state = MediaCategoryUiState(
            items = listOf(sampleMovie1, sampleMovie2),
            itemSearchQuery = "   "
        )

        val result = state.displayedItems

        assertThat(result).hasSize(2)
    }

    // ===== selectedCategory tests =====

    @Test
    fun `selectedCategory returns category at selectedCategoryIndex`() {
        val state = MediaCategoryUiState(
            categories = listOf("Action", "Comedy", "Drama"),
            selectedCategoryIndex = 1
        )

        assertThat(state.selectedCategory).isEqualTo("Comedy")
    }

    @Test
    fun `selectedCategory returns empty string when index is out of bounds`() {
        val state = MediaCategoryUiState(
            categories = listOf("Action", "Comedy"),
            selectedCategoryIndex = 5
        )

        assertThat(state.selectedCategory).isEmpty()
    }

    @Test
    fun `selectedCategory returns first category when index is 0`() {
        val state = MediaCategoryUiState(
            categories = listOf("Action", "Comedy", "Drama"),
            selectedCategoryIndex = 0
        )

        assertThat(state.selectedCategory).isEqualTo("Action")
    }

    @Test
    fun `selectedCategory respects filtering`() {
        val state = MediaCategoryUiState(
            categories = listOf("Action", "Comedy", "Drama"),
            categorySearchQuery = "co",
            selectedCategoryIndex = 0
        )

        // After filtering, only "Comedy" remains
        assertThat(state.selectedCategory).isEqualTo("Comedy")
    }

    // ===== Default state tests =====

    @Test
    fun `default state has empty collections and false loading states`() {
        val state = MediaCategoryUiState()

        assertThat(state.isLoading).isFalse()
        assertThat(state.categories).isEmpty()
        assertThat(state.items).isEmpty()
        assertThat(state.favoriteCategories).isEmpty()
        assertThat(state.categorySearchQuery).isEmpty()
        assertThat(state.itemSearchQuery).isEmpty()
        assertThat(state.error).isNull()
        assertThat(state.selectedCategoryIndex).isEqualTo(0)
    }

    @Test
    fun `selectedCategory returns empty for empty categories`() {
        val state = MediaCategoryUiState(categories = emptyList())

        assertThat(state.selectedCategory).isEmpty()
    }
}
