package com.arflix.tv.util

import android.net.Uri
import androidx.media3.common.StreamKey
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HlsDownloadUtilTest {

    private fun parseMaster(text: String): HlsMultivariantPlaylist =
        HlsPlaylistParser().parse(
            Uri.parse("https://example.com/master.m3u8"),
            text.trimIndent().byteInputStream()
        ) as HlsMultivariantPlaylist

    private fun parseMedia(text: String): HlsMediaPlaylist =
        HlsPlaylistParser().parse(
            Uri.parse("https://example.com/media.m3u8"),
            text.trimIndent().byteInputStream()
        ) as HlsMediaPlaylist

    private fun classify(text: String): HlsInspection =
        HlsDownloadUtil.classifyMediaPlaylist(parseMedia(text), text.trimIndent())

    private val masterPlaylist = """
        #EXTM3U
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud1",NAME="English",DEFAULT=YES,URI="a1/prog_index.m3u8"
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud1",NAME="Spanish",DEFAULT=NO,URI="a2/prog_index.m3u8"
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud2",NAME="Atmos",DEFAULT=NO,URI="a3/prog_index.m3u8"
        #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,AUDIO="aud1"
        v1080/prog_index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1280x720,AUDIO="aud1"
        v720/prog_index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=8000000,RESOLUTION=1920x1080,AUDIO="aud2"
        v1080hi/prog_index.m3u8
    """

    @Test
    fun `buildVariantOptions maps variants with matching audio group keys`() {
        val options = HlsDownloadUtil.buildVariantOptions(parseMaster(masterPlaylist))

        assertThat(options).hasSize(3)
        // Sorted best first: 1080p@8M (variant 2), 1080p@5M (variant 0), 720p@2M (variant 1)
        assertThat(options.map { it.variantIndex }).containsExactly(2, 0, 1).inOrder()

        val best = options[0]
        assertThat(best.height).isEqualTo(1080)
        assertThat(best.streamKeys).containsExactly(
            StreamKey(HlsMultivariantPlaylist.GROUP_INDEX_VARIANT, 2),
            // Only the aud2 rendition (declaration index 2) belongs to this variant
            StreamKey(HlsMultivariantPlaylist.GROUP_INDEX_AUDIO, 2)
        )

        val aud1Variant = options[1]
        assertThat(aud1Variant.streamKeys).containsExactly(
            StreamKey(HlsMultivariantPlaylist.GROUP_INDEX_VARIANT, 0),
            StreamKey(HlsMultivariantPlaylist.GROUP_INDEX_AUDIO, 0),
            StreamKey(HlsMultivariantPlaylist.GROUP_INDEX_AUDIO, 1)
        )
    }

    @Test
    fun `variant labels include resolution and bitrate`() {
        val options = HlsDownloadUtil.buildVariantOptions(parseMaster(masterPlaylist))
        assertThat(options[0].label).isEqualTo("1080p • 8.0 Mbps")
        assertThat(options[2].label).isEqualTo("720p • 2.0 Mbps")
    }

    @Test
    fun `stream keys survive a serialization round trip`() {
        val keys = listOf(StreamKey(0, 2), StreamKey(1, 0), StreamKey(1, 1))
        val json = HlsDownloadUtil.serializeStreamKeys(keys)
        assertThat(HlsDownloadUtil.deserializeStreamKeys(json)).isEqualTo(keys)
    }

    @Test
    fun `deserializing null blank or malformed json yields empty list`() {
        assertThat(HlsDownloadUtil.deserializeStreamKeys(null)).isEmpty()
        assertThat(HlsDownloadUtil.deserializeStreamKeys("")).isEmpty()
        assertThat(HlsDownloadUtil.deserializeStreamKeys("not json")).isEmpty()
    }

    @Test
    fun `media playlist with end tag classifies as VOD`() {
        val result = classify(
            """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.0,
            seg0.ts
            #EXT-X-ENDLIST
            """
        )
        assertThat(result).isEqualTo(HlsInspection.MediaPlaylistVod)
    }

    @Test
    fun `media playlist without end tag or VOD type classifies as live`() {
        val result = classify(
            """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:1234
            #EXTINF:6.0,
            seg1234.ts
            """
        )
        assertThat(result).isEqualTo(HlsInspection.Live)
    }

    @Test
    fun `AES-128 full segment encryption is downloadable`() {
        val result = classify(
            """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:6
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x00000000000000000000000000000001
            #EXTINF:6.0,
            seg0.ts
            #EXT-X-ENDLIST
            """
        )
        assertThat(result).isEqualTo(HlsInspection.MediaPlaylistVod)
    }

    @Test
    fun `SAMPLE-AES DRM classifies as unsupported encryption`() {
        val result = classify(
            """
            #EXTM3U
            #EXT-X-VERSION:5
            #EXT-X-TARGETDURATION:6
            #EXT-X-KEY:METHOD=SAMPLE-AES,URI="skd://key",KEYFORMAT="com.apple.streamingkeydelivery",KEYFORMATVERSIONS="1"
            #EXTINF:6.0,
            seg0.ts
            #EXT-X-ENDLIST
            """
        )
        assertThat(result).isEqualTo(HlsInspection.UnsupportedEncryption)
    }
}
