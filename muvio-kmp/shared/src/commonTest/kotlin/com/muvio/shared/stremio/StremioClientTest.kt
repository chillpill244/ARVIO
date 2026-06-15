package com.muvio.shared.stremio

import com.muvio.shared.network.StremioClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StremioClientTest {

    private fun mockClient(responseJson: String): StremioClient {
        val engine = MockEngine {
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
        return StremioClient(httpClient)
    }

    @Test
    fun getAddonStreams_parsesTorrentioResponse() = runTest {
        val json = """
            {
              "streams": [
                {
                  "name": "Torrentio\n⚙️ Cached",
                  "title": "1080p\nBetter.Call.Saul.S01E01.WEBRip.1080p.mkv\n👤 127 💾 6.03 GB ⚙️ DTS",
                  "url": "https://torrentio.strem.fun/stream/tt0903747.mkv",
                  "behaviorHints": {
                    "notWebReady": false,
                    "bingeGroup": "torrentio|tt0903747"
                  }
                }
              ]
            }
        """.trimIndent()

        val client = mockClient(json)
        val result = client.getAddonStreams("https://torrentio.strem.fun/stream/series/tt0903747:1:1.json")

        assertEquals(1, result.streams?.size)
        val stream = result.streams!!.first()
        assertEquals("https://torrentio.strem.fun/stream/tt0903747.mkv", stream.url)
        assertEquals("1080p", stream.getQuality())
        assertEquals("1080p", stream.getSourceName())
        assertTrue(stream.hasPlayableLink())
        assertNull(stream.infoHash)
        assertEquals(false, stream.behaviorHints?.notWebReady)
        assertEquals("torrentio|tt0903747", stream.behaviorHints?.bingeGroup)
    }

    @Test
    fun getAddonStreams_parsesProxyHeaders() = runTest {
        val json = """
            {
              "streams": [
                {
                  "url": "https://example.com/stream.mp4",
                  "behaviorHints": {
                    "notWebReady": true,
                    "proxyHeaders": {
                      "request": {
                        "Authorization": "Bearer tok123",
                        "Referer": "https://example.com/"
                      }
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val client = mockClient(json)
        val result = client.getAddonStreams("https://addon.example.com/stream/movie/tt1234.json")

        val stream = result.streams!!.first()
        assertEquals(true, stream.behaviorHints?.notWebReady)
        assertEquals("Bearer tok123", stream.behaviorHints?.proxyHeaders?.request?.get("Authorization"))
    }

    @Test
    fun getAddonManifest_parsesManifest() = runTest {
        val json = """
            {
              "id": "com.linvo.torrentio",
              "version": "0.0.14",
              "name": "Torrentio",
              "description": "Provides torrent streams from scrapers",
              "types": ["movie", "series"],
              "resources": ["stream", "catalog"],
              "catalogs": [],
              "idPrefixes": ["tt", "kitsu"],
              "behaviorHints": {
                "configurable": true,
                "configurationRequired": false
              }
            }
        """.trimIndent()

        val client = mockClient(json)
        val result = client.getAddonManifest("https://torrentio.strem.fun/manifest.json")

        assertEquals("com.linvo.torrentio", result.id)
        assertEquals("Torrentio", result.name)
        assertEquals(listOf("movie", "series"), result.types)
        assertEquals(listOf("tt", "kitsu"), result.idPrefixes)
        assertEquals(true, result.behaviorHints?.configurable)
        assertNotNull(result.resources)
    }

    @Test
    fun getAddonCatalog_parsesEmptyCatalog() = runTest {
        val json = """{"metas": []}"""
        val client = mockClient(json)
        val result = client.getAddonCatalog("https://addon.example.com/catalog/movie/top.json")
        assertEquals(0, result.metas?.size)
        assertNull(result.items)
    }

    @Test
    fun getAddonStreams_emptyResponse_returnsNull() = runTest {
        val json = """{}"""
        val client = mockClient(json)
        val result = client.getAddonStreams("https://addon.example.com/stream/movie/tt99.json")
        assertNull(result.streams)
    }

    @Test
    fun stremioStream_getQuality_handles4K() = runTest {
        val json = """
            {
              "streams": [
                {"title": "4K HDR BluRay\nMovie.Title.2160p.mkv", "url": "https://cdn.example.com/4k.mkv"},
                {"title": "BD 720p Remux", "url": "https://cdn.example.com/720.mkv"},
                {"name": "Source 1080p WEBRip", "url": "https://cdn.example.com/1080.mkv"}
              ]
            }
        """.trimIndent()

        val client = mockClient(json)
        val streams = client.getAddonStreams("https://example.com/stream/movie/tt1.json").streams!!

        assertEquals("4K", streams[0].getQuality())
        assertEquals("720p", streams[1].getQuality())
        assertEquals("1080p", streams[2].getQuality())
    }
}
