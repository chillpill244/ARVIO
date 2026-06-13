package com.arflix.tv.util

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.StreamKey
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.hls.offline.HlsDownloader
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import com.arflix.tv.network.OkHttpProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.Executor

/** Result of probing an HLS URL to decide whether/how it can be downloaded. */
sealed interface HlsInspection {
    /** Master playlist — user must pick one of [variants]. */
    data class MasterPlaylist(val variants: List<HlsVariantOption>) : HlsInspection

    /** Single VOD media playlist — downloadable as-is, no quality choice. */
    data object MediaPlaylistVod : HlsInspection

    /** Live/event playlist — cannot be downloaded. */
    data object Live : HlsInspection

    /** SAMPLE-AES / DRM protected — cannot be played offline. */
    data object UnsupportedEncryption : HlsInspection

    data class Error(val message: String) : HlsInspection
}

/**
 * The user's HLS download choice, threaded from DownloadSheet through DetailsViewModel to
 * DownloadsRepository. Empty [streamKeys] = single media playlist (whole playlist download).
 */
data class HlsDownloadSelection(
    val streamKeys: List<StreamKey>,
    val qualityLabel: String?
)

/** A selectable quality variant of a master playlist, with the [StreamKey]s needed to download it. */
data class HlsVariantOption(
    val variantIndex: Int,
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val label: String,
    val streamKeys: List<StreamKey>
)

