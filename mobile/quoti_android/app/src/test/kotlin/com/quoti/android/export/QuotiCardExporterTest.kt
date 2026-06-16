package com.quoti.android.export

import org.junit.Assert.assertEquals
import org.junit.Test

class QuotiCardExporterTest {
    @Test
    fun `video export keeps enough duration and cadence for X clips`() {
        assertEquals(60_000L, VideoExportMaxDurationMs)
        assertEquals(30, VideoExportFrameRate)
    }

    @Test
    fun `video export prefers 720p source over oversized variants`() {
        val selected =
            selectExportVideoUrl(
                listOf(
                    "https://video.twimg.com/amplify_video/1/vid/avc1/1920x1080/high.mp4?tag=28",
                    "https://video.twimg.com/amplify_video/1/vid/avc1/1280x720/export.mp4?tag=28",
                    "https://video.twimg.com/amplify_video/1/vid/avc1/640x360/low.mp4?tag=28",
                ),
            )

        assertEquals(
            "https://video.twimg.com/amplify_video/1/vid/avc1/1280x720/export.mp4?tag=28",
            selected,
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
