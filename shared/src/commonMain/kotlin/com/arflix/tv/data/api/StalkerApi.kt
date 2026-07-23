package com.arflix.tv.data.api

import com.arflix.tv.data.model.IptvChannel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter

/**
 * Stalker/Ministra portal API client for MAC-based IPTV authentication.
 * Converts Stalker portal channels into the same IptvChannel format as Xtream/M3U.
 */
class StalkerApi(
    private val portalUrl: String,
    private val macAddress: String
) {
    private val client = HttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private var token: String = ""

    private val baseHeaders: Map<String, String>
        get() = mapOf(
            "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
            "Cookie" to "mac=$macAddress; stb_lang=en; timezone=Europe/London",
            "X-User-Agent" to "Model: MAG250; Link: WiFi",
            "Authorization" to "Bearer $token"
        )

    /** Step 1: Handshake to get auth token */
    suspend fun handshake(): Boolean {
        return try {
            val url = "$portalUrl/server/load.php?type=stb&action=handshake&token=&JsHttpRequest=1-xml"
            val response = doGet(url)
            val parsed = json.decodeFromString<StalkerHandshakeResponse>(response)
            token = parsed.js?.token ?: return false
            true
        } catch (e: Exception) {
            println("[Stalker] Handshake failed: ${e.message}")
            false
        }
    }

    /** Step 2: Get profile (validates the connection) */
    suspend fun getProfile(): Boolean {
        return try {
            val url = "$portalUrl/server/load.php?type=stb&action=get_profile&JsHttpRequest=1-xml"
            val response = doGet(url)
            response.contains("\"id\"")
        } catch (_: Exception) { false }
    }

    /** Step 3: Get all channels */
    suspend fun getChannels(): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        try {
            // Get genres first for group names
            val genreUrl = "$portalUrl/server/load.php?type=itv&action=get_genres&JsHttpRequest=1-xml"
            val genreResponse = doGet(genreUrl)
            val genres = json.decodeFromString<StalkerGenreResponse>(genreResponse)
            val genreMap = genres.js?.mapNotNull { g -> g.id?.let { it to (g.title ?: "Unknown") } }?.toMap() ?: emptyMap()

            // Get all channels page by page
            var page = 1
            var hasMore = true
            while (hasMore) {
                val url = "$portalUrl/server/load.php?type=itv&action=get_all_channels&p=$page&JsHttpRequest=1-xml"
                val response = doGet(url)
                val parsed = json.decodeFromString<StalkerChannelResponse>(response)
                val data = parsed.js?.data ?: break

                for (ch in data) {
                    val streamCmd = ch.cmd ?: continue
                    val groupName = ch.tvGenreId?.let { genreMap[it] } ?: "Uncategorized"
                    channels.add(
                        IptvChannel(
                            id = ch.id?.toString() ?: continue,
                            name = ch.name ?: "Unknown",
                            logo = ch.logo,
                            group = groupName,
                            streamUrl = streamCmd // Will be resolved via create_link before playback
                        )
                    )
                }

                val totalItems = parsed.js?.totalItems ?: 0
                val maxPageItems = parsed.js?.maxPageItems ?: 20
                hasMore = page * maxPageItems < totalItems
                page++
            }
        } catch (e: Exception) {
            println("[Stalker] Get channels failed: ${e.message}")
        }
        return channels
    }

    /** Resolve a channel's cmd to a playable stream URL */
    suspend fun resolveStreamUrl(cmd: String): String? {
        return try {
            val encodedCmd = cmd.encodeURLParameter()
            val url = "$portalUrl/server/load.php?type=itv&action=create_link&cmd=$encodedCmd&forced_storage=undefined&disable_ad=0&JsHttpRequest=1-xml"
            val response = doGet(url)
            val parsed = json.decodeFromString<StalkerLinkResponse>(response)
            parsed.js?.cmd?.replace("ffmpeg ", "")?.trim()
        } catch (e: Exception) {
            println("[Stalker] Resolve stream failed: ${e.message}")
            null
        }
    }

    private suspend fun doGet(url: String): String {
        return client.get(url) {
            headers {
                baseHeaders.forEach { (k, v) -> append(k, v) }
            }
        }.bodyAsText()
    }

    // ── Response models ──

    @Serializable
    data class StalkerHandshakeResponse(val js: StalkerToken? = null)
    
    @Serializable
    data class StalkerToken(val token: String? = null)

    @Serializable
    data class StalkerGenreResponse(val js: List<StalkerGenre>? = null)
    
    @Serializable
    data class StalkerGenre(val id: String? = null, val title: String? = null)

    @Serializable
    data class StalkerChannelResponse(val js: StalkerChannelData? = null)
    
    @Serializable
    data class StalkerChannelData(
        val data: List<StalkerChannel>? = null,
        @SerialName("total_items") val totalItems: Int? = null,
        @SerialName("max_page_items") val maxPageItems: Int? = null
    )
    
    @Serializable
    data class StalkerChannel(
        val id: Int? = null,
        val name: String? = null,
        val logo: String? = null,
        val cmd: String? = null,
        @SerialName("tv_genre_id") val tvGenreId: String? = null
    )

    @Serializable
    data class StalkerLinkResponse(val js: StalkerLink? = null)
    
    @Serializable
    data class StalkerLink(val cmd: String? = null)
}
