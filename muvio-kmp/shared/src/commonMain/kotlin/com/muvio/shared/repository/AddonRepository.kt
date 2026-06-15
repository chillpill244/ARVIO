package com.muvio.shared.repository

import com.muvio.shared.domain.*
import com.muvio.shared.network.StremioClient
import com.muvio.shared.stremio.StremioManifestResponse
import com.muvio.shared.stremio.StremioStream
import com.muvio.shared.stremio.StreamBehaviorHints as WireHints
import com.muvio.shared.storage.PlatformPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val ADDON_TIMEOUT_MS = 8_000L

class AddonRepository(
    private val stremioClient: StremioClient,
    private val prefs: PlatformPreferences,
    private val profileId: String,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val addonsKey get() = "addons_${profileId}"

    // ── Addon management ──────────────────────────────────────────────────────

    suspend fun getInstalledAddons(): List<Addon> {
        val raw = prefs.getString(addonsKey) ?: return defaultAddons()
        return runCatching {
            json.decodeFromString<List<StoredAddon>>(raw).mapNotNull { it.toDomain() }
        }.getOrDefault(defaultAddons())
    }

    suspend fun installAddon(url: String, customName: String? = null): Addon {
        val manifestUrl = resolveManifestUrl(url)
        val manifest = stremioClient.getAddonManifest(manifestUrl)
        val addon = manifest.toDomain(url = url, customName = customName)
        saveAddon(addon)
        return addon
    }

    suspend fun removeAddon(addonId: String) {
        val current = getInstalledAddons().filter { it.id != addonId }
        prefs.putString(addonsKey, json.encodeToString(current.map { StoredAddon.from(it) }))
    }

    suspend fun toggleAddon(addonId: String) {
        val current = getInstalledAddons().map { addon ->
            if (addon.id == addonId) addon.copy(isEnabled = !addon.isEnabled) else addon
        }
        prefs.putString(addonsKey, json.encodeToString(current.map { StoredAddon.from(it) }))
    }

    private suspend fun saveAddon(addon: Addon) {
        val current = getInstalledAddons().filter { it.id != addon.id } + addon
        prefs.putString(addonsKey, json.encodeToString(current.map { StoredAddon.from(it) }))
    }

    // ── Stream resolution ─────────────────────────────────────────────────────

    suspend fun resolveMovieStreams(imdbId: String): List<StreamSource> {
        val addons = getInstalledAddons().filter { it.isEnabled && it.type != AddonType.SUBTITLE }
        return coroutineScope {
            addons.map { addon ->
                async {
                    val baseUrl = addon.transportUrl ?: return@async emptyList()
                    val query = addon.url?.substringAfter("?", "")?.takeIf { it.isNotBlank() }
                    val streamUrl = buildString {
                        append("$baseUrl/stream/movie/$imdbId.json")
                        if (!query.isNullOrBlank()) append("?$query")
                    }
                    withTimeoutOrNull(ADDON_TIMEOUT_MS) {
                        runCatching {
                            stremioClient.getAddonStreams(streamUrl)
                                .streams.orEmpty()
                                .filter { it.hasPlayableLink() }
                                .map { it.toDomain(addonId = addon.id, addonName = addon.name) }
                        }.getOrDefault(emptyList())
                    } ?: emptyList()
                }
            }.map { it.await() }.flatten()
        }
    }

    suspend fun resolveEpisodeStreams(imdbId: String, season: Int, episode: Int): List<StreamSource> {
        val addons = getInstalledAddons().filter { it.isEnabled && it.type != AddonType.SUBTITLE }
        val episodeId = "$imdbId:$season:$episode"
        return coroutineScope {
            addons.map { addon ->
                async {
                    val baseUrl = addon.transportUrl ?: return@async emptyList()
                    val query = addon.url?.substringAfter("?", "")?.takeIf { it.isNotBlank() }
                    val streamUrl = buildString {
                        append("$baseUrl/stream/series/$episodeId.json")
                        if (!query.isNullOrBlank()) append("?$query")
                    }
                    withTimeoutOrNull(ADDON_TIMEOUT_MS) {
                        runCatching {
                            stremioClient.getAddonStreams(streamUrl)
                                .streams.orEmpty()
                                .filter { it.hasPlayableLink() }
                                .map { it.toDomain(addonId = addon.id, addonName = addon.name) }
                        }.getOrDefault(emptyList())
                    } ?: emptyList()
                }
            }.map { it.await() }.flatten()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resolveManifestUrl(url: String): String {
        var clean = url.trim()
        if (clean.startsWith("stremio://", ignoreCase = true)) {
            val payload = clean.substringAfter("://")
            clean = if (payload.startsWith("http")) payload else "https://$payload"
        }
        if (!clean.startsWith("http")) clean = "https://$clean"
        clean = clean.trimEnd('/')
        return if (clean.endsWith("manifest.json")) clean else "$clean/manifest.json"
    }

    private fun defaultAddons(): List<Addon> = listOf(
        Addon(
            id = "opensubtitles",
            name = "OpenSubtitles v3",
            version = "1.0.0",
            description = "Subtitles from OpenSubtitles",
            isInstalled = true,
            isEnabled = true,
            type = AddonType.SUBTITLE,
            url = "https://opensubtitles-v3.strem.io/subtitles",
            transportUrl = "https://opensubtitles-v3.strem.io",
        )
    )

    private fun StremioManifestResponse.toDomain(url: String, customName: String? = null): Addon {
        val transport = url.trimEnd('/').removeSuffix("/manifest.json")
        val resourceNames = resources?.mapNotNull { element ->
            runCatching {
                element.jsonPrimitive.content
            }.getOrElse {
                element.jsonObject["name"]?.jsonPrimitive?.content
            }
        }?.toSet() ?: emptySet()
        val hasStream = "stream" in resourceNames
        val hasSubtitles = "subtitles" in resourceNames
        val addonType = if (hasSubtitles && !hasStream) AddonType.SUBTITLE else AddonType.CUSTOM
        return Addon(
            id = "${id}_${transport.hashCode().toUInt()}",
            name = customName?.trim()?.takeIf { it.isNotBlank() } ?: name,
            version = version,
            description = description.orEmpty(),
            isInstalled = true,
            isEnabled = true,
            type = addonType,
            url = url,
            logo = logo,
            transportUrl = transport,
        )
    }

    private fun StremioStream.toDomain(addonId: String, addonName: String): StreamSource {
        val hints = behaviorHints
        return StreamSource(
            source = getTorrentName(),
            addonName = "$addonName - ${getSourceName()}",
            addonId = addonId,
            quality = getQuality(),
            size = getSize(),
            url = getStreamUrl(),
            infoHash = infoHash,
            fileIdx = fileIdx,
            behaviorHints = hints?.let {
                StreamBehaviorHints(
                    notWebReady = it.notWebReady ?: false,
                    cached = it.cached,
                    bingeGroup = it.bingeGroup,
                    proxyHeaders = it.proxyHeaders?.let { ph ->
                        ProxyHeaders(request = ph.request, response = ph.response)
                    },
                    videoSize = it.videoSize,
                    filename = it.filename,
                )
            },
            sources = sources ?: emptyList(),
        )
    }

    private fun StremioStream.getSize(): String {
        behaviorHints?.videoSize?.let { bytes ->
            if (bytes > 0) return formatBytes(bytes)
        }
        val texts = listOfNotNull(title, name, description)
        for (text in texts) {
            val emojiMatch = Regex("""💾\s*([\d.]+\s*[GMKT]B)""", RegexOption.IGNORE_CASE).find(text)
            emojiMatch?.groupValues?.getOrNull(1)?.let { return it }
            val plainMatch = Regex("""(\d+\.?\d*)\s*(GB|MB|TB|KB)""", RegexOption.IGNORE_CASE).find(text)
            if (plainMatch != null) return "${plainMatch.groupValues[1]} ${plainMatch.groupValues[2].uppercase()}"
        }
        return ""
    }

    private fun StremioStream.getTorrentName(): String {
        behaviorHints?.filename?.takeIf { it.isNotBlank() }?.let { return it }
        val full = title ?: name ?: ""
        val parts = full.split("\n")
        for (i in parts.indices.reversed()) {
            val part = parts[i].trim()
            if (part.isNotBlank() && part.contains(".") && !part.contains("👤") && !part.contains("💾"))
                return part
        }
        return parts.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
            ?: parts.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() && !it.contains("👤") }
            ?: full.trim().ifBlank { "Unknown" }
    }

    private fun formatBytes(bytes: Long): String =
        com.muvio.shared.util.formatBytes(bytes)
}

// ── Serializable DTO for persistence ──────────────────────────────────────────

@kotlinx.serialization.Serializable
private data class StoredAddon(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val isInstalled: Boolean = true,
    val isEnabled: Boolean = true,
    val type: String = "CUSTOM",
    val url: String? = null,
    val logo: String? = null,
    val transportUrl: String? = null,
) {
    fun toDomain(): Addon? {
        if (id.isBlank() || name.isBlank()) return null
        return Addon(
            id = id,
            name = name,
            version = version,
            description = description,
            isInstalled = isInstalled,
            isEnabled = isEnabled,
            type = runCatching { AddonType.valueOf(type) }.getOrDefault(AddonType.CUSTOM),
            url = url,
            logo = logo,
            transportUrl = transportUrl,
        )
    }

    companion object {
        fun from(addon: Addon) = StoredAddon(
            id = addon.id,
            name = addon.name,
            version = addon.version,
            description = addon.description,
            isInstalled = addon.isInstalled,
            isEnabled = addon.isEnabled,
            type = addon.type.name,
            url = addon.url,
            logo = addon.logo,
            transportUrl = addon.transportUrl,
        )
    }
}
