package com.arflix.tv.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


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


