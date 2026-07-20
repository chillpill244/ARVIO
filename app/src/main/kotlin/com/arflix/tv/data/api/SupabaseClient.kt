package com.arflix.tv.data.api

import com.arflix.tv.util.Constants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Supabase REST API interface for watch history and user data
 *
 * Tables used:
 * - watch_history: Playback progress (position, duration, progress%)
 * - watched_movies: Movies marked as watched (source of truth)
 * - watched_episodes: Episodes marked as watched (source of truth)
 * - episode_progress: In-progress episode playback state
 * - sync_state: Tracks last Trakt sync timestamps
 */
interface SupabaseApi {

    // ========== Watch History (Playback Progress) ==========

    @GET("rest/v1/watch_history")
    suspend fun getWatchHistory(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Query("user_id") userId: String,
        @Query("profile_id") profileId: String? = null,
        @Query("source") source: String? = null,
        @Query("media_type") mediaType: String? = null,
        @Query("select") select: String = "*",
        @Query("order") order: String = "updated_at.desc",
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int? = null
    ): List<WatchHistoryRecord>
    
    @POST("rest/v1/watch_history")
    suspend fun upsertWatchHistory(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates",
        @Body item: WatchHistoryRecord
    )
    
    @GET("rest/v1/watch_history")
    suspend fun getWatchHistoryItem(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Query("user_id") userId: String,
        @Query("profile_id") profileId: String? = null,
        @Query("show_tmdb_id") showTmdbId: String,
        @Query("media_type") mediaType: String,
        @Query("source") source: String? = null,
        @Query("season") season: String? = null,
        @Query("episode") episode: String? = null,
        @Query("select") select: String = "*",
        @Query("order") order: String? = null,
        @Query("limit") limit: Int? = null
    ): List<WatchHistoryRecord>

    @retrofit2.http.HTTP(method = "DELETE", path = "rest/v1/watch_history", hasBody = false)
    suspend fun deleteWatchHistory(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Query("user_id") userId: String,
        @Query("profile_id") profileId: String? = null,
        @Query("show_tmdb_id") showTmdbId: String? = null,
        @Query("media_type") mediaType: String? = null,
        @Query("season") season: String? = null,
        @Query("episode") episode: String? = null,
        @Query("source") source: String? = null
    )
    
    @retrofit2.http.HTTP(method = "DELETE", path = "rest/v1/watch_history", hasBody = false)
    suspend fun deleteWatchHistoryByIds(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Query("id") idIn: String
    )

    // ========== User Profiles ==========
    
    @GET("rest/v1/profiles")
    suspend fun getProfile(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Query("id") userId: String,
        @Query("select") select: String = "*"
    ): List<UserProfile>
    
    @PATCH("rest/v1/profiles")
    suspend fun updateProfile(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Query("id") userId: String,
        @Body profile: UserProfileUpdate
    )
    
    // ========== Watchlist ==========

    @GET("rest/v1/watchlist")
    suspend fun getWatchlist(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Query("user_id") userId: String,
        @Query("media_type") mediaType: String? = null,
        @Query("tmdb_id") tmdbId: String? = null,
        @Query("select") select: String = "*",
        @Query("order") order: String = "added_at.desc"
    ): List<WatchlistRecord>

    @POST("rest/v1/watchlist")
    suspend fun upsertWatchlist(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates",
        @Body record: WatchlistRecord
    )

    @retrofit2.http.HTTP(method = "DELETE", path = "rest/v1/watchlist", hasBody = false)
    suspend fun deleteWatchlist(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Query("user_id") userId: String,
        @Query("tmdb_id") tmdbId: String,
        @Query("media_type") mediaType: String
    )

    // ========== Watched Status (from Trakt sync) ==========
    
    @GET("rest/v1/watched_movies")
    suspend fun getWatchedMovies(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Query("user_id") userId: String,
        @Query("profile_id") profileId: String? = null,
        @Query("select") select: String = "user_id,profile_id,tmdb_id,trakt_id,watched_at",
        @Query("order") order: String = "tmdb_id",
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 1000
    ): List<WatchedMovieRecord>
    
    @GET("rest/v1/watched_episodes")
    suspend fun getWatchedEpisodes(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Query("user_id") userId: String,
        @Query("profile_id") profileId: String? = null,
        @Query("select") select: String = "user_id,profile_id,tmdb_id,show_trakt_id,season,episode,trakt_episode_id,tmdb_episode_id,watched_at,updated_at,source",
        @Query("order") order: String = "tmdb_id,season,episode",
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 1000
    ): List<WatchedEpisodeRecord>

