package com.muvio.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val BASE_URL = "https://api.trakt.tv"

class TraktClient(
    private val httpClient: HttpClient,
    private val clientId: String,
) {
    private fun traktHeaders(builder: io.ktor.client.request.HttpRequestBuilder, accessToken: String? = null) {
        builder.header("trakt-api-key", clientId)
        builder.header("trakt-api-version", "2")
        accessToken?.let { builder.header("Authorization", "Bearer $it") }
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    suspend fun getDeviceCode(clientId: String): TraktDeviceCode =
        httpClient.post("$BASE_URL/oauth/device/code") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("client_id" to clientId))
        }.body()

    suspend fun pollToken(code: String, clientId: String, clientSecret: String): TraktToken =
        httpClient.post("$BASE_URL/oauth/device/token") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("code" to code, "client_id" to clientId, "client_secret" to clientSecret))
        }.body()

    suspend fun refreshToken(refreshToken: String, clientId: String, clientSecret: String): TraktToken =
        httpClient.post("$BASE_URL/oauth/token") {
            contentType(ContentType.Application.Json)
            setBody(mapOf(
                "refresh_token" to refreshToken,
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "grant_type" to "refresh_token",
            ))
        }.body()

    // ── Sync ──────────────────────────────────────────────────────────────────

    suspend fun getLastActivities(accessToken: String): TraktLastActivities =
        httpClient.get("$BASE_URL/sync/last_activities") {
            traktHeaders(this, accessToken)
        }.body()

    suspend fun getWatchedMovies(accessToken: String): List<TraktWatchedMovie> =
        httpClient.get("$BASE_URL/sync/watched/movies") {
            traktHeaders(this, accessToken)
        }.body()

    suspend fun getWatchedShows(accessToken: String): List<TraktWatchedShow> =
        httpClient.get("$BASE_URL/sync/watched/shows") {
            traktHeaders(this, accessToken)
        }.body()

    suspend fun getPlaybackProgress(accessToken: String, type: String? = null): List<TraktPlaybackItem> =
        httpClient.get("$BASE_URL/sync/playback") {
            traktHeaders(this, accessToken)
            if (type != null) parameter("type", type)
        }.body()

    // ── History ───────────────────────────────────────────────────────────────

    suspend fun addToHistory(accessToken: String, body: TraktHistoryBody): TraktSyncResponse =
        httpClient.post("$BASE_URL/sync/history") {
            traktHeaders(this, accessToken)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun removeFromHistory(accessToken: String, body: TraktHistoryBody): TraktSyncResponse =
        httpClient.post("$BASE_URL/sync/history/remove") {
            traktHeaders(this, accessToken)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    // ── Scrobble ──────────────────────────────────────────────────────────────

    suspend fun scrobbleStart(accessToken: String, body: TraktScrobbleBody): TraktScrobbleResponse =
        httpClient.post("$BASE_URL/scrobble/start") {
            traktHeaders(this, accessToken)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun scrobblePause(accessToken: String, body: TraktScrobbleBody): TraktScrobbleResponse =
        httpClient.post("$BASE_URL/scrobble/pause") {
            traktHeaders(this, accessToken)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun scrobbleStop(accessToken: String, body: TraktScrobbleBody): TraktScrobbleResponse =
        httpClient.post("$BASE_URL/scrobble/stop") {
            traktHeaders(this, accessToken)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    // ── Search ────────────────────────────────────────────────────────────────

    suspend fun searchByTmdb(tmdbId: Int, type: String): List<TraktSearchResult> =
        httpClient.get("$BASE_URL/search/tmdb/$tmdbId") {
            traktHeaders(this)
            parameter("type", type)
        }.body()

    // ── Watchlist ─────────────────────────────────────────────────────────────

    suspend fun getWatchlist(accessToken: String, type: String = "movies"): List<TraktWatchlistItem> =
        httpClient.get("$BASE_URL/sync/watchlist/$type") {
            traktHeaders(this, accessToken)
            parameter("extended", "full")
        }.body()

    suspend fun addToWatchlist(accessToken: String, body: TraktWatchlistBody): TraktSyncResponse =
        httpClient.post("$BASE_URL/sync/watchlist") {
            traktHeaders(this, accessToken)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun removeFromWatchlist(accessToken: String, body: TraktWatchlistBody): TraktSyncResponse =
        httpClient.post("$BASE_URL/sync/watchlist/remove") {
            traktHeaders(this, accessToken)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    // ── Ratings ───────────────────────────────────────────────────────────────

    suspend fun addRating(accessToken: String, body: TraktRatingBody): TraktSyncResponse =
        httpClient.post("$BASE_URL/sync/ratings") {
            traktHeaders(this, accessToken)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun removeRating(accessToken: String, body: TraktRatingBody): TraktSyncResponse =
        httpClient.post("$BASE_URL/sync/ratings/remove") {
            traktHeaders(this, accessToken)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    suspend fun removePlaybackItem(accessToken: String, id: Long) =
        httpClient.delete("$BASE_URL/sync/playback/$id") {
            traktHeaders(this, accessToken)
        }
}

// ── Response DTOs ──────────────────────────────────────────────────────────────

@Serializable
data class TraktDeviceCode(
    @SerialName("device_code") val deviceCode: String = "",
    @SerialName("user_code") val userCode: String = "",
    @SerialName("verification_url") val verificationUrl: String = "",
    @SerialName("expires_in") val expiresIn: Int = 600,
    val interval: Int = 5,
)

@Serializable
data class TraktToken(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("token_type") val tokenType: String = "Bearer",
)

@Serializable
data class TraktLastActivities(
    val movies: TraktMovieActivities? = null,
    val episodes: TraktEpisodeActivities? = null,
    val shows: TraktShowActivities? = null,
)

@Serializable
data class TraktMovieActivities(@SerialName("watched_at") val watchedAt: String? = null)

@Serializable
data class TraktEpisodeActivities(@SerialName("watched_at") val watchedAt: String? = null)

@Serializable
data class TraktShowActivities(@SerialName("watched_at") val watchedAt: String? = null)

@Serializable
data class TraktWatchedMovie(
    @SerialName("last_watched_at") val lastWatchedAt: String? = null,
    val movie: TraktMovie? = null,
)

@Serializable
data class TraktWatchedShow(
    @SerialName("last_watched_at") val lastWatchedAt: String? = null,
    val show: TraktShow? = null,
    val seasons: List<TraktWatchedSeason> = emptyList(),
)

@Serializable
data class TraktWatchedSeason(
    val number: Int = 0,
    val episodes: List<TraktWatchedEpisode> = emptyList(),
)

@Serializable
data class TraktWatchedEpisode(
    val number: Int = 0,
    @SerialName("last_watched_at") val lastWatchedAt: String? = null,
)

@Serializable
data class TraktPlaybackItem(
    val id: Long = 0,
    val progress: Float = 0f,
    val type: String = "",
    val movie: TraktMovie? = null,
    val show: TraktShow? = null,
    val episode: TraktEpisode? = null,
)

@Serializable
data class TraktMovie(
    val title: String = "",
    val year: Int? = null,
    val ids: TraktIds = TraktIds(),
)

@Serializable
data class TraktShow(
    val title: String = "",
    val year: Int? = null,
    val ids: TraktIds = TraktIds(),
)

@Serializable
data class TraktEpisode(
    val season: Int = 0,
    val number: Int = 0,
    val title: String? = null,
    val ids: TraktIds = TraktIds(),
)

@Serializable
data class TraktIds(
    val trakt: Int = 0,
    val slug: String? = null,
    val tmdb: Int? = null,
    val imdb: String? = null,
    val tvdb: Int? = null,
)

@Serializable
data class TraktSyncResponse(val added: TraktSyncCounts? = null, val deleted: TraktSyncCounts? = null)

@Serializable
data class TraktSyncCounts(val movies: Int = 0, val episodes: Int = 0)

@Serializable
data class TraktScrobbleBody(val movie: TraktMovieRef? = null, val show: TraktShowRef? = null, val episode: TraktEpisodeRef? = null, val progress: Float = 0f)

@Serializable
data class TraktMovieRef(val ids: TraktIds)

@Serializable
data class TraktShowRef(val ids: TraktIds)

@Serializable
data class TraktEpisodeRef(val season: Int, val number: Int)

@Serializable
data class TraktScrobbleResponse(val id: Long = 0, val progress: Float = 0f, val action: String = "")

@Serializable
data class TraktHistoryBody(val movies: List<TraktMovieRef> = emptyList(), val shows: List<TraktShowRef> = emptyList(), val episodes: List<TraktEpisodeRef> = emptyList())

@Serializable
data class TraktWatchlistBody(val movies: List<TraktMovieRef> = emptyList(), val shows: List<TraktShowRef> = emptyList())

@Serializable
data class TraktRatingBody(val movies: List<TraktRatedMovie> = emptyList(), val shows: List<TraktRatedShow> = emptyList())

@Serializable
data class TraktRatedMovie(val ids: TraktIds, val rating: Int)

@Serializable
data class TraktRatedShow(val ids: TraktIds, val rating: Int)

@Serializable
data class TraktSearchResult(val type: String = "", val movie: TraktMovie? = null, val show: TraktShow? = null)

@Serializable
data class TraktWatchlistItem(
    @SerialName("listed_at") val listedAt: String? = null,
    val type: String = "",
    val movie: TraktMovie? = null,
    val show: TraktShow? = null,
)
