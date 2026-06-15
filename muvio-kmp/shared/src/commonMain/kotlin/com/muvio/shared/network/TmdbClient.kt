package com.muvio.shared.network

import com.muvio.shared.domain.*
import com.muvio.shared.util.toDecStr
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val BASE_URL = "https://api.themoviedb.org/3"
private const val IMAGE_BASE = "https://image.tmdb.org/t/p"

private fun Float.to1f(): String = if (this > 0f) toDouble().toDecStr(1) else ""

class TmdbClient(private val httpClient: HttpClient, private val apiKey: String) {

    // ── Trending ──────────────────────────────────────────────────────────────

    suspend fun getTrendingMovies(page: Int = 1, language: String? = null): TmdbListResponse =
        httpClient.get("$BASE_URL/trending/movie/day") {
            parameter("api_key", apiKey)
            parameter("page", page)
            language?.let { parameter("language", it) }
        }.body()

    suspend fun getTrendingTv(page: Int = 1, language: String? = null): TmdbListResponse =
        httpClient.get("$BASE_URL/trending/tv/day") {
            parameter("api_key", apiKey)
            parameter("page", page)
            language?.let { parameter("language", it) }
        }.body()

    // ── Discover ──────────────────────────────────────────────────────────────

    suspend fun discoverMovies(
        genres: String? = null,
        sortBy: String = "popularity.desc",
        minVoteCount: Int? = null,
        keywords: String? = null,
        year: Int? = null,
        watchProviders: Int? = null,
        watchRegion: String? = null,
        language: String? = null,
        page: Int = 1,
    ): TmdbListResponse =
        httpClient.get("$BASE_URL/discover/movie") {
            parameter("api_key", apiKey)
            parameter("sort_by", sortBy)
            parameter("page", page)
            genres?.let { parameter("with_genres", it) }
            minVoteCount?.let { parameter("vote_count.gte", it) }
            keywords?.let { parameter("with_keywords", it) }
            year?.let { parameter("primary_release_year", it) }
            watchProviders?.let { parameter("with_watch_providers", it) }
            watchRegion?.let { parameter("watch_region", it) }
            language?.let { parameter("language", it) }
        }.body()

    suspend fun discoverTv(
        genres: String? = null,
        sortBy: String = "popularity.desc",
        minVoteCount: Int? = null,
        keywords: String? = null,
        watchProviders: Int? = null,
        watchRegion: String = "US",
        language: String? = null,
        page: Int = 1,
    ): TmdbListResponse =
        httpClient.get("$BASE_URL/discover/tv") {
            parameter("api_key", apiKey)
            parameter("sort_by", sortBy)
            parameter("page", page)
            parameter("watch_region", watchRegion)
            genres?.let { parameter("with_genres", it) }
            minVoteCount?.let { parameter("vote_count.gte", it) }
            keywords?.let { parameter("with_keywords", it) }
            watchProviders?.let { parameter("with_watch_providers", it) }
            language?.let { parameter("language", it) }
        }.body()

    // ── Details ───────────────────────────────────────────────────────────────

    suspend fun getMovieDetails(movieId: Int, language: String? = null): TmdbMovieDetails =
        httpClient.get("$BASE_URL/movie/$movieId") {
            parameter("api_key", apiKey)
            language?.let { parameter("language", it) }
        }.body()

    suspend fun getTvDetails(tvId: Int, language: String? = null): TmdbTvDetails =
        httpClient.get("$BASE_URL/tv/$tvId") {
            parameter("api_key", apiKey)
            language?.let { parameter("language", it) }
        }.body()

    suspend fun getTvSeason(tvId: Int, seasonNumber: Int, language: String? = null): TmdbSeasonDetails =
        httpClient.get("$BASE_URL/tv/$tvId/season/$seasonNumber") {
            parameter("api_key", apiKey)
            language?.let { parameter("language", it) }
        }.body()

    suspend fun getMovieExternalIds(movieId: Int): TmdbExternalIds =
        httpClient.get("$BASE_URL/movie/$movieId/external_ids") {
            parameter("api_key", apiKey)
        }.body()

