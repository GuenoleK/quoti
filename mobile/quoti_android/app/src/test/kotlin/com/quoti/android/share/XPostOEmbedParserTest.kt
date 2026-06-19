package com.quoti.android.share

import org.junit.Assert.assertEquals
import org.junit.Test

class XPostOEmbedParserTest {
    @Test
    fun extractsTweetTextFromOEmbedHtmlWithoutMediaLink() {
        val html =
            """
            <blockquote class="twitter-tweet"><p lang="en" dir="ltr">Hello &amp; welcome from X<br><br>Question with trailing space <br><br>(<a href="https://x.com/source"> @source</a>) <a href="https://t.co/example">pic.twitter.com/example</a></p>&mdash; Source (@source)</blockquote>
            """.trimIndent()

        assertEquals(
            "Hello & welcome from X\n\nQuestion with trailing space\n\n(@source)",
            XPostOEmbedParser.extractTweetText(html),
        )
    }

    @Test
    fun removesTrailingShortLinkFromXCardHtml() {
        val html =
            """
            <blockquote class="twitter-tweet"><p lang="fr" dir="ltr">Mais comment ca on ? Vous avez pas gagne hein <a href="https://t.co/Cq6nGbLuQe">https://t.co/Cq6nGbLuQe</a></p>&mdash; Source (@source)</blockquote>
            """.trimIndent()

        assertEquals(
            "Mais comment ca on ? Vous avez pas gagne hein",
            XPostOEmbedParser.extractTweetText(html),
        )
    }

    @Test
    fun replacesCollapsedOEmbedMarkerWithBracketedEllipsis() {
        val html =
            """
            <blockquote class="twitter-tweet"><p lang="en" dir="ltr">A new headphone company called Daisy Sound reached out and sent me its first product, the Daisy One.<br><br>Here’s a quick unboxing and first look. These are beautiful headphones with really nice build quality. I especially like this dark green color called “Kelp,” as well as the… <a href="https://t.co/1o8R0hH8nx">pic.twitter.com/1o8R0hH8nx</a></p>&mdash; Ben Geskin (@BenGeskin)</blockquote>
            """.trimIndent()

        assertEquals(
            "A new headphone company called Daisy Sound reached out and sent me its first product, the Daisy One.\n\nHere’s a quick unboxing and first look. These are beautiful headphones with really nice build quality. I especially like this dark green color called “Kelp,” as well as the [...]",
            XPostOEmbedParser.extractTweetText(html),
        )
    }

    @Test
    fun keepsNaturalTrailingEllipsisWhenThereIsNoCollapsedMarker() {
        val html =
            """
            <blockquote class="twitter-tweet"><p lang="en" dir="ltr">Some thoughts are meant to trail off…</p>&mdash; Source (@source)</blockquote>
            """.trimIndent()

        assertEquals(
            "Some thoughts are meant to trail off…",
            XPostOEmbedParser.extractTweetText(html),
        )
    }
}
