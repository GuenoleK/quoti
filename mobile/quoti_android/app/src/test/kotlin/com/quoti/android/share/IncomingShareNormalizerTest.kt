package com.quoti.android.share

import com.quoti.android.core.model.SocialPlatform
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class IncomingShareNormalizerTest {
    @Test
    fun normalizesXStatusUrlIntoCardDraft() {
        val normalizer =
            IncomingShareNormalizer(
                clock = { Instant.parse("2026-06-15T10:00:00.000Z") },
            )

        val draft =
            normalizer.normalize(
                IncomingSharePayload(
                    text = "Regarde ca https://x.com/dexerto/status/123",
                    mimeType = "text/plain",
                ),
            )

        assertNotNull(draft)
        assertEquals("Regarde ca", draft!!.post.content)
        assertEquals(SocialPlatform.X, draft.post.platform)
        assertEquals("dexerto", draft.post.authorName)
        assertEquals("@dexerto", draft.post.authorHandle)
        assertEquals("https://x.com/dexerto/status/123", draft.post.sourceUrl)
        assertEquals("2026-06-15T10:00:00Z", draft.post.capturedAt)
    }
}