    /** Targeted query for a single show's watched episodes */
    @GET("rest/v1/watched_episodes")
    suspend fun getWatchedEpisodesForShow(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Query("user_id") userId: String,
        @Query("profile_id") profileId: String? = null,
        @Query("tmdb_id") tmdbId: String,
        @Query("select") select: String = "user_id,profile_id,tmdb_id,show_trakt_id,season,episode,trakt_episode_id,tmdb_episode_id,watched_at,updated_at,source"
    ): List<WatchedEpisodeRecord>
    
    @POST("rest/v1/watched_movies")
    suspend fun markMovieWatched(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates",
        @Body record: WatchedMovieRecord
    )
    
    @POST("rest/v1/watched_episodes")
    suspend fun markEpisodeWatched(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates",
        @Body record: WatchedEpisodeRecord
    )

    /** RPC-based episode watched write — bypasses PostgREST table endpoint for reliable persistence */
    @POST("rest/v1/rpc/mark_episode_watched")
    suspend fun markEpisodeWatchedRpc(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Header("Cache-Control") cacheControl: String = "no-cache, no-store",
        @Body params: MarkEpisodeWatchedParams
    )

    @retrofit2.http.HTTP(method = "DELETE", path = "rest/v1/watched_movies", hasBody = false)
    suspend fun deleteWatchedMovie(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Query("user_id") userId: String,
        @Query("profile_id") profileId: String? = null,
        @Query("tmdb_id") tmdbId: String
    )

    @retrofit2.http.HTTP(method = "DELETE", path = "rest/v1/watched_episodes", hasBody = false)
    suspend fun deleteWatchedEpisode(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Query("user_id") userId: String,
        @Query("profile_id") profileId: String? = null,
        @Query("tmdb_id") tmdbId: String,
        @Query("season") season: String,
        @Query("episode") episode: String
    )

    // ========== Episode Progress (In-progress playback) ==========

    @GET("rest/v1/episode_progress")
    suspend fun getEpisodeProgress(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Query("user_id") userId: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "last_updated_at.desc"
    ): List<EpisodeProgressRecord>

    @POST("rest/v1/episode_progress")
    suspend fun upsertEpisodeProgress(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates",
        @Body record: EpisodeProgressRecord
    )

    @retrofit2.http.HTTP(method = "DELETE", path = "rest/v1/episode_progress", hasBody = false)
    suspend fun deleteEpisodeProgress(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Query("user_id") userId: String,
        @Query("tmdb_id") tmdbId: String,
        @Query("season") season: String,
        @Query("episode") episode: String
    )

    // ========== Sync State (Trakt sync tracking) ==========

    @GET("rest/v1/sync_state")
    suspend fun getSyncState(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Query("user_id") userId: String,
        @Query("profile_id") profileId: String? = null,
        @Query("select") select: String = "*"
    ): List<SyncStateRecord>

    @POST("rest/v1/sync_state")
    suspend fun upsertSyncState(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates",
        @Body record: SyncStateRecord
    )

    // ========== Bulk Operations ==========

    @POST("rest/v1/watched_episodes")
    suspend fun bulkUpsertWatchedEpisodes(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates",
        @Body records: List<WatchedEpisodeRecord>
    )

    @POST("rest/v1/watched_movies")
    suspend fun bulkUpsertWatchedMovies(
        @Header("Authorization") auth: String,
        @Header("apikey") apiKey: String = Constants.SUPABASE_ANON_KEY,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates",
        @Body records: List<WatchedMovieRecord>
    )
}

// ========== Data Models ==========

