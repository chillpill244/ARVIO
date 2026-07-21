package com.arflix.tv.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Trakt.tv API interface
 */
class TraktApi(private val client: HttpClient) {
    
    // ========== Authentication ==========
    
    suspend fun getDeviceCode(
        request: DeviceCodeRequest
    ): TraktDeviceCode {
        val response = client.post("oauth/device/code") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.body()
    }

    
    suspend fun pollToken(
        request: TokenPollRequest
    ): TraktToken {
        val response = client.post("oauth/device/token") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.body()
    }

    
    suspend fun refreshToken(
        request: RefreshTokenRequest
    ): TraktToken {
        val response = client.post("oauth/token") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.body()
    }

    
    // ========== Sync ==========

    suspend fun getLastActivities(
        auth: String,
        clientId: String,
        version: String = "2"
    ): TraktLastActivities {
        val response = client.get("sync/last_activities") {
            contentType(ContentType.Application.Json)
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
        }
        return response.body()
    }


    suspend fun getWatchedMovies(
        auth: String,
        clientId: String,
        version: String = "2"
    ): List<TraktWatchedMovie> {
        val response = client.get("sync/watched/movies") {
            contentType(ContentType.Application.Json)
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
        }
        return response.body()
    }

    
    suspend fun getWatchedShows(
        auth: String,
        clientId: String,
        version: String = "2"
    ): List<TraktWatchedShow> {
        val response = client.get("sync/watched/shows") {
            contentType(ContentType.Application.Json)
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
        }
        return response.body()
    }

    
    suspend fun getPlaybackProgress(
        auth: String,
        clientId: String,
        version: String = "2",
        type: String? = null,
        page: Int? = null,
        limit: Int? = null
    ): List<TraktPlaybackItem> {
        val response = client.get("sync/playback") {
            contentType(ContentType.Application.Json)
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            if (type != null) parameter("type", type)
            if (page != null) parameter("page", page)
            if (limit != null) parameter("limit", limit)
        }
        return response.body()
    }

    
    suspend fun removePlaybackItem(
        auth: String,
        clientId: String,
        version: String = "2",
        id: Long
    ) {
        val response = client.delete("sync/playback/$id") {
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
        }
    }

    
    suspend fun addToHistory(
        auth: String,
        clientId: String,
        version: String = "2",
        body: TraktHistoryBody
    ): TraktSyncResponse {
        val response = client.post("sync/history") {
            contentType(ContentType.Application.Json)
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            setBody(body)
        }
        return response.body()
    }

    
    suspend fun removeFromHistory(
        auth: String,
        clientId: String,
        version: String = "2",
        body: TraktHistoryBody
    ): TraktSyncResponse {
        val response = client.post("sync/history/remove") {
            contentType(ContentType.Application.Json)
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            setBody(body)
        }
        return response.body()
    }

    
    suspend fun scrobbleStart(
        auth: String,
        clientId: String,
        version: String = "2",
        body: TraktScrobbleBody
    ): TraktScrobbleResponse {
        val response = client.post("scrobble/start") {
            contentType(ContentType.Application.Json)
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            setBody(body)
        }
        return response.body()
    }


    suspend fun scrobblePause(
        auth: String,
        clientId: String,
        version: String = "2",
        body: TraktScrobbleBody
    ): TraktScrobbleResponse {
        val response = client.post("scrobble/pause") {
            contentType(ContentType.Application.Json)
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            setBody(body)
        }
        return response.body()
    }


    suspend fun scrobbleStop(
        auth: String,
        clientId: String,
        version: String = "2",
        body: TraktScrobbleBody
    ): TraktScrobbleResponse {
        val response = client.post("scrobble/stop") {
            contentType(ContentType.Application.Json)
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            setBody(body)
        }
        return response.body()
    }


    // ========== Search ==========

    suspend fun searchByTmdb(
        clientId: String,
        tmdbId: Int,
        type: String
    ): List<TraktSearchResult> {
        val response = client.get("search/tmdb/$tmdbId") {
            header("trakt-api-key", clientId)
            if (type != null) parameter("type", type)
        }
        return response.body()
    }


