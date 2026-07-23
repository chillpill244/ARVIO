package com.arflix.tv.data.model

import kotlinx.serialization.Serializable

import androidx.compose.runtime.Immutable

/**
 * Media item - represents a movie or TV show
 * Matches webapp's MediaItem type
 */
@Serializable
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
    val primaryNetworkLogo: String? = null,
    val isOngoing: Boolean = false,
    val totalEpisodes: Int? = null,
    val watchedEpisodes: Int? = null,
    val nextEpisode: NextEpisode? = null,
    // Additional movie-specific fields
    val budget: Long? = null,
    val revenue: Long? = null,
    // TV show status
    val status: String? = null, // "Returning Series", "Ended", "Canceled"
    val collectionGroup: CollectionGroupKind? = null,
    val collectionTileShape: CollectionTileShape? = null,
    val collectionHideTitle: Boolean = false,
    // Character name (for person filmography / known for)
    val character: String = "",
    // Popularity score from TMDB (higher = more mainstream content)
    val popularity: Float = 0f,
    // Source-specific added timestamp, used for exact newest-first watchlist ordering.
    val addedAt: Long = 0L,
    // Explicit source order when a remote list already gives the correct order.
    val sourceOrder: Int = Int.MAX_VALUE,
    // Placeholder card - shows skeleton loading animation
    val isPlaceholder: Boolean = false,
    // Continue Watching: formatted time remaining (e.g., "23min left", "1hr 15min left")
    val timeRemainingLabel: String? = null,
    // Continue Watching: true only when progress represents current movie/episode playback.
    val showPlaybackProgress: Boolean = true,
    // IPTV Xtream stream ID for VOD movies (used to resolve direct stream URL)
    val iptvMovieId: String? = null,
    // IPTV Xtream series ID for VOD series (used to resolve episode URLs)
    val iptvSeriesId: String? = null,
    // MDBList-sourced scores (only populated for items loaded from MDBList catalogs)
    val rtScore: String? = null,
    val popcornScore: String? = null,
)

@Serializable
enum class MediaType {
    MOVIE, TV
}

/**
 * Next episode to watch
 */
@Serializable
@Immutable
data class NextEpisode(
    val id: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String,
    val overview: String = ""
)

/**
 * Episode details
 */
@Serializable
@Immutable
data class Episode(
    val id: Int,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val name: String,
    val overview: String = "",
    val stillPath: String? = null,
    val voteAverage: Float = 0f,
    val imdbRating: String = "",
    val runtime: Int = 0,
    val airDate: String = "",
    val isWatched: Boolean = false
)

/**
 * Cast member
 */
@Serializable
@Immutable
data class CastMember(
    val id: Int,
    val name: String,
    val character: String = "",
    val profilePath: String? = null
)

/**
 * Community review shown on details pages.
 */
@Serializable
@Immutable
data class Review(
    val id: String,
    val author: String,
    val authorUsername: String = "",
    val authorAvatar: String? = null,
    val content: String,
    val rating: Float? = null,
    val createdAt: String = ""
)

/**
 * Person details (for cast modal)
 */
@Serializable
@Immutable
data class PersonDetails(
    val id: Int,
    val name: String,
    val biography: String = "",
    val placeOfBirth: String? = null,
    val birthday: String? = null,
    val profilePath: String? = null,
    val knownFor: List<MediaItem> = emptyList()
)

/**
 * Category/Row of media items
 */
@Serializable
@Immutable
data class Category(
    val id: String,
    val title: String,
    val items: List<MediaItem>
)

/**
 * Stream source from addons - enhanced with behavior hints.
 *
 * Marked @Immutable so Compose can skip recomposition on stable list renders
 * in the source picker. All fields are read-only primitives or nested
 * immutable types (StreamBehaviorHints is also @Immutable; subtitles/sources
 * lists are constructed once and treated as immutable by convention).
 */
@Serializable
@Immutable
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
    val description: String? = null
)

/**
 * Stream behavior hints - all primitive / immutable fields, safe for
 * @Immutable so it composes stably inside a StreamSource list.
 */
@Serializable
@Immutable
data class StreamBehaviorHints(
    val notWebReady: Boolean = false,
    val cached: Boolean? = null,
    val bingeGroup: String? = null,
    val countryWhitelist: List<String>? = null,
    val proxyHeaders: ProxyHeaders? = null,
    val videoHash: String? = null,
    val videoSize: Long? = null,
    val filename: String? = null
)

