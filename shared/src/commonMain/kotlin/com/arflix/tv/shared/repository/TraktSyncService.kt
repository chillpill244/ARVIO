package com.arflix.tv.shared.repository

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow

interface TraktSyncService {
    val syncProgress: StateFlow<SyncProgress>
    val isSyncing: StateFlow<Boolean>
    val syncEvents: SharedFlow<SyncStatus>
    
    suspend fun performFullSync(): SyncResult
    suspend fun performIncrementalSync(): SyncResult
    suspend fun getLastSyncTime(): String?

    suspend fun getWatchedEpisodes(): Set<String>
    suspend fun getWatchedEpisodesForShow(tmdbId: Int): Set<String>
    suspend fun getWatchedMovies(): Set<Int>
    suspend fun markEpisodeUnwatched(showTmdbId: Int, season: Int, episode: Int): Boolean
    suspend fun markEpisodeWatched(showTmdbId: Int, season: Int, episode: Int, traktShowId: Int?): Boolean
    suspend fun markEpisodeWatchedInSupabaseOnly(showTmdbId: Int, season: Int, episode: Int, traktShowId: Int?)
    suspend fun markMovieUnwatched(tmdbId: Int): Boolean
    suspend fun markMovieWatched(tmdbId: Int, traktId: Int? = null): Boolean
}

data class SyncProgress(
    val status: SyncStatus = SyncStatus.IDLE,
    val message: String = "",
    val moviesProcessed: Int = 0,
    val totalMovies: Int = 0,
    val episodesProcessed: Int = 0,
    val totalEpisodes: Int = 0
)

enum class SyncStatus {
    IDLE,
    STARTING,
    SYNCING_MOVIES,
    SYNCING_EPISODES,
    SYNCING_PROGRESS,
    COMPLETED,
    ERROR
}

sealed class SyncResult {
    data class Success(val moviesSynced: Int, val episodesSynced: Int) : SyncResult()
    data class Error(val message: String) : SyncResult()
}