    suspend fun searchLists(
        clientId: String,
        version: String = "2",
        query: String,
        page: Int = 1,
        limit: Int = 20,
        extended: String = "full"
    ): List<TraktListSearchResult> {
        val response = client.get("search/list") {
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            if (query != null) parameter("query", query)
            if (page != null) parameter("page", page)
            if (limit != null) parameter("limit", limit)
            if (extended != null) parameter("extended", extended)
        }
        return response.body()
    }


    // ========== Collection ==========

    suspend fun getCollectionMovies(
        auth: String,
        clientId: String,
        version: String = "2",
        extended: String = "full"
    ): List<TraktCollectionMovie> {
        val response = client.get("sync/collection/movies") {
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            if (extended != null) parameter("extended", extended)
        }
        return response.body()
    }


    suspend fun getCollectionShows(
        auth: String,
        clientId: String,
        version: String = "2",
        extended: String = "full"
    ): List<TraktCollectionShow> {
        val response = client.get("sync/collection/shows") {
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            if (extended != null) parameter("extended", extended)
        }
        return response.body()
    }


    suspend fun addToCollection(
        auth: String,
        clientId: String,
        version: String = "2",
        body: TraktCollectionBody
    ): TraktSyncResponse {
        val response = client.post("sync/collection") {
            contentType(ContentType.Application.Json)
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            setBody(body)
        }
        return response.body()
    }


    suspend fun removeFromCollection(
        auth: String,
        clientId: String,
        version: String = "2",
        body: TraktCollectionBody
    ): TraktSyncResponse {
        val response = client.post("sync/collection/remove") {
            contentType(ContentType.Application.Json)
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            setBody(body)
        }
        return response.body()
    }


    // ========== Ratings ==========

    suspend fun getRatingsMovies(
        auth: String,
        clientId: String,
        version: String = "2"
    ): List<TraktRatingItem> {
        val response = client.get("sync/ratings/movies") {
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
        }
        return response.body()
    }


    suspend fun getRatingsShows(
        auth: String,
        clientId: String,
        version: String = "2"
    ): List<TraktRatingItem> {
        val response = client.get("sync/ratings/shows") {
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
        }
        return response.body()
    }


    suspend fun getRatingsEpisodes(
        auth: String,
        clientId: String,
        version: String = "2"
    ): List<TraktRatingItem> {
        val response = client.get("sync/ratings/episodes") {
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
        }
        return response.body()
    }


    suspend fun addRating(
        auth: String,
        clientId: String,
        version: String = "2",
        body: TraktRatingBody
    ): TraktSyncResponse {
        val response = client.post("sync/ratings") {
            contentType(ContentType.Application.Json)
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            setBody(body)
        }
        return response.body()
    }


    suspend fun removeRating(
        auth: String,
        clientId: String,
        version: String = "2",
        body: TraktRatingBody
    ): TraktSyncResponse {
        val response = client.post("sync/ratings/remove") {
            contentType(ContentType.Application.Json)
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            setBody(body)
        }
        return response.body()
    }


    // ========== Comments ==========

    suspend fun getMovieComments(
        clientId: String,
        version: String = "2",
        movieId: String,
        sort: String = "newest",
        page: Int = 1,
        limit: Int = 10
    ): List<TraktComment> {
        val response = client.get("movies/$movieId/comments/$sort") {
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            if (page != null) parameter("page", page)
            if (limit != null) parameter("limit", limit)
        }
        return response.body()
    }


    suspend fun getShowComments(
        clientId: String,
        version: String = "2",
        showId: String,
        sort: String = "newest",
        page: Int = 1,
        limit: Int = 10
    ): List<TraktComment> {
        val response = client.get("shows/$showId/comments/$sort") {
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            if (page != null) parameter("page", page)
            if (limit != null) parameter("limit", limit)
        }
        return response.body()
    }


    suspend fun getSeasonComments(
        clientId: String,
        version: String = "2",
        showId: String,
        season: Int,
        sort: String = "newest",
        page: Int = 1,
        limit: Int = 10
    ): List<TraktComment> {
        val response = client.get("shows/$showId/seasons/$season/comments/$sort") {
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            if (page != null) parameter("page", page)
            if (limit != null) parameter("limit", limit)
        }
        return response.body()
    }


