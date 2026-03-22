package com.arflix.tv.ui.screens.home

import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeUiStateTest {

    private val sampleMovie = MediaItem(
        id = 1,
        title = "Test Movie",
        mediaType = MediaType.MOVIE
    )

    private val sampleCategory = Category(
        id = "action",
        title = "Action",
        items = listOf(sampleMovie)
    )

    // ===== Default state tests =====

    @Test
    fun `default state is not loading`() {
        val state = HomeUiState()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `default state is initial load`() {
        val state = HomeUiState()
        assertThat(state.isInitialLoad).isTrue()
    }

    @Test
    fun `default state has empty categories`() {
        val state = HomeUiState()
        assertThat(state.categories).isEmpty()
    }

    @Test
    fun `default state has no error`() {
        val state = HomeUiState()
        assertThat(state.error).isNull()
    }

    @Test
    fun `default state has no hero item`() {
        val state = HomeUiState()
        assertThat(state.heroItem).isNull()
        assertThat(state.heroLogoUrl).isNull()
    }

    @Test
    fun `default state has no previous hero`() {
        val state = HomeUiState()
        assertThat(state.previousHeroItem).isNull()
        assertThat(state.previousHeroLogoUrl).isNull()
    }

    @Test
    fun `default state is not transitioning`() {
        val state = HomeUiState()
        assertThat(state.isHeroTransitioning).isFalse()
    }

    @Test
    fun `default state is not authenticated`() {
        val state = HomeUiState()
        assertThat(state.isAuthenticated).isFalse()
    }

    @Test
    fun `default toast type is INFO`() {
        val state = HomeUiState()
        assertThat(state.toastType).isEqualTo(ToastType.INFO)
    }

    @Test
    fun `default toast message is null`() {
        val state = HomeUiState()
        assertThat(state.toastMessage).isNull()
    }

    @Test
    fun `default cardLogoUrls is empty`() {
        val state = HomeUiState()
        assertThat(state.cardLogoUrls).isEmpty()
    }

    // ===== State copy tests =====

    @Test
    fun `copy with loading preserves categories`() {
        val original = HomeUiState(categories = listOf(sampleCategory))

        val copied = original.copy(isLoading = true)

        assertThat(copied.isLoading).isTrue()
        assertThat(copied.categories).hasSize(1)
    }

    @Test
    fun `copy with hero item`() {
        val original = HomeUiState()

        val copied = original.copy(
            heroItem = sampleMovie,
            heroLogoUrl = "https://example.com/logo.png"
        )

        assertThat(copied.heroItem).isEqualTo(sampleMovie)
        assertThat(copied.heroLogoUrl).isEqualTo("https://example.com/logo.png")
    }

    @Test
    fun `copy for hero transition`() {
        val newHero = MediaItem(id = 2, title = "New Hero", mediaType = MediaType.MOVIE)
        val original = HomeUiState(
            heroItem = sampleMovie,
            heroLogoUrl = "https://example.com/old.png"
        )

        val copied = original.copy(
            previousHeroItem = sampleMovie,
            previousHeroLogoUrl = "https://example.com/old.png",
            heroItem = newHero,
            heroLogoUrl = "https://example.com/new.png",
            isHeroTransitioning = true
        )

        assertThat(copied.previousHeroItem).isEqualTo(sampleMovie)
        assertThat(copied.heroItem).isEqualTo(newHero)
        assertThat(copied.isHeroTransitioning).isTrue()
    }

    @Test
    fun `copy with authentication state`() {
        val original = HomeUiState(isAuthenticated = false)

        val copied = original.copy(isAuthenticated = true)

        assertThat(copied.isAuthenticated).isTrue()
    }

    @Test
    fun `copy with toast message`() {
        val original = HomeUiState()

        val copied = original.copy(
            toastMessage = "Added to watchlist",
            toastType = ToastType.SUCCESS
        )

        assertThat(copied.toastMessage).isEqualTo("Added to watchlist")
        assertThat(copied.toastType).isEqualTo(ToastType.SUCCESS)
    }

    @Test
    fun `copy with error clears toast`() {
        val original = HomeUiState(
            toastMessage = "Previous message",
            toastType = ToastType.SUCCESS
        )

        val copied = original.copy(
            error = "Network error",
            toastMessage = null
        )

        assertThat(copied.error).isEqualTo("Network error")
        assertThat(copied.toastMessage).isNull()
    }

    @Test
    fun `copy with cardLogoUrls`() {
        val original = HomeUiState()

        val logoUrls = mapOf(
            "movie_1" to "https://example.com/logo1.png",
            "tv_2" to "https://example.com/logo2.png"
        )
        val copied = original.copy(cardLogoUrls = logoUrls)

        assertThat(copied.cardLogoUrls).hasSize(2)
        assertThat(copied.cardLogoUrls["movie_1"]).isEqualTo("https://example.com/logo1.png")
    }

    @Test
    fun `copy with multiple categories`() {
        val category2 = Category(
            id = "comedy",
            title = "Comedy",
            items = listOf(sampleMovie)
        )
        val original = HomeUiState()

        val copied = original.copy(
            categories = listOf(sampleCategory, category2),
            isInitialLoad = false
        )

        assertThat(copied.categories).hasSize(2)
        assertThat(copied.isInitialLoad).isFalse()
    }

    // ===== ToastType enum tests =====

    @Test
    fun `ToastType has SUCCESS value`() {
        assertThat(ToastType.SUCCESS).isNotNull()
    }

    @Test
    fun `ToastType has ERROR value`() {
        assertThat(ToastType.ERROR).isNotNull()
    }

    @Test
    fun `ToastType has INFO value`() {
        assertThat(ToastType.INFO).isNotNull()
    }

    @Test
    fun `ToastType values count is 3`() {
        assertThat(ToastType.values()).hasLength(3)
    }

    // ===== State equality tests =====

    @Test
    fun `same state values are equal`() {
        val state1 = HomeUiState(
            isLoading = true,
            heroItem = sampleMovie
        )
        val state2 = HomeUiState(
            isLoading = true,
            heroItem = sampleMovie
        )

        assertThat(state1).isEqualTo(state2)
    }

    @Test
    fun `different hero items are not equal`() {
        val state1 = HomeUiState(heroItem = sampleMovie)
        val state2 = HomeUiState(heroItem = null)

        assertThat(state1).isNotEqualTo(state2)
    }

    @Test
    fun `different loading states are not equal`() {
        val state1 = HomeUiState(isLoading = true)
        val state2 = HomeUiState(isLoading = false)

        assertThat(state1).isNotEqualTo(state2)
    }

    // ===== Complex state scenarios =====

    @Test
    fun `fully populated state`() {
        val state = HomeUiState(
            isLoading = false,
            isInitialLoad = false,
            categories = listOf(sampleCategory),
            heroItem = sampleMovie,
            heroLogoUrl = "https://example.com/hero.png",
            previousHeroItem = null,
            isHeroTransitioning = false,
            isAuthenticated = true,
            cardLogoUrls = mapOf("key" to "value"),
            toastMessage = null,
            toastType = ToastType.INFO,
            error = null
        )

        assertThat(state.isLoading).isFalse()
        assertThat(state.isInitialLoad).isFalse()
        assertThat(state.categories).hasSize(1)
        assertThat(state.heroItem).isEqualTo(sampleMovie)
        assertThat(state.isAuthenticated).isTrue()
    }

    @Test
    fun `loading state with partial data`() {
        val state = HomeUiState(
            isLoading = true,
            isInitialLoad = false,
            categories = listOf(sampleCategory),
            heroItem = sampleMovie
        )

        assertThat(state.isLoading).isTrue()
        assertThat(state.isInitialLoad).isFalse()
        assertThat(state.categories).isNotEmpty()
        assertThat(state.heroItem).isNotNull()
    }
}
