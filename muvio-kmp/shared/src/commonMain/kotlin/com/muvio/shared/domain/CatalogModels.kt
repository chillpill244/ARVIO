package com.muvio.shared.domain

enum class CatalogSourceType { PREINSTALLED, TRAKT, MDBLIST, ADDON, HOME_SERVER }
enum class CatalogKind { STANDARD, COLLECTION, COLLECTION_RAIL }
enum class CollectionGroupKind { FEATURED, SERVICE, GENRE, DECADE, FRANCHISE, NETWORK }
enum class CollectionTileShape { LANDSCAPE, POSTER }

enum class CollectionSourceKind {
    ADDON_CATALOG,
    TMDB_GENRE,
    TMDB_PERSON,
    TMDB_COLLECTION,
    TMDB_KEYWORD,
    TMDB_WATCH_PROVIDER,
    CURATED_IDS,
    MDBLIST_PUBLIC,
}

data class CollectionSourceConfig(
    val kind: CollectionSourceKind,
    val mediaType: String? = null,
    val addonId: String? = null,
    val addonCatalogType: String? = null,
    val addonCatalogId: String? = null,
    val tmdbGenreId: Int? = null,
    val tmdbPersonId: Int? = null,
    val tmdbCollectionId: Int? = null,
    val tmdbKeywordId: Int? = null,
    val tmdbWatchProviderId: Int? = null,
    val watchRegion: String? = null,
    val sortBy: String? = null,
    val curatedRefs: List<String>? = null,
    val mdblistSlug: String? = null,
)

data class CatalogConfig(
    val id: String,
    val title: String,
    val sourceType: CatalogSourceType,
    val sourceUrl: String? = null,
    val sourceRef: String? = null,
    val isPreinstalled: Boolean = false,
    val addonId: String? = null,
    val addonCatalogType: String? = null,
    val addonCatalogId: String? = null,
    val addonName: String? = null,
    val kind: CatalogKind = CatalogKind.STANDARD,
    val collectionGroup: CollectionGroupKind? = null,
    val collectionDescription: String? = null,
    val collectionCoverImageUrl: String? = null,
    val collectionTileShape: CollectionTileShape = CollectionTileShape.LANDSCAPE,
    val collectionHideTitle: Boolean = false,
    val collectionSources: List<CollectionSourceConfig> = emptyList(),
    val requiredAddonUrls: List<String> = emptyList(),
)

data class CatalogDiscoveryResult(
    val id: String,
    val title: String,
    val description: String?,
    val sourceType: CatalogSourceType,
    val sourceUrl: String,
    val creatorName: String?,
    val itemCount: Int?,
    val previewPosterUrls: List<String> = emptyList(),
)
