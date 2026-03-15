package com.arflix.tv.ui.screens.watchlist

import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WatchlistUiStateTest {

    private val sampleMovie = MediaItem(
        id = 1,
        title = "Test Movie",
        mediaType = MediaType.MOVIE
    )

    private val sampleSeries = MediaItem(
        id = 2,
        title = "Test Series",
        mediaType = MediaType.TV
    )

    private val sampleLiveTv = MediaItem(
        id = 3,
        title = "Test Channel",
        mediaType = MediaType.LIVE_TV
    )

    // ===== Default state tests =====

    @Test
    fun `default state is loading`() {
        val state = WatchlistUiState()
        assertThat(state.isLoading).isTrue()
    }

    @Test
    fun `default state has empty lists`() {
        val state = WatchlistUiState()
        assertThat(state.movies).isEmpty()
        assertThat(state.series).isEmpty()
        assertThat(state.liveTv).isEmpty()
    }

    @Test
    fun `default state has no error`() {
        val state = WatchlistUiState()
        assertThat(state.error).isNull()
    }

    @Test
    fun `default toast is INFO type`() {
        val state = WatchlistUiState()
        assertThat(state.toastType).isEqualTo(ToastType.INFO)
    }

    @Test
    fun `default toast message is null`() {
        val state = WatchlistUiState()
        assertThat(state.toastMessage).isNull()
    }

    // ===== items extension property tests =====

    @Test
    fun `items combines all lists`() {
        val state = WatchlistUiState(
            movies = listOf(sampleMovie),
            series = listOf(sampleSeries),
            liveTv = listOf(sampleLiveTv)
        )

        assertThat(state.items).hasSize(3)
        assertThat(state.items).containsExactly(sampleMovie, sampleSeries, sampleLiveTv)
    }

    @Test
    fun `items is empty when all lists are empty`() {
        val state = WatchlistUiState(
            movies = emptyList(),
            series = emptyList(),
            liveTv = emptyList()
        )

        assertThat(state.items).isEmpty()
    }

    @Test
    fun `items contains only movies when series and liveTv are empty`() {
        val state = WatchlistUiState(
            movies = listOf(sampleMovie),
            series = emptyList(),
            liveTv = emptyList()
        )

        assertThat(state.items).hasSize(1)
        assertThat(state.items.first()).isEqualTo(sampleMovie)
    }

    @Test
    fun `items order is movies then series then liveTv`() {
        val state = WatchlistUiState(
            movies = listOf(sampleMovie),
            series = listOf(sampleSeries),
            liveTv = listOf(sampleLiveTv)
        )

        assertThat(state.items[0]).isEqualTo(sampleMovie)
        assertThat(state.items[1]).isEqualTo(sampleSeries)
        assertThat(state.items[2]).isEqualTo(sampleLiveTv)
    }

    // ===== State copy tests =====

    @Test
    fun `copy with loading preserves lists`() {
        val original = WatchlistUiState(
            isLoading = true,
            movies = listOf(sampleMovie),
            series = listOf(sampleSeries)
        )

        val copied = original.copy(isLoading = false)

        assertThat(copied.isLoading).isFalse()
        assertThat(copied.movies).hasSize(1)
        assertThat(copied.series).hasSize(1)
    }

    @Test
    fun `copy with error preserves loading state`() {
        val original = WatchlistUiState(isLoading = false)

        val copied = original.copy(error = "Network error")

        assertThat(copied.error).isEqualTo("Network error")
        assertThat(copied.isLoading).isFalse()
    }

    @Test
    fun `copy can update toast message and type`() {
        val original = WatchlistUiState()

        val copied = original.copy(
            toastMessage = "Item removed",
            toastType = ToastType.SUCCESS
        )

        assertThat(copied.toastMessage).isEqualTo("Item removed")
        assertThat(copied.toastType).isEqualTo(ToastType.SUCCESS)
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
        val state1 = WatchlistUiState(
            isLoading = false,
            movies = listOf(sampleMovie)
        )
        val state2 = WatchlistUiState(
            isLoading = false,
            movies = listOf(sampleMovie)
        )

        assertThat(state1).isEqualTo(state2)
    }

    @Test
    fun `different movie lists are not equal`() {
        val state1 = WatchlistUiState(movies = listOf(sampleMovie))
        val state2 = WatchlistUiState(movies = emptyList())

        assertThat(state1).isNotEqualTo(state2)
    }
}