    suspend fun getTvExternalIds(tvId: Int): TmdbExternalIds =
        httpClient.get("$BASE_URL/tv/$tvId/external_ids") {
            parameter("api_key", apiKey)
        }.body()

    suspend fun getTvEpisodeExternalIds(tvId: Int, seasonNumber: Int, episodeNumber: Int): TmdbExternalIds =
        httpClient.get("$BASE_URL/tv/$tvId/season/$seasonNumber/episode/$episodeNumber/external_ids") {
            parameter("api_key", apiKey)
        }.body()

    // ── Credits / Images / Videos ─────────────────────────────────────────────

    suspend fun getCredits(mediaType: String, id: Int, language: String? = null): TmdbCreditsResponse =
        httpClient.get("$BASE_URL/$mediaType/$id/credits") {
            parameter("api_key", apiKey)
            language?.let { parameter("language", it) }
        }.body()

    suspend fun getImages(mediaType: String, id: Int): TmdbImagesResponse =
        httpClient.get("$BASE_URL/$mediaType/$id/images") {
            parameter("api_key", apiKey)
        }.body()

    suspend fun getVideos(mediaType: String, id: Int, language: String? = null): TmdbVideosResponse =
        httpClient.get("$BASE_URL/$mediaType/$id/videos") {
            parameter("api_key", apiKey)
            language?.let { parameter("language", it) }
        }.body()

    // ── Similar / Recommendations ─────────────────────────────────────────────

    suspend fun getSimilar(mediaType: String, id: Int, language: String? = null): TmdbListResponse =
        httpClient.get("$BASE_URL/$mediaType/$id/similar") {
            parameter("api_key", apiKey)
            language?.let { parameter("language", it) }
        }.body()

    suspend fun getRecommendations(mediaType: String, id: Int, language: String? = null): TmdbListResponse =
        httpClient.get("$BASE_URL/$mediaType/$id/recommendations") {
            parameter("api_key", apiKey)
            language?.let { parameter("language", it) }
        }.body()

    // ── Person ────────────────────────────────────────────────────────────────

    suspend fun getPersonDetails(personId: Int, language: String? = null): TmdbPersonDetails =
        httpClient.get("$BASE_URL/person/$personId") {
            parameter("api_key", apiKey)
            parameter("append_to_response", "combined_credits")
            language?.let { parameter("language", it) }
        }.body()

    // ── Search ────────────────────────────────────────────────────────────────

    suspend fun searchMulti(query: String, language: String? = null, page: Int = 1): TmdbListResponse =
        httpClient.get("$BASE_URL/search/multi") {
            parameter("api_key", apiKey)
            parameter("query", query)
            parameter("page", page)
            language?.let { parameter("language", it) }
        }.body()

    suspend fun searchMovies(query: String, language: String? = null, page: Int = 1): TmdbListResponse =
        httpClient.get("$BASE_URL/search/movie") {
            parameter("api_key", apiKey)
            parameter("query", query)
            parameter("page", page)
            language?.let { parameter("language", it) }
        }.body()

    suspend fun searchTv(query: String, language: String? = null, page: Int = 1): TmdbListResponse =
        httpClient.get("$BASE_URL/search/tv") {
            parameter("api_key", apiKey)
            parameter("query", query)
            parameter("page", page)
            language?.let { parameter("language", it) }
        }.body()

    // ── Find / Reviews / Collection ────────────────────────────────────────────

    suspend fun findByImdbId(imdbId: String): TmdbFindResponse =
        httpClient.get("$BASE_URL/find/$imdbId") {
            parameter("api_key", apiKey)
            parameter("external_source", "imdb_id")
        }.body()

    suspend fun getReviews(mediaType: String, id: Int, language: String? = null): TmdbReviewsResponse =
        httpClient.get("$BASE_URL/$mediaType/$id/reviews") {
            parameter("api_key", apiKey)
            language?.let { parameter("language", it) }
        }.body()

