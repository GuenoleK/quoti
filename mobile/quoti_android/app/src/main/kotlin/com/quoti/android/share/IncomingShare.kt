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
    val title: String? = null,
    val mimeType: String? = null,
) {
    val isEmpty: Boolean
        get() = text.isNullOrBlank() && subject.isNullOrBlank() && title.isNullOrBlank()
}

data class IncomingShareDraft(
    val post: QuotiPost,
    val rawText: String,
    val rawSubject: String? = null,
    val rawTitle: String? = null,
    val missingFields: Set<IncomingShareMissingField> = emptySet(),
)

enum class IncomingShareMissingField {
    Content,
    AuthorName,
    AuthorHandle,
    SourceUrl,
}

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
                text = intent.textExtra(Intent.EXTRA_TEXT),
                subject = intent.textExtra(Intent.EXTRA_SUBJECT),
                title = intent.textExtra(Intent.EXTRA_TITLE),
                mimeType = mimeType,
            )

        return payload.takeUnless { it.isEmpty }
    }
}

class IncomingShareNormalizer(
    private val clock: () -> Instant = { Instant.now() },
) {
    fun normalize(payload: IncomingSharePayload): IncomingShareDraft? {
        val rawText = firstNonEmpty(payload.text, payload.subject, payload.title) ?: return null
        val allSharedText = listOfNotNull(payload.text, payload.subject, payload.title).joinToString("\n")
        val sourceUrl = extractSourceUrl(allSharedText)
        val platform = resolvePlatform(sourceUrl)
        val parsedMetadata = parseSharedMetadata(allSharedText, platform)
        val authorHandle = resolveAuthorHandle(sourceUrl) ?: parsedMetadata.authorHandle
        val content = resolveContent(payload, sourceUrl, platform, parsedMetadata)
        val capturedAt = DateTimeFormatter.ISO_INSTANT.format(clock())
        val authorName =
            parsedMetadata.authorName
                ?: resolveAuthorName(authorHandle, platform)
        val missingFields =
            buildSet {
                if (sourceUrl == null) add(IncomingShareMissingField.SourceUrl)
                if (parsedMetadata.content == null && sourceUrl != null && content == sourceUrl) {
                    add(IncomingShareMissingField.Content)
                }
                if (parsedMetadata.authorName == null && authorHandle == null) {
                    add(IncomingShareMissingField.AuthorName)
                }
                if (authorHandle == null) {
                    add(IncomingShareMissingField.AuthorHandle)
                }
            }

        return IncomingShareDraft(
            rawText = rawText,
            rawSubject = payload.subject?.trim()?.takeIf { it.isNotEmpty() },
            rawTitle = payload.title?.trim()?.takeIf { it.isNotEmpty() },
            missingFields = missingFields,
            post =
                QuotiPost(
                    id = "incoming-${capturedAt.hashCode()}",
                    platform = platform,
                    authorName = authorName,
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
    payload: IncomingSharePayload,
    sourceUrl: String?,
    platform: SocialPlatform,
    parsedMetadata: ParsedShareMetadata,
): String {
    parsedMetadata.content?.let { content ->
        return content
    }

    val candidates =
        listOfNotNull(payload.text, payload.subject, payload.title)
            .map { value -> removeUrls(value).trim() }
            .map { value -> removePlatformNoise(value, platform).trim() }
            .filter { value ->
                value.isNotEmpty() &&
                    !value.equals(platform.label, ignoreCase = true) &&
                    !value.isGenericShareLabel(platform)
            }

    if (sourceUrl == null) {
        return candidates.firstOrNull() ?: ""
    }

    return candidates.firstOrNull() ?: sourceUrl
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
            segments[1] == "status" &&
            !segments.first().isReservedXPathSegment()
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

private fun extractSourceUrl(value: String): String? {
    val urls =
        Regex("""https?://\S+""")
            .findAll(value)
            .map { match -> match.value.cleanSharedUrl() }
            .toList()

    return urls.firstOrNull { url -> url.isXStatusUrl() } ?: urls.firstOrNull()
}

private fun parseUri(value: String?): URI? {
    if (value.isNullOrBlank()) {
        return null
    }

    return runCatching { URI(value) }.getOrNull()
}

private data class ParsedShareMetadata(
    val authorName: String? = null,
    val authorHandle: String? = null,
    val content: String? = null,
)

private fun parseSharedMetadata(
    value: String,
    platform: SocialPlatform,
): ParsedShareMetadata {
    val compact = removeUrls(value).lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

    parseAuthorOnPlatform(compact, platform)?.let { return it }
    parsePostedByAuthor(compact)?.let { return it }

    val authorHandle = Regex("""(?<![\w@])@([A-Za-z0-9_]{1,15})(?![\w])""").find(compact)?.value
    return ParsedShareMetadata(authorHandle = authorHandle)
}

private fun parseAuthorOnPlatform(
    value: String,
    platform: SocialPlatform,
): ParsedShareMetadata? {
    val match =
        Regex(
            pattern = """^(.+?)\s+on\s+(?:${Regex.escape(platform.label)}|Twitter):\s+"?(.+?)"?$""",
            options = setOf(RegexOption.IGNORE_CASE),
        ).find(value) ?: return null
    val author = match.groupValues[1].trim().takeIf { it.isNotEmpty() }
    val content = cleanSharedContent(match.groupValues[2])

    return ParsedShareMetadata(
        authorName = author,
        content = content,
    )
}

private fun parsePostedByAuthor(value: String): ParsedShareMetadata? {
    val match =
        Regex(
            pattern = """^(.+?)\s+\(@([A-Za-z0-9_]{1,15})\)\s+(?:posted|postee?)[:\s-]+(.+)$""",
            options = setOf(RegexOption.IGNORE_CASE),
        ).find(value) ?: return null

    return ParsedShareMetadata(
        authorName = match.groupValues[1].trim().takeIf { it.isNotEmpty() },
        authorHandle = "@${match.groupValues[2]}",
        content = cleanSharedContent(match.groupValues[3]),
    )
}

private fun cleanSharedContent(value: String): String? {
    return value
        .trim()
        .trim('"', '\'')
        .replace(Regex("""(?m)\s*(?:https?://)?(?:pic\.twitter\.com|t\.co)/\S+\s*$"""), "")
        .trim()
        .takeIf { it.isNotEmpty() }
}

private fun removeUrls(value: String): String {
    return value.replace(Regex("""https?://\S+"""), " ")
}

private fun removePlatformNoise(
    value: String,
    platform: SocialPlatform,
): String {
    return value
        .replace(Regex("""(?i)\s*/\s*${Regex.escape(platform.label)}\s*$"""), "")
        .replace(Regex("""(?i)\s*-\s*${Regex.escape(platform.label)}\s*$"""), "")
}

private fun String.isGenericShareLabel(platform: SocialPlatform): Boolean {
    val normalized =
        trim()
            .trimEnd('.')
            .lowercase(Locale.US)

    return normalized == "shared from ${platform.label.lowercase(Locale.US)}" ||
        normalized == "shared via ${platform.label.lowercase(Locale.US)}" ||
        normalized == "shared on ${platform.label.lowercase(Locale.US)}" ||
        normalized == "partage depuis ${platform.label.lowercase(Locale.US)}" ||
        normalized == "partage sur ${platform.label.lowercase(Locale.US)}"
}

private fun String.isReservedXPathSegment(): Boolean {
    return lowercase(Locale.US) in
        setOf(
            "i",
            "intent",
            "share",
            "home",
            "explore",
            "notifications",
            "messages",
            "search",
        )
}

private fun String.cleanSharedUrl(): String {
    return replace(Regex("""[\])}>,.;"']+$"""), "")
}

private fun String.isXStatusUrl(): Boolean {
    val uri = parseUri(this) ?: return false
    val host = uri.host?.lowercase(Locale.US).orEmpty()
    val segments = uri.path.split("/").filter { it.isNotBlank() }

    return (host == "x.com" || host.endsWith(".x.com") || host == "twitter.com" || host.endsWith(".twitter.com")) &&
        segments.size >= 3 &&
        segments[1] == "status"
}

private fun Intent.textExtra(name: String): String? {
    return getCharSequenceExtra(name)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}
