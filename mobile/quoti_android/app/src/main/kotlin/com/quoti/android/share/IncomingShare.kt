package com.quoti.android.share

import android.content.Intent
import com.quoti.android.core.model.QuotiPost
import com.quoti.android.core.model.SocialPlatform
import java.net.URI
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

data class IncomingSharePayload(
    val text: String? = null,
    val subject: String? = null,
    val mimeType: String? = null,
) {
    val isEmpty: Boolean
        get() = text.isNullOrBlank() && subject.isNullOrBlank()
}

data class IncomingShareDraft(
    val post: QuotiPost,
    val rawText: String,
)

object IncomingShareReader {
    fun fromIntent(intent: Intent?): IncomingSharePayload? {
        if (intent?.action != Intent.ACTION_SEND) {
            return null
        }

        val mimeType = intent.type ?: return null

        if (!mimeType.startsWith("text/")) {
            return null
        }

        val payload =
            IncomingSharePayload(
                text = intent.getStringExtra(Intent.EXTRA_TEXT),
                subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
                mimeType = mimeType,
            )

        return payload.takeUnless { it.isEmpty }
    }
}

class IncomingShareNormalizer(
    private val clock: () -> Instant = { Instant.now() },
) {
    fun normalize(payload: IncomingSharePayload): IncomingShareDraft? {
        val rawText = firstNonEmpty(payload.text, payload.subject) ?: return null
        val sourceUrl = extractFirstUrl(rawText)
        val platform = resolvePlatform(sourceUrl)
        val authorHandle = resolveAuthorHandle(sourceUrl)
        val content = resolveContent(rawText, sourceUrl, platform)
        val capturedAt = DateTimeFormatter.ISO_INSTANT.format(clock())

        return IncomingShareDraft(
            rawText = rawText,
            post =
                QuotiPost(
                    id = "incoming-${capturedAt.hashCode()}",
                    platform = platform,
                    authorName = resolveAuthorName(authorHandle, platform),
                    authorHandle = authorHandle ?: platform.label,
                    content = content,
                    sourceUrl = sourceUrl,
                    capturedAt = capturedAt,
                ),
        )
    }
}

private fun firstNonEmpty(vararg values: String?): String? {
    return values.firstNotNullOfOrNull { value -> value?.trim()?.takeIf { it.isNotEmpty() } }
}

private fun resolveContent(
    rawText: String,
    sourceUrl: String?,
    platform: SocialPlatform,
): String {
    if (sourceUrl == null) {
        return rawText.trim()
    }

    val withoutUrl = rawText.replaceFirst(sourceUrl, "").trim()

    return withoutUrl.ifEmpty { "Shared from ${platform.label}." }
}

private fun resolvePlatform(sourceUrl: String?): SocialPlatform {
    val host = parseUri(sourceUrl)?.host?.lowercase(Locale.US).orEmpty()

    return when {
        host == "x.com" || host.endsWith(".x.com") -> SocialPlatform.X
        host == "twitter.com" || host.endsWith(".twitter.com") -> SocialPlatform.X
        host == "threads.net" || host.endsWith(".threads.net") -> SocialPlatform.Threads
        host == "bsky.app" || host.endsWith(".bsky.app") -> SocialPlatform.Bluesky
        host == "linkedin.com" || host.endsWith(".linkedin.com") -> SocialPlatform.LinkedIn
        else -> SocialPlatform.X
    }
}

private fun resolveAuthorHandle(sourceUrl: String?): String? {
    val uri = parseUri(sourceUrl) ?: return null
    val host = uri.host?.lowercase(Locale.US).orEmpty()
    val segments = uri.path.split("/").filter { it.isNotBlank() }

    if (
        (host == "x.com" || host.endsWith(".x.com") || host == "twitter.com" || host.endsWith(".twitter.com")) &&
            segments.size >= 3 &&
            segments[1] == "status"
    ) {
        return "@${segments.first()}"
    }

    if ((host == "threads.net" || host.endsWith(".threads.net")) && segments.isNotEmpty()) {
        return segments.first().let { if (it.startsWith("@")) it else "@$it" }
    }

    return null
}

private fun resolveAuthorName(
    authorHandle: String?,
    platform: SocialPlatform,
): String {
    return authorHandle?.removePrefix("@")?.takeIf { it.isNotBlank() }
        ?: "Shared ${platform.label} post"
}

private fun extractFirstUrl(value: String): String? {
    val match = Regex("""https?://\S+""").find(value) ?: return null
    return match.value.replace(Regex("""[),.;]+$"""), "")
}

private fun parseUri(value: String?): URI? {
    if (value.isNullOrBlank()) {
        return null
    }

    return runCatching { URI(value) }.getOrNull()
}
