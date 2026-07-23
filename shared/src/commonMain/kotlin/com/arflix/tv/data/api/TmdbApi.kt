package com.arflix.tv.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.call.*

/**
 * TMDB API interface
 */
class TmdbApi(private val client: HttpClient) {
    
    suspend fun getTrendingMovies(
        apiKey: String,
        language: String? = null,
        page: Int = 1
    ): TmdbListResponse {
        return client.get("trending/movie/day") {
            parameter("api_key", apiKey)
            if (language != null) parameter("language", language)
            parameter("page", page)
        }.body()
    }

    suspend fun getTrendingTv(
        apiKey: String,
        language: String? = null,
        page: Int = 1
    ): TmdbListResponse {
        return client.get("trending/tv/day") {
            parameter("api_key", apiKey)
            if (language != null) parameter("language", language)
            parameter("page", page)
        }.body()
    }
    
    suspend fun discoverTv(
        apiKey: String,
        watchProviders: Int? = null,
        watchRegion: String = "US",
        sortBy: String = "popularity.desc",
        genres: String? = null,
        people: String? = null,
        originalLanguage: String? = null,
        year: Int? = null,
        minVoteCount: Int? = null,
        keywords: String? = null,
        airDateGte: String? = null,
        airDateLte: String? = null,
        language: String? = null,
        page: Int = 1
    ): TmdbListResponse {
        return client.get("discover/tv") {
            parameter("api_key", apiKey)
            if (watchProviders != null) parameter("with_watch_providers", watchProviders)
            parameter("watch_region", watchRegion)
            parameter("sort_by", sortBy)
            if (genres != null) parameter("with_genres", genres)
            if (people != null) parameter("with_people", people)
            if (originalLanguage != null) parameter("with_original_language", originalLanguage)
            if (year != null) parameter("first_air_date_year", year)
            if (minVoteCount != null) parameter("vote_count.gte", minVoteCount)
            if (keywords != null) parameter("with_keywords", keywords)
            if (airDateGte != null) parameter("air_date.gte", airDateGte)
            if (airDateLte != null) parameter("air_date.lte", airDateLte)
            if (language != null) parameter("language", language)
            parameter("page", page)
        }.body()
    }

    suspend fun discoverMovies(
        apiKey: String,
        genres: String? = null,
        crew: String? = null,
        sortBy: String = "popularity.desc",
        minVoteCount: Int? = null,
        keywords: String? = null,
        originalLanguage: String? = null,
        year: Int? = null,
        releaseDateGte: String? = null,
        releaseDateLte: String? = null,
        watchProviders: Int? = null,
        watchRegion: String? = null,
        language: String? = null,
        page: Int = 1
    ): TmdbListResponse {
        return client.get("discover/movie") {
            parameter("api_key", apiKey)
            if (genres != null) parameter("with_genres", genres)
            if (crew != null) parameter("with_crew", crew)
            parameter("sort_by", sortBy)
            if (minVoteCount != null) parameter("vote_count.gte", minVoteCount)
            if (keywords != null) parameter("with_keywords", keywords)
            if (originalLanguage != null) parameter("with_original_language", originalLanguage)
            if (year != null) parameter("primary_release_year", year)
            if (releaseDateGte != null) parameter("release_date.gte", releaseDateGte)
            if (releaseDateLte != null) parameter("release_date.lte", releaseDateLte)
            if (watchProviders != null) parameter("with_watch_providers", watchProviders)
            if (watchRegion != null) parameter("watch_region", watchRegion)
            if (language != null) parameter("language", language)
            parameter("page", page)
        }.body()
    }
    
    suspend fun getMovieDetails(
        movieId: Int,
        apiKey: String,
        language: String? = null
    ): TmdbMovieDetails {
        return client.get("movie/$movieId") {
            parameter("api_key", apiKey)
            if (language != null) parameter("language", language)
        }.body()
    }
    
    suspend fun getTvDetails(
        tvId: Int,
        apiKey: String,
        language: String? = null
    ): TmdbTvDetails {
        return client.get("tv/$tvId") {
            parameter("api_key", apiKey)
            if (language != null) parameter("language", language)
        }.body()
    }
    
    suspend fun getTvSeason(
        tvId: Int,
        seasonNumber: Int,
        apiKey: String,
        language: String? = null
    ): TmdbSeasonDetails {
        return client.get("tv/$tvId/season/$seasonNumber") {
            parameter("api_key", apiKey)
            if (language != null) parameter("language", language)
        }.body()
    }

    suspend fun getTvEpisodeExternalIds(
        tvId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        apiKey: String
    ): TmdbExternalIds {
        return client.get("tv/$tvId/season/$seasonNumber/episode/$episodeNumber/external_ids") {
            parameter("api_key", apiKey)
        }.body()
    }
    
    suspend fun getCredits(
        mediaType: String,
        id: Int,
        apiKey: String,
        language: String? = null
    ): TmdbCreditsResponse {
        return client.get("$mediaType/$id/credits") {
            parameter("api_key", apiKey)
            if (language != null) parameter("language", language)
        }.body()
    }
    
