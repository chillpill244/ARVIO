package com.arflix.tv.ui.screens.movies

import android.content.Context
import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.repository.IptvRepository
import com.arflix.tv.ui.screens.shared.MediaCategoryViewModel
import com.arflix.tv.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class MoviesViewModel @Inject constructor(
    iptvRepository: IptvRepository,
    private val tmdbApi: TmdbApi,
    @ApplicationContext context: Context
) : MediaCategoryViewModel(MediaType.MOVIE, iptvRepository, context) {

    override suspend fun fetchCategoriesAndItems(): Pair<List<String>, Map<String, List<MediaItem>>> {
        val categoriesList = iptvRepository.getVodCategories()
        val categoriesItemsMap = iptvRepository.getVodStreamsByCategory()

        // Build category items map
        val newCategoriesMap = mutableMapOf<String, List<MediaItem>>()
        val categoryNames = mutableListOf<String>()

        categoriesList.forEach { category ->
            val streams = categoriesItemsMap[category.categoryId] ?: emptyList()
            val items = streams.mapNotNull { stream ->
                iptvRepository.convertVodStreamToMediaItem(stream)
            }
            if (items.isNotEmpty()) {
                categoryNames.add(category.categoryName)
                newCategoriesMap[category.categoryName] = items
            }
        }

        return categoryNames to newCategoriesMap
    }

    suspend fun getMovieStreamUrl(streamId: Int, extension: String): String? {
        return iptvRepository.getVodStreamUrl(streamId, extension)
    }

    /**
     * Enrich movie item with TMDB metadata before navigation.
     * Fetches VOD info to get tmdb_id, then fetches TMDB details (cast, genres, images).
     * Builds stream URL and attaches to vodStreams to avoid redundant fetching in DetailsViewModel.
     * Completely overrides VOD data with TMDB metadata.
     * Preserves iptvMovieId for VOD lookups. If no tmdb_id found, returns item with iptvMovieId only (id will be 0).
     */
    suspend fun getMovieDetailsWithTmdbId(item: MediaItem): MediaItem? {
        return try {
            // Fetch VOD info to get tmdb_id
            val vodInfo = iptvRepository.getVodInfo(item.id)
            
            // Build VOD stream URL from the fetched info (to avoid redundant API call later)
            val vodStreams = if (vodInfo?.movieData?.streamId != null && !vodInfo.movieData.extension.isNullOrBlank()) {
                val streamUrl = iptvRepository.getVodStreamUrl(vodInfo.movieData.streamId, vodInfo.movieData.extension)
                if (!streamUrl.isNullOrBlank()) {
                    listOf(
                        com.arflix.tv.data.model.StreamSource(
                            source = vodInfo.info?.name ?: "IPTV VOD",
                            addonName = "IPTV",
                            addonId = "iptv_xtream_vod",
                            quality = "HD",
                            size = "",
                            url = streamUrl
                        )
                    )
                } else emptyList()
            } else emptyList()
            
            if (vodInfo != null && !vodInfo.info?.tmdbId.isNullOrBlank()) {
                val tmdbIdAsInt = vodInfo.info?.tmdbId?.toIntOrNull() ?: return item.copy(
                    id = 0,
                    iptvMovieId = item.id.toString(),
                    vodStreams = vodStreams
                )
                
                // Fetch full TMDB details using tmdb_id from VOD info
                val movieDetails = runCatching {
                    tmdbApi.getMovieDetails(tmdbIdAsInt, Constants.TMDB_API_KEY)
                }.getOrNull()
                
                if (movieDetails != null) {
                    // Return enriched item with TMDB data (completely override item data but preserve iptvMovieId)
                    val enrichedItem = item.copy(
                        id = tmdbIdAsInt,
                        title = movieDetails.title,
                        overview = movieDetails.overview ?: "",
                        image = movieDetails.posterPath?.let { Constants.IMAGE_BASE + it } ?: "",
                        backdrop = movieDetails.backdropPath?.let { Constants.BACKDROP_BASE_LARGE + it },
                        tmdbRating = movieDetails.voteAverage.toString(),
                        releaseDate = movieDetails.releaseDate ?: "",
                        year = movieDetails.releaseDate?.take(4) ?: "",
                        status = "Released",
                        genreIds = movieDetails.genres.map { it.id },
                        budget = movieDetails.budget.takeIf { it > 0 },
                        // Preserve iptvMovieId for VOD info lookups
                        iptvMovieId = item.id.toString(),
                        // Attach pre-built vodStreams to skip redundant fetch in DetailsViewModel
                        vodStreams = vodStreams
                    )
                    android.util.Log.d("MoviesViewModel", "Enriched item with TMDB data: ${enrichedItem.title} (ID: ${enrichedItem.id}, genres: ${movieDetails.genres.map { it.name }}, vodStreams: ${vodStreams.size})")
                    enrichedItem
                } else {
                    // TMDB fetch failed, return item with iptvMovieId only
                    item.copy(id = 0, iptvMovieId = item.id.toString(), vodStreams = vodStreams)
                }
            } else {
                // If no tmdb_id found in VOD info, return the original item with vodStreams
                item.copy(iptvMovieId = item.id.toString(), vodStreams = vodStreams)
            }
        } catch (e: Exception) {
            android.util.Log.e("MoviesViewModel", "Error enriching movie: ${e.message}", e)
            // On error, return original item so navigation can still happen
            item.copy(iptvMovieId = item.id.toString())
        }
    }
}
