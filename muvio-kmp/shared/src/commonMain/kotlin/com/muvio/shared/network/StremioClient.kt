package com.muvio.shared.network

import com.muvio.shared.stremio.StremioCatalogResponse
import com.muvio.shared.stremio.StremioManifestResponse
import com.muvio.shared.stremio.StremioStreamResponse
import com.muvio.shared.stremio.StremioSubtitleResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/**
 * Thin Ktor wrapper for the Stremio addon protocol.
 * All endpoints accept a full URL (e.g. https://torrentio.strem.fun/stream/movie/tt1234567.json)
 * matching the addon's transport URL pattern.
 */
class StremioClient(private val httpClient: HttpClient) {

    suspend fun getAddonManifest(url: String): StremioManifestResponse =
        httpClient.get(url).body()

    suspend fun getAddonStreams(url: String): StremioStreamResponse =
        httpClient.get(url).body()

    suspend fun getAddonCatalog(url: String): StremioCatalogResponse =
        httpClient.get(url).body()

    suspend fun getSubtitles(url: String): StremioSubtitleResponse =
        httpClient.get(url).body()
}