    suspend fun getEpisodeComments(
        clientId: String,
        version: String = "2",
        showId: String,
        season: Int,
        episode: Int,
        sort: String = "newest",
        page: Int = 1,
        limit: Int = 10
    ): List<TraktComment> {
        val response = client.get("shows/$showId/seasons/$season/episodes/$episode/comments/$sort") {
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            if (page != null) parameter("page", page)
            if (limit != null) parameter("limit", limit)
        }
        return response.body()
    }


    // ========== History ==========

    suspend fun getHistoryMovies(
        auth: String,
        clientId: String,
        version: String = "2",
        page: Int = 1,
        limit: Int = 20,
        startAt: String? = null
    ): List<TraktHistoryItem> {
        val response = client.get("users/me/history/movies") {
            contentType(ContentType.Application.Json)
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            if (page != null) parameter("page", page)
            if (limit != null) parameter("limit", limit)
            if (startAt != null) parameter("start_at", startAt)
        }
        return response.body()
    }


    suspend fun getHistoryEpisodes(
        auth: String,
        clientId: String,
        version: String = "2",
        page: Int = 1,
        limit: Int = 20,
        startAt: String? = null
    ): List<TraktHistoryItem> {
        val response = client.get("users/me/history/episodes") {
            contentType(ContentType.Application.Json)
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            if (page != null) parameter("page", page)
            if (limit != null) parameter("limit", limit)
            if (startAt != null) parameter("start_at", startAt)
        }
        return response.body()
    }


    suspend fun removeFromHistoryByIds(
        auth: String,
        clientId: String,
        version: String = "2",
        body: TraktHistoryRemoveBody
    ): TraktSyncResponse {
        val response = client.post("sync/history/remove") {
            contentType(ContentType.Application.Json)
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            setBody(body)
        }
        return response.body()
    }


    // ========== Watchlist ==========
    
    suspend fun getWatchlist(
        auth: String,
        clientId: String,
        version: String = "2",
        type: String? = null,
        extended: String = "full"
    ): List<TraktWatchlistItem> {
        val response = client.get("users/me/watchlist") {
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            if (type != null) parameter("type", type)
            if (extended != null) parameter("extended", extended)
        }
        return response.body()
    }


    suspend fun getWatchlistPage(
        auth: String,
        clientId: String,
        version: String = "2",
        type: String? = null,
        extended: String = "full",
        page: Int,
        limit: Int
    ): List<TraktWatchlistItem> {
        val response = client.get("users/me/watchlist") {
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            if (type != null) parameter("type", type)
            if (extended != null) parameter("extended", extended)
            if (page != null) parameter("page", page)
            if (limit != null) parameter("limit", limit)
        }
        return response.body()
    }


    suspend fun getWatchlistAddedPage(
        auth: String,
        clientId: String,
        version: String = "2",
        type: String,
        extended: String = "full",
        page: Int,
        limit: Int
    ): List<TraktWatchlistItem> {
        val response = client.get("users/me/watchlist/$type/added") {
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            if (extended != null) parameter("extended", extended)
            if (page != null) parameter("page", page)
            if (limit != null) parameter("limit", limit)
        }
        return response.body()
    }

    
    suspend fun addToWatchlist(
        auth: String,
        clientId: String,
        version: String = "2",
        body: TraktWatchlistBody
    ): TraktSyncResponse {
        val response = client.post("sync/watchlist") {
            contentType(ContentType.Application.Json)
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            setBody(body)
        }
        return response.body()
    }

    
    suspend fun removeFromWatchlist(
        auth: String,
        clientId: String,
        version: String = "2",
        body: TraktWatchlistBody
    ): TraktSyncResponse {
        val response = client.post("sync/watchlist/remove") {
            contentType(ContentType.Application.Json)
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            setBody(body)
        }
        return response.body()
    }

    
    // ========== Up Next ==========

