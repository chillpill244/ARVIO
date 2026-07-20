package com.arflix.tv.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * TMDB API interface
 */
interface TmdbApi {
    
    @GET("trending/movie/day")
    suspend fun getTrendingMovies(
        @Query("api_key") apiKey: String,
        @Query("language") language: String? = null,
        @Query("page") page: Int = 1
    ): TmdbListResponse

    @GET("trending/tv/day")
    suspend fun getTrendingTv(
        @Query("api_key") apiKey: String,
        @Query("language") language: String? = null,
        @Query("page") page: Int = 1
    ): TmdbListResponse
    
    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("api_key") apiKey: String,
        @Query("with_watch_providers") watchProviders: Int? = null,
        @Query("watch_region") watchRegion: String = "US",
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("with_genres") genres: String? = null,
        @Query("with_people") people: String? = null,
        @Query("with_original_language") originalLanguage: String? = null,
        @Query("first_air_date_year") year: Int? = null,
        @Query("vote_count.gte") minVoteCount: Int? = null,
        @Query("with_keywords") keywords: String? = null,
        @Query("air_date.gte") airDateGte: String? = null,
        @Query("air_date.lte") airDateLte: String? = null,
        @Query("language") language: String? = null,
        @Query("page") page: Int = 1
    ): TmdbListResponse

    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("api_key") apiKey: String,
        @Query("with_genres") genres: String? = null,
        @Query("with_crew") crew: String? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("vote_count.gte") minVoteCount: Int? = null,
        @Query("with_keywords") keywords: String? = null,
        @Query("with_original_language") originalLanguage: String? = null,
        @Query("primary_release_year") year: Int? = null,
        @Query("release_date.gte") releaseDateGte: String? = null,
        @Query("release_date.lte") releaseDateLte: String? = null,
        @Query("with_watch_providers") watchProviders: Int? = null,
        @Query("watch_region") watchRegion: String? = null,
        @Query("language") language: String? = null,
        @Query("page") page: Int = 1
    ): TmdbListResponse
    
    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String? = null
    ): TmdbMovieDetails
    
    @GET("tv/{tv_id}")
    suspend fun getTvDetails(
        @Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String? = null
    ): TmdbTvDetails
    
    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getTvSeason(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String? = null
    ): TmdbSeasonDetails

    @GET("tv/{tv_id}/season/{season_number}/episode/{episode_number}/external_ids")
    suspend fun getTvEpisodeExternalIds(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int,
        @Path("episode_number") episodeNumber: Int,
        @Query("api_key") apiKey: String
    ): TmdbExternalIds
    
    @GET("{media_type}/{id}/credits")
    suspend fun getCredits(
        @Path("media_type") mediaType: String,
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String? = null
    ): TmdbCreditsResponse
    
    @GET("{media_type}/{id}/similar")
    suspend fun getSimilar(
        @Path("media_type") mediaType: String,
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String? = null
    ): TmdbListResponse

    @GET("{media_type}/{id}/recommendations")
    suspend fun getRecommendations(
        @Path("media_type") mediaType: String,
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String? = null
    ): TmdbListResponse

    @GET("{media_type}/{id}/images")
    suspend fun getImages(
        @Path("media_type") mediaType: String,
        @Path("id") id: Int,
        @Query("api_key") apiKey: String
    ): TmdbImagesResponse
    
    @GET("{media_type}/{id}/videos")
    suspend fun getVideos(
        @Path("media_type") mediaType: String,
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String? = null
    ): TmdbVideosResponse
    
    @GET("person/{person_id}")
    suspend fun getPersonDetails(
        @Path("person_id") personId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "combined_credits",
        @Query("language") language: String? = null
    ): TmdbPersonDetails

    @GET("movie/{movie_id}/external_ids")
    suspend fun getMovieExternalIds(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String
    ): TmdbExternalIds

    @GET("tv/{tv_id}/external_ids")
    suspend fun getTvExternalIds(
        @Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String
    ): TmdbExternalIds

    @GET("movie/{movie_id}/watch/providers")
    suspend fun getMovieWatchProviders(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String
    ): TmdbWatchProvidersResponse

    @GET("tv/{tv_id}/watch/providers")
    suspend fun getTvWatchProviders(
        @Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String
    ): TmdbWatchProvidersResponse
    
    @GET("search/multi")
    suspend fun searchMulti(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String? = null,
        @Query("page") page: Int = 1
    ): TmdbListResponse

    @GET("search/movie")
    suspend fun searchMovies(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String? = null,
        @Query("page") page: Int = 1,
        @Query("primary_release_year") primaryReleaseYear: Int? = null,
        @Query("year") year: Int? = null
    ): TmdbListResponse

    @GET("search/tv")
    suspend fun searchTv(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String? = null,
        @Query("page") page: Int = 1,
        @Query("first_air_date_year") firstAirDateYear: Int? = null
    ): TmdbListResponse

    @GET("find/{external_id}")
    suspend fun findByExternalId(
        @Path("external_id") externalId: String,
        @Query("api_key") apiKey: String,
        @Query("external_source") externalSource: String = "imdb_id"
    ): TmdbFindResponse

    @GET("{media_type}/{id}/reviews")
    suspend fun getReviews(
        @Path("media_type") mediaType: String,
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String? = null
    ): TmdbReviewsResponse

    /**
     * TMDB "collection" endpoint. Returns the explicit list of films that belong
     * to a franchise (e.g. Harry Potter = 1241, LOTR = 119, James Bond = 645).
     * Used by the Collections feature to populate franchise rows without
     * relying on external addons.
     */
    @GET("collection/{collection_id}")
    suspend fun getTmdbCollection(
        @Path("collection_id") collectionId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String? = null
    ): TmdbCollectionResponse
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