/**
 * HLS offline-download helpers: playlist inspection, variant→[StreamKey] mapping,
 * stream-key persistence, and [HlsDownloader] construction over [DownloadCacheSingleton].
 *
 * Used In: DownloadSheet (inspection + quality step), DownloadWorker (HLS branch),
 * DownloadsRepository (cache purge on delete), PlayerViewModel (stream-key deserialization).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object HlsDownloadUtil {

    private val gson = Gson()

    /**
     * Fetches and classifies an HLS playlist.
     *
     * For master playlists, the lowest-bandwidth variant's media playlist is probed once to
     * detect live streams and unsupported encryption. Network I/O on [Dispatchers.IO];
     * never throws — failures surface as [HlsInspection.Error].
     */
    suspend fun inspect(url: String, headers: Map<String, String>): HlsInspection =
        withContext(Dispatchers.IO) {
            runCatching {
                val text = fetchPlaylistText(url, headers)
                when (val playlist = parsePlaylist(url, text)) {
                    is HlsMediaPlaylist -> classifyMediaPlaylist(playlist, text)
                    is HlsMultivariantPlaylist -> {
                        val variants = buildVariantOptions(playlist)
                        if (variants.isEmpty()) return@runCatching HlsInspection.Error("No playable variants")
                        val probeUrl = playlist.variants.minByOrNull { it.format.bitrate }?.url
                        val probeText = probeUrl?.let { fetchPlaylistText(it.toString(), headers) }
                        val probe = probeText?.let { parsePlaylist(probeUrl.toString(), it) }
                        when {
                            probe is HlsMediaPlaylist &&
                                classifyMediaPlaylist(probe, probeText) != HlsInspection.MediaPlaylistVod ->
                                classifyMediaPlaylist(probe, probeText)
                            else -> HlsInspection.MasterPlaylist(variants)
                        }
                    }
                    else -> HlsInspection.Error("Unrecognized playlist")
                }
            }.getOrElse { HlsInspection.Error(it.message ?: "Failed to load playlist") }
        }

    internal fun classifyMediaPlaylist(playlist: HlsMediaPlaylist, rawPlaylist: String): HlsInspection = when {
        // The parsed model only carries DRM data for widevine/playready key formats; FairPlay
        // and identity-keyformat SAMPLE-AES leave no marker, so detect via the raw tag instead.
        rawPlaylist.contains("METHOD=SAMPLE-AES", ignoreCase = true) ||
            playlist.protectionSchemes != null || playlist.segments.any { it.drmInitData != null } ->
            HlsInspection.UnsupportedEncryption
        playlist.hasEndTag || playlist.playlistType == HlsMediaPlaylist.PLAYLIST_TYPE_VOD ->
            HlsInspection.MediaPlaylistVod
        else -> HlsInspection.Live
    }

    /**
     * Maps each variant of a master playlist to an [HlsVariantOption] whose stream keys select
     * the variant plus every audio rendition in its audio group. HLS subtitle renditions are
     * excluded — the app has its own subtitle download flow. Sorted best-quality first.
     */
    fun buildVariantOptions(playlist: HlsMultivariantPlaylist): List<HlsVariantOption> =
        playlist.variants.mapIndexed { index, variant ->
            val streamKeys = buildList {
                add(StreamKey(HlsMultivariantPlaylist.GROUP_INDEX_VARIANT, index))
                playlist.audios.forEachIndexed { audioIndex, audio ->
                    if (variant.audioGroupId != null && audio.groupId == variant.audioGroupId) {
                        add(StreamKey(HlsMultivariantPlaylist.GROUP_INDEX_AUDIO, audioIndex))
                    }
                }
            }
            HlsVariantOption(
                variantIndex = index,
                width = variant.format.width,
                height = variant.format.height,
                bitrate = variant.format.bitrate,
                label = variantLabel(variant.format.height, variant.format.bitrate),
                streamKeys = streamKeys
            )
        }.sortedWith(compareByDescending<HlsVariantOption> { it.height }.thenByDescending { it.bitrate })

    private fun variantLabel(height: Int, bitrate: Int): String {
        val resolution = if (height > 0) "${height}p" else null
        val rate = if (bitrate > 0) "%.1f Mbps".format(bitrate / 1_000_000f) else null
        return listOfNotNull(resolution, rate).joinToString(" • ").ifEmpty { "Unknown quality" }
    }

    /** Serializes stream keys as a JSON array of [groupIndex, streamIndex] pairs. */
    fun serializeStreamKeys(keys: List<StreamKey>): String =
        gson.toJson(keys.map { listOf(it.groupIndex, it.streamIndex) })

    /** Inverse of [serializeStreamKeys]; null/blank/malformed input yields an empty list (= whole playlist). */
    fun deserializeStreamKeys(json: String?): List<StreamKey> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<List<Int>>>() {}.type
            gson.fromJson<List<List<Int>>>(json, type)
                .filter { it.size >= 2 }
                .map { StreamKey(it[0], it[1]) }
        }.getOrDefault(emptyList())
    }

    /**
     * Builds an [HlsDownloader] writing into the shared download cache.
     *
     * Also used for removal: `createDownloader(...).remove()` purges a download's cached
     * segments (works offline — segment list is enumerated from the cached playlists).
     *
     * @param executor runs segment fetches; pass a multi-thread executor for parallel
     *   downloading or `Runnable::run` for inline execution (remove path).
     */
    fun createDownloader(
        context: Context,
        url: String,
        streamKeysJson: String?,
        userAgent: String,
        headers: Map<String, String>,
        executor: Executor
    ): HlsDownloader {
        val upstreamFactory = OkHttpDataSource.Factory(OkHttpProvider.playbackClient)
            .setDefaultRequestProperties(headers)
            .apply { if (userAgent.isNotBlank()) setUserAgent(userAgent) }
        val cacheFactory = CacheDataSource.Factory()
            .setCache(DownloadCacheSingleton.getInstance(context))
            .setUpstreamDataSourceFactory(upstreamFactory)
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setStreamKeys(deserializeStreamKeys(streamKeysJson))
            .build()
        return HlsDownloader(mediaItem, cacheFactory, executor)
    }

    private fun fetchPlaylistText(url: String, headers: Map<String, String>): String {
        val request = Request.Builder().url(url).apply {
            header("User-Agent", OkHttpProvider.userAgent)
            headers.forEach { (k, v) -> if (k.isNotBlank()) header(k, v) }
        }.build()
        OkHttpProvider.playbackClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.string() ?: error("Empty playlist body")
        }
    }

    private fun parsePlaylist(url: String, text: String): HlsPlaylist =
        HlsPlaylistParser().parse(Uri.parse(url), text.byteInputStream())
}
