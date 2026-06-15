package com.muvio.shared.repository

import com.muvio.shared.domain.*
import com.muvio.shared.network.TmdbClient
import com.muvio.shared.network.TmdbClient.Companion.toDomain
import com.muvio.shared.network.TmdbClient.Companion.backdropUrl
import com.muvio.shared.network.TmdbClient.Companion.posterUrl
import com.muvio.shared.util.toDecStr
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private fun voteAverageStr(v: Float): String = v.toDouble().toDecStr(1)

class MediaRepository(private val tmdb: TmdbClient) {

    // ── Home feed ─────────────────────────────────────────────────────────────

    suspend fun getTrendingMovies(page: Int = 1): List<MediaItem> =
        tmdb.getTrendingMovies(page).results.map { it.toDomain() }

    suspend fun getTrendingTv(page: Int = 1): List<MediaItem> =
        tmdb.getTrendingTv(page).results.map { it.toDomain() }

    suspend fun discoverMovies(
        genres: String? = null,
        sortBy: String = "popularity.desc",
        minVoteCount: Int? = null,
        keywords: String? = null,
        year: Int? = null,
        watchProviders: Int? = null,
        watchRegion: String? = null,
        page: Int = 1,
    ): List<MediaItem> =
        tmdb.discoverMovies(genres, sortBy, minVoteCount, keywords, year, watchProviders, watchRegion, page = page)
            .results.map { it.toDomain() }

    suspend fun discoverTv(
        genres: String? = null,
        sortBy: String = "popularity.desc",
        minVoteCount: Int? = null,
        keywords: String? = null,
        watchProviders: Int? = null,
        watchRegion: String = "US",
        page: Int = 1,
    ): List<MediaItem> =
        tmdb.discoverTv(genres, sortBy, minVoteCount, keywords, watchProviders, watchRegion, page = page)
            .results.map { it.toDomain() }

    // ── Details ───────────────────────────────────────────────────────────────

    suspend fun getMovieDetails(tmdbId: Int): MovieDetails = coroutineScope {
        val detailsDeferred = async { tmdb.getMovieDetails(tmdbId) }
        val creditsDeferred = async { runCatching { tmdb.getCredits("movie", tmdbId) }.getOrNull() }
        val similarDeferred = async { runCatching { tmdb.getSimilar("movie", tmdbId) }.getOrNull() }
        val imagesDeferred = async { runCatching { tmdb.getImages("movie", tmdbId) }.getOrNull() }
        val videosDeferred = async { runCatching { tmdb.getVideos("movie", tmdbId) }.getOrNull() }
        val externalIdsDeferred = async { runCatching { tmdb.getMovieExternalIds(tmdbId) }.getOrNull() }

        val details = detailsDeferred.await()
        val credits = creditsDeferred.await()
        val similar = similarDeferred.await()
        val images = imagesDeferred.await()
        val videos = videosDeferred.await()
        val externalIds = externalIdsDeferred.await()

        val mediaItem = MediaItem(
            id = details.id,
            title = details.title,
            overview = details.overview.orEmpty(),
            year = details.releaseDate?.take(4).orEmpty(),
            releaseDate = details.releaseDate,
            rating = if (details.voteAverage > 0) voteAverageStr(details.voteAverage) else "",
            tmdbRating = if (details.voteAverage > 0) voteAverageStr(details.voteAverage) else "",
            mediaType = MediaType.MOVIE,
            image = posterUrl(details.posterPath),
            backdrop = backdropUrl(details.backdropPath),
            duration = details.runtime?.let { "${it}min" } ?: "",
            originalLanguage = details.originalLanguage,
            status = details.status,
            budget = details.budget,
        )

        MovieDetails(
            item = mediaItem,
            imdbId = externalIds?.imdbId,
            cast = credits?.cast?.take(20)?.map { it.toDomain() } ?: emptyList(),
            similar = similar?.results?.take(20)?.map { it.toDomain() } ?: emptyList(),
            trailerKey = videos?.results
                ?.filter { it.site == "YouTube" && it.type in listOf("Trailer", "Teaser") && it.official }
                ?.firstOrNull()?.key,
            clearLogoUrl = images?.logos
                ?.filter { it.iso6391 == "en" || it.iso6391 == null }
                ?.maxByOrNull { it.voteAverage }
                ?.filePath?.let { "https://image.tmdb.org/t/p/w500$it" },
            genres = details.genres.map { it.name },
        )
    }