@Serializable
data class WatchHistoryRecord(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("profile_id") val profileId: String? = null,
    @SerialName("media_type") val mediaType: String, // "movie" or "tv"
    @SerialName("show_tmdb_id") val showTmdbId: Int? = null,
    @SerialName("show_trakt_id") val showTraktId: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("trakt_episode_id") val traktEpisodeId: Int? = null,
    @SerialName("tmdb_episode_id") val tmdbEpisodeId: Int? = null,
    val progress: Float, // 0.0 - 1.0
    @SerialName("position_seconds") val positionSeconds: Long,
    @SerialName("duration_seconds") val durationSeconds: Long,
    @SerialName("paused_at") val pausedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val source: String? = null, // "trakt" or "arvio"
    val title: String? = null,
    @SerialName("episode_title") val episodeTitle: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("stream_key") val streamKey: String? = null,
    @SerialName("stream_addon_id") val streamAddonId: String? = null,
    @SerialName("stream_title") val streamTitle: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class UserProfile(
    val id: String,
    val email: String?,
    @SerialName("trakt_token") val traktToken: kotlinx.serialization.json.JsonObject?,
    @SerialName("default_subtitle") val defaultSubtitle: String?,
    @SerialName("auto_play_next") val autoPlayNext: Boolean?,
    val addons: String?, // JSON string of addon configs
    @SerialName("created_at") val createdAt: String?,
    @SerialName("updated_at") val updatedAt: String?
)

@Serializable
data class UserProfileUpdate(
    @SerialName("trakt_token") val traktToken: kotlinx.serialization.json.JsonObject? = null,
    @SerialName("default_subtitle") val defaultSubtitle: String? = null,
    @SerialName("auto_play_next") val autoPlayNext: Boolean? = null,
    val addons: String? = null
)

@Serializable
data class WatchlistRecord(
    @SerialName("user_id") val userId: String,
    @SerialName("tmdb_id") val tmdbId: Int,
    @SerialName("media_type") val mediaType: String,
    @SerialName("added_at") val addedAt: String? = null
)

@Serializable
data class WatchedMovieRecord(
    @SerialName("user_id") val userId: String,
    @SerialName("profile_id") val profileId: String? = null,
    @SerialName("tmdb_id") val tmdbId: Int,
    @SerialName("trakt_id") val traktId: Int? = null,
    @SerialName("watched_at") val watchedAt: String? = null
)

@Serializable
data class WatchedEpisodeRecord(
    @SerialName("user_id") val userId: String,
    @SerialName("profile_id") val profileId: String? = null,
    @SerialName("tmdb_id") val showTmdbId: Int, // Show TMDB ID
    val season: Int,
    val episode: Int,
    @SerialName("trakt_episode_id") val traktEpisodeId: Int? = null,
    @SerialName("tmdb_episode_id") val tmdbEpisodeId: Int? = null,
    @SerialName("show_trakt_id") val showTraktId: Int? = null,
    @SerialName("watched") val watched: Boolean? = true,
    @SerialName("watched_at") val watchedAt: String? = null,
    val source: String? = null, // "trakt" or "arvio"
    @SerialName("updated_at") val updatedAt: String? = null
)

/** Parameters for the mark_episode_watched RPC function */
@Serializable
data class MarkEpisodeWatchedParams(
    @SerialName("p_user_id") val userId: String,
    @SerialName("p_tmdb_id") val tmdbId: Int,
    @SerialName("p_season") val season: Int,
    @SerialName("p_episode") val episode: Int,
    @SerialName("p_show_trakt_id") val showTraktId: Int? = null,
    @SerialName("p_source") val source: String = "arvio"
)

/**
 * Episode progress record - tracks in-progress playback
 * Unique constraint: (user_id, tmdb_id, season, episode)
 */
@Serializable
data class EpisodeProgressRecord(
    @SerialName("user_id") val userId: String,
    @SerialName("tmdb_id") val tmdbId: Int, // Show TMDB ID (or movie TMDB ID)
    @SerialName("media_type") val mediaType: String, // "movie" or "tv"
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("trakt_id") val traktId: Int? = null, // Trakt episode ID
    @SerialName("show_trakt_id") val showTraktId: Int? = null, // Trakt show ID
    val progress: Float, // 0.0-1.0
    @SerialName("position_seconds") val positionSeconds: Long,
    @SerialName("duration_seconds") val durationSeconds: Long,
    @SerialName("paused_at") val pausedAt: String? = null,
    @SerialName("last_updated_at") val lastUpdatedAt: String? = null,
    val source: String? = null, // "trakt" or "arvio"
    val title: String? = null,
    @SerialName("episode_title") val episodeTitle: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("poster_path") val posterPath: String? = null
)

/**
 * Sync state record - tracks Trakt sync status per user
 * Unique constraint: (user_id)
 */
@Serializable
data class SyncStateRecord(
    @SerialName("user_id") val userId: String,
    @SerialName("profile_id") val profileId: String? = null,
    @SerialName("last_sync_at") val lastSyncAt: String? = null,
    @SerialName("last_full_sync_at") val lastFullSyncAt: String? = null,
    @SerialName("last_trakt_activities") val lastTraktActivities: String? = null, // JSON string (legacy)
    @SerialName("last_trakt_activities_json") val lastTraktActivitiesJson: String? = null,
    @SerialName("movies_synced") val moviesSynced: Int = 0,
    @SerialName("episodes_synced") val episodesSynced: Int = 0,
    @SerialName("sync_in_progress") val syncInProgress: Boolean = false,
    @SerialName("last_error") val lastError: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)


