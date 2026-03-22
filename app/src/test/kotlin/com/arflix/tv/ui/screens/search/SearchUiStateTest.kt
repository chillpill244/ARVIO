package com.arflix.tv.ui.screens.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchUiStateTest {

    // ===== Default state tests =====

    @Test
    fun `default state has empty query`() {
        val state = SearchUiState()
        assertThat(state.query).isEmpty()
    }

    @Test
    fun `default state is not loading`() {
        val state = SearchUiState()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `default state has empty results`() {
        val state = SearchUiState()
        assertThat(state.results).isEmpty()
        assertThat(state.movieResults).isEmpty()
        assertThat(state.tvResults).isEmpty()
        assertThat(state.livetvResults).isEmpty()
    }

    @Test
    fun `default state has no error`() {
        val state = SearchUiState()
        assertThat(state.error).isNull()
    }

    @Test
    fun `default state has empty cardLogoUrls`() {
        val state = SearchUiState()
        assertThat(state.cardLogoUrls).isEmpty()
    }

    // ===== State copy tests =====

    @Test
    fun `copy with query preserves other fields`() {
        val original = SearchUiState(
            query = "test",
            isLoading = true,
            error = "some error"
        )

        val copied = original.copy(query = "new query")

        assertThat(copied.query).isEqualTo("new query")
        assertThat(copied.isLoading).isTrue()
        assertThat(copied.error).isEqualTo("some error")
    }

    @Test
    fun `copy with isLoading preserves query`() {
        val original = SearchUiState(query = "my search")

        val copied = original.copy(isLoading = true)

        assertThat(copied.query).isEqualTo("my search")
        assertThat(copied.isLoading).isTrue()
    }

    @Test
    fun `copy can clear error`() {
        val original = SearchUiState(error = "Previous error")

        val copied = original.copy(error = null)

        assertThat(copied.error).isNull()
    }

    // ===== State equality tests =====

    @Test
    fun `same state values are equal`() {
        val state1 = SearchUiState(query = "test", isLoading = true)
        val state2 = SearchUiState(query = "test", isLoading = true)

        assertThat(state1).isEqualTo(state2)
    }

    @Test
    fun `different query values are not equal`() {
        val state1 = SearchUiState(query = "test1")
        val state2 = SearchUiState(query = "test2")

        assertThat(state1).isNotEqualTo(state2)
    }

    @Test
    fun `different loading states are not equal`() {
        val state1 = SearchUiState(isLoading = true)
        val state2 = SearchUiState(isLoading = false)

        assertThat(state1).isNotEqualTo(state2)
    }
}