@Serializable
data class ProxyHeaders(
    val request: Map<String, String>? = null,
    val response: Map<String, String>? = null
)

/**
 * Subtitle track
 */
@Serializable
data class Subtitle(
    val id: String,
    val url: String,
    val lang: String,
    val label: String,
    val provider: String = "",
    val isEmbedded: Boolean = false,
    val groupIndex: Int? = null,
    val trackIndex: Int? = null,
    val isForced: Boolean = false,
)

/**
 * Stremio Addon Manifest - full support for any Stremio addon
 * Based on: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/manifest.md
 */
@Serializable
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
)

/**
 * Addon resource descriptor
 */
@Serializable
data class AddonResource(
    val name: String,                              // "stream", "meta", "catalog", "subtitles"
    val types: List<String> = emptyList(),         // ["movie", "series"]
    val idPrefixes: List<String>? = null           // ID prefix filter
)

/**
 * Addon catalog descriptor
 */
@Serializable
data class AddonCatalog(
    val type: String,                              // "movie", "series"
    val id: String,                                // catalog ID
    val name: String = "",
    val genres: List<String>? = null,
    val extra: List<AddonCatalogExtra>? = null
)

@Serializable
data class AddonCatalogExtra(
    val name: String,                              // "search", "genre", "skip"
    val isRequired: Boolean = false,
    val options: List<String>? = null
)

@Serializable
data class AddonBehaviorHints(
    val adult: Boolean = false,
    val p2p: Boolean = false,
    val configurable: Boolean = false,
    val configurationRequired: Boolean = false
)

/**
 * Installed addon with manifest data
 */
@Serializable
data class Addon(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val isInstalled: Boolean,
    val isEnabled: Boolean = true,
    val type: AddonType,
    val runtimeKind: RuntimeKind = RuntimeKind.STREMIO,
    val installSource: AddonInstallSource = AddonInstallSource.DIRECT_URL,
    val url: String? = null,
    val logo: String? = null,
    val manifest: AddonManifest? = null,           // Full manifest for advanced filtering
    val transportUrl: String? = null               // Base URL for API calls (without manifest.json)
)

@Serializable
enum class AddonType {
    OFFICIAL, COMMUNITY, SUBTITLE, METADATA, CUSTOM
}

@Serializable
enum class RuntimeKind {
    STREMIO
}

@Serializable
enum class AddonInstallSource {
    DIRECT_URL
}

/**
 * Stream fetch result with addon info for callback-based fetching
 */
data class AddonStreamResult(
    val streams: List<StreamSource>,
    val addonId: String,
    val addonName: String,
    val error: Exception? = null
)

/**
 * Quality filter entry - device-scoped regex patterns to exclude quality tiers.
 * These filters apply to ALL profiles on this device (e.g., 1080p TV always excludes 4K)
 * regardless of which profile is logged in. This ensures device capabilities limit quality,
 * not user profiles.
 * 
 * Example: 1080p TV with regex "4K|2160p" excludes 4K streams for all users
 */
@Serializable
@Immutable
data class QualityFilterConfig(
    val id: String = "", // UUID for unique identification
    val deviceName: String = "", // Display name (e.g., "Living Room TV", "Bedroom Fire TV")
    val regexPattern: String = "", // Regex pattern to EXCLUDE matching qualities (e.g., "4K|2160p")
    val enabled: Boolean = true, // Enable/disable filter without deleting
    val createdAt: Long = com.arflix.tv.shared.util.currentTimeMillis()
)

@Serializable
@Immutable
data class IptvSeriesFullInfo(
    val seriesId: Int,
    val name: String,
    val plot: String?,
    val cast: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val coverUrl: String?,
    val backdropUrl: String?,
    val youtubeTrailer: String?,
    val seasons: List<IptvSeasonInfo>,
    val episodes: List<IptvEpisodeInfo>,
    val tmdbId: Int = 0
)

@Serializable
@Immutable
data class IptvSeasonInfo(
    val seasonNumber: Int,
    val name: String,
    val overview: String?,
    val episodeCount: Int,
    val coverUrl: String?,
    val airDate: String?
)

@Serializable
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
    val rating: Float = 0f
)
