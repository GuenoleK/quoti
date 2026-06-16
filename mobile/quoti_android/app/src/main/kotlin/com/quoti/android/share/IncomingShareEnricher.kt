package com.quoti.android.share

import com.quoti.android.core.model.PostMedia
import com.quoti.android.core.model.RelatedPost
import com.quoti.android.core.model.SocialPlatform
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class IncomingShareEnricher(
    private val xAdapter: XPostEnrichmentAdapter = XPostEnrichmentAdapter(),
) {
    suspend fun enrich(draft: IncomingShareDraft): IncomingShareDraft {
        val sourceUrl = draft.post.sourceUrl ?: return draft
        if (draft.post.platform != SocialPlatform.X) {
            return draft
        }

        val enrichment = xAdapter.fetch(sourceUrl) ?: return draft
        return draft.copy(
            post =
                draft.post.copy(
                    authorName = enrichment.authorName ?: draft.post.authorName,
                    authorHandle = enrichment.authorHandle ?: draft.post.authorHandle,
                    content = enrichment.content ?: draft.post.content,
                    sourceUrl = enrichment.canonicalUrl ?: draft.post.sourceUrl,
                    media = enrichment.media.ifEmpty { draft.post.media },
                    relatedPost = enrichment.relatedPost ?: draft.post.relatedPost,
                ),
            missingFields =
                draft.missingFields
                    .removeIfPresent(IncomingShareMissingField.AuthorName, enrichment.authorName)
                    .removeIfPresent(IncomingShareMissingField.AuthorHandle, enrichment.authorHandle)
                    .removeIfPresent(IncomingShareMissingField.Content, enrichment.content),
        )
    }
}

class XPostEnrichmentAdapter {
    suspend fun fetch(sourceUrl: String): XPostEnrichment? =
        withContext(Dispatchers.IO) {
            runCatching {
                fetchPost(sourceUrl = sourceUrl, includeRelatedPost = true)
            }.getOrNull()
        }

    private fun fetchPost(
        sourceUrl: String,
        includeRelatedPost: Boolean,
    ): XPostEnrichment? {
        val oEmbed = fetchOEmbed(sourceUrl) ?: return null
        val canonicalUrl = oEmbed.optString("url").takeIf { it.isNotBlank() }
        val authorUrl = oEmbed.optString("author_url").takeIf { it.isNotBlank() }
        val authorHandle = authorUrl?.toAuthorHandle() ?: canonicalUrl?.toAuthorHandle()
        val content = XPostOEmbedParser.extractTweetText(oEmbed.optString("html"))
        val html = canonicalUrl?.let(::fetchText)
        val pageMedia = html?.let(XPostPageParser::extractMedia).orEmpty()
        val relatedPost =
            if (includeRelatedPost && html != null) {
                XPostPageParser.extractRelatedStatusUrl(html, canonicalUrl = canonicalUrl.orEmpty())
                    ?.let { relatedUrl -> fetchPost(relatedUrl, includeRelatedPost = false) }
                    ?.toRelatedPost()
            } else {
                null
            }
        val media = pageMedia.withoutMediaAlreadyOwnedBy(relatedPost?.media.orEmpty())

        return XPostEnrichment(
            canonicalUrl = canonicalUrl,
            authorName = oEmbed.optString("author_name").takeIf { it.isNotBlank() },
            authorHandle = authorHandle,
            content = content,
            media = media,
            relatedPost = relatedPost,
        )
    }

    private fun fetchOEmbed(sourceUrl: String): JSONObject? {
        val encodedUrl = URLEncoder.encode(sourceUrl, StandardCharsets.UTF_8.name())
        val endpoint = "https://publish.twitter.com/oembed?omit_script=true&dnt=true&url=$encodedUrl"
        val body = fetchText(endpoint) ?: return null
        return JSONObject(body)
    }

