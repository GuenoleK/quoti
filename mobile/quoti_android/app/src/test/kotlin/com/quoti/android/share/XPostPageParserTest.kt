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
    fun extractsLinkCardPreviewImageFromXPageHtml() {
        val html =
            """
            {
              "rest_id":"2069663474871853282",
              "card_image":"https:\/\/pbs.twimg.com\/card_img\/2069662000000000000\/rmc-sport-preview.jpg?format=jpg&amp;name=small"
            }
            """.trimIndent()

        val media =
            XPostPageParser.extractMedia(
                html = html,
                canonicalUrl = "https://x.com/RMCsport/status/2069663474871853282",
            )

        assertEquals(1, media.size)
        assertTrue(media[0] is PostMedia.Image)
        assertEquals(
            "https://pbs.twimg.com/card_img/2069662000000000000/rmc-sport-preview.jpg?format=jpg&name=large",
            (media[0] as PostMedia.Image).url,
        )
    }

    @Test
    fun treatsGenericMediaThumbnailAsVideoPosterWhenVideoInfoOwnsVariant() {
        val html =
            """
            {
              "rest_id":"789",
              "legacy":{
                "extended_entities":{
                  "media":[
                    {
                      "media_url_https":"https://pbs.twimg.com/media/hexed-teaser.jpg?format=jpg&amp;name=small",
                      "type":"video",
                      "video_info":{
                        "variants":[
                          {"url":"https://video.twimg.com/amplify_video/789/vid/avc1/1280x720/hexed.mp4?tag=16"}
                        ]
                      }
                    }
                  ]
                }
              }
            }
            """.trimIndent()

        val media =
            XPostPageParser.extractMedia(
                html = html,
                canonicalUrl = "https://x.com/DisneyAnimation/status/789",
            )

        assertEquals(1, media.size)
        assertTrue(media[0] is PostMedia.Video)
        assertEquals(
            "https://pbs.twimg.com/media/hexed-teaser.jpg?format=jpg&name=large",
            (media[0] as PostMedia.Video).posterUrl,
        )
        assertEquals(
            "https://video.twimg.com/amplify_video/789/vid/avc1/1280x720/hexed.mp4?tag=16",
            (media[0] as PostMedia.Video).url,
        )
    }

    @Test
    fun treatsRenderedVideoTagWithGenericMediaPosterAsVideo() {
        val html =
            """
            <article>
              <video
                src="https://video.twimg.com/amplify_video/2066907599577157632/pl/zddek6iXdypq0_Bv.m3u8?tag=28&amp;v=74b"
                poster="https://pbs.twimg.com/media/HK8nLPebwAAgGxO.jpg"
                muted=""
                playsInline="">
              </video>
            </article>
            """.trimIndent()

        val media = XPostPageParser.extractMedia(html)

        assertEquals(1, media.size)
        assertTrue(media[0] is PostMedia.Video)
        assertEquals(
            "https://pbs.twimg.com/media/HK8nLPebwAAgGxO.jpg",
            (media[0] as PostMedia.Video).posterUrl,
        )
        assertEquals(
            "https://video.twimg.com/amplify_video/2066907599577157632/pl/zddek6iXdypq0_Bv.m3u8?tag=28&v=74b",
            (media[0] as PostMedia.Video).url,
        )
    }

    @Test
    fun extractsOnlyMediaForRequestedStatusWhenPageContainsRelatedMedia() {
        val html =
            """
            {
              "rest_id":"123",
              "legacy":{
                "entities":{
                  "media":[
                    {"media_url_https":"https://pbs.twimg.com/media/main-first.jpg?format=jpg&amp;name=small"},
                    {"media_url_https":"https://pbs.twimg.com/media/main-second.jpg?format=jpg&amp;name=small"}
                  ]
                }
              },
              "quoted_status_result":{
                "result":{
                  "rest_id":"456",
                  "legacy":{
                    "extended_entities":{
                      "media":[
                        {
                          "media_url_https":"https://pbs.twimg.com/ext_tw_video_thumb/456/pu/img/quoted-poster.jpg",
                          "video_info":{
                            "variants":[
                              {"url":"https://video.twimg.com/ext_tw_video/456/pu/vid/avc1/720x720/quoted.mp4?tag=12"}
                            ]
                          }
                        }
                      ]
                    }
                  }
                }
              }
            }
            """.trimIndent()

        val mainMedia =
            XPostPageParser.extractMedia(
                html = html,
                canonicalUrl = "https://x.com/main/status/123",
            )
        val relatedMedia =
            XPostPageParser.extractMedia(
                html = html,
                canonicalUrl = "https://x.com/source/status/456",
            )

        assertEquals(
            listOf(
                "https://pbs.twimg.com/media/main-first.jpg?format=jpg&name=large",
                "https://pbs.twimg.com/media/main-second.jpg?format=jpg&name=large",
            ),
            mainMedia.filterIsInstance<PostMedia.Image>().map { image -> image.url },
        )
        assertEquals(0, mainMedia.filterIsInstance<PostMedia.Video>().size)
        assertEquals(1, relatedMedia.size)
        assertTrue(relatedMedia.first() is PostMedia.Video)
        assertEquals(
            "https://pbs.twimg.com/ext_tw_video_thumb/456/pu/img/quoted-poster.jpg",
            (relatedMedia.first() as PostMedia.Video).posterUrl,
        )
    }

    @Test
    fun prefersTargetSchemaVideoOverPageWideRecommendedMedia() {
        val targetVideoUrl =
            "https://video.twimg.com/amplify_video/2089754351552049152/vid/avc1/1280x720/target.mp4?tag=14"
        val targetPosterUrl = "https://pbs.twimg.com/media/HQBMjhfWUAAW7c8.jpg"
        val unrelatedImageUrl = "https://pbs.twimg.com/media/HQBM2gBbgAAYYy4.jpg"
        val unrelatedVideoPosterUrl = "https://pbs.twimg.com/tweet_video_thumb/HQBNaEDXAAEeyvi.jpg"
        val unrelatedVideoUrl = "https://video.twimg.com/tweet_video/HQBNaEDXAAEeyvi.mp4"
        val html =
            """
            <html>
              <head>
                <meta content="https://x.com/FCBarcelona/status/2089754425812275488" itemProp="url" />
                <meta content="$targetVideoUrl" itemProp="contentUrl" />
                <meta content="$targetPosterUrl" itemProp="thumbnailUrl" />
              </head>
              <body>
                $targetVideoUrl
                $targetPosterUrl
                $unrelatedVideoUrl
                $unrelatedVideoPosterUrl
                $unrelatedImageUrl
              </body>
            </html>
            """.trimIndent()

        val media =
            XPostPageParser.extractMedia(
                html = html,
                canonicalUrl = "https://x.com/FCBarcelona/status/2089754425812275488",
            )

        assertEquals(1, media.size)
        val video = media.single() as PostMedia.Video
        assertEquals(listOf(targetVideoUrl), video.variants)
        assertEquals(targetVideoUrl, video.url)
        assertEquals(targetPosterUrl, video.posterUrl)
    }

    @Test
    fun extractsAuthorAvatarNearHandleFromXPageHtml() {
        val html =
            """
            {
              "other_user":"other",
              "other_avatar":"https:\/\/pbs.twimg.com\/profile_images\/1\/other_normal.jpg",
              "screen_name":"maya_laurent",
              "profile_image_url_https":"https:\/\/pbs.twimg.com\/profile_images\/2\/maya_normal.jpg"
            }
            """.trimIndent()

        assertEquals(
            "https://pbs.twimg.com/profile_images/2/maya_400x400.jpg",
            XPostPageParser.extractAuthorAvatarUrl(html, authorHandle = "@maya_laurent"),
        )
    }

    @Test
    fun fallsBackToFirstProfileImageWhenHandleIsUnavailable() {
        val html =
            """
            {
              "profile_image_url_https":"https:\/\/pbs.twimg.com\/profile_images\/1\/fallback_normal.png"
            }
            """.trimIndent()

        assertEquals(
            "https://pbs.twimg.com/profile_images/1/fallback_400x400.png",
            XPostPageParser.extractAuthorAvatarUrl(html, authorHandle = null),
        )
    }

    @Test
    fun extractsFullTargetTweetTextFromPageData() {
        val html =
            """
            {
              "rest_id":"123",
              "legacy":{
                "full_text":"The visible part starts here and then keeps going after show more with the complete ending."
              },
              "quoted_status_result":{
                "result":{
                  "rest_id":"456",
                  "legacy":{
                    "full_text":"A different quoted post should not replace the target tweet even when it is nearby."
                  }
                }
              }
            }
            """.trimIndent()

        assertEquals(
            "The visible part starts here and then keeps going after show more with the complete ending.",
            XPostPageParser.extractTweetText(
                html = html,
                canonicalUrl = "https://x.com/main/status/123",
                fallbackText = "The visible part starts here...",
            ),
        )
    }

    @Test
    fun extractsTweetTextFromMetaDescriptionWithoutTrailingMediaLink() {
        val html =
            """
            <html>
              <head>
                <meta property="og:description" content="A club is sinking in https://t.co/UgYMum95d0" />
              </head>
            </html>
            """.trimIndent()

        assertEquals(
            "A club is sinking in",
            XPostPageParser.extractMetaTweetText(html),
        )
    }

    @Test
    fun prefersNoteTweetTextOverTruncatedLegacyText() {
        val html =
            """
            {
              "rest_id":"123",
              "legacy":{
                "full_text":"Start of long post..."
              },
              "note_tweet_results":{
                "result":{
                  "text":"Start of long post with all details that X hides behind Voir plus on mobile."
                }
              }
            }
            """.trimIndent()

        assertEquals(
            "Start of long post with all details that X hides behind Voir plus on mobile.",
            XPostPageParser.extractTweetText(
                html = html,
                canonicalUrl = "https://x.com/main/status/123",
                fallbackText = "Start of long post...",
            ),
        )
    }

    @Test
    fun extractsUnquotedNoteTweetTextFromCurrentXPagePayload() {
        val html =
            """
            client:VHdlZXQ6MjA2NzU1Mzg0OTYyODQwNTgzMQ==:${
                "$"
            }R[10]={
              __typename:"Tweet",
              rest_id:"2067553849628405831",
              details:${"$"}R[20]={__ref:"client:VHdlZXQ6MjA2NzU1Mzg0OTYyODQwNTgzMQ==:details"},
              note_tweet:${"$"}R[25]={__ref:"client:VHdlZXQ6MjA2NzU1Mzg0OTYyODQwNTgzMQ==:note_tweet"}
            },
            "client:VHdlZXQ6MjA2NzU1Mzg0OTYyODQwNTgzMQ==:details":${"$"}R[69]={
              __typename:"TBirdData",
              full_text:"A new headphone company called Daisy Sound reached out and sent me its first product, the Daisy One.\n\nHere’s a quick unboxing and first look. These are beautiful headphones with really nice build quality. I especially like this dark green color called “Kelp,” as well as the https://t.co/1o8R0hH8nx"
            },
            "client:VHdlZXQ6MjA2NzU1Mzg0OTYyODQwNTgzMQ==:note_tweet":${"$"}R[77]={
              __typename:"NoteTweetData",
              is_expandable:!0,
              note_tweet_results:${"$"}R[78]={__ref:"Tm90ZVR3ZWV0UmVzdWx0czoyMDY3NTUzODQ5NDY0ODIzODA4"}
            },
            "Tm90ZVR3ZWV0OjIwNjc1NTM4NDk0NjQ4MjM4MDg=":${"$"}R[81]={
              __typename:"NoteTweet",
              text:"A new headphone company called Daisy Sound reached out and sent me its first product, the Daisy One.\n\nHere’s a quick unboxing and first look. These are beautiful headphones with really nice build quality. I especially like this dark green color called “Kelp,” as well as the aluminum control dial and magnetic memory foam ear cushions.\n\nWhat do you think of the design?"
            }
            """.trimIndent()

        assertEquals(
            "A new headphone company called Daisy Sound reached out and sent me its first product, the Daisy One.\n\nHere’s a quick unboxing and first look. These are beautiful headphones with really nice build quality. I especially like this dark green color called “Kelp,” as well as the aluminum control dial and magnetic memory foam ear cushions.\n\nWhat do you think of the design?",
            XPostPageParser.extractTweetText(
                html = html,
                canonicalUrl = "https://x.com/BenGeskin/status/2067553849628405831",
                fallbackText = "A new headphone company called Daisy Sound reached out and sent me its first product, the Daisy One.\n\nHere’s a quick unboxing and first look. These are beautiful headphones with really nice build quality. I especially like this dark green color called “Kelp,” as well as the…",
            ),
        )
    }

    @Test
    fun extractsNoteTweetTextWhenTargetStatusIdIsRepeatedManyTimesBeforeIt() {
        val repeatedAnchors =
            (1..28).joinToString("\n") { index ->
                """rest_id:"2071962561696637060", repeated_anchor_$index:"${"x".repeat(2_000)}""""
            }
        val html =
            """
            $repeatedAnchors
            "client:VHdlZXQ6MjA3MTk2MjU2MTY5NjYzNzA2MA==:details":${"$"}R[55]={
              __typename:"TBirdData",
              full_text:"Post starts and is in the process of sinking in https://t.co/UgYMum95d0"
            },
            "client:VHdlZXQ6MjA3MTk2MjU2MTY5NjYzNzA2MA==:note_tweet":${"$"}R[63]={
              __typename:"NoteTweetData",
              note_tweet_results:${"$"}R[67]={__ref:"Tm90ZVR3ZWV0OjIwNzE5NjI1NjE2Mjk1MzYyNTY="}
            },
            "Tm90ZVR3ZWV0OjIwNzE5NjI1NjE2Mjk1MzYyNTY=":${"$"}R[67]={
              __typename:"NoteTweet",
              text:"Post starts and is in the process of sinking into oblivion..."
            }
            """.trimIndent()

        assertEquals(
            "Post starts and is in the process of sinking into oblivion...",
            XPostPageParser.extractTweetText(
                html = html,
                canonicalUrl = "https://x.com/lnstantFoot/status/2071962561696637060",
                fallbackText = "Post starts and is in the process of sinking in...",
            ),
        )
    }

    @Test
    fun extractsFullTextForFetchedRelatedPostPage() {
        val html =
            """
            {
              "entryId":"tweet-456",
              "content":{
                "itemContent":{
                  "tweet_results":{
                    "result":{
                      "__typename":"Tweet",
                      "rest_id":"456",
                      "legacy":{
                        "full_text":"Quoted context begins here and continues past the collapsed preview until the real final sentence."
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent()

        assertEquals(
            "Quoted context begins here and continues past the collapsed preview until the real final sentence.",
            XPostPageParser.extractTweetText(
                html = html,
                canonicalUrl = "https://x.com/source/status/456",
                fallbackText = "Quoted context begins here...",
            ),
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
    fun extractsRelatedStatusUrlFromCurrentQuotedTweetResultsReference() {
        val html =
            """
            "TweetResults:2067889372402205117":${"$"}R[11]={
              rest_id:"2067889372402205117",
              result:${"$"}R[12]={__ref:"VHdlZXQ6MjA2Nzg4OTM3MjQwMjIwNTExNw=="}
            },
            "VHdlZXQ6MjA2Nzg4OTM3MjQwMjIwNTExNw==":${"$"}R[13]={
              rest_id:"2067889372402205117",
              quoted_tweet_results:${"$"}R[20]={__ref:"TweetResults:2067874979631317467"}
            }
            <div data-href="/HandofArsenal/status/2067874979631317467" role="link">
              <div class="line-clamp-5">ARSENAL PREMIER LEAGUE 26/27 FIXTURES</div>
            </div>
            """.trimIndent()

        assertEquals(
            "https://x.com/HandofArsenal/status/2067874979631317467",
            XPostPageParser.extractRelatedStatusUrl(
                html = html,
                canonicalUrl = "https://x.com/yannkees14/status/2067889372402205117",
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
