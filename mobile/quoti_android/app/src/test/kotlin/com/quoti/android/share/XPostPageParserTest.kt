package com.quoti.android.share

import com.quoti.android.core.model.PostMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XPostPageParserTest {
    @Test
    fun extractsImagesAndVideoPosterFromXPageHtml() {
        val html =
            """
            {
              "media_url_https":"https:\/\/pbs.twimg.com\/media\/first.jpg:large",
              "same_media_small":"https:\/\/pbs.twimg.com\/media\/first?format=jpg&amp;name=small",
              "next_media":"https://pbs.twimg.com/media/second.jpg?format=jpg&amp;name=large",
              "avatar":"https://pbs.twimg.com/profile_images/not-a-post.jpg",
              "poster":"https:\/\/pbs.twimg.com\/ext_tw_video_thumb\/123\/pu\/img\/poster.jpg",
              "variant":"https:\/\/video.twimg.com\/ext_tw_video\/123\/pu\/vid\/avc1\/720x1280\/video.mp4?tag=12"
            }
            """.trimIndent()

        val media = XPostPageParser.extractMedia(html)

        assertEquals(3, media.size)
        assertTrue(media[0] is PostMedia.Video)
        assertEquals(
            "https://pbs.twimg.com/ext_tw_video_thumb/123/pu/img/poster.jpg",
            (media[0] as PostMedia.Video).posterUrl,
        )
        assertEquals(
            "https://video.twimg.com/ext_tw_video/123/pu/vid/avc1/720x1280/video.mp4?tag=12",
            (media[0] as PostMedia.Video).url,
        )
        assertEquals(
            listOf(
                "https://pbs.twimg.com/media/first.jpg:large",
                "https://pbs.twimg.com/media/second.jpg?format=jpg&name=large",
            ),
            media.filterIsInstance<PostMedia.Image>().map { image -> image.url },
        )
    }

    @Test
    fun ignoresDetachedVideoVariantWhenOnlyPostImagesArePresent() {
        val html =
            """
            {
              "image":"https://pbs.twimg.com/media/HK9DARsXcAA85qp.png",
              "variant_low":"https://video.twimg.com/amplify_video/2066944321085980673/vid/avc1/320x320/bEUytEjThxvW3vmH.mp4?tag=14",
              "variant_high":"https://video.twimg.com/amplify_video/2066944321085980673/vid/avc1/720x720/FkCzBBNOi21_mqBy.mp4?tag=14"
            }
            """.trimIndent()

        val media = XPostPageParser.extractMedia(html)

        assertEquals(1, media.size)
        assertTrue(media[0] is PostMedia.Image)
        assertEquals(
            "https://pbs.twimg.com/media/HK9DARsXcAA85qp.png",
            (media[0] as PostMedia.Image).url,
        )
    }

    @Test
    fun extractsRelatedStatusUrlFromQuotedTweetReference() {
        val html =
            """
            "quoted_status_permalink":{"expanded":"https://x.com/source/status/456"}
            "recommendation":"https://x.com/other/status/789"
            """.trimIndent()

        assertEquals(
            "https://x.com/source/status/456",
            XPostPageParser.extractRelatedStatusUrl(
                html = html,
                canonicalUrl = "https://x.com/main/status/123",
            ),
        )
    }

    @Test
    fun extractsRelativeEmbeddedStatusCardUrl() {
        val html =
            """
            <div data-href="/MarshallFCB/status/2066329547398611274" role="link">
              <div class="line-clamp-5">Related text</div>
            </div>
            """.trimIndent()

        assertEquals(
            "https://x.com/MarshallFCB/status/2066329547398611274",
            XPostPageParser.extractRelatedStatusUrl(
                html = html,
                canonicalUrl = "https://x.com/BamDarius_/status/2066866642169036946",
            ),
        )
    }

    @Test
    fun ignoresUnrelatedStatusUrlsWithoutRelatedSignals() {
        val html = """"recommendation":"https://x.com/other/status/789""""

        assertEquals(
            null,
            XPostPageParser.extractRelatedStatusUrl(
                html = html,
                canonicalUrl = "https://x.com/main/status/123",
            ),
        )
    }
}
