package com.arflix.tv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContinueWatchingSelectorTest {

    // ===== selectNextEpisodeAfterLastWatched tests =====

    @Test
    fun selectNextEpisodeAfterLastWatched_returnsNextUnwatched() {
        val episodes = listOf(
            EpisodeProgressSnapshot(1, 1, completed = true),
            EpisodeProgressSnapshot(1, 2, completed = true),
            EpisodeProgressSnapshot(1, 3, completed = false)
        )
        val watched = setOf(EpisodePointer(1, 1), EpisodePointer(1, 2))
        val lastWatched = WatchedEpisodeSnapshot(1, 2, "2025-01-01T00:00:00Z")

        val next = ContinueWatchingSelector.selectNextEpisodeAfterLastWatched(
            episodes = episodes,
            watched = watched,
            lastWatched = lastWatched,
            includeSpecials = true
        )

        assertEquals(EpisodePointer(1, 3), next)
    }

    @Test
    fun selectNextEpisodeAfterLastWatched_skipsSpecialsByDefault() {
        val episodes = listOf(
            EpisodeProgressSnapshot(0, 1, completed = false),
            EpisodeProgressSnapshot(1, 1, completed = false)
        )

        val next = ContinueWatchingSelector.selectNextEpisodeAfterLastWatched(
            episodes = episodes,
            watched = emptySet(),
            lastWatched = null,
            includeSpecials = false
        )

        assertEquals(EpisodePointer(1, 1), next)
    }

    @Test
    fun selectNextEpisodeAfterLastWatched_fallsBackWhenLastNotFound() {
        val episodes = listOf(
            EpisodeProgressSnapshot(1, 1, completed = false),
            EpisodeProgressSnapshot(1, 2, completed = false)
        )
        val lastWatched = WatchedEpisodeSnapshot(2, 1, "2025-01-01T00:00:00Z")

        val next = ContinueWatchingSelector.selectNextEpisodeAfterLastWatched(
            episodes = episodes,
            watched = emptySet(),
            lastWatched = lastWatched,
            includeSpecials = true
        )

        assertEquals(EpisodePointer(1, 1), next)
    }

    @Test
    fun selectNextEpisodeAfterLastWatched_includesSpecialsWhenEnabled() {
        val episodes = listOf(
            EpisodeProgressSnapshot(0, 1, completed = false),
            EpisodeProgressSnapshot(1, 1, completed = false)
        )

        val next = ContinueWatchingSelector.selectNextEpisodeAfterLastWatched(
            episodes = episodes,
            watched = emptySet(),
            lastWatched = null,
            includeSpecials = true
        )

        assertEquals(EpisodePointer(0, 1), next)
    }

    @Test
    fun selectNextEpisodeAfterLastWatched_returnsNullWhenAllWatched() {
        val episodes = listOf(
            EpisodeProgressSnapshot(1, 1, completed = true),
            EpisodeProgressSnapshot(1, 2, completed = true)
        )
        val watched = setOf(EpisodePointer(1, 1), EpisodePointer(1, 2))

        val next = ContinueWatchingSelector.selectNextEpisodeAfterLastWatched(
            episodes = episodes,
            watched = watched,
            lastWatched = WatchedEpisodeSnapshot(1, 2, "2025-01-01T00:00:00Z"),
            includeSpecials = true
        )

        assertNull(next)
    }

    @Test
    fun selectNextEpisodeAfterLastWatched_returnsNullForEmptyList() {
        val next = ContinueWatchingSelector.selectNextEpisodeAfterLastWatched(
            episodes = emptyList(),
            watched = emptySet(),
            lastWatched = null,
            includeSpecials = true
        )

        assertNull(next)
    }

    @Test
    fun selectNextEpisodeAfterLastWatched_handlesCrossSeasonProgression() {
        val episodes = listOf(
            EpisodeProgressSnapshot(1, 1, completed = true),
            EpisodeProgressSnapshot(1, 2, completed = true),
            EpisodeProgressSnapshot(2, 1, completed = false),
            EpisodeProgressSnapshot(2, 2, completed = false)
        )
        val watched = setOf(EpisodePointer(1, 1), EpisodePointer(1, 2))
        val lastWatched = WatchedEpisodeSnapshot(1, 2, "2025-01-01T00:00:00Z")

        val next = ContinueWatchingSelector.selectNextEpisodeAfterLastWatched(
            episodes = episodes,
            watched = watched,
            lastWatched = lastWatched,
            includeSpecials = false
        )

        assertEquals(EpisodePointer(2, 1), next)
    }

    @Test
    fun selectNextEpisodeAfterLastWatched_skipsCompletedEpisodes() {
        val episodes = listOf(
            EpisodeProgressSnapshot(1, 1, completed = true),
            EpisodeProgressSnapshot(1, 2, completed = true),
            EpisodeProgressSnapshot(1, 3, completed = false)
        )

        val next = ContinueWatchingSelector.selectNextEpisodeAfterLastWatched(
            episodes = episodes,
            watched = emptySet(),
            lastWatched = null,
            includeSpecials = true
        )

        assertEquals(EpisodePointer(1, 3), next)
    }

    @Test
    fun selectNextEpisodeAfterLastWatched_skipsWatchedEpisodes() {
        val episodes = listOf(
            EpisodeProgressSnapshot(1, 1, completed = false),
            EpisodeProgressSnapshot(1, 2, completed = false),
            EpisodeProgressSnapshot(1, 3, completed = false)
        )
        val watched = setOf(EpisodePointer(1, 1), EpisodePointer(1, 2))

        val next = ContinueWatchingSelector.selectNextEpisodeAfterLastWatched(
            episodes = episodes,
            watched = watched,
            lastWatched = null,
            includeSpecials = true
        )

        assertEquals(EpisodePointer(1, 3), next)
    }

    @Test
    fun selectNextEpisodeAfterLastWatched_sortsEpisodesCorrectly() {
        // Episodes in random order
        val episodes = listOf(
            EpisodeProgressSnapshot(2, 1, completed = false),
            EpisodeProgressSnapshot(1, 3, completed = false),
            EpisodeProgressSnapshot(1, 1, completed = false),
            EpisodeProgressSnapshot(1, 2, completed = false)
        )

        val next = ContinueWatchingSelector.selectNextEpisodeAfterLastWatched(
            episodes = episodes,
            watched = emptySet(),
            lastWatched = null,
            includeSpecials = true
        )

        assertEquals(EpisodePointer(1, 1), next)
    }

    // ===== selectInProgressEpisode tests =====

    @Test
    fun selectInProgressEpisode_prefersMostRecent() {
        val inProgress = listOf(
            InProgressSnapshot(1, 1, progress = 0.4f, updatedAt = "2025-01-01T00:00:00Z"),
            InProgressSnapshot(1, 2, progress = 0.5f, updatedAt = "2025-01-02T00:00:00Z")
        )

        val next = ContinueWatchingSelector.selectInProgressEpisode(
            inProgress = inProgress,
            watched = emptySet(),
            completionThreshold = 0.9f
        )

        assertEquals(EpisodePointer(1, 2), next)
    }

    @Test
    fun selectInProgressEpisode_ignoresCompleted() {
        val inProgress = listOf(
            InProgressSnapshot(1, 1, progress = 0.95f, updatedAt = "2025-01-01T00:00:00Z")
        )

        val next = ContinueWatchingSelector.selectInProgressEpisode(
            inProgress = inProgress,
            watched = emptySet(),
            completionThreshold = 0.9f
        )

        assertNull(next)
    }

    @Test
    fun selectInProgressEpisode_ignoresZeroProgress() {
        val inProgress = listOf(
            InProgressSnapshot(1, 1, progress = 0f, updatedAt = "2025-01-01T00:00:00Z"),
            InProgressSnapshot(1, 2, progress = 0.5f, updatedAt = "2025-01-02T00:00:00Z")
        )

        val next = ContinueWatchingSelector.selectInProgressEpisode(
            inProgress = inProgress,
            watched = emptySet(),
            completionThreshold = 0.9f
        )

        assertEquals(EpisodePointer(1, 2), next)
    }

    @Test
    fun selectInProgressEpisode_ignoresWatchedEpisodes() {
        val inProgress = listOf(
            InProgressSnapshot(1, 1, progress = 0.5f, updatedAt = "2025-01-02T00:00:00Z"),
            InProgressSnapshot(1, 2, progress = 0.3f, updatedAt = "2025-01-01T00:00:00Z")
        )
        val watched = setOf(EpisodePointer(1, 1))

        val next = ContinueWatchingSelector.selectInProgressEpisode(
            inProgress = inProgress,
            watched = watched,
            completionThreshold = 0.9f
        )

        assertEquals(EpisodePointer(1, 2), next)
    }

    @Test
    fun selectInProgressEpisode_returnsNullForEmptyList() {
        val next = ContinueWatchingSelector.selectInProgressEpisode(
            inProgress = emptyList(),
            watched = emptySet(),
            completionThreshold = 0.9f
        )

        assertNull(next)
    }

    @Test
    fun selectInProgressEpisode_handlesExactThreshold() {
        val inProgress = listOf(
            InProgressSnapshot(1, 1, progress = 0.9f, updatedAt = "2025-01-01T00:00:00Z")
        )

        val next = ContinueWatchingSelector.selectInProgressEpisode(
            inProgress = inProgress,
            watched = emptySet(),
            completionThreshold = 0.9f
        )

        // Progress 0.9 is NOT less than threshold 0.9, so should be ignored
        assertNull(next)
    }

    @Test
    fun selectInProgressEpisode_handlesBelowThreshold() {
        val inProgress = listOf(
            InProgressSnapshot(1, 1, progress = 0.89f, updatedAt = "2025-01-01T00:00:00Z")
        )

        val next = ContinueWatchingSelector.selectInProgressEpisode(
            inProgress = inProgress,
            watched = emptySet(),
            completionThreshold = 0.9f
        )

        assertEquals(EpisodePointer(1, 1), next)
    }

    @Test
    fun selectInProgressEpisode_handlesNullUpdatedAt() {
        val inProgress = listOf(
            InProgressSnapshot(1, 1, progress = 0.5f, updatedAt = null),
            InProgressSnapshot(1, 2, progress = 0.3f, updatedAt = "2025-01-01T00:00:00Z")
        )

        val next = ContinueWatchingSelector.selectInProgressEpisode(
            inProgress = inProgress,
            watched = emptySet(),
            completionThreshold = 0.9f
        )

        // Episode with actual timestamp should be preferred
        assertEquals(EpisodePointer(1, 2), next)
    }

    @Test
    fun selectInProgressEpisode_allEpisodesWatched() {
        val inProgress = listOf(
            InProgressSnapshot(1, 1, progress = 0.5f, updatedAt = "2025-01-01T00:00:00Z"),
            InProgressSnapshot(1, 2, progress = 0.3f, updatedAt = "2025-01-02T00:00:00Z")
        )
        val watched = setOf(EpisodePointer(1, 1), EpisodePointer(1, 2))

        val next = ContinueWatchingSelector.selectInProgressEpisode(
            inProgress = inProgress,
            watched = watched,
            completionThreshold = 0.9f
        )

        assertNull(next)
    }

    // ===== EpisodePointer tests =====

    @Test
    fun `EpisodePointer equality works correctly`() {
        val pointer1 = EpisodePointer(1, 5)
        val pointer2 = EpisodePointer(1, 5)
        val pointer3 = EpisodePointer(2, 5)

        assertEquals(pointer1, pointer2)
        assert(pointer1 != pointer3)
    }

    @Test
    fun `EpisodePointer can be used in set`() {
        val set = setOf(EpisodePointer(1, 1), EpisodePointer(1, 2), EpisodePointer(1, 1))
        assertEquals(2, set.size)
    }
}
