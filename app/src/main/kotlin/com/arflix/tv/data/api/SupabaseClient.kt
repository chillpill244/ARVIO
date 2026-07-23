package com.arflix.tv.data.api

import com.arflix.tv.util.Constants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.request.*

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
class SupabaseApi(private val client: HttpClient) {

    // ========== Watch History (Playback Progress) ==========

    suspend fun getWatchHistory(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        userId: String,
        profileId: String? = null,
        source: String? = null,
        mediaType: String? = null,
        select: String = "*",
        order: String = "updated_at.desc",
        limit: Int = 50,
        offset: Int? = null
    ): List<WatchHistoryRecord> {
        return client.get("rest/v1/watch_history") {
            header("Authorization", auth)
            header("apikey", apiKey)
            parameter("user_id", userId)
            parameter("profile_id", profileId)
            parameter("source", source)
            parameter("media_type", mediaType)
            parameter("select", select)
            parameter("order", order)
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()
    }
    
    suspend fun upsertWatchHistory(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        prefer: String = "resolution=merge-duplicates",
        item: WatchHistoryRecord
    ) {
        client.post("rest/v1/watch_history") {
            header("Authorization", auth)
            header("apikey", apiKey)
            header("Prefer", prefer)
            setBody(item)
        }
    }
    
    suspend fun getWatchHistoryItem(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        userId: String,
        profileId: String? = null,
        showTmdbId: String,
        mediaType: String,
        source: String? = null,
        season: String? = null,
        episode: String? = null,
        select: String = "*",
        order: String? = null,
        limit: Int? = null
    ): List<WatchHistoryRecord> {
        return client.get("rest/v1/watch_history") {
            header("Authorization", auth)
            header("apikey", apiKey)
            parameter("user_id", userId)
            parameter("profile_id", profileId)
            parameter("show_tmdb_id", showTmdbId)
            parameter("media_type", mediaType)
            parameter("source", source)
            parameter("season", season)
            parameter("episode", episode)
            parameter("select", select)
            parameter("order", order)
            parameter("limit", limit)
        }.body()
    }

    suspend fun deleteWatchHistory(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        userId: String,
        profileId: String? = null,
        showTmdbId: String? = null,
        mediaType: String? = null,
        season: String? = null,
        episode: String? = null,
        source: String? = null
    ) {
        client.delete("rest/v1/watch_history") {
            header("Authorization", auth)
            header("apikey", apiKey)
            parameter("user_id", userId)
            parameter("profile_id", profileId)
            parameter("show_tmdb_id", showTmdbId)
            parameter("media_type", mediaType)
            parameter("season", season)
            parameter("episode", episode)
            parameter("source", source)
        }
    }
    
    suspend fun deleteWatchHistoryByIds(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        idIn: String
    ) {
        client.delete("rest/v1/watch_history") {
            header("Authorization", auth)
            header("apikey", apiKey)
            parameter("id", idIn)
        }
    }

    // ========== User Profiles ==========
    
    suspend fun getProfile(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        userId: String,
        select: String = "*"
    ): List<UserProfile> {
        return client.get("rest/v1/profiles") {
            header("Authorization", auth)
            header("apikey", apiKey)
            parameter("id", userId)
            parameter("select", select)
        }.body()
    }
    
    suspend fun updateProfile(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        userId: String,
        profile: UserProfileUpdate
    ) {
        client.patch("rest/v1/profiles") {
            header("Authorization", auth)
            header("apikey", apiKey)
            parameter("id", userId)
            setBody(profile)
        }
    }
    
    // ========== Watchlist ==========

    suspend fun getWatchlist(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        userId: String,
        mediaType: String? = null,
        tmdbId: String? = null,
        select: String = "*",
        order: String = "added_at.desc"
    ): List<WatchlistRecord> {
        return client.get("rest/v1/watchlist") {
            header("Authorization", auth)
            header("apikey", apiKey)
            parameter("user_id", userId)
            parameter("media_type", mediaType)
            parameter("tmdb_id", tmdbId)
            parameter("select", select)
            parameter("order", order)
        }.body()
    }

    suspend fun upsertWatchlist(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        prefer: String = "resolution=merge-duplicates",
        record: WatchlistRecord
    ) {
        client.post("rest/v1/watchlist") {
            header("Authorization", auth)
            header("apikey", apiKey)
            header("Prefer", prefer)
            setBody(record)
        }
    }

    suspend fun deleteWatchlist(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        userId: String,
        tmdbId: String,
        mediaType: String
    ) {
        client.delete("rest/v1/watchlist") {
            header("Authorization", auth)
            header("apikey", apiKey)
            parameter("user_id", userId)
            parameter("tmdb_id", tmdbId)
            parameter("media_type", mediaType)
        }
    }

    // ========== Watched Status (from Trakt sync) ==========
    
    suspend fun getWatchedMovies(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        userId: String,
        profileId: String? = null,
        select: String = "user_id,profile_id,tmdb_id,trakt_id,watched_at",
        order: String = "tmdb_id",
        offset: Int = 0,
        limit: Int = 1000
    ): List<WatchedMovieRecord> {
        return client.get("rest/v1/watched_movies") {
            header("Authorization", auth)
            header("apikey", apiKey)
            parameter("user_id", userId)
            parameter("profile_id", profileId)
            parameter("select", select)
            parameter("order", order)
            parameter("offset", offset)
            parameter("limit", limit)
        }.body()
    }
    
    suspend fun getWatchedEpisodes(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        userId: String,
        profileId: String? = null,
        select: String = "user_id,profile_id,tmdb_id,show_trakt_id,season,episode,trakt_episode_id,tmdb_episode_id,watched_at,updated_at,source",
        order: String = "tmdb_id,season,episode",
        offset: Int = 0,
        limit: Int = 1000
    ): List<WatchedEpisodeRecord> {
        return client.get("rest/v1/watched_episodes") {
            header("Authorization", auth)
            header("apikey", apiKey)
            parameter("user_id", userId)
            parameter("profile_id", profileId)
            parameter("select", select)
            parameter("order", order)
            parameter("offset", offset)
            parameter("limit", limit)
        }.body()
    }

    /** Targeted query for a single show's watched episodes */
    suspend fun getWatchedEpisodesForShow(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        userId: String,
        profileId: String? = null,
        tmdbId: String,
        select: String = "user_id,profile_id,tmdb_id,show_trakt_id,season,episode,trakt_episode_id,tmdb_episode_id,watched_at,updated_at,source"
    ): List<WatchedEpisodeRecord> {
        return client.get("rest/v1/watched_episodes") {
            header("Authorization", auth)
            header("apikey", apiKey)
            parameter("user_id", userId)
            parameter("profile_id", profileId)
            parameter("tmdb_id", tmdbId)
            parameter("select", select)
        }.body()
    }
    
    suspend fun markMovieWatched(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        prefer: String = "resolution=merge-duplicates",
        record: WatchedMovieRecord
    ) {
        client.post("rest/v1/watched_movies") {
            header("Authorization", auth)
            header("apikey", apiKey)
            header("Prefer", prefer)
            setBody(record)
        }
    }
    
    suspend fun markEpisodeWatched(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        prefer: String = "resolution=merge-duplicates",
        record: WatchedEpisodeRecord
    ) {
        client.post("rest/v1/watched_episodes") {
            header("Authorization", auth)
            header("apikey", apiKey)
            header("Prefer", prefer)
            setBody(record)
        }
    }

    /** RPC-based episode watched write — bypasses PostgREST table endpoint for reliable persistence */
    suspend fun markEpisodeWatchedRpc(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        cacheControl: String = "no-cache, no-store",
        params: MarkEpisodeWatchedParams
    ) {
        client.post("rest/v1/rpc/mark_episode_watched") {
            header("Authorization", auth)
            header("apikey", apiKey)
            header("Cache-Control", cacheControl)
            setBody(params)
        }
    }

    suspend fun deleteWatchedMovie(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        userId: String,
        profileId: String? = null,
        tmdbId: String
    ) {
        client.delete("rest/v1/watched_movies") {
            header("Authorization", auth)
            header("apikey", apiKey)
            parameter("user_id", userId)
            parameter("profile_id", profileId)
            parameter("tmdb_id", tmdbId)
        }
    }

    suspend fun deleteWatchedEpisode(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        userId: String,
        profileId: String? = null,
        tmdbId: String,
        season: String,
        episode: String
    ) {
        client.delete("rest/v1/watched_episodes") {
            header("Authorization", auth)
            header("apikey", apiKey)
            parameter("user_id", userId)
            parameter("profile_id", profileId)
            parameter("tmdb_id", tmdbId)
            parameter("season", season)
            parameter("episode", episode)
        }
    }

    // ========== Episode Progress (In-progress playback) ==========

    suspend fun getEpisodeProgress(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        userId: String,
        select: String = "*",
        order: String = "last_updated_at.desc"
    ): List<EpisodeProgressRecord> {
        return client.get("rest/v1/episode_progress") {
            header("Authorization", auth)
            header("apikey", apiKey)
            parameter("user_id", userId)
            parameter("select", select)
            parameter("order", order)
        }.body()
    }

    suspend fun upsertEpisodeProgress(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        prefer: String = "resolution=merge-duplicates",
        record: EpisodeProgressRecord
    ) {
        client.post("rest/v1/episode_progress") {
            header("Authorization", auth)
            header("apikey", apiKey)
            header("Prefer", prefer)
            setBody(record)
        }
    }

    suspend fun deleteEpisodeProgress(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        userId: String,
        tmdbId: String,
        season: String,
        episode: String
    ) {
        client.delete("rest/v1/episode_progress") {
            header("Authorization", auth)
            header("apikey", apiKey)
            parameter("user_id", userId)
            parameter("tmdb_id", tmdbId)
            parameter("season", season)
            parameter("episode", episode)
        }
    }

    // ========== Sync State (Trakt sync tracking) ==========

    suspend fun getSyncState(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        userId: String,
        profileId: String? = null,
        select: String = "*"
    ): List<SyncStateRecord> {
        return client.get("rest/v1/sync_state") {
            header("Authorization", auth)
            header("apikey", apiKey)
            parameter("user_id", userId)
            parameter("profile_id", profileId)
            parameter("select", select)
        }.body()
    }

    suspend fun upsertSyncState(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        prefer: String = "resolution=merge-duplicates",
        record: SyncStateRecord
    ) {
        client.post("rest/v1/sync_state") {
            header("Authorization", auth)
            header("apikey", apiKey)
            header("Prefer", prefer)
            setBody(record)
        }
    }

    // ========== Bulk Operations ==========

    suspend fun bulkUpsertWatchedEpisodes(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        prefer: String = "resolution=merge-duplicates",
        records: List<WatchedEpisodeRecord>
    ) {
        client.post("rest/v1/watched_episodes") {
            header("Authorization", auth)
            header("apikey", apiKey)
            header("Prefer", prefer)
            setBody(records)
        }
    }

    suspend fun bulkUpsertWatchedMovies(
        auth: String,
        apiKey: String = Constants.SUPABASE_ANON_KEY,
        prefer: String = "resolution=merge-duplicates",
        records: List<WatchedMovieRecord>
    ) {
        client.post("rest/v1/watched_movies") {
            header("Authorization", auth)
            header("apikey", apiKey)
            header("Prefer", prefer)
            setBody(records)
        }
    }
}