    suspend fun getShowProgress(
        auth: String,
        clientId: String,
        version: String = "2",
        showId: String,
        hidden: String = "false",
        specials: String = "false",
        countSpecials: String = "false"
    ): TraktShowProgress {
        val response = client.get("shows/$showId/progress/watched") {
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            if (hidden != null) parameter("hidden", hidden)
            if (specials != null) parameter("specials", specials)
            if (countSpecials != null) parameter("count_specials", countSpecials)
        }
        return response.body()
    }

    
    // ========== Hidden Items ==========

    suspend fun getHiddenProgressShows(
        auth: String,
        clientId: String,
        version: String = "2",
        type: String = "show",
        limit: Int = 100,
        page: Int? = null
    ): List<TraktHiddenItem> {
        val response = client.get("users/hidden/progress_watched") {
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            if (type != null) parameter("type", type)
            if (limit != null) parameter("limit", limit)
            if (page != null) parameter("page", page)
        }
        return response.body()
    }


    suspend fun getHiddenProgressResetShows(
        auth: String,
        clientId: String,
        version: String = "2",
        type: String = "show",
        limit: Int = 100,
        page: Int? = null
    ): List<TraktHiddenItem> {
        val response = client.get("users/hidden/progress_watched_reset") {
            header("Authorization", auth)
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            if (type != null) parameter("type", type)
            if (limit != null) parameter("limit", limit)
            if (page != null) parameter("page", page)
        }
        return response.body()
    }


    // ========== Anime (Custom Lists) ==========

    suspend fun getTrendingAnime(
        clientId: String,
        version: String = "2"
    ): List<TraktListItem> {
        val response = client.get("lists/anime-streaming/anime-trending/items") {
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
        }
        return response.body()
    }


    // ========== Public Lists ==========

    suspend fun getUserListSummary(
        clientId: String,
        version: String = "2",
        username: String,
        listId: String
    ): TraktPublicListSummary {
        val response = client.get("users/$username/lists/$listId") {
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
        }
        return response.body()
    }


    suspend fun getUserListItems(
        clientId: String,
        version: String = "2",
        username: String,
        listId: String,
        type: String,
        extended: String = "full",
        page: Int = 1,
        limit: Int = 100
    ): List<TraktPublicListItem> {
        val response = client.get("users/$username/lists/$listId/items/$type") {
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            if (extended != null) parameter("extended", extended)
            if (page != null) parameter("page", page)
            if (limit != null) parameter("limit", limit)
        }
        return response.body()
    }


    suspend fun getListSummary(
        clientId: String,
        version: String = "2",
        listId: String
    ): TraktPublicListSummary {
        val response = client.get("lists/$listId") {
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
        }
        return response.body()
    }


    suspend fun getListItems(
        clientId: String,
        version: String = "2",
        listId: String,
        type: String,
        extended: String = "full",
        page: Int = 1,
        limit: Int = 100
    ): List<TraktPublicListItem> {
        val response = client.get("lists/$listId/items/$type") {
            header("trakt-api-key", clientId)
            header("trakt-api-version", version)
            if (extended != null) parameter("extended", extended)
            if (page != null) parameter("page", page)
            if (limit != null) parameter("limit", limit)
        }
        return response.body()
    }

}

// ========== Request Bodies ==========

@Serializable
data class DeviceCodeRequest(
    @SerialName("client_id") val clientId: String
)

@Serializable
data class TokenPollRequest(
    @SerialName("code") val code: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String
)

@Serializable
data class RefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
    @SerialName("grant_type") val grantType: String = "refresh_token"
)

@Serializable
data class TraktHistoryBody(
    val movies: List<TraktMovieId>? = null,
    val shows: List<TraktHistoryShowWithSeasons>? = null,
    val episodes: List<TraktEpisodeId>? = null
)

// For adding shows/episodes to history
// - With seasons: marks specific episodes
// - Without seasons (null): marks entire show
@Serializable
data class TraktHistoryShowWithSeasons(
    val ids: TraktIds,
    val seasons: List<TraktHistorySeason>? = null
)

@Serializable
data class TraktHistorySeason(
    val number: Int,
    val episodes: List<TraktHistoryEpisodeNumber>
)

@Serializable
data class TraktHistoryEpisodeNumber(
    val number: Int
)

@Serializable
data class TraktWatchlistBody(
    val movies: List<TraktMovieId>? = null,
    val shows: List<TraktShowId>? = null
)