    suspend fun getTvDetails(tmdbId: Int): TvDetails = coroutineScope {
        val detailsDeferred = async { tmdb.getTvDetails(tmdbId) }
        val creditsDeferred = async { runCatching { tmdb.getCredits("tv", tmdbId) }.getOrNull() }
        val similarDeferred = async { runCatching { tmdb.getSimilar("tv", tmdbId) }.getOrNull() }
        val imagesDeferred = async { runCatching { tmdb.getImages("tv", tmdbId) }.getOrNull() }
        val videosDeferred = async { runCatching { tmdb.getVideos("tv", tmdbId) }.getOrNull() }
        val externalIdsDeferred = async { runCatching { tmdb.getTvExternalIds(tmdbId) }.getOrNull() }

        val details = detailsDeferred.await()
        val credits = creditsDeferred.await()
        val similar = similarDeferred.await()
        val images = imagesDeferred.await()
        val videos = videosDeferred.await()
        val externalIds = externalIdsDeferred.await()

        val mediaItem = MediaItem(
            id = details.id,
            title = details.name,
            overview = details.overview.orEmpty(),
            year = details.firstAirDate?.take(4).orEmpty(),
            releaseDate = details.firstAirDate,
            rating = if (details.voteAverage > 0) voteAverageStr(details.voteAverage) else "",
            tmdbRating = if (details.voteAverage > 0) voteAverageStr(details.voteAverage) else "",
            mediaType = MediaType.TV,
            image = posterUrl(details.posterPath),
            backdrop = backdropUrl(details.backdropPath),
            duration = details.episodeRunTime.firstOrNull()?.let { "${it}min" } ?: "",
            originalLanguage = details.originalLanguage,
            status = details.status,
            totalEpisodes = details.numberOfEpisodes,
            isOngoing = details.status == "Returning Series",
        )

        TvDetails(
            item = mediaItem,
            imdbId = externalIds?.imdbId,
            tvdbId = externalIds?.tvdbId,
            cast = credits?.cast?.take(20)?.map { it.toDomain() } ?: emptyList(),
            similar = similar?.results?.take(20)?.map { it.toDomain() } ?: emptyList(),
            trailerKey = videos?.results
                ?.filter { it.site == "YouTube" && it.type in listOf("Trailer", "Teaser") && it.official }
                ?.firstOrNull()?.key,
            clearLogoUrl = images?.logos
                ?.filter { it.iso6391 == "en" || it.iso6391 == null }
                ?.maxByOrNull { it.voteAverage }
                ?.filePath?.let { "https://image.tmdb.org/t/p/w500$it" },
            seasons = details.seasons.filter { it.seasonNumber > 0 }.map { s ->
                SeasonInfo(
                    seasonNumber = s.seasonNumber,
                    name = s.name ?: "Season ${s.seasonNumber}",
                    episodeCount = s.episodeCount,
                    posterUrl = posterUrl(s.posterPath, "w342"),
                    airDate = s.airDate,
                )
            },
            genres = details.genres.map { it.name },
        )
    }

    suspend fun getEpisodes(tmdbId: Int, seasonNumber: Int): List<Episode> =
        tmdb.getTvSeason(tmdbId, seasonNumber).episodes.map { it.toDomain() }

    // ── Search ────────────────────────────────────────────────────────────────

    suspend fun searchMulti(query: String, page: Int = 1): List<MediaItem> =
        tmdb.searchMulti(query, page = page).results.map { it.toDomain() }

    suspend fun searchMovies(query: String, page: Int = 1): List<MediaItem> =
        tmdb.searchMovies(query, page = page).results.map { it.toDomain() }

    suspend fun searchTv(query: String, page: Int = 1): List<MediaItem> =
        tmdb.searchTv(query, page = page).results.map { it.toDomain() }

    // ── Person ────────────────────────────────────────────────────────────────

    suspend fun getPersonDetails(personId: Int): PersonDetails {
        val p = tmdb.getPersonDetails(personId)
        return PersonDetails(
            id = p.id,
            name = p.name,
            biography = p.biography.orEmpty(),
            placeOfBirth = p.placeOfBirth,
            birthday = p.birthday,
            profilePath = p.profilePath?.let { "https://image.tmdb.org/t/p/w185$it" },
            knownFor = p.combinedCredits?.cast?.take(20)?.map { it.toDomain() } ?: emptyList(),
        )
    }

    // ── Reviews ───────────────────────────────────────────────────────────────

    suspend fun getReviews(mediaType: String, tmdbId: Int): List<Review> =
        tmdb.getReviews(mediaType, tmdbId).results.map { it.toDomain() }

    // ── IMDB lookup ───────────────────────────────────────────────────────────

    suspend fun findByImdbId(imdbId: String): MediaItem? {
        val resp = tmdb.findByImdbId(imdbId)
        return (resp.movieResults.firstOrNull() ?: resp.tvResults.firstOrNull())
            ?.let {
                // TmdbFindItem only has id and popularity; build a stub MediaItem
                MediaItem(id = it.id, title = "", popularity = it.popularity)
            }
    }
}

// ── Value objects returned from details queries ────────────────────────────────

data class MovieDetails(
    val item: MediaItem,
    val imdbId: String?,
    val cast: List<CastMember>,
    val similar: List<MediaItem>,
    val trailerKey: String?,
    val clearLogoUrl: String?,
    val genres: List<String>,
)

data class TvDetails(
    val item: MediaItem,
    val imdbId: String?,
    val tvdbId: Int?,
    val cast: List<CastMember>,
    val similar: List<MediaItem>,
    val trailerKey: String?,
    val clearLogoUrl: String?,
    val seasons: List<SeasonInfo>,
    val genres: List<String>,
)

data class SeasonInfo(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int,
    val posterUrl: String,
    val airDate: String?,
)
