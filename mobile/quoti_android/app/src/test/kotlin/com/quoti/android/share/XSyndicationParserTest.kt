package com.quoti.android.share

import com.quoti.android.core.model.PostMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XSyndicationParserTest {
    @Test
    fun computesSyndicationTokenLikeXEmbedWidget() {
        assertEquals("54kpk6p5ht", XSyndicationToken.forStatusId("2115076845796270152"))
        assertEquals("3y54libozsy", XSyndicationToken.forStatusId("1628832338187636740"))
        assertEquals("42y6zv7ufp", XSyndicationToken.forStatusId("1683920951807971329"))
        assertEquals("5dhw8fvp5c", XSyndicationToken.forStatusId("2066944321085980673"))
        assertEquals("6dq1a2xwd93", XSyndicationToken.forStatusId("20"))
        assertEquals(null, XSyndicationToken.forStatusId("not-a-status"))
    }

    @Test
    fun extractsVideoFromSyndicationPayload() {
        val json =
            """
            {
              "__typename":"Tweet",
              "id_str":"2115076845796270152",
              "mediaDetails":[
                {
                  "type":"video",
                  "media_url_https":"https://pbs.twimg.com/ext_tw_video_thumb/2115076800000000000/pu/img/poster.jpg",
                  "ext_alt_text":null,
                  "video_info":{
                    "duration_millis":12345,
                    "variants":[
                      {"content_type":"application/x-mpegURL","url":"https://video.twimg.com/ext_tw_video/2115076800000000000/pu/pl/list.m3u8?tag=12"},
                      {"bitrate":632000,"content_type":"video/mp4","url":"https://video.twimg.com/ext_tw_video/2115076800000000000/pu/vid/avc1/320x568/low.mp4?tag=12"},
                      {"bitrate":2176000,"content_type":"video/mp4","url":"https://video.twimg.com/ext_tw_video/2115076800000000000/pu/vid/avc1/720x1280/high.mp4?tag=12"}
                    ]
                  }
                }
              ]
            }
            """.trimIndent()

        val media = XSyndicationParser.extractMedia(json)

        assertEquals(1, media.size)
        val video = media[0] as PostMedia.Video
        assertEquals(
            "https://video.twimg.com/ext_tw_video/2115076800000000000/pu/vid/avc1/720x1280/high.mp4?tag=12",
            video.url,
        )
        assertEquals(
            "https://pbs.twimg.com/ext_tw_video_thumb/2115076800000000000/pu/img/poster.jpg",
            video.posterUrl,
        )
        assertEquals(3, video.variants.size)
        assertEquals(12.345, video.duration!!, 0.0001)
        assertEquals(null, video.alt)
    }

    @Test
    fun extractsPhotosFromSyndicationPayload() {
        val json =
            """
            {
              "__typename":"Tweet",
              "mediaDetails":[
                {"type":"photo","media_url_https":"https://pbs.twimg.com/media/first.jpg","ext_alt_text":"Chess board"},
                {"type":"photo","media_url_https":"https://pbs.twimg.com/media/second.png"}
              ]
            }
            """.trimIndent()

        val media = XSyndicationParser.extractMedia(json)

        assertEquals(2, media.size)
        assertTrue(media.all { item -> item is PostMedia.Image })
        assertEquals("https://pbs.twimg.com/media/first.jpg", (media[0] as PostMedia.Image).url)
        assertEquals("Chess board", media[0].alt)
        assertEquals("https://pbs.twimg.com/media/second.png", (media[1] as PostMedia.Image).url)
    }

    @Test
    fun ignoresTombstonesAndPostsWithoutMedia() {
        assertTrue(XSyndicationParser.extractMedia("""{"__typename":"TweetTombstone"}""").isEmpty())
        assertTrue(XSyndicationParser.extractMedia("""{"__typename":"Tweet","text":"hello"}""").isEmpty())
    }
}
