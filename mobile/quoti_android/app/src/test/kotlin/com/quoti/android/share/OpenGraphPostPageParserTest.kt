package com.quoti.android.share

import com.quoti.android.core.model.PostMedia
import com.quoti.android.core.model.SocialPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenGraphPostPageParserTest {
    @Test
    fun extractsThreadsOpenGraphPost() {
        val html =
            """
            <html>
                <head>
                    <meta property="og:title" content="Ikwue Inalegwu (&#064;disgruntled.dev) on Threads" />
                    <meta property="og:description" content="Next year is going to be a tricky one SEO wise for Android" />
                    <meta property="og:url" content="https://www.threads.com/&#064;disgruntled.dev/post/DZyfGbrCJWh" />
                    <meta property="og:image" content="https://scontent.example/profile_pic.jpg?x=1&amp;y=2" />
                </head>
            </html>
            """.trimIndent()

        val enrichment =
            OpenGraphPostPageParser.extract(
                html = html,
                sourceUrl = "https://www.threads.com/@disgruntled.dev/post/DZyfGbrCJWh?xmt=token",
                platform = SocialPlatform.Threads,
            )

        assertNotNull(enrichment)
        assertEquals("Ikwue Inalegwu", enrichment!!.authorName)
        assertEquals("@disgruntled.dev", enrichment.authorHandle)
        assertEquals("Next year is going to be a tricky one SEO wise for Android", enrichment.content)
        assertEquals("https://www.threads.com/@disgruntled.dev/post/DZyfGbrCJWh", enrichment.canonicalUrl)
        assertEquals("https://scontent.example/profile_pic.jpg?x=1&y=2", enrichment.authorAvatarUrl)
        assertTrue(enrichment.media.isEmpty())
    }

    @Test
    fun extractsThreadsAvatarAndPostImagesSeparately() {
        val avatarUrl = "https://scontent-cdg6-1.cdninstagram.com/v/t51.82787-19/avatar_n.jpg?x=1&amp;y=2"
        val firstMediaUrl = "https://scontent-cdg6-1.cdninstagram.com/v/t51.29350-15/post_a_n.jpg?x=3&amp;y=4"
        val secondMediaUrl = "https://scontent-cdg6-1.cdninstagram.com/v/t51.29350-15/post_b_n.webp?x=5&amp;y=6"
        val html =
            """
            <html>
                <head>
                    <meta property="og:title" content="Shane Craig (&#064;shanec.irl) on Threads" />
                    <meta property="og:description" content="This is factually false." />
                    <meta property="og:url" content="https://www.threads.com/&#064;shanec.irl/post/example" />
                    <meta property="og:image" content="$firstMediaUrl" />
                </head>
                <body>
                    <script type="application/json">
                        {"avatar":"${avatarUrl.replace("/", "\\/")}","image":"${secondMediaUrl.replace("/", "\\/")}"}
                    </script>
                </body>
            </html>
            """.trimIndent()

        val enrichment =
            OpenGraphPostPageParser.extract(
                html = html,
                sourceUrl = "https://www.threads.com/@shanec.irl/post/example",
                platform = SocialPlatform.Threads,
            )

        assertNotNull(enrichment)
        assertEquals("https://scontent-cdg6-1.cdninstagram.com/v/t51.82787-19/avatar_n.jpg?x=1&y=2", enrichment!!.authorAvatarUrl)
        assertEquals(2, enrichment.media.size)
        assertEquals(
            "https://scontent-cdg6-1.cdninstagram.com/v/t51.29350-15/post_a_n.jpg?x=3&y=4",
            enrichment.media[0].let { media -> (media as PostMedia.Image).url },
        )
        assertEquals(
            "https://scontent-cdg6-1.cdninstagram.com/v/t51.29350-15/post_b_n.webp?x=5&y=6",
            enrichment.media[1].let { media -> (media as PostMedia.Image).url },
        )
    }

    @Test
    fun extractsUpToFourThreadsCarouselImagesFromRichPayload() {
        val avatarUrl = "https://instagram.fguw2-1.fna.fbcdn.net/v/t51.2885-19/avatar_n.png?stp=dst-jpg_e0_s150x150_tt6"
        val unsignedImageUrl =
            "https://scontent-cdg4-1.cdninstagram.com/v/t51.82787-15/726674025_17933787402341861_157655259621143737_n.jpg?stp=dst-jpg_e35_tt6"
        val imageUrls =
            listOf(
                "https://scontent-cdg4-1.cdninstagram.com/v/t51.82787-15/728196045_17976039024107797_7008582197683970056_n.jpg?stp=dst-jpg_e35_s750x750_tt6&ccb=7-5&oh=sig1&oe=exp1",
                unsignedImageUrl,
                "https://scontent-cdg4-1.cdninstagram.com/v/t51.82787-15/726538794_17976039027107797_888897763228663464_n.jpg?stp=dst-jpg_e35_s360x360_tt6&ccb=7-5&oh=sig2&oe=exp2",
                "https://scontent-cdg4-2.cdninstagram.com/v/t51.82787-15/726758516_17976039036107797_1292612928432753365_n.jpg?stp=c0.224.896.896a_dst-jpg_e35_s896x896_tt6&ccb=7-5&oh=sig3&oe=exp3",
                "https://scontent-cdg4-1.cdninstagram.com/v/t51.82787-15/726948726_17976039048107797_2754296650089317608_n.jpg?stp=dst-jpg_e35_s480x480_tt6&ccb=7-5&oh=sig4&oe=exp4",
            )
        val firstImageFallback =
            "https://scontent-cdg4-1.cdninstagram.com/v/t51.82787-15/728196045_17976039024107797_7008582197683970056_n.jpg?stp=dst-jpg_e35_s480x480_tt6&ccb=7-5&oh=sig1b&oe=exp1b"
        val html =
            """
            <html>
                <head>
                    <meta property="og:title" content="Gizem Akdag (&#064;giz.akdag) on Threads" />
                    <meta property="og:description" content="World Cup postcards" />
                    <meta property="og:url" content="https://www.threads.com/&#064;giz.akdag/post/DZ0Ve_BDEH6" />
                    <meta property="og:image" content="${imageUrls.first()}" />
                </head>
                <body>
                    <script type="application/json">
                        {
                            "profile":"${avatarUrl.replace("/", "\\/")}",
                            "carousel":["${(imageUrls + firstImageFallback).joinToString("\",\"") { url -> url.replace("/", "\\/") }}"]
                        }
                    </script>
                </body>
            </html>
            """.trimIndent()

        val enrichment =
            OpenGraphPostPageParser.extract(
                html = html,
                sourceUrl = "https://www.threads.com/@giz.akdag/post/DZ0Ve_BDEH6",
                platform = SocialPlatform.Threads,
            )

        assertNotNull(enrichment)
        assertEquals(avatarUrl, enrichment!!.authorAvatarUrl)
        assertEquals(4, enrichment.media.size)
        val extractedUrls = enrichment.media.map { media -> (media as PostMedia.Image).url }
        val extractedVariants = enrichment.media.flatMap { media -> (media as PostMedia.Image).variants }
        assertFalse(extractedUrls.contains(unsignedImageUrl))
        assertEquals(listOf(imageUrls[0], imageUrls[2], imageUrls[3], imageUrls[4]).toSet(), extractedUrls.toSet())
        assertTrue(extractedVariants.isNotEmpty())
    }

    @Test
    fun extractsThreadsStructuredCarouselVideoAndImagesInOrder() {
        val avatarUrl = "https://scontent-cdg4-2.cdninstagram.com/v/t51.82787-19/avatar_n.jpg?stp=dst-jpg_s150x150_tt6"
        val videoPosterUrl =
            "https://scontent-cdg4-1.cdninstagram.com/v/t51.82787-15/726969366_18536626273079954_286701435546493934_n.jpg?stp=dst-jpg_e15_tt6&efg=video_default_cover_frame&ccb=7-5&oh=poster&oe=exp"
        val videoUrl =
            "https://scontent-cdg4-1.cdninstagram.com/o1/v/t16/f2/m84/clip.mp4?_nc_cat=104&ccb=17-1&oh=video&oe=exp"
        val imageUrls =
            listOf(
                "https://scontent-cdg4-1.cdninstagram.com/v/t51.82787-15/photo-one.jpg?stp=dst-jpg_e35_tt6&ccb=7-5&oh=one&oe=exp",
                "https://scontent-cdg4-1.cdninstagram.com/v/t51.82787-15/photo-two.jpg?stp=dst-jpg_e35_tt6&ccb=7-5&oh=two&oe=exp",
                "https://scontent-cdg4-2.cdninstagram.com/v/t51.82787-15/photo-three.jpg?stp=dst-jpg_e35_tt6&ccb=7-5&oh=three&oe=exp",
                "https://scontent-cdg6-1.cdninstagram.com/v/t51.82787-15/photo-four.jpg?stp=dst-jpg_e35_tt6&ccb=7-5&oh=four&oe=exp",
            )
        val html =
            """
            <html>
                <head>
                    <meta property="og:title" content="Brayan (&#064;psydesignerr) on Threads" />
                    <meta property="og:description" content="Clockwork Orange." />
                    <meta property="og:url" content="https://www.threads.com/&#064;psydesignerr/post/DZ0tLlkD9pe" />
                    <meta property="og:image" content="${imageUrls[1]}" />
                </head>
                <body>
                    <script type="application/json">
                        {
                            "target": {
                                "code": "DZ0tLlkD9pe",
                                "user": {
                                    "profile_pic_url": "$avatarUrl"
                                },
                                "carousel_media": [
                                    {
                                        "pk": "3923959858496230664",
                                        "accessibility_caption": "Animated opener",
                                        "image_versions2": {
                                            "candidates": [
                                                {"url": "$videoPosterUrl", "height": 900, "width": 720}
                                            ]
                                        },
                                        "video_versions": [
                                            {"type": 101, "url": "$videoUrl"}
                                        ]
                                    },
                                    {
                                        "pk": "3923959856675881614",
                                        "image_versions2": {
                                            "candidates": [
                                                {"url": "${imageUrls[0]}", "height": 1350, "width": 1080}
                                            ]
                                        },
                                        "video_versions": null
                                    },
                                    {
                                        "pk": "3923959857414139326",
                                        "image_versions2": {
                                            "candidates": [
                                                {"url": "${imageUrls[1]}", "height": 1350, "width": 1080}
                                            ]
                                        },
                                        "video_versions": null
                                    },
                                    {
                                        "pk": "3923959856977919505",
                                        "image_versions2": {
                                            "candidates": [
                                                {"url": "${imageUrls[2]}", "height": 1350, "width": 1080}
                                            ]
                                        },
                                        "video_versions": null
                                    },
                                    {
                                        "pk": "3923959857330250831",
                                        "image_versions2": {
                                            "candidates": [
                                                {"url": "${imageUrls[3]}", "height": 1350, "width": 1080}
                                            ]
                                        },
                                        "video_versions": null
                                    }
                                ]
                            }
                        }
                    </script>
                </body>
            </html>
            """.trimIndent()

        val enrichment =
            OpenGraphPostPageParser.extract(
                html = html,
                sourceUrl = "https://www.threads.com/@psydesignerr/post/DZ0tLlkD9pe?xmt=token",
                platform = SocialPlatform.Threads,
            )

        assertNotNull(enrichment)
        val media = enrichment!!.media
        assertEquals(4, media.size)
        val firstMedia = media[0] as PostMedia.Video
        assertEquals(videoUrl, firstMedia.url)
        assertEquals(videoPosterUrl, firstMedia.posterUrl)
        assertEquals("Animated opener", firstMedia.alt)
        assertEquals(imageUrls[0], (media[1] as PostMedia.Image).url)
        assertEquals(imageUrls[1], (media[2] as PostMedia.Image).url)
        assertEquals(imageUrls[2], (media[3] as PostMedia.Image).url)
    }

    @Test
    fun extractsLinkedInAvatarAndUpToFourPostImages() {
        val avatarUrl = "https://media.licdn.com/dms/image/v2/C4D03AQ/profile-displayphoto-shrink_200_200/profile"
        val imageUrls =
            listOf(
                "https://media.licdn.com/dms/image/v2/D4E22AQ/post-one",
                "https://media.licdn.com/dms/image/v2/D4E22AQ/post-two",
                "https://media.licdn.com/dms/image/v2/D4E22AQ/post-three",
                "https://media.licdn.com/dms/image/v2/D4E22AQ/post-four",
                "https://media.licdn.com/dms/image/v2/D4E22AQ/post-five",
            )
        val html =
            """
            <html>
                <head>
                    <meta property="og:title" content="Ada Lovelace on LinkedIn: &quot;Shipping context beats screenshots.&quot;" />
                    <meta property="og:description" content="Shipping context beats screenshots." />
                    <meta property="og:url" content="https://www.linkedin.com/feed/update/urn:li:activity:123" />
                    ${imageUrls.joinToString("\n") { url -> """<meta property="og:image" content="$url" />""" }}
                </head>
                <body>
                    <script type="application/json">
                        {"avatar":"$avatarUrl","extra":"${imageUrls.last()}"}
                    </script>
                </body>
            </html>
            """.trimIndent()

        val enrichment =
            OpenGraphPostPageParser.extract(
                html = html,
                sourceUrl = "https://www.linkedin.com/feed/update/urn:li:activity:123",
                platform = SocialPlatform.LinkedIn,
            )

        assertNotNull(enrichment)
        assertEquals(avatarUrl, enrichment!!.authorAvatarUrl)
        assertEquals(4, enrichment.media.size)
        assertEquals(
            imageUrls.take(4),
            enrichment.media.map { media -> (media as PostMedia.Image).url },
        )
    }

    @Test
    fun extractsLinkedInCreatorFromJsonLdPostPage() {
        val avatarUrl = "https://media.licdn.com/dms/image/v2/D4E03AQFotXGdRV7GHA/profile-displayphoto-scale_200_200/profile"
        val postImageUrl = "https://dms.licdn.com/playlist/vid/v2/D4E05AQGe2EMWDUGXBg/thumbnail"
        val html =
            """
            <html>
                <head>
                    <meta property="og:title" content="J-10 avant de retourner a la Google I/O Connect de Berlin | Guenole Kikabou" />
                    <meta property="og:url" content="https://fr.linkedin.com/posts/guenole_j-10-activity-7472316938749214720-ZDa6" />
                    <meta property="og:image" content="$postImageUrl" />
                    <script type="application/ld+json">
                        {
                            "@context": "http://schema.org",
                            "@type": "VideoObject",
                            "headline": "J-10 avant de retourner a la Google I/O Connect de Berlin",
                            "creator": {
                                "@type": "Person",
                                "name": "Guenole Kikabou",
                                "description": "INFJ - Ingenieur logiciel Full-Stack chez Klee Group",
                                "url": "https://fr.linkedin.com/in/guenole/en",
                                "image": {
                                    "@type": "ImageObject",
                                    "url": "$avatarUrl"
                                }
                            }
                        }
                    </script>
                </head>
            </html>
            """.trimIndent()

        val enrichment =
            OpenGraphPostPageParser.extract(
                html = html,
                sourceUrl = "https://www.linkedin.com/posts/guenole_j-10-activity-7472316938749214720-ZDa6",
                platform = SocialPlatform.LinkedIn,
            )

        assertNotNull(enrichment)
        assertEquals("Guenole Kikabou", enrichment!!.authorName)
        assertEquals("in/guenole", enrichment.authorHandle)
        assertEquals(avatarUrl, enrichment.authorAvatarUrl)
        assertEquals("J-10 avant de retourner a la Google I/O Connect de Berlin", enrichment.content)
    }

    @Test
    fun usesLinkedInProfilePathWhenCreatorHasNoDescription() {
        val html =
            """
            <html>
                <head>
                    <meta property="og:title" content="Shipping context beats screenshots. | Ada Lovelace" />
                    <script type="application/ld+json">
                        {
                            "@context": "http://schema.org",
                            "@type": "SocialMediaPosting",
                            "creator": {
                                "@type": "Person",
                                "name": "Ada Lovelace",
                                "url": "https://www.linkedin.com/in/ada-lovelace/en"
                            }
                        }
                    </script>
                </head>
            </html>
            """.trimIndent()

        val enrichment =
            OpenGraphPostPageParser.extract(
                html = html,
                sourceUrl = "https://www.linkedin.com/posts/ada-lovelace_activity-123",
                platform = SocialPlatform.LinkedIn,
            )

        assertNotNull(enrichment)
        assertEquals("Ada Lovelace", enrichment!!.authorName)
        assertEquals("in/ada-lovelace", enrichment.authorHandle)
        assertEquals("Shipping context beats screenshots.", enrichment.content)
    }

    @Test
    fun usesLinkedInPostsPathWhenNoCreatorProfileExists() {
        val html =
            """
            <html>
                <head>
                    <meta property="og:title" content="Google I/O Connect - had a blast | Danitsa Kostova" />
                    <meta property="og:url" content="https://www.linkedin.com/posts/danitsa_google-io-connect-activity-123" />
                </head>
            </html>
            """.trimIndent()

        val enrichment =
            OpenGraphPostPageParser.extract(
                html = html,
                sourceUrl = "https://www.linkedin.com/posts/danitsa_google-io-connect-activity-123",
                platform = SocialPlatform.LinkedIn,
            )

        assertNotNull(enrichment)
        assertEquals("Danitsa Kostova", enrichment!!.authorName)
        assertEquals("in/danitsa", enrichment.authorHandle)
        assertEquals("Google I/O Connect - had a blast", enrichment.content)
    }

    @Test
    fun extractsFacebookAvatarAndPostImagesSeparately() {
        val avatarUrl = "https://scontent.xx.fbcdn.net/v/t39.30808-1/profile_pic.jpg?x=1&amp;y=2"
        val firstImageUrl = "https://scontent.xx.fbcdn.net/v/t39.30808-6/post-one.jpg?x=3&amp;y=4"
        val secondImageUrl = "https://lookaside.fbsbx.com/lookaside/crawler/media/?media_id=12345"
        val html =
            """
            <html>
                <head>
                    <meta property="og:title" content="Marie Dupont on Facebook" />
                    <meta property="og:description" content="Le contexte change tout." />
                    <meta property="og:url" content="https://www.facebook.com/marie/posts/123" />
                    <meta property="og:image" content="$firstImageUrl" />
                </head>
                <body>
                    <script type="application/json">
                        {"profile":"${avatarUrl.replace("/", "\\/")}","image":"${secondImageUrl.replace("/", "\\/")}"}
                    </script>
                </body>
            </html>
            """.trimIndent()

        val enrichment =
            OpenGraphPostPageParser.extract(
                html = html,
                sourceUrl = "https://www.facebook.com/marie/posts/123",
                platform = SocialPlatform.Facebook,
            )

        assertNotNull(enrichment)
        assertEquals("https://scontent.xx.fbcdn.net/v/t39.30808-1/profile_pic.jpg?x=1&y=2", enrichment!!.authorAvatarUrl)
        assertEquals(2, enrichment.media.size)
        assertEquals(
            "https://scontent.xx.fbcdn.net/v/t39.30808-6/post-one.jpg?x=3&y=4",
            enrichment.media[0].let { media -> (media as PostMedia.Image).url },
        )
        assertEquals(
            secondImageUrl,
            enrichment.media[1].let { media -> (media as PostMedia.Image).url },
        )
    }
}
