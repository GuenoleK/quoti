package com.quoti.android.share

import com.quoti.android.core.model.SocialPlatform
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    @Test
    fun keepsUrlOnlyXShareAsSharedLinkWithMissingContentMarker() {
        val normalizer =
            IncomingShareNormalizer(
                clock = { Instant.parse("2026-06-15T10:00:00.000Z") },
            )

        val draft =
            normalizer.normalize(
                IncomingSharePayload(
                    text = "https://twitter.com/maya_laurent/status/123?s=46&t=abc",
                    mimeType = "text/plain",
                ),
        )

        assertNotNull(draft)
        assertEquals(
            "https://twitter.com/maya_laurent/status/123?s=46&t=abc",
            draft!!.post.content,
        )
        assertEquals("maya_laurent", draft.post.authorName)
        assertEquals("@maya_laurent", draft.post.authorHandle)
        assertEquals(
            "https://twitter.com/maya_laurent/status/123?s=46&t=abc",
            draft.post.sourceUrl,
        )
        assertTrue(draft.missingFields.contains(IncomingShareMissingField.Content))
    }

    @Test
    fun usesShareSubjectWhenXTextOnlyContainsSourceUrl() {
        val normalizer =
            IncomingShareNormalizer(
                clock = { Instant.parse("2026-06-15T10:00:00.000Z") },
            )

        val draft =
            normalizer.normalize(
                IncomingSharePayload(
                    text = "https://x.com/maya_laurent/status/123",
                    subject = "Maya Laurent on X: \"The best product moments are quiet.\"",
                    mimeType = "text/plain",
                ),
            )

        assertNotNull(draft)
        assertEquals("The best product moments are quiet.", draft!!.post.content)
        assertEquals("Maya Laurent", draft.post.authorName)
        assertEquals("@maya_laurent", draft.post.authorHandle)
        assertTrue(draft.missingFields.isEmpty())
    }

    @Test
    fun doesNotTreatXInternalStatusUrlAsAuthorHandle() {
        val normalizer =
            IncomingShareNormalizer(
                clock = { Instant.parse("2026-06-15T10:00:00.000Z") },
            )

        val draft =
            normalizer.normalize(
                IncomingSharePayload(
                    text = "Shared from X. https://x.com/i/status/1934942792144113976",
                    mimeType = "text/plain",
                ),
            )

        assertNotNull(draft)
        assertEquals("https://x.com/i/status/1934942792144113976", draft!!.post.content)
        assertEquals("Shared X post", draft.post.authorName)
        assertEquals("X", draft.post.authorHandle)
        assertEquals("https://x.com/i/status/1934942792144113976", draft.post.sourceUrl)
        assertTrue(draft.missingFields.contains(IncomingShareMissingField.Content))
        assertTrue(draft.missingFields.contains(IncomingShareMissingField.AuthorName))
        assertTrue(draft.missingFields.contains(IncomingShareMissingField.AuthorHandle))
    }

    @Test
    fun prefersXStatusUrlOverShortMediaUrlInRichSharePayload() {
        val normalizer =
            IncomingShareNormalizer(
                clock = { Instant.parse("2026-06-15T10:00:00.000Z") },
            )

        val draft =
            normalizer.normalize(
                IncomingSharePayload(
                    text = "Mais comment ca « on » ? Vous avez pas gagné hein https://t.co/Cq6nGbLuQe https://x.com/i/status/2066866642169036946",
                    mimeType = "text/plain",
                ),
            )

        assertNotNull(draft)
        assertEquals("https://x.com/i/status/2066866642169036946", draft!!.post.sourceUrl)
        assertEquals("Mais comment ca « on » ? Vous avez pas gagné hein", draft.post.content)
    }
}
