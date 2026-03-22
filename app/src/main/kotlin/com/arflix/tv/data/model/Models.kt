package com.arflix.tv.data.model

import androidx.compose.runtime.Immutable
import java.io.Serializable

/**
 * Media item - represents a movie or TV show
 * Matches webapp's MediaItem type
 */
@Immutable
data class MediaItem(
    val id: Int,
    val title: String,
    val subtitle: String = "",
    val overview: String = "",
    val year: String = "",
    val releaseDate: String? = null,
    val rating: String = "",
    val duration: String = "",
    val imdbRating: String = "",
    val tmdbRating: String = "",
    val mediaType: MediaType = MediaType.MOVIE,
    val image: String = "",
    val backdrop: String? = null,
    val progress: Int = 0,
    val isWatched: Boolean = false,
    val traktId: Int? = null,
    val badge: String? = null,
    val genreIds: List<Int> = emptyList(),
    val originalLanguage: String? = null,
    val isOngoing: Boolean = false,
    val totalEpisodes: Int? = null,
    val watchedEpisodes: Int? = null,
    val nextEpisode: NextEpisode? = null,
    // Additional movie-specific fields
    val budget: Long? = null,
    val revenue: Long? = null,
    // TV show status
    val status: String? = null, // "Returning Series", "Ended", "Canceled"
    // Character name (for person filmography / known for)
    val character: String = "",
    // Popularity score from TMDB (higher = more mainstream content)
    val popularity: Float = 0f,
    // Placeholder card - shows skeleton loading animation
    val isPlaceholder: Boolean = false,
    // IMDB ID for stream lookups (may differ from TMDB ID)
    val imdbId: String? = null,
    // Pre-populated VOD streams from IPTV sources (bypasses stream fetching)
    val vodStreams: List<StreamSource>? = null,
    // IPTV Series ID - for direct get_series_info lookup (bypasses name matching)
    val iptvSeriesId: String? = null,
    // IPTV Movie ID - for direct get_vod_info lookup (bypasses name matching)
    val iptvMovieId: String? = null
) : Serializable

enum class MediaType {
    MOVIE, TV, LIVE_TV
}

/**
 * Next episode to watch
 */
@Immutable
data class NextEpisode(
    val id: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String,
    val overview: String = ""
) : Serializable

/**
 * Episode details
 */
@Immutable
data class Episode(
    val id: Int,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val name: String,
    val overview: String = "",
    val stillPath: String? = null,
    val voteAverage: Float = 0f,
    val runtime: Int = 0,
    val airDate: String = "",
    val isWatched: Boolean = false
) : Serializable

/**
 * Cast member
 */
@Immutable
data class CastMember(
    val id: Int,
    val name: String,
    val character: String = "",
    val profilePath: String? = null
) : Serializable

/**
 * Review from TMDB
 */
@Immutable
data class Review(
    val id: String,
    val author: String,
    val authorUsername: String = "",
    val authorAvatar: String? = null,
    val content: String,
    val rating: Float? = null,
    val createdAt: String = ""
) : Serializable

/**
 * Person details (for cast modal)
 */
@Immutable
data class PersonDetails(
    val id: Int,
    val name: String,
    val biography: String = "",
    val placeOfBirth: String? = null,
    val birthday: String? = null,
    val profilePath: String? = null,
    val knownFor: List<MediaItem> = emptyList()
) : Serializable

/**
 * Category/Row of media items
 */
@Immutable
data class Category(
    val id: String,
    val title: String,
    val items: List<MediaItem>
) : Serializable

/**
 * Stream source from addons - enhanced with behavior hints like NuvioStreaming
 */
data class StreamSource(
    val source: String,
    val addonName: String,
    val addonId: String = "",
    val quality: String,
    val size: String,
    val sizeBytes: Long? = null,
    val url: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val behaviorHints: StreamBehaviorHints? = null,
    val subtitles: List<Subtitle> = emptyList(),
    // Stremio "sources" are commonly tracker URLs. Keeping them helps P2P playback (TorrServer) work
    // across more addons.
    val sources: List<String> = emptyList(),
    // Match score for sorting by similarity (0.0-1.0, higher = better match)
    // Used for IPTV VOD sources to sort by TMDB/IMDB/title match confidence
    val matchScore: Float = 0f
) : Serializable

/**
 * Stream behavior hints - matches Stremio protocol
 */
data class StreamBehaviorHints(
    val notWebReady: Boolean = false,
    val cached: Boolean? = null,
    val bingeGroup: String? = null,
    val countryWhitelist: List<String>? = null,
    val proxyHeaders: ProxyHeaders? = null,
    val videoHash: String? = null,
    val videoSize: Long? = null,
    val filename: String? = null
) : Serializable

data class ProxyHeaders(
    val request: Map<String, String>? = null,
    val response: Map<String, String>? = null
) : Serializable

/**
 * Subtitle track
 */
