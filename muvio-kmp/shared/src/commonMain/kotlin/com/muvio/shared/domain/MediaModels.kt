package com.muvio.shared.domain

enum class MediaType { MOVIE, TV }

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
    val budget: Long? = null,
    val revenue: Long? = null,
    val status: String? = null,
    val character: String = "",
    val popularity: Float = 0f,
    val addedAt: Long = 0L,
    val sourceOrder: Int = Int.MAX_VALUE,
    val isPlaceholder: Boolean = false,
    val timeRemainingLabel: String? = null,
    val showPlaybackProgress: Boolean = true,
    val iptvMovieId: String? = null,
    val iptvSeriesId: String? = null,
    val rtScore: String? = null,
    val popcornScore: String? = null,
)

data class NextEpisode(
    val id: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String,
    val overview: String = "",
)

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
    val isWatched: Boolean = false,
)

data class CastMember(
    val id: Int,
    val name: String,
    val character: String = "",
    val profilePath: String? = null,
)

data class Review(
    val id: String,
    val author: String,
    val authorUsername: String = "",
    val authorAvatar: String? = null,
    val content: String,
    val rating: Float? = null,
    val createdAt: String = "",
)

data class PersonDetails(
    val id: Int,
    val name: String,
    val biography: String = "",
    val placeOfBirth: String? = null,
    val birthday: String? = null,
    val profilePath: String? = null,
    val knownFor: List<MediaItem> = emptyList(),
)

data class Category(
    val id: String,
    val title: String,
    val items: List<MediaItem>,
)

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
    val sources: List<String> = emptyList(),
    val description: String? = null,
)

data class StreamBehaviorHints(
    val notWebReady: Boolean = false,
    val cached: Boolean? = null,
    val bingeGroup: String? = null,
    val countryWhitelist: List<String>? = null,
    val proxyHeaders: ProxyHeaders? = null,
    val videoHash: String? = null,
    val videoSize: Long? = null,
    val filename: String? = null,
)

data class ProxyHeaders(
    val request: Map<String, String>? = null,
    val response: Map<String, String>? = null,
)

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

data class AddonManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val logo: String? = null,
    val background: String? = null,
    val types: List<String> = emptyList(),
    val resources: List<AddonResource> = emptyList(),
    val catalogs: List<AddonCatalog> = emptyList(),
    val idPrefixes: List<String>? = null,
    val behaviorHints: AddonBehaviorHints? = null,
)

data class AddonResource(
    val name: String,
    val types: List<String> = emptyList(),
    val idPrefixes: List<String>? = null,
)

data class AddonCatalog(
    val type: String,
    val id: String,
    val name: String = "",
    val genres: List<String>? = null,
    val extra: List<AddonCatalogExtra>? = null,
)

data class AddonCatalogExtra(
    val name: String,
    val isRequired: Boolean = false,
    val options: List<String>? = null,
)

data class AddonBehaviorHints(
    val adult: Boolean = false,
    val p2p: Boolean = false,
    val configurable: Boolean = false,
    val configurationRequired: Boolean = false,
)

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
    val manifest: AddonManifest? = null,
    val transportUrl: String? = null,
)

enum class AddonType { OFFICIAL, COMMUNITY, SUBTITLE, METADATA, CUSTOM }
enum class RuntimeKind { STREMIO }
enum class AddonInstallSource { DIRECT_URL }

data class QualityFilterConfig(
    val id: String = "",
    val deviceName: String = "",
    val regexPattern: String = "",
    val enabled: Boolean = true,
)

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
    val tmdbId: Int = 0,
)

data class IptvSeasonInfo(
    val seasonNumber: Int,
    val name: String,
    val overview: String?,
    val episodeCount: Int,
    val coverUrl: String?,
    val airDate: String?,
)

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
)
