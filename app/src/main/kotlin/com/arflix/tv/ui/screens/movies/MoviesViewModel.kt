package com.arflix.tv.ui.screens.movies

import com.arflix.tv.data.repository.PreferenceStore
import com.arflix.tv.data.repository.PlatformEnvironment

import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.repository.IptvRepository
import com.arflix.tv.ui.screens.shared.MediaCategoryViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MoviesViewModel @Inject constructor(
    iptvRepository: IptvRepository,
    private val tmdbApi: TmdbApi,
    preferenceStore: PreferenceStore, platformEnvironment: PlatformEnvironment,) : MediaCategoryViewModel(MediaType.MOVIE, iptvRepository, preferenceStore, platformEnvironment) {

    override suspend fun fetchCategoriesAndItems(): Pair<List<String>, Map<String, List<MediaItem>>> {
        val catalog = iptvRepository.getVodCatalog()
        val categories = catalog.keys.toList()
        return categories to catalog
    }

    suspend fun getMovieDetailsWithTmdbId(item: MediaItem): MediaItem? {
        val vodId = item.iptvMovieId?.toIntOrNull() ?: return item
        return try {
            val vodInfo = iptvRepository.getVodInfo(vodId)
            val tmdbIdStr = vodInfo?.info?.tmdbId
            val tmdbId = tmdbIdStr?.toIntOrNull()
            if (tmdbId != null && tmdbId > 0) {
                val movieDetails = runCatching {
                    tmdbApi.getMovieDetails(tmdbId, com.arflix.tv.util.Constants.TMDB_API_KEY)
                }.getOrNull()
                if (movieDetails != null) {
                    item.copy(
                        id = tmdbId,
                        title = movieDetails.title,
                        overview = movieDetails.overview ?: "",
                        image = movieDetails.posterPath?.let { com.arflix.tv.util.Constants.IMAGE_BASE + it } ?: item.image,
                        backdrop = movieDetails.backdropPath?.let { com.arflix.tv.util.Constants.BACKDROP_BASE_LARGE + it },
                        tmdbRating = movieDetails.voteAverage.toString(),
                        releaseDate = movieDetails.releaseDate ?: "",
                        year = movieDetails.releaseDate?.take(4) ?: item.year,
                        status = "Released",
                        genreIds = movieDetails.genres.map { it.id },
                        iptvMovieId = item.iptvMovieId
                    )
                } else {
                    item.copy(id = 0, iptvMovieId = item.iptvMovieId)
                }
            } else {
                item.copy(id = 0, iptvMovieId = item.iptvMovieId)
            }
        } catch (e: Exception) {
            item.copy(id = 0, iptvMovieId = item.iptvMovieId)
        }
    }
}