@Serializable
data class TraktScrobbleBody(
    val movie: TraktMovieId? = null,
    val episode: TraktEpisodeId? = null,
    val show: TraktShowId? = null,
    val progress: Float
)

@Serializable
data class TraktMovieId(
    val ids: TraktIds
)

@Serializable
data class TraktShowId(
    val ids: TraktIds
)

@Serializable
data class TraktEpisodeId(
    val ids: TraktIds? = null,
    val season: Int? = null,
    val number: Int? = null
)

@Serializable
data class TraktIds(
    val trakt: Int? = null,
    val tmdb: Int? = null,
    val tvdb: Int? = null,
    val imdb: String? = null,
    val slug: String? = null
)

// ========== Response Models ==========

@Serializable
data class TraktDeviceCode(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_url") val verificationUrl: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int
)

@Serializable
data class TraktToken(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("token_type") val tokenType: String
)

/**
 * Last activities response for incremental sync
 * Each timestamp indicates when that activity type was last updated
 */
@Serializable
data class TraktLastActivities(
    val all: String?, // Overall last activity
    val movies: TraktActivityTimestamps?,
    val episodes: TraktActivityTimestamps?,
    val shows: TraktShowActivityTimestamps?,
    val seasons: TraktActivityTimestamps?,
    val comments: TraktActivityTimestamps?,
    val lists: TraktActivityTimestamps?,
    val watchlist: TraktActivityTimestamps?,
    val favorites: TraktActivityTimestamps?,
    val recommendations: TraktActivityTimestamps?,
    val collaborations: TraktActivityTimestamps?,
    val account: TraktActivityTimestamps?,
    @SerialName("saved_filters") val savedFilters: TraktActivityTimestamps?
)