    private fun fetchText(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 5_000
            connection.readTimeout = 7_000
            connection.setRequestProperty("User-Agent", "Quoti Android")

            if (connection.responseCode !in 200..299) {
                return null
            }

            connection.inputStream.bufferedReader().use { reader -> reader.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

data class XPostEnrichment(
    val canonicalUrl: String?,
    val authorName: String?,
    val authorHandle: String?,
    val content: String?,
    val media: List<PostMedia>,
    val relatedPost: RelatedPost?,
) {
    fun toRelatedPost(): RelatedPost? {
        val relatedContent = content?.takeIf { it.isNotBlank() } ?: return null
        return RelatedPost(
            authorHandle = authorHandle,
            authorName = authorName,
            content = relatedContent,
            media = media,
            sourceUrl = canonicalUrl,
        )
    }
}

internal object XPostOEmbedParser {
    fun extractTweetText(html: String): String? {
        val paragraph =
            Regex("""<p\b[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
                .find(html)
                ?.groupValues
                ?.get(1)
                ?: return null

        return paragraph
            .replace(Regex("""(?i)<br\s*/?>"""), "\n")
            .replace(Regex("""<[^>]+>"""), "")
            .decodeHtmlEntities()
            .replace(Regex("""(?m)\s*(?:https?://)?(?:pic\.twitter\.com|t\.co)/\S+\s*$"""), "")
            .lines()
            .joinToString("\n") { line -> line.trim() }
            .replace(Regex("""\(\s+@"""), "(@")
            .trim()
            .takeIf { it.isNotBlank() }
    }
}

internal object XPostPageParser {
    private val imageUrlPattern =
        Regex("""https://pbs\.twimg\.com/media/[^"'<\s]+""")
    private val videoPosterPattern =
        Regex("""https://pbs\.twimg\.com/(?:ext_tw_video_thumb|amplify_video_thumb|tweet_video_thumb)/[^"'<\s]+""")
    private val videoVariantPattern =
        Regex("""https://video\.twimg\.com/[^"'<\s]+?(?:\.mp4|\.m3u8)(?:\?[^"'<\s]+)?""")
    private val statusUrlPattern =
        Regex("""(?:https://(?:x|twitter)\.com)?/([A-Za-z0-9_]{1,15})/status/(\d+)""")

    fun extractMedia(html: String): List<PostMedia> {
        val normalized = html.normalizedHtml()
        val imageUrls =
            imageUrlPattern
                .findAll(normalized)
                .map { match -> match.value.normalizedMediaUrl() }
                .filterNot { imageUrl -> imageUrl.contains("profile_images") }
                .distinctBy { imageUrl -> imageUrl.mediaIdentity() }
                .take(4)
                .toList()
        val videos = extractVideos(normalized, imageUrls)
        val videoPosters = videos.mapNotNull { media -> media.posterUrl }.toSet()
        val images =
            imageUrls
                .filterNot { imageUrl -> imageUrl in videoPosters }
                .distinctBy { imageUrl -> imageUrl.mediaIdentity() }
                .take(4)
                .map { imageUrl -> PostMedia.Image(url = imageUrl) }
                .toList()

        return (videos + images).take(4)
    }

    fun extractRelatedStatusUrl(
        html: String,
        canonicalUrl: String,
    ): String? {
        val normalized = html.normalizedHtml()
        val canonicalStatusId = canonicalUrl.statusId()
        val quotedStatusIds = extractQuotedStatusIds(normalized)
        val candidates =
            statusUrlPattern
                .findAll(normalized)
                .map { match ->
                    XStatusCandidate(
                        handle = match.groupValues[1],
                        statusId = match.groupValues[2],
                        range = match.range,
                    )
                }
                .filterNot { candidate -> candidate.statusId == canonicalStatusId }
                .filterNot { candidate -> candidate.handle.isReservedXPathSegment() }
                .distinctBy { candidate -> candidate.statusId }
                .toList()

        val selected =
            candidates.firstOrNull { candidate -> candidate.statusId in quotedStatusIds }
                ?: candidates.firstOrNull { candidate -> hasRelatedSignalAround(normalized, candidate.range) }
                ?: candidates.firstOrNull { candidate -> hasEmbeddedStatusCardAround(normalized, candidate.range) }

        return selected?.toString()
    }

    private fun extractQuotedStatusIds(normalizedHtml: String): Set<String> {
        return Regex("""quoted_tweet_results:[^{}]+TweetResults:(\d+)""")
            .findAll(normalizedHtml)
            .map { match -> match.groupValues[1] }
            .toSet()
    }

    private fun extractVideos(
        normalizedHtml: String,
        fallbackPosters: List<String>,
    ): List<PostMedia.Video> {
        val variants =
            videoVariantPattern
                .findAll(normalizedHtml)
                .map { match -> match.value.normalizedMediaUrl() }
                .distinct()
                .sortedWith(
                    compareByDescending<String> { url -> url.videoVariantScore() },
                )
                .take(6)
                .toList()
        val posters =
            videoPosterPattern
                .findAll(normalizedHtml)
                .map { match -> match.value.normalizedMediaUrl() }
                .distinct()
                .take(2)
                .toList()
                .ifEmpty { fallbackPosters.take(1) }

        if (posters.isEmpty() && variants.isEmpty()) {
            return emptyList()
        }

        return listOf(
            PostMedia.Video(
                variants = variants,
                url = variants.firstOrNull { url -> ".mp4" in url } ?: variants.firstOrNull(),
                posterUrl = posters.firstOrNull(),
            ),
        )
    }

    private fun hasRelatedSignalAround(
        value: String,
        range: IntRange,
    ): Boolean {
        val start = (range.first - 1_000).coerceAtLeast(0)
        val end = (range.last + 1_000).coerceAtMost(value.lastIndex)
        val window = value.substring(start, end + 1).lowercase()

        return "quoted_status" in window ||
            "quotedstatus" in window ||
            "quoted_status_permalink" in window ||
            "quoted" in window ||
            "reply_to_results" in window ||
            "reply_to_user_results" in window
    }

    private fun hasEmbeddedStatusCardAround(
        value: String,
        range: IntRange,
    ): Boolean {
        val start = (range.first - 500).coerceAtLeast(0)
        val end = (range.last + 5_000).coerceAtMost(value.lastIndex)
        val window = value.substring(start, end + 1).lowercase()

        return "data-href" in window &&
            "role=\"link\"" in window &&
            ("line-clamp-5" in window || "media_entities" in window || "<video" in window)
    }

    private data class XStatusCandidate(
        val handle: String,
        val statusId: String,
        val range: IntRange,
    ) {
        override fun toString(): String = "https://x.com/$handle/status/$statusId"
    }
}

private fun String.toAuthorHandle(): String? {
    val path = runCatching { URI(this).path }.getOrNull() ?: return null
    val handle = path.split("/").firstOrNull { it.isNotBlank() } ?: return null
    return "@$handle"
}

private fun String.statusId(): String? {
    val path = runCatching { URI(this).path }.getOrNull() ?: return null
    val segments = path.split("/").filter { it.isNotBlank() }
    val statusIndex = segments.indexOf("status")
    return segments.getOrNull(statusIndex + 1)
}

private fun String.normalizedHtml(): String {
    return replace("\\/", "/")
        .replace("\\u002F", "/")
        .decodeHtmlEntities()
}

private fun String.normalizedMediaUrl(): String {
    return trim()
        .trimEnd('\\')
        .replace("\\/", "/")
        .replace("\\u002F", "/")
        .decodeHtmlEntities()
        .trimEnd(',', ';')
        .replace(Regex("""([?&]name=)[A-Za-z0-9_]+"""), "$1large")
        .replace(Regex(""":(?:small|medium|large|orig)$"""), ":large")
}

private fun String.mediaIdentity(): String {
    val path = runCatching { URI(this).path }.getOrNull().orEmpty()
    val fileName = path.substringAfterLast("/")
    return fileName
        .substringBefore("?")
        .substringBefore(":")
        .substringBeforeLast(".", fileName)
}

private fun String.isReservedXPathSegment(): Boolean {
    return lowercase() in
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

private fun String.videoVariantScore(): Int {
    val resolution =
        Regex("""/(\d+)x(\d+)/""")
            .find(this)
            ?.let { match -> match.groupValues[1].toInt() * match.groupValues[2].toInt() }
            ?: 0
    val formatScore = if (".mp4" in this) 1_000_000_000 else 0

    return formatScore + resolution
}

private fun List<PostMedia>.withoutMediaAlreadyOwnedBy(ownedMedia: List<PostMedia>): List<PostMedia> {
    if (ownedMedia.isEmpty()) {
        return this
    }

    val ownedKeys = ownedMedia.flatMap(PostMedia::mediaKeys).toSet()
    return filterNot { media -> media.mediaKeys().any { key -> key in ownedKeys } }
}

private fun PostMedia.mediaKeys(): List<String> {
    return when (this) {
        is PostMedia.Image -> listOf(url.mediaIdentity())
        is PostMedia.Video -> (listOfNotNull(posterUrl, url) + variants)
            .map { value -> value.mediaIdentity() }
    }
}

private fun String.decodeHtmlEntities(): String {
    return replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
}

private fun Set<IncomingShareMissingField>.removeIfPresent(
    field: IncomingShareMissingField,
    value: String?,
): Set<IncomingShareMissingField> {
    return if (value.isNullOrBlank()) this else this - field
}