data class Subtitle(
    val id: String,
    val url: String,
    val lang: String,
    val label: String,
    val isEmbedded: Boolean = false,
    val groupIndex: Int? = null,
    val trackIndex: Int? = null
) : Serializable

/**
 * Stremio Addon Manifest - full support for any Stremio addon
 * Based on: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/manifest.md
 */
data class AddonManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val logo: String? = null,
    val background: String? = null,
    val types: List<String> = emptyList(),         // ["movie", "series", "channel", "tv"]
    val resources: List<AddonResource> = emptyList(),
    val catalogs: List<AddonCatalog> = emptyList(),
    val idPrefixes: List<String>? = null,          // ["tt"] for IMDB, ["kitsu:"] for Kitsu
    val behaviorHints: AddonBehaviorHints? = null
) : Serializable

/**
 * Addon resource descriptor
 */
data class AddonResource(
    val name: String,                              // "stream", "meta", "catalog", "subtitles"
    val types: List<String> = emptyList(),         // ["movie", "series"]
    val idPrefixes: List<String>? = null           // ID prefix filter
) : Serializable

/**
 * Addon catalog descriptor
 */
data class AddonCatalog(
    val type: String,                              // "movie", "series"
    val id: String,                                // catalog ID
    val name: String = "",
    val genres: List<String>? = null,
    val extra: List<AddonCatalogExtra>? = null
) : Serializable

data class AddonCatalogExtra(
    val name: String,                              // "search", "genre", "skip"
    val isRequired: Boolean = false,
    val options: List<String>? = null
) : Serializable

data class AddonBehaviorHints(
    val adult: Boolean = false,
    val p2p: Boolean = false,
    val configurable: Boolean = false,
    val configurationRequired: Boolean = false
) : Serializable

/**
 * Installed addon with manifest data
 */
data class Addon(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val isInstalled: Boolean,
    val isEnabled: Boolean = true,
    val type: AddonType,
    val url: String? = null,
    val logo: String? = null,
    val manifest: AddonManifest? = null,           // Full manifest for advanced filtering
    val transportUrl: String? = null               // Base URL for API calls (without manifest.json)
)

enum class AddonType {
    OFFICIAL, COMMUNITY, SUBTITLE, METADATA, CUSTOM
}

/**
 * Stream fetch result with addon info - for callback-based fetching like NuvioStreaming
 */
data class AddonStreamResult(
    val streams: List<StreamSource>,
    val addonId: String,
    val addonName: String,
    val error: Exception? = null
) : Serializable

/**
 * IPTV Series match result for stream selection UI.
 * Represents a potential match from the IPTV provider's series catalog.
 */
@Immutable
data class IptvSeriesMatch(
    val seriesId: Int,
    val seriesName: String,
    val providerName: String = "IPTV",
    val confidence: Float,             // 0.0 - 1.0 confidence score
    val matchMethod: String,           // "tmdb_id", "imdb_id", "title_canonical", "title_tokens"
    val episodeCount: Int = 0,         // Number of episodes available
    val coverUrl: String? = null       // Poster/cover image from provider
) : Serializable

/**
 * Cached IPTV series episode info for instant URL building.
 */
@Immutable
data class IptvSeriesEpisodeInfo(
    val seriesId: Int,
    val season: Int,
    val episode: Int,
    val streamId: Int,
    val containerExtension: String?,
    val title: String
) : Serializable

/**
 * Series context for continue watching and next episode navigation.
 * Stored alongside watch history to enable instant episode URL building.
 */
@Immutable
data class IptvSeriesContext(
    val seriesId: Int,
    val seriesName: String,
    val cachedAtMs: Long
) : Serializable

/**
 * Full series info from IPTV provider's get_series_info API.
 * Contains all metadata needed to populate DetailsScreen without TMDB.
 */
@Immutable
data class IptvSeriesFullInfo(
    val seriesId: Int,
    val name: String,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val coverUrl: String?,
    val backdropUrl: String?,
    val youtubeTrailer: String?,
    val seasons: List<IptvSeasonInfo>,
    val episodes: List<IptvEpisodeInfo>,
    val cachedAtMs: Long = System.currentTimeMillis()
) : Serializable

/**
 * Season info from IPTV get_series_info response.
 */
@Immutable
data class IptvSeasonInfo(
    val seasonNumber: Int,
    val name: String,
    val overview: String?,
    val episodeCount: Int,
    val coverUrl: String?,
    val airDate: String?,
    val voteAverage: Float = 0f
) : Serializable

/**
 * Episode info from IPTV get_series_info response.
 */
@Immutable
data class IptvEpisodeInfo(
    val streamId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val plot: String?,
    val releaseDate: String?,
    val duration: String?,
    val stillPath: String?,
    val containerExtension: String?,
    val rating: Float = 0f,
    val tmdbId: Int? = null,
    val bitrate: Int = 0
) : Serializable


