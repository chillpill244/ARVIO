package com.arflix.tv.util

import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class MediaBadgesTest {

    // ===== isInCinema() tests =====

    @Test
    fun `isInCinema returns true for movie released within 60 days`() {
        val today = LocalDate.of(2026, 3, 12)
        val releaseDate = today.minusDays(30).toString()
        val movie = MediaItem(
            id = 1,
            title = "Test Movie",
            mediaType = MediaType.MOVIE,
            releaseDate = releaseDate
        )

        val result = isInCinema(movie, now = today)

        assertThat(result).isTrue()
    }

    @Test
    fun `isInCinema returns false for movie released more than 60 days ago`() {
        val today = LocalDate.of(2026, 3, 12)
        val releaseDate = today.minusDays(61).toString()
        val movie = MediaItem(
            id = 1,
            title = "Old Movie",
            mediaType = MediaType.MOVIE,
            releaseDate = releaseDate
        )

        val result = isInCinema(movie, now = today)

        assertThat(result).isFalse()
    }

    @Test
    fun `isInCinema returns false for future release`() {
        val today = LocalDate.of(2026, 3, 12)
        val releaseDate = today.plusDays(10).toString()
        val movie = MediaItem(
            id = 1,
            title = "Future Movie",
            mediaType = MediaType.MOVIE,
            releaseDate = releaseDate
        )

        val result = isInCinema(movie, now = today)

        assertThat(result).isFalse()
    }

    @Test
    fun `isInCinema returns false for TV shows`() {
        val today = LocalDate.of(2026, 3, 12)
        val releaseDate = today.minusDays(10).toString()
        val tvShow = MediaItem(
            id = 1,
            title = "Test Show",
            mediaType = MediaType.TV,
            releaseDate = releaseDate
        )

        val result = isInCinema(tvShow, now = today)

        assertThat(result).isFalse()
    }

    @Test
    fun `isInCinema returns false when releaseDate is null`() {
        val movie = MediaItem(
            id = 1,
            title = "No Date Movie",
            mediaType = MediaType.MOVIE,
            releaseDate = null
        )

        val result = isInCinema(movie)

        assertThat(result).isFalse()
    }

    @Test
    fun `isInCinema returns false when releaseDate is blank`() {
        val movie = MediaItem(
            id = 1,
            title = "Blank Date Movie",
            mediaType = MediaType.MOVIE,
            releaseDate = "  "
        )

        val result = isInCinema(movie)

        assertThat(result).isFalse()
    }

    @Test
    fun `isInCinema returns false for invalid date format`() {
        val movie = MediaItem(
            id = 1,
            title = "Invalid Date Movie",
            mediaType = MediaType.MOVIE,
            releaseDate = "not-a-date"
        )

        val result = isInCinema(movie)

        assertThat(result).isFalse()
    }

    @Test
    fun `isInCinema returns true for movie released exactly today`() {
        val today = LocalDate.of(2026, 3, 12)
        val releaseDate = today.toString()
        val movie = MediaItem(
            id = 1,
            title = "Today Movie",
            mediaType = MediaType.MOVIE,
            releaseDate = releaseDate
        )

        val result = isInCinema(movie, now = today)

        assertThat(result).isTrue()
    }

    @Test
    fun `isInCinema returns true for movie released exactly 60 days ago`() {
        val today = LocalDate.of(2026, 3, 12)
        val releaseDate = today.minusDays(60).toString()
        val movie = MediaItem(
            id = 1,
            title = "60 Days Movie",
            mediaType = MediaType.MOVIE,
            releaseDate = releaseDate
        )

        val result = isInCinema(movie, now = today)

        assertThat(result).isTrue()
    }

    // ===== parseRatingValue() tests =====

    @Test
    fun `parseRatingValue parses integer rating`() {
        val result = parseRatingValue("8")
        assertThat(result).isEqualTo(8f)
    }

    @Test
    fun `parseRatingValue parses decimal rating with dot`() {
        val result = parseRatingValue("7.5")
        assertThat(result).isEqualTo(7.5f)
    }

    @Test
    fun `parseRatingValue parses decimal rating with comma`() {
        val result = parseRatingValue("8,3")
        assertThat(result).isEqualTo(8.3f)
    }

    @Test
    fun `parseRatingValue trims whitespace`() {
        val result = parseRatingValue("  9.1  ")
        assertThat(result).isEqualTo(9.1f)
    }

    @Test
    fun `parseRatingValue returns 0 for blank string`() {
        val result = parseRatingValue("")
        assertThat(result).isEqualTo(0f)
    }

    @Test
    fun `parseRatingValue returns 0 for whitespace-only string`() {
        val result = parseRatingValue("   ")
        assertThat(result).isEqualTo(0f)
    }

    @Test
    fun `parseRatingValue returns 0 for invalid format`() {
        val result = parseRatingValue("not a number")
        assertThat(result).isEqualTo(0f)
    }

    @Test
    fun `parseRatingValue handles zero`() {
        val result = parseRatingValue("0")
        assertThat(result).isEqualTo(0f)
    }

    @Test
    fun `parseRatingValue handles high precision`() {
        val result = parseRatingValue("7.89")
        assertThat(result).isWithin(0.01f).of(7.89f)
    }
}
