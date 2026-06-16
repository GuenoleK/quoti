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
}
