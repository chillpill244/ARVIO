package com.muvio.shared.stremio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ========== Manifest ==========

@Serializable
data class StremioManifestResponse(
    val id: String,
    val name: String,
    val version: String,
    val description: String? = null,
    val logo: String? = null,
    val background: String? = null,
    val types: List<String>? = null,
    // Each element is either a plain String or a StremioResourceDescriptor JSON object.
    // Decoded downstream via decodeResourceElements().
    val resources: List<JsonElement>? = null,
    val catalogs: List<StremioCatalog>? = null,
    val idPrefixes: List<String>? = null,
    val behaviorHints: StremioAddonBehaviorHints? = null,
)

@Serializable
data class StremioResourceDescriptor(
    val name: String,
    val types: List<String>? = null,
    val idPrefixes: List<String>? = null,
)

@Serializable
data class StremioCatalog(
    val type: String,
    val id: String,
    val name: String? = null,
    val genres: List<String>? = null,
    val extra: List<StremioCatalogExtra>? = null,
)

@Serializable
data class StremioCatalogExtra(
    val name: String,
    val isRequired: Boolean? = null,
    val options: List<String>? = null,
)

@Serializable
data class StremioAddonBehaviorHints(
    val adult: Boolean? = null,
    val p2p: Boolean? = null,
    val configurable: Boolean? = null,
    val configurationRequired: Boolean? = null,
)

// ========== Streams ==========

@Serializable
data class StremioStreamResponse(
    val streams: List<StremioStream>? = null,
)

@Serializable
data class StremioStream(
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val url: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val ytId: String? = null,
    val externalUrl: String? = null,
    @SerialName("headers") val headers: Map<String, String>? = null,
    val behaviorHints: StreamBehaviorHints? = null,
    val sources: List<String>? = null,
    val subtitles: List<StremioSubtitle>? = null,
) {
    fun hasPlayableLink(): Boolean =
        url != null || infoHash != null || ytId != null || externalUrl != null

    fun getStreamUrl(): String? = url ?: externalUrl

    fun getQuality(): String {
        val text = listOfNotNull(name, title, description).joinToString(" ")
        return when {
            text.contains("2160p", ignoreCase = true) || text.contains("4K", ignoreCase = true) -> "4K"
            text.contains("1080p", ignoreCase = true) -> "1080p"
            text.contains("720p", ignoreCase = true) -> "720p"
            text.contains("480p", ignoreCase = true) -> "480p"
            else -> (title ?: name ?: "").split("\n").getOrNull(1)?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown"
        }
    }

    fun getSourceName(): String =
        (title ?: name ?: "").split("\n").getOrNull(0)?.trim() ?: "Unknown"

    fun getSeeders(): Int? =
        SEEDER_REGEX.find(title ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()

    companion object {
        private val SEEDER_REGEX = Regex("""👤\s*(\d+)""")
    }
}

@Serializable
data class StreamBehaviorHints(
    val notWebReady: Boolean? = null,
    val cached: Boolean? = null,
    val bingeGroup: String? = null,
    val countryWhitelist: List<String>? = null,
    val proxyHeaders: StremioProxyHeaders? = null,
    @SerialName("headers") val headers: Map<String, String>? = null,
    val videoHash: String? = null,
    val videoSize: Long? = null,
    val filename: String? = null,
)

@Serializable
data class StremioProxyHeaders(
    val request: Map<String, String>? = null,
    val response: Map<String, String>? = null,
)

@Serializable
data class StremioSubtitle(
    val id: String? = null,
    val url: String? = null,
    val lang: String? = null,
    val label: String? = null,
)

@Serializable
data class StremioSubtitleResponse(
    val subtitles: List<StremioSubtitle>? = null,
)

// ========== Catalog / Meta ==========

@Serializable
data class StremioCatalogResponse(
    val metas: List<StremioMetaPreview>? = null,
    val items: List<StremioMetaPreview>? = null,
)

@Serializable
data class StremioMetaPreview(
    val id: String? = null,
    val type: String? = null,
    val name: String? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("tmdb_id") val tmdbId: String? = null,
    @SerialName("moviedb_id") val moviedbId: String? = null,
)

@Serializable
data class StremioMetaResponse(
    val meta: StremioMetaPreview? = null,
)