    suspend fun getSimilar(
        mediaType: String,
        id: Int,
        apiKey: String,
        language: String? = null
    ): TmdbListResponse {
        return client.get("$mediaType/$id/similar") {
            parameter("api_key", apiKey)
            if (language != null) parameter("language", language)
        }.body()
    }

    suspend fun getRecommendations(
        mediaType: String,
        id: Int,
        apiKey: String,
        language: String? = null
    ): TmdbListResponse {
        return client.get("$mediaType/$id/recommendations") {
            parameter("api_key", apiKey)
            if (language != null) parameter("language", language)
        }.body()
    }

    suspend fun getImages(
        mediaType: String,
        id: Int,
        apiKey: String
    ): TmdbImagesResponse {
        return client.get("$mediaType/$id/images") {
            parameter("api_key", apiKey)
        }.body()
    }
    
    suspend fun getVideos(
        mediaType: String,
        id: Int,
        apiKey: String,
        language: String? = null
    ): TmdbVideosResponse {
        return client.get("$mediaType/$id/videos") {
            parameter("api_key", apiKey)
            if (language != null) parameter("language", language)
        }.body()
    }
    
    suspend fun getPersonDetails(
        personId: Int,
        apiKey: String,
        appendToResponse: String = "combined_credits",
        language: String? = null
    ): TmdbPersonDetails {
        return client.get("person/$personId") {
            parameter("api_key", apiKey)
            parameter("append_to_response", appendToResponse)
            if (language != null) parameter("language", language)
        }.body()
    }

    suspend fun getMovieExternalIds(
        movieId: Int,
        apiKey: String
    ): TmdbExternalIds {
        return client.get("movie/$movieId/external_ids") {
            parameter("api_key", apiKey)
        }.body()
    }

    suspend fun getTvExternalIds(
        tvId: Int,
        apiKey: String
    ): TmdbExternalIds {
        return client.get("tv/$tvId/external_ids") {
            parameter("api_key", apiKey)
        }.body()
    }

    suspend fun getMovieWatchProviders(
        movieId: Int,
        apiKey: String
    ): TmdbWatchProvidersResponse {
        return client.get("movie/$movieId/watch/providers") {
            parameter("api_key", apiKey)
        }.body()
    }

    suspend fun getTvWatchProviders(
        tvId: Int,
        apiKey: String
    ): TmdbWatchProvidersResponse {
        return client.get("tv/$tvId/watch/providers") {
            parameter("api_key", apiKey)
        }.body()
    }
    
    suspend fun searchMulti(
        apiKey: String,
        query: String,
        language: String? = null,
        page: Int = 1
    ): TmdbListResponse {
        return client.get("search/multi") {
            parameter("api_key", apiKey)
            parameter("query", query)
            if (language != null) parameter("language", language)
            parameter("page", page)
        }.body()
    }

    suspend fun searchMovies(
        apiKey: String,
        query: String,
        language: String? = null,
        page: Int = 1,
        primaryReleaseYear: Int? = null,
        year: Int? = null
    ): TmdbListResponse {
        return client.get("search/movie") {
            parameter("api_key", apiKey)
            parameter("query", query)
            if (language != null) parameter("language", language)
            parameter("page", page)
            if (primaryReleaseYear != null) parameter("primary_release_year", primaryReleaseYear)
            if (year != null) parameter("year", year)
        }.body()
    }

    suspend fun searchTv(
        apiKey: String,
        query: String,
        language: String? = null,
        page: Int = 1,
        firstAirDateYear: Int? = null
    ): TmdbListResponse {
        return client.get("search/tv") {
            parameter("api_key", apiKey)
            parameter("query", query)
            if (language != null) parameter("language", language)
            parameter("page", page)
            if (firstAirDateYear != null) parameter("first_air_date_year", firstAirDateYear)
        }.body()
    }

    suspend fun findByExternalId(
        externalId: String,
        apiKey: String,
        externalSource: String = "imdb_id"
    ): TmdbFindResponse {
        return client.get("find/$externalId") {
            parameter("api_key", apiKey)
            parameter("external_source", externalSource)
        }.body()
    }

    suspend fun getReviews(
        mediaType: String,
        id: Int,
        apiKey: String,
        language: String? = null
    ): TmdbReviewsResponse {
        return client.get("$mediaType/$id/reviews") {
            parameter("api_key", apiKey)
            if (language != null) parameter("language", language)
        }.body()
    }

    /**
     * TMDB "collection" endpoint. Returns the explicit list of films that belong
     * to a franchise (e.g. Harry Potter = 1241, LOTR = 119, James Bond = 645).
     * Used by the Collections feature to populate franchise rows without
     * relying on external addons.
     */
    suspend fun getTmdbCollection(
        collectionId: Int,
        apiKey: String,
        language: String? = null
    ): TmdbCollectionResponse {
        return client.get("collection/$collectionId") {
            parameter("api_key", apiKey)
            if (language != null) parameter("language", language)
        }.body()
    }
}

// Response data classes