    suspend fun getCollection(collectionId: Int, language: String? = null): TmdbCollectionResponse =
        httpClient.get("$BASE_URL/collection/$collectionId") {
            parameter("api_key", apiKey)
            language?.let { parameter("language", it) }
        }.body()

    // ── Mapper helpers ────────────────────────────────────────────────────────

    companion object {
        fun posterUrl(path: String?, size: String = "w500"): String =
            path?.let { "$IMAGE_BASE/$size$it" } ?: ""

        fun backdropUrl(path: String?, size: String = "w1280"): String =
            path?.let { "$IMAGE_BASE/$size$it" } ?: ""

        fun TmdbMediaItem.toDomain(): MediaItem {
            val type = when (mediaType?.lowercase()) {
                "tv", "show", "series" -> MediaType.TV
                else -> if (firstAirDate != null && title == null) MediaType.TV else MediaType.MOVIE
            }
            return MediaItem(
                id = id,
                title = (title ?: name ?: originalTitle ?: originalName ?: "").trim(),
                overview = overview.orEmpty(),
                year = (releaseDate ?: firstAirDate ?: "").take(4),
                releaseDate = releaseDate ?: firstAirDate,
                rating = voteAverage.to1f(),
                tmdbRating = voteAverage.to1f(),
                mediaType = type,
                image = posterUrl(posterPath),
                backdrop = backdropUrl(backdropPath),
                genreIds = genreIds,
                originalLanguage = originalLanguage,
                character = character.orEmpty(),
                popularity = popularity,
            )
        }

        fun TmdbEpisode.toDomain(): Episode = Episode(
            id = id,
            episodeNumber = episodeNumber,
            seasonNumber = seasonNumber,
            name = name,
            overview = overview.orEmpty(),
            stillPath = stillPath?.let { "$IMAGE_BASE/w300$it" },
            voteAverage = voteAverage,
            runtime = runtime ?: 0,
            airDate = airDate.orEmpty(),
        )

        fun TmdbCastMember.toDomain(): CastMember = CastMember(
            id = id,
            name = name,
            character = character.orEmpty(),
            profilePath = profilePath?.let { "$IMAGE_BASE/w185$it" },
        )

        fun TmdbReview.toDomain(): Review = Review(
            id = id,
            author = author,
            authorUsername = authorDetails?.username.orEmpty(),
            authorAvatar = authorDetails?.avatarPath?.let { "$IMAGE_BASE/w185$it" },
            content = content,
            rating = authorDetails?.rating,
            createdAt = createdAt,
        )
    }
}

// ── Response DTOs (kotlinx.serialization) ─────────────────────────────────────

@Serializable
data class TmdbListResponse(
    val page: Int = 1,
    val results: List<TmdbMediaItem> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_results") val totalResults: Int = 0,
)

@Serializable
data class TmdbMediaItem(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    val overview: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Float = 0f,
    @SerialName("vote_count") val voteCount: Int = 0,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val adult: Boolean = false,
    val popularity: Float = 0f,
    val character: String? = null,
    @SerialName("known_for") val knownFor: List<TmdbMediaItem> = emptyList(),
)

@Serializable
data class TmdbMovieDetails(
    val id: Int = 0,
    val title: String = "",
    @SerialName("original_title") val originalTitle: String? = null,
    val overview: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("vote_average") val voteAverage: Float = 0f,
    val runtime: Int? = null,
    val budget: Long = 0,
    val genres: List<TmdbGenre> = emptyList(),
    val status: String? = null,
    val adult: Boolean = false,
    @SerialName("belongs_to_collection") val belongsToCollection: TmdbCollectionRef? = null,
)

@Serializable
data class TmdbCollectionRef(
    val id: Int = 0,
    val name: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
)

@Serializable
data class TmdbTvDetails(
    val id: Int = 0,
    val name: String = "",
    @SerialName("original_name") val originalName: String? = null,
    val overview: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("vote_average") val voteAverage: Float = 0f,
    @SerialName("number_of_seasons") val numberOfSeasons: Int = 1,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int = 0,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    val status: String? = null,
    val genres: List<TmdbGenre> = emptyList(),
    val seasons: List<TmdbTvSeason> = emptyList(),
)

