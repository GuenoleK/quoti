package com.quoti.android.export

import org.junit.Assert.assertEquals
import org.junit.Test

class QuotiCardExporterTest {
    @Test
    fun `file name keeps safe post id characters`() {
        assertEquals(
            "quoti-post_123-42.png",
            quotiCardFileName("Post_123", timestampMillis = 42),
        )
    }

    @Test
    fun `file name strips unsafe characters and falls back when empty`() {
        assertEquals(
            "quoti-card-42.png",
            quotiCardFileName("!!", timestampMillis = 42),
        )
    }
}