@Serializable
data class TmdbListResponse(
    val page: Int = 1,
    val results: List<TmdbMediaItem> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_results") val totalResults: Int = 0
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
    @SerialName("known_for") val knownFor: List<TmdbMediaItem> = emptyList()
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
    @SerialName("belongs_to_collection") val belongsToCollection: TmdbCollectionRef? = null
)

/** Reference to a TMDB collection (franchise) returned inside movie/TV details. */
@Serializable
data class TmdbCollectionRef(
    val id: Int = 0,
    val name: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null
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
    val seasons: List<TmdbTvSeason> = emptyList()
)

@Serializable
data class TmdbSeasonDetails(
    val id: Int = 0,
    @SerialName("season_number") val seasonNumber: Int = 1,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val episodes: List<TmdbEpisode> = emptyList()
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
    @SerialName("air_date") val airDate: String? = null
)

@Serializable
data class TmdbGenre(val id: Int = 0, val name: String = "")
@Serializable
data class TmdbCreditsResponse(val id: Int = 0, val cast: List<TmdbCastMember> = emptyList(), val crew: List<TmdbCrewMember> = emptyList())
@Serializable
data class TmdbCastMember(val id: Int = 0, val name: String = "", val character: String? = null, @SerialName("profile_path") val profilePath: String? = null, val order: Int = 0)
@Serializable
data class TmdbCrewMember(val id: Int = 0, val name: String = "", val job: String = "", @SerialName("profile_path") val profilePath: String? = null, val department: String = "")
@Serializable
data class TmdbImagesResponse(val id: Int = 0, val logos: List<TmdbImage> = emptyList(), val backdrops: List<TmdbImage> = emptyList())
@Serializable
data class TmdbImage(@SerialName("file_path") val filePath: String? = null, @SerialName("iso_639_1") val iso6391: String? = null, val width: Int = 0, val height: Int = 0, @SerialName("vote_average") val voteAverage: Float = 0f, @SerialName("vote_count") val voteCount: Int = 0)
@Serializable
data class TmdbVideosResponse(val id: Int = 0, val results: List<TmdbVideo> = emptyList())
@Serializable
data class TmdbVideo(val id: String = "", val key: String = "", val name: String = "", val site: String = "", val type: String = "", val official: Boolean = false)
@Serializable
data class TmdbExternalIds(@SerialName("imdb_id") val imdbId: String? = null, @SerialName("tvdb_id") val tvdbId: Int? = null)
@Serializable
data class TmdbWatchProvidersResponse(val id: Int = 0, val results: Map<String, TmdbWatchProviderRegion> = emptyMap())
@Serializable
data class TmdbWatchProviderRegion(val link: String? = null, val flatrate: List<TmdbWatchProvider> = emptyList(), val free: List<TmdbWatchProvider> = emptyList(), val ads: List<TmdbWatchProvider> = emptyList(), val rent: List<TmdbWatchProvider> = emptyList(), val buy: List<TmdbWatchProvider> = emptyList())
@Serializable
data class TmdbWatchProvider(@SerialName("provider_id") val providerId: Int = 0, @SerialName("provider_name") val providerName: String = "", @SerialName("logo_path") val logoPath: String? = null, @SerialName("display_priority") val displayPriority: Int = 0)
@Serializable
data class TmdbPersonDetails(val id: Int = 0, val name: String = "", val biography: String? = null, @SerialName("place_of_birth") val placeOfBirth: String? = null, val birthday: String? = null, @SerialName("profile_path") val profilePath: String? = null, @SerialName("combined_credits") val combinedCredits: TmdbCombinedCredits? = null)
@Serializable
data class TmdbCombinedCredits(val cast: List<TmdbMediaItem> = emptyList())
@Serializable
data class TmdbReviewsResponse(val id: Int = 0, val page: Int = 1, val results: List<TmdbReview> = emptyList(), @SerialName("total_pages") val totalPages: Int = 1, @SerialName("total_results") val totalResults: Int = 0)
@Serializable
data class TmdbReview(val id: String = "", val author: String = "", @SerialName("author_details") val authorDetails: TmdbAuthorDetails? = null, val content: String = "", @SerialName("created_at") val createdAt: String = "", @SerialName("updated_at") val updatedAt: String = "", val url: String = "")
@Serializable
data class TmdbAuthorDetails(val name: String = "", val username: String = "", @SerialName("avatar_path") val avatarPath: String? = null, val rating: Float? = null)
@Serializable
data class TmdbFindResponse(@SerialName("movie_results") val movieResults: List<TmdbFindItem> = emptyList(), @SerialName("tv_results") val tvResults: List<TmdbFindItem> = emptyList())
@Serializable
data class TmdbFindItem(val id: Int = 0, val popularity: Float = 0f)

/** Response for /collection/{id} — the `parts` array contains the films in a franchise. */
@Serializable
data class TmdbCollectionResponse(
    val id: Int = 0,
    val name: String = "",
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val parts: List<TmdbMediaItem> = emptyList()
)

@Serializable
data class TmdbTvSeason(
    val id: Int = 0,
    @SerialName("season_number") val seasonNumber: Int = 1,
    @SerialName("episode_count") val episodeCount: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("air_date") val airDate: String? = null
)
