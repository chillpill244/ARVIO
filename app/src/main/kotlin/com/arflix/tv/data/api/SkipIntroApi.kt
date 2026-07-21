package com.arflix.tv.data.api

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * IntroDB segments API.
 */
class IntroDbApi(private val client: HttpClient) {
    suspend fun getSegments(
        imdbId: String,
        season: Int,
        episode: Int
    ): IntroDbSegmentsResponse {
        return client.get("segments") {
            parameter("imdb_id", imdbId)
            parameter("season", season)
            parameter("episode", episode)
        }.body()
    }
}

@Keep
@Serializable
data class IntroDbSegmentsResponse(
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("season") val season: Int? = null,
    @SerialName("episode") val episode: Int? = null,
    @SerialName("intro") val intro: IntroDbSegment? = null,
    @SerialName("recap") val recap: IntroDbSegment? = null,
    @SerialName("outro") val outro: IntroDbSegment? = null
)

@Keep
@Serializable
data class IntroDbSegment(
    @SerialName("start_ms") val startMs: Long = 0L,
    @SerialName("end_ms") val endMs: Long = 0L,
    @SerialName("start_sec") val startSec: Double? = null,
    @SerialName("end_sec") val endSec: Double? = null,
    @SerialName("confidence") val confidence: Double? = null,
    @SerialName("submission_count") val submissionCount: Int? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/**
 * AniSkip API (anime OP/ED/recap skip times).
 */
class AniSkipApi(private val client: HttpClient) {
    suspend fun getSkipTimes(
        malId: String,
        episode: Int,
        types: List<String>,
        episodeLength: Int = 0
    ): AniSkipResponse {
        return client.get("skip-times/$malId/$episode") {
            parameter("types", types)
            parameter("episodeLength", episodeLength)
        }.body()
    }
}

@Keep
@Serializable
data class AniSkipResponse(
    @SerialName("found") val found: Boolean = false,
    @SerialName("results") val results: List<AniSkipResult>? = null
)

@Keep
@Serializable
data class AniSkipResult(
    @SerialName("interval") val interval: AniSkipInterval,
    @SerialName("skipType") val skipType: String,
    @SerialName("skipId") val skipId: String? = null
)

@Keep
@Serializable
data class AniSkipInterval(
    @SerialName("startTime") val startTime: Double,
    @SerialName("endTime") val endTime: Double
)

/**
 * ARM API (IMDB -> MAL ID resolution).
 */
class ArmApi(private val client: HttpClient) {
    suspend fun resolve(
        imdbId: String,
        include: String = "myanimelist"
    ): List<ArmEntry> {
        return client.get("imdb") {
            parameter("id", imdbId)
            parameter("include", include)
        }.body()
    }
}

@Keep
@Serializable
data class ArmEntry(
    @SerialName("myanimelist") val myanimelist: Int? = null
)
