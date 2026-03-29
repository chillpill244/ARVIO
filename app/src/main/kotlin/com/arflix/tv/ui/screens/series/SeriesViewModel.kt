package com.arflix.tv.ui.screens.series

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
class SeriesViewModel @Inject constructor(
    iptvRepository: IptvRepository,
    private val tmdbApi: TmdbApi,
    @ApplicationContext context: Context
) : MediaCategoryViewModel(MediaType.TV, iptvRepository, context) {

    override suspend fun fetchCategoriesAndItems(): Pair<List<String>, Map<String, List<MediaItem>>> {
        val categoriesList = iptvRepository.getSeriesCategories()
        val categoriesItemsMap = iptvRepository.getSeriesByCategoryMap()

        // Build category items map
        val newCategoriesMap = mutableMapOf<String, List<MediaItem>>()
        val categoryNames = mutableListOf<String>()

        categoriesList.forEach { category ->
            val seriesList = categoriesItemsMap[category.categoryId] ?: emptyList()
            val items = seriesList.mapNotNull { series ->
                iptvRepository.convertSeriesItemToMediaItem(series)
            }
            if (items.isNotEmpty()) {
                categoryNames.add(category.categoryName)
                newCategoriesMap[category.categoryName] = items
            }
        }

        return categoryNames to newCategoriesMap
    }

    /**
     * Enrich series item with TMDB metadata before navigation.
     * Searches TMDB, fetches full details (cast, genres, images), populates MediaItem.
     * If TMDB search fails, returns item with iptvSeriesId only (id will be 0).
     */
    suspend fun getSeriesDetailsWithTmdbId(item: MediaItem): MediaItem? {
        return try {
            // Search TMDB for this series
            val searchResults = tmdbApi.searchMulti(
                apiKey = Constants.TMDB_API_KEY,
                query = item.title
            )
            
            // Find the first TV show match
            val tvShowResult = searchResults.results.firstOrNull { result ->
                result.mediaType == "tv"
            }
            
            if (tvShowResult != null && tvShowResult.id != 0) {
                // Fetch full TMDB details
                val tvDetails = runCatching {
                    tmdbApi.getTvDetails(tvShowResult.id, Constants.TMDB_API_KEY)
                }.getOrNull()
                
                if (tvDetails != null) {
                    // Return enriched item with TMDB data (completely override item data)
                    val enrichedItem = item.copy(
                        id = tvShowResult.id,
                        title = tvDetails.name,
                        overview = tvDetails.overview ?: "",
                        image = tvDetails.posterPath?.let { Constants.IMAGE_BASE + it } ?: "",
                        backdrop = tvDetails.backdropPath?.let { Constants.BACKDROP_BASE_LARGE + it },
                        tmdbRating = tvDetails.voteAverage.toString(),
                        releaseDate = tvDetails.firstAirDate ?: "",
                        year = tvDetails.firstAirDate?.take(4) ?: "",
                        status = tvDetails.status,
                        genreIds = tvDetails.genres.map { it.id }
                    )
                    android.util.Log.d("SeriesViewModel", "Enriched item with TMDB data: ${enrichedItem.title} (ID: ${enrichedItem.id}, genres: ${tvDetails.genres.map { it.name }})")
                    enrichedItem
                } else {
                    // TMDB fetch failed, return item with iptvSeriesId only
                    item.copy(id = 0)
                }
            } else {
                // No TMDB match found, return item with iptvSeriesId only, id = 0
                item.copy(id = 0)
            }
        } catch (e: Exception) {
            // On error, return item with id = 0
            item.copy(id = 0)
        }
    }
}