@Serializable
data class TraktActivityTimestamps(
    @SerialName("watched_at") val watchedAt: String? = null,
    @SerialName("collected_at") val collectedAt: String? = null,
    @SerialName("rated_at") val ratedAt: String? = null,
    @SerialName("watchlisted_at") val watchlistedAt: String? = null,
    @SerialName("favorited_at") val favoritedAt: String? = null,
    @SerialName("commented_at") val commentedAt: String? = null,
    @SerialName("paused_at") val pausedAt: String? = null,
    @SerialName("hidden_at") val hiddenAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class TraktShowActivityTimestamps(
    @SerialName("watched_at") val watchedAt: String? = null,
    @SerialName("collected_at") val collectedAt: String? = null,
    @SerialName("rated_at") val ratedAt: String? = null,
    @SerialName("watchlisted_at") val watchlistedAt: String? = null,
    @SerialName("favorited_at") val favoritedAt: String? = null,
    @SerialName("commented_at") val commentedAt: String? = null,
    @SerialName("hidden_at") val hiddenAt: String? = null
)

@Serializable
data class TraktWatchedMovie(
    val plays: Int,
    @SerialName("last_watched_at") val lastWatchedAt: String?,
    @SerialName("last_updated_at") val lastUpdatedAt: String?,
    val movie: TraktMovieInfo
)

@Serializable
data class TraktWatchedShow(
    val plays: Int,
    @SerialName("last_watched_at") val lastWatchedAt: String?,
    @SerialName("last_updated_at") val lastUpdatedAt: String?,
    val show: TraktShowInfo,
    val seasons: List<TraktWatchedSeason>?
)

@Serializable
data class TraktWatchedSeason(
    val number: Int,
    val episodes: List<TraktWatchedEpisode>
)

@Serializable
data class TraktWatchedEpisode(
    val number: Int,
    val plays: Int,
    @SerialName("last_watched_at") val lastWatchedAt: String?
)

@Serializable
data class TraktPlaybackItem(
    val id: Long,
    val progress: Float,
    @SerialName("paused_at") val pausedAt: String?,
    val type: String,
    val movie: TraktMovieInfo?,
    val episode: TraktEpisodeInfo?,
    val show: TraktShowInfo?
)

@Serializable
data class TraktMovieInfo(
    val title: String,
    val year: Int?,
    val ids: TraktIds
)

@Serializable
data class TraktShowInfo(
    val title: String,
    val year: Int?,
    val ids: TraktIds
)

@Serializable
data class TraktHiddenItem(
    @SerialName("hidden_at") val hiddenAt: String?,
    val type: String?,
    val show: TraktShowInfo?
)

@Serializable
data class TraktEpisodeInfo(
    val season: Int,
    val number: Int,
    val title: String?,
    val ids: TraktIds
)

@Serializable
data class TraktWatchlistItem(
    val rank: Int,
    @SerialName("listed_at") val listedAt: String,
    val type: String,
    val movie: TraktMovieInfo?,
    val show: TraktShowInfo?
)

@Serializable
data class TraktShowProgress(
    val aired: Int,
    val completed: Int,
    @SerialName("last_watched_at") val lastWatchedAt: String?,
    @SerialName("reset_at") val resetAt: String?,
    @SerialName("next_episode") val nextEpisode: TraktNextEpisode?,
    val seasons: List<TraktProgressSeason>?
)

@Serializable
data class TraktNextEpisode(
    val season: Int,
    val number: Int,
    val title: String?,
    val ids: TraktIds
)

@Serializable
data class TraktProgressSeason(
    val number: Int,
    val aired: Int,
    val completed: Int,
    val episodes: List<TraktProgressEpisode>?
)

@Serializable
data class TraktProgressEpisode(
    val number: Int,
    val completed: Boolean,
    @SerialName("last_watched_at") val lastWatchedAt: String?
)

@Serializable
data class TraktListItem(
    val rank: Int,
    val type: String,
    val show: TraktShowInfo?
)

@Serializable
data class TraktPublicListSummary(
    val name: String,
    val description: String? = null
)

@Serializable
data class TraktPublicListItem(
    val rank: Int? = null,
    val type: String,
    val movie: TraktMovieInfo? = null,
    val show: TraktShowInfo? = null
)

@Serializable
data class TraktSyncResponse(
    val added: TraktSyncCounts?,
    val deleted: TraktSyncCounts?,
    val existing: TraktSyncCounts?,
    @SerialName("not_found") val notFound: TraktSyncNotFound?
)

@Serializable
data class TraktSyncCounts(
    val movies: Int = 0,
    val shows: Int = 0,
    val episodes: Int = 0
)

@Serializable
data class TraktSyncNotFound(
    val movies: List<TraktMovieId>?,
    val shows: List<TraktShowId>?,
    val episodes: List<TraktEpisodeId>?
)

@Serializable
data class TraktScrobbleResponse(
    val id: Long,
    val action: String,
    val progress: Float,
    val movie: TraktMovieInfo?,
    val episode: TraktEpisodeInfo?,
    val show: TraktShowInfo?
)

// ========== Search Models ==========

@Serializable
data class TraktSearchResult(
    val type: String,
    val score: Float?,
    val movie: TraktMovieInfo?,
    val show: TraktShowInfo?
)

@Serializable
data class TraktListSearchResult(
    val type: String,
    val score: Float?,
    val list: TraktSearchList?
)

@Serializable
data class TraktSearchList(
    val name: String? = null,
    val description: String? = null,
    val privacy: String? = null,
    val type: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("item_count") val itemCount: Int? = null,
    val likes: Int? = null,
    val ids: TraktSearchListIds? = null,
    val user: TraktSearchListUser? = null,
    val images: TraktSearchListImages? = null
)

@Serializable
data class TraktSearchListIds(
    val trakt: Int? = null,
    val slug: String? = null
)

@Serializable
data class TraktSearchListUser(
    val username: String? = null,
    val name: String? = null,
    val ids: TraktSearchListUserIds? = null
)

@Serializable
data class TraktSearchListUserIds(
    val slug: String? = null,
    val trakt: Int? = null
)

@Serializable
data class TraktSearchListImages(
    val posters: List<String> = emptyList()
)

// ========== Collection Models ==========

@Serializable
data class TraktCollectionBody(
    val movies: List<TraktMovieId>? = null,
    val shows: List<TraktShowId>? = null
)

@Serializable
data class TraktCollectionMovie(
    @SerialName("collected_at") val collectedAt: String?,
    @SerialName("updated_at") val updatedAt: String?,
    val movie: TraktMovieInfo
)

@Serializable
data class TraktCollectionShow(
    @SerialName("collected_at") val collectedAt: String?,
    @SerialName("updated_at") val updatedAt: String?,
    val show: TraktShowInfo,
    val seasons: List<TraktCollectionSeason>?
)

@Serializable
data class TraktCollectionSeason(
    val number: Int,
    val episodes: List<TraktCollectionEpisode>
)

@Serializable
data class TraktCollectionEpisode(
    val number: Int,
    @SerialName("collected_at") val collectedAt: String?
)

// ========== Rating Models ==========

@Serializable
data class TraktRatingBody(
    val movies: List<TraktRatingMovieItem>? = null,
    val shows: List<TraktRatingShowItem>? = null,
    val episodes: List<TraktRatingEpisodeItem>? = null
)

@Serializable
data class TraktRatingMovieItem(
    val rating: Int,
    @SerialName("rated_at") val ratedAt: String? = null,
    val ids: TraktIds
)

@Serializable
data class TraktRatingShowItem(
    val rating: Int,
    @SerialName("rated_at") val ratedAt: String? = null,
    val ids: TraktIds
)

@Serializable
data class TraktRatingEpisodeItem(
    val rating: Int,
    @SerialName("rated_at") val ratedAt: String? = null,
    val ids: TraktIds? = null,
    val season: Int? = null,
    val number: Int? = null
)

@Serializable
data class TraktRatingItem(
    @SerialName("rated_at") val ratedAt: String?,
    val rating: Int,
    val type: String,
    val movie: TraktMovieInfo?,
    val show: TraktShowInfo?,
    val episode: TraktEpisodeInfo?
)

// ========== Comment Models ==========

@Serializable
data class TraktComment(
    val id: Long,
    @SerialName("parent_id") val parentId: Long?,
    val comment: String,
    val spoiler: Boolean,
    val review: Boolean,
    val replies: Int,
    val likes: Int,
    @SerialName("user_stats") val userStats: TraktCommentUserStats?,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String?,
    val user: TraktUser?
)

@Serializable
data class TraktCommentUserStats(
    val rating: Int?,
    @SerialName("play_count") val playCount: Int?,
    @SerialName("completed_count") val completedCount: Int?
)

@Serializable
data class TraktUser(
    val username: String,
    val private: Boolean,
    val name: String?,
    val vip: Boolean?,
    @SerialName("vip_ep") val vipEp: Boolean?,
    val ids: TraktUserIds?
)

@Serializable
data class TraktUserIds(
    val slug: String?
)

// ========== History Models ==========

@Serializable
data class TraktHistoryItem(
    val id: Long,
    @SerialName("watched_at") val watchedAt: String,
    val action: String,
    val type: String,
    val movie: TraktMovieInfo?,
    val show: TraktShowInfo?,
    val episode: TraktEpisodeInfo?
)

@Serializable
data class TraktHistoryRemoveBody(
    val ids: List<Long>? = null,
    val movies: List<TraktMovieId>? = null,
    val shows: List<TraktShowId>? = null,
    val episodes: List<TraktEpisodeId>? = null,
    val seasons: List<TraktSeasonId>? = null
)

@Serializable
data class TraktSeasonId(
    val ids: TraktIds? = null,
    val seasons: List<TraktSeasonNumber>? = null
)

@Serializable
data class TraktSeasonNumber(
    val number: Int,
    val episodes: List<TraktEpisodeNumber>? = null
)

@Serializable
data class TraktEpisodeNumber(
    val number: Int
)

// ========== Bulk Watch Models ==========

@Serializable
data class TraktBulkShowBody(
    val shows: List<TraktBulkShowItem>
)

@Serializable
data class TraktBulkShowItem(
    val ids: TraktIds,
    val seasons: List<TraktBulkSeasonItem>? = null
)

@Serializable
data class TraktBulkSeasonItem(
    val number: Int,
    val episodes: List<TraktBulkEpisodeItem>? = null
)

@Serializable
data class TraktBulkEpisodeItem(
    val number: Int,
    @SerialName("watched_at") val watchedAt: String? = null
)


