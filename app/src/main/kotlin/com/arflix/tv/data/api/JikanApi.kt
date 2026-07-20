package com.arflix.tv.data.api

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Jikan v4 is an unofficial REST API for MyAnimeList.net data.
 *
 * Used by ARVIO to display the MAL community score next to IMDB/TMDB ratings
 * on anime details pages. See issue #45.
 *
 * Base URL: `https://api.jikan.moe/v4/`
 * Rate limit: ~3 req/s, 60 req/min (unofficial, subject to change). ARVIO
 * caches scores in memory so a typical details load performs at most one
 * Jikan request per unique MAL ID per session.
 *
 * Docs: https://docs.api.jikan.moe/
 */
interface JikanApi {
    @GET("anime/{malId}")
    suspend fun getAnime(@Path("malId") malId: Int): JikanAnimeResponse
}

@Keep
@Serializable
data class JikanAnimeResponse(
    @SerialName("data") val data: JikanAnimeData?
)

@Keep
@Serializable
data class JikanAnimeData(
    @SerialName("mal_id") val malId: Int?,
    @SerialName("title") val title: String?,
    /** Community score 0-10 with 2 decimal places. Null if the entry is too new or unscored. */
    @SerialName("score") val score: Double?,
    @SerialName("scored_by") val scoredBy: Int?
)
