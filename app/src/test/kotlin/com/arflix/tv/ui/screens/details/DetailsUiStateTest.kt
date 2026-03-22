package com.arflix.tv.ui.screens.details

import com.arflix.tv.data.model.CastMember
import com.arflix.tv.data.model.Episode
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.model.StreamSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DetailsUiStateTest {

    private val sampleMovie = MediaItem(
        id = 1,
        title = "Test Movie",
        mediaType = MediaType.MOVIE
    )

    private val sampleEpisode = Episode(
        id = 1,
        episodeNumber = 1,
        seasonNumber = 1,
        name = "Pilot"
    )

    private val sampleCast = CastMember(
        id = 1,
        name = "John Doe",
        character = "Hero",
        profilePath = "/path.jpg"
    )

    // ===== Default state tests =====

    @Test
    fun `default state is loading`() {
        val state = DetailsUiState()
        assertThat(state.isLoading).isTrue()
    }

    @Test
    fun `default state has no item`() {
        val state = DetailsUiState()
        assertThat(state.item).isNull()
    }

    @Test
    fun `default state has empty episodes`() {
        val state = DetailsUiState()
        assertThat(state.episodes).isEmpty()
    }

    @Test
    fun `default state has empty cast`() {
        val state = DetailsUiState()
        assertThat(state.cast).isEmpty()
    }

    @Test
    fun `default state has empty streams`() {
        val state = DetailsUiState()
        assertThat(state.streams).isEmpty()
    }

    @Test
    fun `default state has no error`() {
        val state = DetailsUiState()
        assertThat(state.error).isNull()
    }

    @Test
    fun `default state is not in watchlist`() {
        val state = DetailsUiState()
        assertThat(state.isInWatchlist).isFalse()
    }

    @Test
    fun `default toast type is INFO`() {
        val state = DetailsUiState()
        assertThat(state.toastType).isEqualTo(ToastType.INFO)
    }

    @Test
    fun `default currentSeason is 1`() {
        val state = DetailsUiState()
        assertThat(state.currentSeason).isEqualTo(1)
    }

    @Test
    fun `default totalSeasons is 1`() {
        val state = DetailsUiState()
        assertThat(state.totalSeasons).isEqualTo(1)
    }

    @Test
    fun `default autoPlaySingleSource is true`() {
        val state = DetailsUiState()
        assertThat(state.autoPlaySingleSource).isTrue()
    }

    @Test
    fun `default autoPlayMinQuality is Any`() {
        val state = DetailsUiState()
        assertThat(state.autoPlayMinQuality).isEqualTo("Any")
    }

    // ===== State copy tests =====

    @Test
    fun `copy with item preserves loading state`() {
        val original = DetailsUiState(isLoading = false)

        val copied = original.copy(item = sampleMovie)

        assertThat(copied.item).isEqualTo(sampleMovie)
        assertThat(copied.isLoading).isFalse()
    }

    @Test
    fun `copy with episodes preserves item`() {
        val original = DetailsUiState(item = sampleMovie)

        val copied = original.copy(episodes = listOf(sampleEpisode))

        assertThat(copied.episodes).hasSize(1)
        assertThat(copied.item).isEqualTo(sampleMovie)
    }

    @Test
    fun `copy with cast and similar`() {
        val original = DetailsUiState()

        val copied = original.copy(
            cast = listOf(sampleCast),
            similar = listOf(sampleMovie)
        )

        assertThat(copied.cast).hasSize(1)
        assertThat(copied.similar).hasSize(1)
    }

    @Test
    fun `copy can update watchlist status`() {
        val original = DetailsUiState(isInWatchlist = false)

        val copied = original.copy(isInWatchlist = true)

        assertThat(copied.isInWatchlist).isTrue()
    }

    @Test
    fun `copy can set error`() {
        val original = DetailsUiState(isLoading = true)

        val copied = original.copy(
            isLoading = false,
            error = "Failed to load"
        )

        assertThat(copied.isLoading).isFalse()
        assertThat(copied.error).isEqualTo("Failed to load")
    }

    @Test
    fun `copy can update toast`() {
        val original = DetailsUiState()

        val copied = original.copy(
            toastMessage = "Added to watchlist",
            toastType = ToastType.SUCCESS
        )

        assertThat(copied.toastMessage).isEqualTo("Added to watchlist")
        assertThat(copied.toastType).isEqualTo(ToastType.SUCCESS)
    }

    @Test
    fun `copy can update season info`() {
        val original = DetailsUiState()

        val copied = original.copy(
            currentSeason = 3,
            totalSeasons = 5,
            availableSeasons = listOf(2, 3, 5)
        )

        assertThat(copied.currentSeason).isEqualTo(3)
        assertThat(copied.totalSeasons).isEqualTo(5)
        assertThat(copied.availableSeasons).containsExactly(2, 3, 5)
    }

    @Test
    fun `copy can update season progress`() {
        val original = DetailsUiState()

        val progress = mapOf(
            1 to Pair(5, 10),
            2 to Pair(3, 8)
        )
        val copied = original.copy(seasonProgress = progress)

        assertThat(copied.seasonProgress).hasSize(2)
        assertThat(copied.seasonProgress[1]).isEqualTo(Pair(5, 10))
        assertThat(copied.seasonProgress[2]).isEqualTo(Pair(3, 8))
    }

    @Test
    fun `copy can update play info`() {
        val original = DetailsUiState()

        val copied = original.copy(
            playSeason = 2,
            playEpisode = 5,
            playLabel = "Continue S2 E5",
            playPositionMs = 150000L
        )

        assertThat(copied.playSeason).isEqualTo(2)
        assertThat(copied.playEpisode).isEqualTo(5)
        assertThat(copied.playLabel).isEqualTo("Continue S2 E5")
        assertThat(copied.playPositionMs).isEqualTo(150000L)
    }

    @Test
    fun `copy can update genres and language`() {
        val original = DetailsUiState()

        val copied = original.copy(
            genres = listOf("Action", "Sci-Fi"),
            language = "English"
        )

        assertThat(copied.genres).containsExactly("Action", "Sci-Fi")
        assertThat(copied.language).isEqualTo("English")
    }

    @Test
    fun `copy can update IDs`() {
        val original = DetailsUiState()

        val copied = original.copy(
            imdbId = "tt1234567",
            tvdbId = 12345
        )

        assertThat(copied.imdbId).isEqualTo("tt1234567")
        assertThat(copied.tvdbId).isEqualTo(12345)
    }

    @Test
    fun `copy can update person modal state`() {
        val original = DetailsUiState()

        val copied = original.copy(
            showPersonModal = true,
            isLoadingPerson = true
        )

        assertThat(copied.showPersonModal).isTrue()
        assertThat(copied.isLoadingPerson).isTrue()
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

    // ===== State equality tests =====

    @Test
    fun `same state values are equal`() {
        val state1 = DetailsUiState(item = sampleMovie, isLoading = false)
        val state2 = DetailsUiState(item = sampleMovie, isLoading = false)

        assertThat(state1).isEqualTo(state2)
    }

    @Test
    fun `different items are not equal`() {
        val state1 = DetailsUiState(item = sampleMovie)
        val state2 = DetailsUiState(item = null)

        assertThat(state1).isNotEqualTo(state2)
    }

    @Test
    fun `different loading states are not equal`() {
        val state1 = DetailsUiState(isLoading = true)
        val state2 = DetailsUiState(isLoading = false)

        assertThat(state1).isNotEqualTo(state2)
    }

    // ===== Complex state scenarios =====

    @Test
    fun `fully populated state`() {
        val state = DetailsUiState(
            isLoading = false,
            item = sampleMovie,
            imdbId = "tt1234567",
            tvdbId = 12345,
            logoUrl = "https://example.com/logo.png",
            trailerKey = "abc123",
            episodes = listOf(sampleEpisode),
            totalSeasons = 5,
            currentSeason = 2,
            availableSeasons = listOf(1, 2, 3, 4, 5),
            cast = listOf(sampleCast),
            similar = listOf(sampleMovie),
            isInWatchlist = true,
            genres = listOf("Action", "Drama"),
            language = "English",
            budget = "$100M",
            seasonProgress = mapOf(1 to Pair(10, 10), 2 to Pair(5, 12)),
            playSeason = 2,
            playEpisode = 6,
            playLabel = "Continue S2 E6",
            playPositionMs = 300000L
        )

        assertThat(state.isLoading).isFalse()
        assertThat(state.item).isEqualTo(sampleMovie)
        assertThat(state.episodes).hasSize(1)
        assertThat(state.cast).hasSize(1)
        assertThat(state.isInWatchlist).isTrue()
        assertThat(state.genres).hasSize(2)
        assertThat(state.playSeason).isEqualTo(2)
    }
}
