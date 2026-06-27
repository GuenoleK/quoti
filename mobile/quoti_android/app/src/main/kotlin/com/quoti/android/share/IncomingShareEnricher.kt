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
import org.json.JSONArray
import org.json.JSONObject

class IncomingShareEnricher(
    private val xAdapter: XPostEnrichmentAdapter = XPostEnrichmentAdapter(),
    private val openGraphAdapter: OpenGraphPostEnrichmentAdapter = OpenGraphPostEnrichmentAdapter(),
) {
    suspend fun enrich(draft: IncomingShareDraft): IncomingShareDraft {
        val sourceUrl = draft.post.sourceUrl ?: return draft
        if (draft.post.platform != SocialPlatform.X) {
            val enrichment =
                openGraphAdapter.fetch(
                    sourceUrl = sourceUrl,
                    platform = draft.post.platform,
                ) ?: return draft

            return draft.copy(
                post =
                    draft.post.copy(
                        authorName = enrichment.authorName ?: draft.post.authorName,
                        authorHandle = enrichment.authorHandle ?: draft.post.authorHandle,
                        authorAvatarUrl = enrichment.authorAvatarUrl ?: draft.post.authorAvatarUrl,
                        content = enrichment.content ?: draft.post.content,
                        sourceUrl = enrichment.canonicalUrl ?: draft.post.sourceUrl,
                        media = enrichment.media.ifEmpty { draft.post.media },
                    ),
                missingFields =
                    draft.missingFields
                        .removeIfPresent(IncomingShareMissingField.AuthorName, enrichment.authorName)
                        .removeIfPresent(IncomingShareMissingField.AuthorHandle, enrichment.authorHandle)
                        .removeIfPresent(IncomingShareMissingField.Content, enrichment.content),
            )
        }

        val enrichment = xAdapter.fetch(sourceUrl, visibleText = draft.post.content) ?: return draft
        return draft.copy(
            post =
                draft.post.copy(
                    authorName = enrichment.authorName ?: draft.post.authorName,
                    authorHandle = enrichment.authorHandle ?: draft.post.authorHandle,
                    authorAvatarUrl = enrichment.authorAvatarUrl ?: draft.post.authorAvatarUrl,
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

class OpenGraphPostEnrichmentAdapter {
    suspend fun fetch(
        sourceUrl: String,
        platform: SocialPlatform,
    ): OpenGraphPostEnrichment? =
        withContext(Dispatchers.IO) {
            runCatching {
                val html = fetchText(sourceUrl) ?: return@runCatching null
                OpenGraphPostPageParser.extract(
                    html = html,
                    sourceUrl = sourceUrl,
                    platform = platform,
                )
            }.getOrNull()
        }

    private fun fetchText(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 5_000
            connection.readTimeout = 7_000
            connection.setRequestProperty("User-Agent", "Quoti Android")
            connection.setRequestProperty("Accept", "application/activity+json, application/json, text/html;q=0.8")

            if (connection.responseCode !in 200..299) {
                return null
            }

            connection.inputStream.bufferedReader().use { reader -> reader.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

data class OpenGraphPostEnrichment(
    val canonicalUrl: String?,
    val authorName: String?,
    val authorHandle: String?,
    val authorAvatarUrl: String?,
    val content: String?,
    val media: List<PostMedia>,
)

internal object OpenGraphPostPageParser {
    private val tagPattern =
        Regex(
            pattern = """<(meta|link)\b[^>]*>""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    private val attributePattern =
        Regex(
            pattern = """([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(["'])(.*?)\2""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    private val remoteUrlPattern =
        Regex("""https?://[^"'<\\\s]+""")
    private val jsonScriptPattern =
        Regex(
            pattern = """<script\b(?=[^>]*\btype\s*=\s*(["'])application/json\1)[^>]*>(.*?)</script>""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    private val jsonLdScriptPattern =
        Regex(
            pattern = """<script\b(?=[^>]*\btype\s*=\s*(["'])application/ld\+json\1)[^>]*>(.*?)</script>""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )

    fun extract(
        html: String,
        sourceUrl: String,
        platform: SocialPlatform,
    ): OpenGraphPostEnrichment? {
        val title =
            readMetaContent(html, "og:title")
                ?: readMetaContent(html, "twitter:title")
        val description =
            readMetaContent(html, "og:description")
                ?: readMetaContent(html, "description")
                ?: readMetaContent(html, "twitter:description")
        val canonicalUrl =
            readMetaContent(html, "og:url")
                ?: readCanonicalUrl(html)
                ?: sourceUrl
        val imageUrls =
            (
                readMetaContents(html, "og:image") +
                    readMetaContents(html, "og:image:url") +
                    readMetaContents(html, "og:image:secure_url") +
                    readMetaContents(html, "twitter:image") +
                    readMetaContents(html, "twitter:image:src")
            )
                .distinctBy { url -> url.normalizedRemoteAssetUrl().remoteAssetIdentity() }
        val videoUrls =
            (
                readMetaContents(html, "og:video") +
                    readMetaContents(html, "og:video:url") +
                    readMetaContents(html, "og:video:secure_url") +
                    readMetaContents(html, "twitter:player:stream")
            )
                .distinctBy { url -> url.normalizedRemoteAssetUrl().remoteAssetIdentity() }
        val titleMetadata = parseTitleMetadata(title, platform)
        val linkedInCreatorMetadata =
            if (platform == SocialPlatform.LinkedIn) {
                extractLinkedInCreatorMetadata(html)
            } else {
                null
            }
        val content = description.displayTextOrNull() ?: titleMetadata.content
        val linkedInFallbackHandle = linkedInCreatorMetadata?.profileUrl.toPlatformAuthorHandle(platform)
        val authorHandle =
            titleMetadata.authorHandle
                ?: linkedInFallbackHandle
                ?: canonicalUrl.toPlatformAuthorHandle(platform)
        val authorName =
            linkedInCreatorMetadata?.authorName
                ?: titleMetadata.authorName
                ?: linkedInFallbackHandle?.substringAfter("/")
                ?: authorHandle?.removePrefix("@")
        val allRemoteUrls = extractRemoteUrls(html)
        val authorAvatarUrl =
            linkedInCreatorMetadata?.authorAvatarUrl
                ?: findAuthorAvatarUrl(platform, imageUrls, allRemoteUrls)
        val media =
            extractStructuredMedia(
                platform = platform,
                html = html,
                sourceUrl = sourceUrl,
                canonicalUrl = canonicalUrl,
                authorAvatarUrl = authorAvatarUrl,
            ).ifEmpty {
                buildMedia(platform, imageUrls, videoUrls + allRemoteUrls, allRemoteUrls, authorAvatarUrl)
            }

        if (content.isNullOrBlank() && authorName.isNullOrBlank() && authorHandle.isNullOrBlank()) {
            return null
        }

        return OpenGraphPostEnrichment(
            canonicalUrl = canonicalUrl,
            authorName = authorName,
            authorHandle = authorHandle,
            authorAvatarUrl = authorAvatarUrl,
            content = content,
            media = media,
        )
    }

    fun readMetaContent(
        html: String,
        name: String,
    ): String? = readMetaContents(html, name).firstOrNull()

    private fun readMetaContents(
        html: String,
        name: String,
    ): List<String> {
        return tagPattern
            .findAll(html)
            .filter { match -> match.groupValues[1].equals("meta", ignoreCase = true) }
            .map { match -> match.value.readHtmlAttributes() }
            .filter { attributes ->
                attributes["property"]?.equals(name, ignoreCase = true) == true ||
                    attributes["name"]?.equals(name, ignoreCase = true) == true
            }
            .mapNotNull { attributes ->
                attributes["content"]
                    ?.decodeHtmlEntities()
                    ?.displayTextOrNull()
            }
            .distinctBy { value -> value.normalizedRemoteAssetUrl() }
            .toList()
    }

    private fun readCanonicalUrl(html: String): String? {
        return tagPattern
            .findAll(html)
            .filter { match -> match.groupValues[1].equals("link", ignoreCase = true) }
            .map { match -> match.value.readHtmlAttributes() }
            .firstOrNull { attributes ->
                attributes["rel"]
                    ?.split(Regex("""\s+"""))
                    ?.any { rel -> rel.equals("canonical", ignoreCase = true) } == true
            }
            ?.get("href")
            ?.decodeHtmlEntities()
            ?.displayTextOrNull()
    }

    private fun parseTitleMetadata(
        title: String?,
        platform: SocialPlatform,
    ): OpenGraphTitleMetadata {
        val cleanedTitle = title?.decodeHtmlEntities()?.displayTextOrNull()
            ?: return OpenGraphTitleMetadata()
        val platformName = Regex.escape(platform.label)

        if (platform == SocialPlatform.Threads) {
            Regex("""^(.+?)\s+\((@[^)]+)\)\s+on\s+Threads$""", RegexOption.IGNORE_CASE)
                .find(cleanedTitle)
                ?.let { match ->
                    return OpenGraphTitleMetadata(
                        authorName = match.groupValues[1].displayTextOrNull(),
                        authorHandle = match.groupValues[2].displayTextOrNull(),
                    )
                }
        }

        if (platform == SocialPlatform.LinkedIn) {
            Regex("""^(.+?)\s+\|\s+(.+?)$""", RegexOption.IGNORE_CASE)
                .find(cleanedTitle)
                ?.let { match ->
                    return OpenGraphTitleMetadata(
                        authorName = match.groupValues[2].displayTextOrNull(),
                        content = match.groupValues[1].displayTextOrNull(),
                    )
                }
        }

        Regex("""^(.+?)\s+on\s+$platformName:\s*"?(.*?)"?$""", RegexOption.IGNORE_CASE)
            .find(cleanedTitle)
            ?.let { match ->
                return OpenGraphTitleMetadata(
                    authorName = match.groupValues[1].displayTextOrNull(),
                    content = match.groupValues[2].displayTextOrNull(),
                )
            }

        Regex("""^(.+?)\s+on\s+$platformName$""", RegexOption.IGNORE_CASE)
            .find(cleanedTitle)
            ?.let { match ->
                return OpenGraphTitleMetadata(
                    authorName = match.groupValues[1].displayTextOrNull(),
                )
            }

        return OpenGraphTitleMetadata(content = cleanedTitle)
    }

    private fun String.readHtmlAttributes(): Map<String, String> {
        return attributePattern
            .findAll(this)
            .associate { match ->
                match.groupValues[1].lowercase() to match.groupValues[3]
            }
    }

    private fun extractRemoteUrls(html: String): List<String> {
        return html
            .replace("\\/", "/")
            .replace("\\u002F", "/")
            .replace("\\u0026", "&")
            .replace("\\u003D", "=")
            .decodeHtmlEntities()
            .let { normalizedHtml -> remoteUrlPattern.findAll(normalizedHtml) }
            .map { match -> match.value.normalizedRemoteAssetUrl() }
            .filter { url -> url.isNotBlank() }
            .distinct()
            .toList()
    }

    private fun extractLinkedInCreatorMetadata(html: String): LinkedInCreatorMetadata? {
        return html
            .extractJsonLdScripts()
            .flatMap { root -> root.findObjects { value -> value.optionalObject("creator") != null } }
            .mapNotNull { value -> value.optionalObject("creator")?.toLinkedInCreatorMetadata() }
            .firstOrNull { metadata ->
                metadata.authorName != null ||
                    metadata.profileUrl != null ||
                    metadata.authorAvatarUrl != null
            }
    }

    private fun JSONObject.toLinkedInCreatorMetadata(): LinkedInCreatorMetadata {
        val imageUrl =
            optionalObject("image")
                ?.optionalString("url")
                ?: optionalString("image")
                    ?.takeIf { value -> value.startsWith("http://") || value.startsWith("https://") }

        return LinkedInCreatorMetadata(
            authorName = optionalString("name")?.displayTextOrNull(),
            profileUrl = optionalString("url")?.displayTextOrNull(),
            authorAvatarUrl = imageUrl?.normalizedRemoteAssetUrl(),
        )
    }

    private fun findAuthorAvatarUrl(
        platform: SocialPlatform,
        imageUrls: List<String>,
        remoteUrls: List<String>,
    ): String? {
        val candidates = (remoteUrls + imageUrls).map { url -> url.normalizedRemoteAssetUrl() }

        return candidates.firstOrNull { url -> url.isPlatformProfileImageUrl(platform) }
    }

    private fun buildMedia(
        platform: SocialPlatform,
        imageUrls: List<String>,
        videoUrls: List<String>,
        remoteUrls: List<String>,
        authorAvatarUrl: String?,
    ): List<PostMedia> {
        val postImageVariants =
            (imageUrls + remoteUrls)
                .map { url -> url.normalizedRemoteAssetUrl() }
                .filter { url -> url.isPlatformImageAssetUrl(platform) }
                .filterNot { url -> url.sameRemoteAssetAs(authorAvatarUrl) }
                .filterNot { url -> url.isPlatformProfileImageUrl(platform) }
                .groupBy { url -> url.remoteAssetIdentity() }
                .values
                .map { variants ->
                    variants
                        .distinct()
                        .sortedWith(
                            compareByDescending<String> { url -> url.remoteAssetScore(platform) }
                                .thenByDescending { url -> url.length },
                        )
                }
                .sortedByDescending { variants -> variants.firstOrNull()?.remoteAssetScore(platform) ?: 0 }
                .take(4)
                .toList()
        val videos =
            videoUrls
                .map { url -> url.normalizedRemoteAssetUrl() }
                .filter { url -> url.isPlatformVideoAssetUrl(platform) }
                .distinctBy { url -> url.remoteAssetIdentity() }
                .take(4)
                .toList()

        if (videos.isNotEmpty()) {
            val video =
                PostMedia.Video(
                    variants = videos,
                    url = videos.firstOrNull { url -> ".mp4" in url.lowercase() } ?: videos.firstOrNull(),
                    posterUrl = postImageVariants.firstOrNull()?.firstOrNull(),
                )
            val remainingImages =
                postImageVariants
                    .drop(1)
                    .mapNotNull { variants ->
                        val primaryUrl = variants.firstOrNull() ?: return@mapNotNull null
                        PostMedia.Image(
                            url = primaryUrl,
                            variants = variants.drop(1),
                        )
                    }

            return (listOf(video) + remainingImages).take(4)
        }

        return postImageVariants.mapNotNull { variants ->
            val primaryUrl = variants.firstOrNull() ?: return@mapNotNull null
            PostMedia.Image(
                url = primaryUrl,
                variants = variants.drop(1),
            )
        }
    }

    private fun extractStructuredMedia(
        platform: SocialPlatform,
        html: String,
        sourceUrl: String,
        canonicalUrl: String?,
        authorAvatarUrl: String?,
    ): List<PostMedia> {
        if (platform != SocialPlatform.Threads) {
            return emptyList()
        }

        val postCode = sourceUrl.threadsPostCode() ?: canonicalUrl.threadsPostCode() ?: return emptyList()
        return html
            .extractJsonScripts()
            .flatMap { root -> root.findObjects { value -> value.optionalString("code") == postCode } }
            .map { post -> post.toThreadsStructuredMedia(authorAvatarUrl) }
            .filter { media -> media.isNotEmpty() }
            .maxByOrNull { media -> media.size }
            ?.take(4)
            .orEmpty()
    }

    private fun String.extractJsonScripts(): List<JSONObject> =
        jsonScriptPattern
            .findAll(this)
            .mapNotNull { match ->
                runCatching { JSONObject(match.groupValues[2]) }.getOrNull()
            }
            .toList()

    private fun String.extractJsonLdScripts(): List<JSONObject> =
        jsonLdScriptPattern
            .findAll(this)
            .mapNotNull { match ->
                runCatching { JSONObject(match.groupValues[2].decodeHtmlEntities()) }.getOrNull()
            }
            .toList()

    private fun JSONObject.findObjects(predicate: (JSONObject) -> Boolean): List<JSONObject> {
        val matches = mutableListOf<JSONObject>()
        visitJsonObjects { value ->
            if (predicate(value)) {
                matches += value
            }
        }
        return matches
    }

    private fun JSONObject.visitJsonObjects(visitor: (JSONObject) -> Unit) {
        visitor(this)
        val keys = keys()
        while (keys.hasNext()) {
            when (val child = get(keys.next())) {
                is JSONObject -> child.visitJsonObjects(visitor)
                is JSONArray -> child.visitJsonObjects(visitor)
            }
        }
    }

    private fun JSONArray.visitJsonObjects(visitor: (JSONObject) -> Unit) {
        for (index in 0 until length()) {
            when (val child = get(index)) {
                is JSONObject -> child.visitJsonObjects(visitor)
                is JSONArray -> child.visitJsonObjects(visitor)
            }
        }
    }

    private fun JSONObject.toThreadsStructuredMedia(authorAvatarUrl: String?): List<PostMedia> {
        val mediaObjects =
            optionalArray("carousel_media")
                ?.jsonObjects()
                ?.takeIf { media -> media.isNotEmpty() }
                ?: listOf(this)

        return mediaObjects
            .mapNotNull { media -> media.toThreadsStructuredMediaItem(authorAvatarUrl) }
            .take(4)
    }

    private fun JSONObject.toThreadsStructuredMediaItem(authorAvatarUrl: String?): PostMedia? {
        val alt = optionalString("accessibility_caption")
        val imageVariants =
            optionalObject("image_versions2")
                ?.optionalArray("candidates")
                .threadImageCandidateUrls(authorAvatarUrl)
        val videoVariants =
            optionalArray("video_versions")
                .threadVideoCandidateUrls()

        return if (videoVariants.isNotEmpty()) {
            PostMedia.Video(
                variants = videoVariants,
                url = videoVariants.firstOrNull { url -> ".mp4" in url.lowercase() } ?: videoVariants.firstOrNull(),
                posterUrl = imageVariants.firstOrNull(),
                alt = alt,
            )
        } else {
            val primaryUrl = imageVariants.firstOrNull() ?: return null
            PostMedia.Image(
                url = primaryUrl,
                variants = imageVariants.drop(1),
                alt = alt,
            )
        }
    }

    private fun JSONArray?.threadImageCandidateUrls(authorAvatarUrl: String?): List<String> {
        if (this == null) {
            return emptyList()
        }

        return jsonObjects()
            .mapNotNull { candidate -> candidate.optionalString("url") }
            .map { url -> url.normalizedRemoteAssetUrl() }
            .filter { url -> url.isPlatformImageAssetUrl(SocialPlatform.Threads) }
            .filterNot { url -> url.sameRemoteAssetAs(authorAvatarUrl) }
            .filterNot { url -> url.isPlatformProfileImageUrl(SocialPlatform.Threads) }
            .distinct()
    }

    private fun JSONArray?.threadVideoCandidateUrls(): List<String> {
        if (this == null) {
            return emptyList()
        }

        return jsonObjects()
            .mapNotNull { candidate -> candidate.optionalString("url") }
            .map { url -> url.normalizedRemoteAssetUrl() }
            .filter { url -> url.isPlatformVideoAssetUrl(SocialPlatform.Threads) }
            .distinct()
    }

    private fun JSONArray.jsonObjects(): List<JSONObject> =
        List(length()) { index -> runCatching { getJSONObject(index) }.getOrNull() }.filterNotNull()

    private fun JSONObject.optionalObject(name: String): JSONObject? =
        if (has(name) && !isNull(name)) runCatching { getJSONObject(name) }.getOrNull() else null

    private fun JSONObject.optionalArray(name: String): JSONArray? =
        if (has(name) && !isNull(name)) runCatching { getJSONArray(name) }.getOrNull() else null

    private fun JSONObject.optionalString(name: String): String? =
        if (has(name) && !isNull(name)) getString(name).takeIf { value -> value.isNotBlank() } else null

    private data class OpenGraphTitleMetadata(
        val authorName: String? = null,
        val authorHandle: String? = null,
        val content: String? = null,
    )

    private data class LinkedInCreatorMetadata(
        val authorName: String?,
        val profileUrl: String?,
        val authorAvatarUrl: String?,
    )
}

class XPostEnrichmentAdapter {
    suspend fun fetch(
        sourceUrl: String,
        visibleText: String? = null,
    ): XPostEnrichment? =
        withContext(Dispatchers.IO) {
            runCatching {
                fetchPost(
                    sourceUrl = sourceUrl,
                    includeRelatedPost = true,
                    visibleText = visibleText,
                )
            }.getOrNull()
        }

    private fun fetchPost(
        sourceUrl: String,
        includeRelatedPost: Boolean,
        visibleText: String? = null,
    ): XPostEnrichment? {
        val oEmbed = fetchOEmbed(sourceUrl) ?: return null
        val canonicalUrl = oEmbed.optString("url").displayTextOrNull()
        val authorUrl = oEmbed.optString("author_url").displayTextOrNull()
        val authorHandle = authorUrl?.toAuthorHandle() ?: canonicalUrl?.toAuthorHandle()
        val oEmbedContent = XPostOEmbedParser.extractTweetText(oEmbed.optString("html"))
        val html = canonicalUrl?.let(::fetchText)
        val pageContent =
            html?.let { pageHtml ->
                XPostPageParser.extractTweetText(
                    html = pageHtml,
                    canonicalUrl = canonicalUrl,
                    fallbackText = oEmbedContent ?: visibleText?.takeUnless { it.isLikelySourceOnlyText() },
                )
            }
        val content = selectTweetText(oEmbedContent = oEmbedContent, pageContent = pageContent)
        val authorAvatarUrl = html?.let { pageHtml ->
            XPostPageParser.extractAuthorAvatarUrl(pageHtml, authorHandle = authorHandle)
        }
        val pageMedia =
            html?.let { pageHtml ->
                XPostPageParser.extractMedia(
                    html = pageHtml,
                    canonicalUrl = canonicalUrl,
                )
            }.orEmpty()
        val relatedPost =
            if (includeRelatedPost && html != null) {
                XPostPageParser.extractRelatedStatusUrl(html, canonicalUrl = canonicalUrl.orEmpty())
                    ?.let { relatedUrl ->
                        fetchPost(
                            sourceUrl = relatedUrl,
                            includeRelatedPost = false,
                        )
                    }
                    ?.toRelatedPost()
            } else {
                null
            }
        val media = pageMedia.withoutMediaAlreadyOwnedBy(relatedPost?.media.orEmpty())

        return XPostEnrichment(
            canonicalUrl = canonicalUrl,
            authorName = oEmbed.optString("author_name").displayTextOrNull(),
            authorHandle = authorHandle,
            authorAvatarUrl = authorAvatarUrl,
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
    val authorAvatarUrl: String?,
    val content: String?,
    val media: List<PostMedia>,
    val relatedPost: RelatedPost?,
) {
    fun toRelatedPost(): RelatedPost? {
        val relatedContent = content?.takeIf { it.isNotBlank() } ?: return null
        return RelatedPost(
            authorHandle = authorHandle.displayTextOrNull(),
            authorName = authorName.displayTextOrNull(),
            authorAvatarUrl = authorAvatarUrl,
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

        return paragraph.cleanTweetText(markCollapsedText = paragraph.hasCollapsedTweetMarker())
    }
}

internal object XPostPageParser {
    private val imageUrlPattern =
        Regex("""https://pbs\.twimg\.com/(?:media|card_img)/[^"'<\s]+""")
    private val profileImageUrlPattern =
        Regex("""https://pbs\.twimg\.com/profile_images/[^"'<\s]+""")
    private val videoPosterPattern =
        Regex("""https://pbs\.twimg\.com/(?:ext_tw_video_thumb|amplify_video_thumb|tweet_video_thumb)/[^"'<\s]+""")
    private val videoVariantPattern =
        Regex("""https://video\.twimg\.com/[^"'<\s]+?(?:\.mp4|\.m3u8)(?:\?[^"'<\s]+)?""")
    private val statusUrlPattern =
        Regex("""(?:https://(?:x|twitter)\.com)?/([A-Za-z0-9_]{1,15})/status/(\d+)""")
    private val tweetTextPattern =
        Regex("(?:\\\\?\")?(full_text|fullText|text)(?:\\\\?\")?\\s*:\\s*\\\\?\"((?:\\\\.|[^\"\\\\])*)\\\\?\"")

    fun extractTweetText(
        html: String,
        canonicalUrl: String,
        fallbackText: String? = null,
    ): String? {
        val statusId = canonicalUrl.statusId() ?: return null
        val normalized = html.normalizedHtml()
        val fallbackComparison = fallbackText
            ?.toTweetComparisonPrefix()
            ?.takeIf { it.isNotBlank() }
        val windows = extractTargetStatusWindows(normalized, statusId)

        return windows
            .flatMap { window -> extractTweetTextCandidates(window, fallbackComparison) }
            .distinctBy { candidate -> candidate.content.compactForTweetComparison() }
            .sortedWith(
                compareByDescending<XTweetTextCandidate> { candidate ->
                    if (fallbackComparison.isNullOrBlank()) candidate.score else candidate.content.length
                }
                    .thenByDescending { candidate -> candidate.score }
                    .thenByDescending { candidate -> candidate.content.length },
            )
            .firstOrNull()
            ?.content
    }

    fun extractMedia(
        html: String,
        canonicalUrl: String? = null,
    ): List<PostMedia> {
        val normalized = html.normalizedHtml()
        val targetStatusId = canonicalUrl?.statusId()
        val belongsToTargetStatus: (IntRange) -> Boolean =
            if (targetStatusId == null) {
                { true }
            } else {
                { range -> normalized.mediaRangeBelongsToStatus(range, targetStatusId) }
            }
        val imageUrls =
            imageUrlPattern
                .findAll(normalized)
                .filter { match -> belongsToTargetStatus(match.range) }
                .map { match -> match.value.normalizedMediaUrl() }
                .filterNot { imageUrl -> imageUrl.contains("profile_images") }
                .distinctBy { imageUrl -> imageUrl.mediaIdentity() }
                .take(4)
                .toList()
        val videos = extractVideos(normalized, imageUrls, belongsToTargetStatus)
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

    fun extractAuthorAvatarUrl(
        html: String,
        authorHandle: String?,
    ): String? {
        val normalized = html.normalizedHtml()
        val profileImageMatches = profileImageUrlPattern.findAll(normalized).toList()
        if (profileImageMatches.isEmpty()) {
            return null
        }

        val handle = authorHandle
            ?.removePrefix("@")
            ?.takeIf { it.isNotBlank() }
        val selected =
            handle?.let { author ->
                val handleRanges =
                    Regex(Regex.escape(author), RegexOption.IGNORE_CASE)
                        .findAll(normalized)
                        .map { match -> match.range }
                        .toList()
                val afterHandleMatch =
                    profileImageMatches
                        .mapNotNull { match ->
                            handleRanges
                                .filter { range -> match.range.first >= range.last }
                                .minOfOrNull { range -> match.range.first - range.last }
                                ?.takeIf { distance -> distance <= 1_200 }
                                ?.let { distance -> match to distance }
                        }
                        .minByOrNull { (_, distance) -> distance }
                        ?.first

                afterHandleMatch
                    ?: profileImageMatches
                        .mapNotNull { match ->
                            handleRanges
                                .minOfOrNull { range -> match.range.distanceTo(range) }
                                ?.takeIf { distance -> distance <= 1_200 }
                                ?.let { distance -> match to distance }
                        }
                        .minByOrNull { (_, distance) -> distance }
                        ?.first
            } ?: profileImageMatches.firstOrNull()

        return selected?.value?.normalizedProfileImageUrl()
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
        return Regex(
            pattern = """quoted_tweet_results.{0,400}?TweetResults:(\d+)""",
            option = RegexOption.DOT_MATCHES_ALL,
        )
            .findAll(normalizedHtml)
            .map { match -> match.groupValues[1] }
            .toSet()
    }

    private fun extractTargetStatusWindows(
        normalizedHtml: String,
        statusId: String,
    ): List<XHtmlWindow> {
        val anchorRanges =
            normalizedHtml
                .extractStatusAnchors()
                .filter { anchor -> anchor.statusId == statusId }
                .map { anchor -> anchor.range }
                .sortedBy { range -> range.first }
                .take(12)

        return anchorRanges
            .map { range ->
                val start = (range.first - 30_000).coerceAtLeast(0)
                val end = (range.last + 50_000).coerceAtMost(normalizedHtml.length)

                start to end
            }
            .distinct()
            .map { (start, end) ->
                XHtmlWindow(
                    text = normalizedHtml.substring(start, end),
                )
            }
    }

    private fun extractTweetTextCandidates(
        window: XHtmlWindow,
        fallbackComparison: String?,
    ): List<XTweetTextCandidate> {
        return tweetTextPattern
            .findAll(window.text)
            .mapNotNull { match ->
                val content =
                    match
                        .groupValues[2]
                        .decodeJsonText()
                        .cleanTweetText()
                        ?: return@mapNotNull null
                val comparison = content.compactForTweetComparison()

                if (!content.isLikelyTweetText()) {
                    return@mapNotNull null
                }

                if (fallbackComparison != null && !comparison.isCompatibleWithFallback(fallbackComparison)) {
                    return@mapNotNull null
                }

                val context = window.text.contextAround(match.range)

                XTweetTextCandidate(
                    content = content,
                    score =
                        scoreTweetTextCandidate(
                            key = match.groupValues[1],
                            context = context,
                            comparison = comparison,
                            fallbackComparison = fallbackComparison,
                        ),
                )
            }
            .toList()
    }

    private fun scoreTweetTextCandidate(
        key: String,
        context: String,
        comparison: String,
        fallbackComparison: String?,
    ): Int {
        var score =
            when (key) {
                "full_text", "fullText" -> 120
                else -> 20
            }
        val normalizedContext = context.lowercase()

        if ("note_tweet" in normalizedContext || "notetweet" in normalizedContext) {
            score += 180
        }

        if ("legacy" in normalizedContext) {
            score += 80
        }

        if ("tweet_results" in normalizedContext || "tweetresults" in normalizedContext) {
            score += 40
        }

        if (fallbackComparison != null) {
            val commonPrefixLength = comparison.commonPrefixLength(fallbackComparison)

            score += (commonPrefixLength * 4).coerceAtMost(220)

            if (comparison.startsWith(fallbackComparison)) {
                score += 260
            }

            if (comparison.length > fallbackComparison.length) {
                score += (comparison.length - fallbackComparison.length).coerceAtMost(120)
            }
        }

        return score + (comparison.length / 4).coerceAtMost(100)
    }

    private fun extractVideos(
        normalizedHtml: String,
        fallbackPosters: List<String>,
        belongsToTargetStatus: (IntRange) -> Boolean = { true },
    ): List<PostMedia.Video> {
        val variantMatches =
            videoVariantPattern
                .findAll(normalizedHtml)
                .filter { match -> belongsToTargetStatus(match.range) }
                .toList()
        val variants =
            variantMatches
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
                .filter { match -> belongsToTargetStatus(match.range) }
                .map { match -> match.value.normalizedMediaUrl() }
                .distinct()
                .take(2)
                .toList()
        val selectedPosters =
            posters.ifEmpty {
                fallbackPosters
                    .takeIf { it.isNotEmpty() && normalizedHtml.hasVideoEntitySignalAround(variantMatches) }
                    ?.take(1)
                    .orEmpty()
            }

        if (variants.isEmpty()) {
            return emptyList()
        }

        if (selectedPosters.isEmpty() && fallbackPosters.isNotEmpty()) {
            return emptyList()
        }

        return listOf(
            PostMedia.Video(
                variants = variants,
                url = variants.firstOrNull { url -> ".mp4" in url } ?: variants.firstOrNull(),
                posterUrl = selectedPosters.firstOrNull(),
            ),
        )
    }

    private fun String.hasVideoEntitySignalAround(matches: List<MatchResult>): Boolean {
        return matches.any { match ->
            val start = (match.range.first - 1_200).coerceAtLeast(0)
            val end = (match.range.last + 1_200).coerceAtMost(lastIndex)
            val window = substring(start, end + 1).lowercase()

            "video_info" in window ||
                "videoinfo" in window ||
                "video_variant" in window ||
                "animated_gif" in window ||
                "<video" in window ||
                "\"type\":\"video\"" in window ||
                "type:\"video\"" in window ||
                "type:video" in window
        }
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

    private data class XHtmlWindow(
        val text: String,
    )

    private data class XTweetTextCandidate(
        val content: String,
        val score: Int,
    )
}

private data class XStatusAnchor(
    val statusId: String,
    val range: IntRange,
)

internal fun selectTweetText(
    oEmbedContent: String?,
    pageContent: String?,
): String? {
    val oEmbedText = oEmbedContent?.takeIf { it.isNotBlank() }
    val pageText = pageContent?.takeIf { it.isNotBlank() }

    if (oEmbedText == null) {
        return pageText
    }
    if (pageText == null) {
        return oEmbedText
    }

    val oEmbedComparison = oEmbedText.toTweetTextSelectionComparison()
    val pageComparison = pageText.toTweetTextSelectionComparison()
    val pageCompletesOEmbed =
        oEmbedComparison.isNotBlank() &&
            pageComparison.length > oEmbedComparison.length &&
            pageComparison.isCompatibleWithFallback(oEmbedComparison)

    return if (pageCompletesOEmbed && oEmbedText.hasBracketedTruncationMarker()) {
        pageText
    } else if (pageCompletesOEmbed && pageText.length > oEmbedText.length) {
        pageText
    } else {
        oEmbedText
    }
}

private fun String.toAuthorHandle(): String? {
    val path = runCatching { URI(this).path }.getOrNull() ?: return null
    val handle = path.split("/").firstOrNull { it.isNotBlank() } ?: return null
    return "@$handle"
}

private fun String?.toPlatformAuthorHandle(platform: SocialPlatform): String? {
    if (isNullOrBlank()) {
        return null
    }

    val uri = runCatching { URI(this) }.getOrNull() ?: return null
    val segments = uri.path.split("/").filter { it.isNotBlank() }

    return when (platform) {
        SocialPlatform.Threads ->
            segments
                .firstOrNull()
                ?.removePrefix("@")
                ?.displayTextOrNull()
                ?.let { handle -> "@$handle" }

        SocialPlatform.LinkedIn -> {
            if (segments.firstOrNull()?.equals("posts", ignoreCase = true) == true) {
                return segments
                    .getOrNull(1)
                    ?.displayTextOrNull()
                    ?.substringBefore("_")
                    ?.let { handle -> "in/$handle" }
            }

            val handleRootIndex =
                segments.indexOfFirst { segment ->
                    segment.equals("in", ignoreCase = true) ||
                        segment.equals("company", ignoreCase = true) ||
                        segment.equals("school", ignoreCase = true)
                }
            if (handleRootIndex == -1) {
                return null
            }

            segments
                .getOrNull(handleRootIndex + 1)
                ?.displayTextOrNull()
                ?.let { handle -> "${segments[handleRootIndex].lowercase()}/$handle" }
        }

        SocialPlatform.Facebook ->
            segments
                .firstOrNull { segment -> !segment.isReservedFacebookPathSegment() }
                ?.displayTextOrNull()
                ?.let { handle -> "@$handle" }

        else -> null
    }
}

private fun String?.threadsPostCode(): String? {
    if (isNullOrBlank()) {
        return null
    }

    val path = runCatching { URI(this).path }.getOrNull() ?: return null
    val segments = path.split("/").filter { segment -> segment.isNotBlank() }
    val postIndex = segments.indexOfFirst { segment -> segment.equals("post", ignoreCase = true) }

    return segments.getOrNull(postIndex + 1)?.displayTextOrNull()
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

private fun String.normalizedProfileImageUrl(): String {
    return normalizedMediaUrl()
        .replace(
            Regex("""_normal(\.(?:jpg|jpeg|png|webp))""", RegexOption.IGNORE_CASE),
            "_400x400$1",
        )
}

private fun String.mediaIdentity(): String {
    val path = runCatching { URI(this).path }.getOrNull().orEmpty()
    val fileName = path.substringAfterLast("/")
    return fileName
        .substringBefore("?")
        .substringBefore(":")
        .substringBeforeLast(".", fileName)
}

private fun IntRange.distanceTo(other: IntRange): Int {
    return when {
        last < other.first -> other.first - last
        other.last < first -> first - other.last
        else -> 0
    }
}

private fun String.mediaRangeBelongsToStatus(
    range: IntRange,
    statusId: String,
): Boolean {
    val anchors = extractStatusAnchors()
    val targetAnchors = anchors.filter { anchor -> anchor.statusId == statusId }
    if (targetAnchors.isEmpty()) {
        return true
    }

    val precedingAnchor =
        anchors
            .filter { anchor -> anchor.range.first <= range.first }
            .maxByOrNull { anchor -> anchor.range.first }
    if (precedingAnchor != null && range.distanceTo(precedingAnchor.range) <= MaxMediaStatusAnchorDistance) {
        return precedingAnchor.statusId == statusId
    }

    val closestTargetDistance =
        targetAnchors.minOfOrNull { anchor -> range.distanceTo(anchor.range) }
            ?: return false
    val closestOtherDistance =
        anchors
            .filterNot { anchor -> anchor.statusId == statusId }
            .minOfOrNull { anchor -> range.distanceTo(anchor.range) }

    if (closestTargetDistance > MaxMediaStatusAnchorDistance) {
        return false
    }

    return closestOtherDistance == null ||
        closestTargetDistance <= closestOtherDistance ||
        closestOtherDistance > NearbyStatusAnchorDistance
}

private fun String.extractStatusAnchors(): List<XStatusAnchor> {
    val idAnchors =
        Regex("""(?:\\?")?(?:rest_id|id_str|id)(?:\\?")?\s*:\s*\\?"?(\d+)""")
            .findAll(this)
            .map { match ->
                XStatusAnchor(
                    statusId = match.groupValues[1],
                    range = match.range,
                )
            }
    val tweetResultAnchors =
        Regex("""TweetResults:(\d+)\b""")
            .findAll(this)
            .map { match ->
                XStatusAnchor(
                    statusId = match.groupValues[1],
                    range = match.range,
                )
            }
    val statusUrlAnchors =
        Regex("""(?:https://(?:x|twitter)\.com)?/[A-Za-z0-9_]{1,15}/status/(\d+)""")
            .findAll(this)
            .map { match ->
                XStatusAnchor(
                    statusId = match.groupValues[1],
                    range = match.range,
                )
            }

    return (idAnchors + tweetResultAnchors + statusUrlAnchors)
        .distinctBy { anchor -> anchor.statusId to anchor.range.first }
        .sortedBy { anchor -> anchor.range.first }
        .toList()
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

private fun String.isReservedFacebookPathSegment(): Boolean {
    return lowercase() in
        setOf(
            "permalink.php",
            "story.php",
            "photo.php",
            "watch",
            "groups",
            "share",
            "reel",
            "reels",
            "events",
            "pages",
            "profile.php",
        )
}

private fun String.isLikelyProfileImageUrl(): Boolean {
    val value = lowercase()

    return "profile_pic" in value ||
        "profile_images" in value ||
        "avatar" in value
}

private fun String.normalizedRemoteAssetUrl(): String {
    return trim()
        .trimEnd('\\', '"', '\'', ',', ';', ')', ']', '}')
        .replace("\\/", "/")
        .replace("\\u002F", "/")
        .replace("\\u0026", "&")
        .replace("\\u003D", "=")
        .decodeHtmlEntities()
}

private fun String.remoteAssetIdentity(): String {
    val normalized = normalizedRemoteAssetUrl()
    val path = runCatching { URI(normalized).path }.getOrNull()
    val fileName = path?.substringAfterLast("/")?.takeIf { it.isNotBlank() }

    return fileName ?: normalized.substringBefore("?")
}

private fun String.remoteAssetScore(platform: SocialPlatform): Int {
    val value = lowercase()
    val dimensions =
        Regex("""[sp](\d+)x(\d+)""")
            .findAll(value)
            .mapNotNull { match ->
                val width = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val height = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                width * height
            }
            .maxOrNull()
            ?: 0
    val signatureScore =
        when (platform) {
            SocialPlatform.Threads ->
                (if ("oh=" in value) 1_000_000_000 else 0) +
                    (if ("oe=" in value) 100_000_000 else 0) +
                    (if ("ccb=" in value) 10_000_000 else 0)

            else -> 0
        }

    return signatureScore + dimensions.coerceAtMost(9_000_000)
}

private fun String.sameRemoteAssetAs(other: String?): Boolean {
    return !other.isNullOrBlank() && remoteAssetIdentity() == other.remoteAssetIdentity()
}

private fun String.isPlatformImageAssetUrl(platform: SocialPlatform): Boolean {
    val value = lowercase()

    if (!value.isPlatformAssetHost(platform) || value.isStaticPlatformAssetUrl(platform)) {
        return false
    }

    return when (platform) {
        SocialPlatform.Threads ->
            Regex("""\.(?:jpg|jpeg|png|webp)(?:[?#]|$)""").containsMatchIn(value) ||
                "/t51." in value

        SocialPlatform.LinkedIn ->
            Regex("""\.(?:jpg|jpeg|png|webp)(?:[?#]|$)""").containsMatchIn(value) ||
                "/dms/image/" in value

        SocialPlatform.Facebook ->
            Regex("""\.(?:jpg|jpeg|png|webp)(?:[?#]|$)""").containsMatchIn(value) ||
                "/lookaside/" in value ||
                "/v/t" in value

        else -> false
    }
}

private fun String.isPlatformVideoAssetUrl(platform: SocialPlatform): Boolean {
    val value = lowercase()

    return value.isPlatformAssetHost(platform) &&
        (".mp4" in value || ".m3u8" in value)
}

private fun String.isPlatformProfileImageUrl(platform: SocialPlatform): Boolean {
    val value = lowercase()

    return when (platform) {
        SocialPlatform.Threads ->
            "/t51.82787-19/" in value ||
                "/t51.2885-19/" in value ||
                "profile_pic" in value ||
                "profilepic" in value ||
                value.isLikelyProfileImageUrl()

        SocialPlatform.LinkedIn ->
            "profile-displayphoto" in value ||
                "profile-framedphoto" in value ||
                "member-photo" in value ||
                value.isLikelyProfileImageUrl()

        SocialPlatform.Facebook ->
            "profile_pic" in value ||
                "profilepic" in value ||
                "/t39.30808-1/" in value ||
                "/t1.6435-1/" in value ||
                value.isLikelyProfileImageUrl()

        else -> false
    }
}

private fun String.isPlatformAssetHost(platform: SocialPlatform): Boolean {
    val value = lowercase()

    return when (platform) {
        SocialPlatform.Threads ->
            "cdninstagram.com" in value ||
                "fbcdn.net" in value ||
                "scontent" in value

        SocialPlatform.LinkedIn ->
            "media.licdn.com" in value ||
                "media-exp" in value ||
                "licdn.com/dms/image/" in value

        SocialPlatform.Facebook ->
            "fbcdn.net" in value ||
                "fbsbx.com" in value ||
                "scontent" in value ||
                "lookaside.facebook.com" in value ||
                "lookaside.fbsbx.com" in value

        else -> false
    }
}

private fun String.isStaticPlatformAssetUrl(platform: SocialPlatform): Boolean {
    val value = lowercase()

    return when (platform) {
        SocialPlatform.Threads ->
            "static.cdninstagram.com/rsrc.php" in value

        SocialPlatform.LinkedIn ->
            "static.licdn.com" in value ||
                "/sc/h/" in value

        SocialPlatform.Facebook ->
            "static.xx.fbcdn.net/rsrc.php" in value ||
                "/rsrc.php/" in value

        else -> false
    }
}

private const val MaxMediaStatusAnchorDistance = 80_000
private const val NearbyStatusAnchorDistance = 12_000

private fun String.videoVariantScore(): Int {
    val resolution =
        Regex("""/(\d+)x(\d+)/""")
            .find(this)
            ?.let { match -> match.groupValues[1].toInt() * match.groupValues[2].toInt() }
            ?: 0
    val formatScore = if (".mp4" in this) 1_000_000_000 else 0

    return formatScore + resolution
}

private fun String.decodeJsonText(): String {
    var decoded = this

    repeat(2) {
        val next =
            runCatching {
                JSONObject("""{"value":"$decoded"}""").getString("value")
            }.getOrNull() ?: return decoded

        if (next == decoded) {
            return decoded
        }

        decoded = next
    }

    return decoded
}

private fun String.cleanTweetText(markCollapsedText: Boolean = false): String? {
    val cleaned =
        replace("\\r\\n", "\n")
        .replace("\\n", "\n")
        .replace("\\r", "\n")
        .replace("\\t", " ")
        .replace(Regex("""(?i)<br\s*/?>"""), "\n")
        .replace(Regex("""<[^>]+>"""), "")
        .decodeHtmlEntities()
        .replace(Regex("""(?m)\s*(?:https?://)?(?:pic\.twitter\.com|t\.co)/\S+\s*$"""), "")
        .lines()
        .joinToString("\n") { line -> line.trim() }
        .replace(Regex("""\(\s+@"""), "(@")
        .trim()

    return (if (markCollapsedText) cleaned.withBracketedTruncationMarker() else cleaned)
        .takeIf { it.isNotBlank() }
}

private fun String.hasCollapsedTweetMarker(): Boolean {
    return Regex(
        pattern = """(?is)(?:…|\.\.\.)\s*<a\b[^>]*>\s*(?:https?://)?(?:pic\.twitter\.com|t\.co)/""",
    ).containsMatchIn(this) ||
        Regex("""(?i)\b(?:voir plus|show more|afficher plus)\b""").containsMatchIn(this)
}

private fun String.withBracketedTruncationMarker(): String {
    return replace(
        Regex("""(?i)\s*(?:…|\.\.\.)?\s*(?:voir plus|show more|afficher plus)\s*$"""),
        " [...]",
    ).replace(
        Regex("""\s*(?:…|\.\.\.)\s*$"""),
        " [...]",
    )
}

private fun String.isLikelyTweetText(): Boolean {
    return isNotBlank() &&
        length <= 4_000 &&
        !startsWith("http://") &&
        !startsWith("https://") &&
        !equals("X", ignoreCase = true)
}

private fun String.isLikelySourceOnlyText(): Boolean {
    val trimmed = trim()

    return trimmed.startsWith("http://") || trimmed.startsWith("https://")
}

private fun String?.displayTextOrNull(): String? = this?.trim()?.takeIf { value -> value.isNotEmpty() }

private fun String.toTweetComparisonPrefix(): String {
    return cleanTweetText()
        ?.replace(Regex("""(?i)\s*(?:…|\.\.\.)?\s*(?:show more|voir plus|afficher plus)\s*$"""), "")
        ?.replace(Regex("""\s*(?:…|\.\.\.)\s*$"""), "")
        ?.compactForTweetComparison()
        .orEmpty()
}

private fun String.toTweetTextSelectionComparison(): String {
    return replace(Regex("""\s*\[\.\.\.\]\s*$"""), "")
        .replace(Regex("""(?i)\s*(?:…|\.\.\.)?\s*(?:show more|voir plus|afficher plus)\s*$"""), "")
        .replace(Regex("""\s*(?:…|\.\.\.)\s*$"""), "")
        .compactForTweetComparison()
}

private fun String.hasBracketedTruncationMarker(): Boolean {
    return Regex("""\s*\[\.\.\.\]\s*$""").containsMatchIn(this)
}

private fun String.compactForTweetComparison(): String {
    return replace(Regex("""\s+"""), " ")
        .trim()
}

private fun String.isCompatibleWithFallback(fallbackComparison: String): Boolean {
    if (fallbackComparison.isBlank()) {
        return true
    }

    if (startsWith(fallbackComparison) || fallbackComparison.startsWith(this)) {
        return true
    }

    val requiredPrefix =
        ((fallbackComparison.length * 0.7).toInt())
            .coerceAtLeast(16)
            .coerceAtMost(48)

    return commonPrefixLength(fallbackComparison) >= requiredPrefix
}

private fun String.commonPrefixLength(other: String): Int {
    return zip(other).takeWhile { (left, right) -> left == right }.size
}

private fun String.contextAround(
    range: IntRange,
    before: Int = 1_200,
    after: Int = 600,
): String {
    val start = (range.first - before).coerceAtLeast(0)
    val end = (range.last + after).coerceAtMost(lastIndex)

    return substring(start, end + 1)
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
        is PostMedia.Image -> (listOf(url) + variants).map { value -> value.mediaIdentity() }
        is PostMedia.Video -> (listOfNotNull(posterUrl, url) + variants)
            .map { value -> value.mediaIdentity() }
    }
}

private fun String.decodeHtmlEntities(): String {
    return replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace(Regex("""&#x([0-9a-fA-F]+);""")) { match ->
            match.groupValues[1].toIntOrNull(16)?.toCodePointString() ?: match.value
        }
        .replace(Regex("""&#(\d+);""")) { match ->
            match.groupValues[1].toIntOrNull()?.toCodePointString() ?: match.value
        }
}

private fun Int.toCodePointString(): String {
    return runCatching { String(Character.toChars(this)) }.getOrElse { "" }
}

private fun Set<IncomingShareMissingField>.removeIfPresent(
    field: IncomingShareMissingField,
    value: String?,
): Set<IncomingShareMissingField> {
    return if (value.isNullOrBlank()) this else this - field
}