@Serializable
data class TmdbTvSeason(
    val id: Int = 0,
    @SerialName("season_number") val seasonNumber: Int = 1,
    @SerialName("episode_count") val episodeCount: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("air_date") val airDate: String? = null,
)

@Serializable
data class TmdbSeasonDetails(
    val id: Int = 0,
    @SerialName("season_number") val seasonNumber: Int = 1,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val episodes: List<TmdbEpisode> = emptyList(),
)

@Serializable
data class TmdbEpisode(
    val id: Int = 0,
    @SerialName("episode_number") val episodeNumber: Int = 1,
    @SerialName("season_number") val seasonNumber: Int = 1,
    val name: String = "",
    val overview: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("vote_average") val voteAverage: Float = 0f,
    val runtime: Int? = null,
    @SerialName("air_date") val airDate: String? = null,
)

@Serializable
data class TmdbGenre(val id: Int = 0, val name: String = "")

@Serializable
data class TmdbCreditsResponse(
    val id: Int = 0,
    val cast: List<TmdbCastMember> = emptyList(),
    val crew: List<TmdbCrewMember> = emptyList(),
)

@Serializable
data class TmdbCastMember(
    val id: Int = 0,
    val name: String = "",
    val character: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    val order: Int = 0,
)

@Serializable
data class TmdbCrewMember(
    val id: Int = 0,
    val name: String = "",
    val job: String = "",
    @SerialName("profile_path") val profilePath: String? = null,
    val department: String = "",
)

@Serializable
data class TmdbImagesResponse(
    val id: Int = 0,
    val logos: List<TmdbImage> = emptyList(),
    val backdrops: List<TmdbImage> = emptyList(),
)

@Serializable
data class TmdbImage(
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("iso_639_1") val iso6391: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("vote_average") val voteAverage: Float = 0f,
    @SerialName("vote_count") val voteCount: Int = 0,
)

@Serializable
data class TmdbVideosResponse(
    val id: Int = 0,
    val results: List<TmdbVideo> = emptyList(),
)

@Serializable
data class TmdbVideo(
    val id: String = "",
    val key: String = "",
    val name: String = "",
    val site: String = "",
    val type: String = "",
    val official: Boolean = false,
)

@Serializable
data class TmdbExternalIds(
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("tvdb_id") val tvdbId: Int? = null,
)

@Serializable
data class TmdbPersonDetails(
    val id: Int = 0,
    val name: String = "",
    val biography: String? = null,
    @SerialName("place_of_birth") val placeOfBirth: String? = null,
    val birthday: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    @SerialName("combined_credits") val combinedCredits: TmdbCombinedCredits? = null,
)

@Serializable
data class TmdbCombinedCredits(val cast: List<TmdbMediaItem> = emptyList())

@Serializable
data class TmdbReviewsResponse(
    val id: Int = 0,
    val page: Int = 1,
    val results: List<TmdbReview> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_results") val totalResults: Int = 0,
)

@Serializable
data class TmdbReview(
    val id: String = "",
    val author: String = "",
    @SerialName("author_details") val authorDetails: TmdbAuthorDetails? = null,
    val content: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class TmdbAuthorDetails(
    val name: String = "",
    val username: String = "",
    @SerialName("avatar_path") val avatarPath: String? = null,
    val rating: Float? = null,
)

@Serializable
data class TmdbFindResponse(
    @SerialName("movie_results") val movieResults: List<TmdbFindItem> = emptyList(),
    @SerialName("tv_results") val tvResults: List<TmdbFindItem> = emptyList(),
)

@Serializable
data class TmdbFindItem(val id: Int = 0, val popularity: Float = 0f)

@Serializable
data class TmdbCollectionResponse(
    val id: Int = 0,
    val name: String = "",
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val parts: List<TmdbMediaItem> = emptyList(),
)
