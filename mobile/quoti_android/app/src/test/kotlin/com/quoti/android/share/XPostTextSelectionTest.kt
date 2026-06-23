package com.quoti.android.share

import org.junit.Assert.assertEquals
import org.junit.Test

class XPostTextSelectionTest {
    @Test
    fun prefersCompletePageTextWhenOEmbedIsCollapsed() {
        val oEmbedContent =
            "A new headphone company called Daisy Sound reached out and sent me its first product, the Daisy One.\n\n" +
                "Here is the visible part [...]"
        val pageContent =
            "A new headphone company called Daisy Sound reached out and sent me its first product, the Daisy One.\n\n" +
                "Here is the visible part with all details from the expanded tweet."

        assertEquals(
            pageContent,
            selectTweetText(
                oEmbedContent = oEmbedContent,
                pageContent = pageContent,
            ),
        )
    }

    @Test
    fun keepsOEmbedTextWhenPageTextDoesNotMatch() {
        val oEmbedContent = "The shared tweet text."

        assertEquals(
            oEmbedContent,
            selectTweetText(
                oEmbedContent = oEmbedContent,
                pageContent = "A nearby quoted post should not replace the target tweet.",
            ),
        )
    }
}
