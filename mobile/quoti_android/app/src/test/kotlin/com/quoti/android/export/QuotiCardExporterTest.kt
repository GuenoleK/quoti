package com.quoti.android.export

import org.junit.Assert.assertEquals
import org.junit.Test

class QuotiCardExporterTest {
    @Test
    fun `video export keeps enough duration and cadence for X clips`() {
        assertEquals(180_000L, VideoExportMaxDurationMs)
        assertEquals(30, VideoExportFrameRate)
        assertEquals(30, VideoExportLongClipFrameRate)
    }

    @Test
    fun `video export uses faster profile for long clips`() {
        assertEquals(
            VideoExportProfile(frameRate = 30, bitmapWidth = 720),
            videoExportProfileForDurationMs(30_000L),
        )
        assertEquals(
            VideoExportProfile(frameRate = 30, bitmapWidth = 640),
            videoExportProfileForDurationMs(118_000L),
        )
    }

    @Test
    fun `video export prefers source size that matches card output`() {
        val selected =
            selectExportVideoUrl(
                listOf(
                    "https://video.twimg.com/amplify_video/1/pl/source.m3u8?tag=28",
                    "https://video.twimg.com/amplify_video/1/vid/avc1/1920x1080/high.mp4?tag=28",
                    "https://video.twimg.com/amplify_video/1/vid/avc1/1280x720/export.mp4?tag=28",
                    "https://video.twimg.com/amplify_video/1/vid/avc1/640x360/low.mp4?tag=28",
                ),
            )

        assertEquals(
            "https://video.twimg.com/amplify_video/1/vid/avc1/640x360/low.mp4?tag=28",
            selected,
        )
    }

    @Test
    fun `video export falls back to hls source when no mp4 exists`() {
        val selected =
            selectExportVideoUrl(
                listOf(
                    "https://video.twimg.com/amplify_video/1/pl/source.m3u8?tag=28",
                ),
            )

        assertEquals(
            "https://video.twimg.com/amplify_video/1/pl/source.m3u8?tag=28",
            selected,
        )
    }

    @Test
    fun `hls export selects bounded video variant and matching audio playlist`() {
        val selection =
            selectHlsMediaPlaylistsForExport(
                masterPlaylist =
                    """
                    #EXTM3U
                    #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio-64000",URI="/video/pl/audio/64k.m3u8"
                    #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio-128000",URI="/video/pl/audio/128k.m3u8"
                    #EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=280000,RESOLUTION=644x270,AUDIO="audio-64000"
                    /video/pl/avc1/644x270/video.m3u8
                    #EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=531384,RESOLUTION=858x360,AUDIO="audio-64000"
                    /video/pl/avc1/858x360/video.m3u8
                    #EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=1471854,RESOLUTION=1718x720,AUDIO="audio-128000"
                    /video/pl/avc1/1718x720/video.m3u8
                    """.trimIndent(),
                playlistUrl = "https://video.twimg.com/amplify_video/1/pl/master.m3u8?tag=28",
            )

        assertEquals(
            HlsPlaylistSelection(
                videoPlaylistUrl = "https://video.twimg.com/video/pl/avc1/644x270/video.m3u8",
                audioPlaylistUrl = "https://video.twimg.com/video/pl/audio/64k.m3u8",
            ),
            selection,
        )
    }

    @Test
    fun `hls export duration uses playlist segments for long clips`() {
        val playlist =
            buildString {
                appendLine("#EXTM3U")
                repeat(39) { index ->
                    appendLine("#EXTINF:3.000,")
                    appendLine("segment-$index.m4s")
                }
                appendLine("#EXTINF:1.000,")
                appendLine("segment-last.m4s")
                appendLine("#EXT-X-ENDLIST")
            }

        assertEquals(118_000L, hlsExportDurationMsForMediaPlaylist(playlist))
        assertEquals(40, hlsExportSegmentCountForMediaPlaylist(playlist))
    }

    @Test
    fun `related single square media keeps source aspect ratio`() {
        assertEquals(
            868f,
            relatedMediaHeightFor(
                contentWidth = 868f,
                mediaCount = 1,
                firstMediaWidth = 720,
                firstMediaHeight = 720,
            ),
            0.001f,
        )
    }

    @Test
    fun `related single landscape media keeps source aspect ratio`() {
        assertEquals(
            488.25f,
            relatedMediaHeightFor(
                contentWidth = 868f,
                mediaCount = 1,
                firstMediaWidth = 1280,
                firstMediaHeight = 720,
            ),
            0.001f,
        )
    }

    @Test
    fun `related single tall video uses compact max height`() {
        assertEquals(
            420f,
            relatedMediaHeightFor(
                contentWidth = 868f,
                mediaCount = 1,
                firstMediaWidth = 720,
                firstMediaHeight = 1280,
                isFirstMediaVideo = true,
            ),
            0.001f,
        )
    }

    @Test
    fun `gpu video frame texture coordinates leave vertical transform to SurfaceTexture`() {
        val vertices = FloatArray(GpuVideoFrameVertexFloatCount)

        populateGpuVideoFrameVertices(
            vertices = vertices,
            rectLeft = 10f,
            rectTop = 20f,
            rectRight = 110f,
            rectBottom = 220f,
            surfaceWidth = 200,
            surfaceHeight = 400,
        )

        assertEquals(0f, vertices[2], 0.001f)
        assertEquals(0f, vertices[3], 0.001f)
        assertEquals(1f, vertices[8], 0.001f)
        assertEquals(0f, vertices[9], 0.001f)
        assertEquals(0f, vertices[14], 0.001f)
        assertEquals(1f, vertices[15], 0.001f)
        assertEquals(1f, vertices[20], 0.001f)
        assertEquals(1f, vertices[21], 0.001f)
    }

    @Test
    fun `related media grid keeps compact grid ratio`() {
        assertEquals(
            434f,
            relatedMediaHeightFor(
                contentWidth = 868f,
                mediaCount = 2,
                firstMediaWidth = 720,
                firstMediaHeight = 720,
            ),
            0.001f,
        )
    }

    @Test
    fun `file name keeps safe post id characters`() {
        assertEquals(
            "quoti-post_123-42.png",
            quotiCardFileName("Post_123", timestampMillis = 42),
        )
    }

    @Test
    fun `file name strips unsafe characters and falls back when empty`() {
        assertEquals(
            "quoti-card-42.png",
            quotiCardFileName("!!", timestampMillis = 42),
        )
    }

    @Test
    fun `video file name uses mp4 extension`() {
        assertEquals(
            "quoti-post_123-42.mp4",
            quotiVideoFileName("Post_123", timestampMillis = 42),
        )
    }
}
