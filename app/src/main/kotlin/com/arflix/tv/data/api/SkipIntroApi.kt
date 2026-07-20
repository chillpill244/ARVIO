package com.arflix.tv.data.api

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * IntroDB segments API.
 */
interface IntroDbApi {
    @GET("segments")
    suspend fun getSegments(
        @Query("imdb_id") imdbId: String,
        @Query("season") season: Int,
        @Query("episode") episode: Int
    ): IntroDbSegmentsResponse
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
interface AniSkipApi {
    @GET("skip-times/{malId}/{episode}")
    suspend fun getSkipTimes(
        @Path("malId") malId: String,
        @Path("episode") episode: Int,
        @Query("types") types: List<String>,
        @Query("episodeLength") episodeLength: Int = 0
    ): AniSkipResponse
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
interface ArmApi {
    @GET("imdb")
    suspend fun resolve(
        @Query("id") imdbId: String,
        @Query("include") include: String = "myanimelist"
    ): List<ArmEntry>
}

@Keep
@Serializable
data class ArmEntry(
    @SerialName("myanimelist") val myanimelist: Int? = null
)
