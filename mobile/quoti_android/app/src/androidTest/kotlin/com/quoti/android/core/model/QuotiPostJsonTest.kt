package com.quoti.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotiPostJsonTest {
    @Test
    fun roundTripPreservesRelatedVideoPost() {
        val post =
            QuotiPost(
                id = "post-123",
                platform = SocialPlatform.X,
                authorName = "Author",
                authorHandle = "@author",
                content = "Main post",
                relatedPost =
                    RelatedPost(
                        content = "Related post",
                        authorName = "Source",
                        authorHandle = "@source",
                        sourceUrl = "https://x.com/source/status/456",
                        media =
                            listOf(
                                PostMedia.Video(
                                    variants =
                                        listOf(
                                            "https://video.twimg.com/ext/vid/avc1/320x320/low.mp4",
                                            "https://video.twimg.com/ext/vid/avc1/720x720/high.mp4",
                                        ),
                                    url = "https://video.twimg.com/ext/vid/avc1/720x720/high.mp4",
                                    posterUrl = "https://pbs.twimg.com/media/poster.jpg",
                                    duration = 20.0,
                                    alt = "Crowd",
                                ),
                            ),
                    ),
                publishedAt = "2026-06-16T12:00:00Z",
                sourceUrl = "https://x.com/author/status/123",
                media = listOf(PostMedia.Image("https://pbs.twimg.com/media/main.jpg")),
                capturedAt = "2026-06-16T12:01:00Z",
            )

        val restored = quotiPostFromJsonString(post.toJsonString())

        assertEquals(post, restored)
        assertTrue(restored.relatedPost?.media?.first() is PostMedia.Video)
    }
}
