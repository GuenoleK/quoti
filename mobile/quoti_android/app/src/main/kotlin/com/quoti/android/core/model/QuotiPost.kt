package com.quoti.android.core.model

enum class SocialPlatform(
    val wireName: String,
    val label: String,
) {
    X("x", "X"),
    Threads("threads", "Threads"),
    Bluesky("bluesky", "Bluesky"),
    LinkedIn("linkedin", "LinkedIn");

    companion object {
        fun fromJson(value: String): SocialPlatform {
            return entries.firstOrNull { it.wireName == value }
                ?: error("Unsupported social platform: $value")
        }
    }
}

enum class CardTone {
    Light,
    Dark,
}

enum class CardContentMode {
    TextOnly,
    WithMedia,
}

data class QuotiPost(
    val id: String,
    val platform: SocialPlatform,
    val authorName: String,
    val authorHandle: String,
    val content: String,
    val relatedPost: RelatedPost? = null,
    val publishedAt: String? = null,
    val sourceUrl: String? = null,
    val media: List<PostMedia> = emptyList(),
    val capturedAt: String,
)

data class RelatedPost(
    val content: String,
    val media: List<PostMedia> = emptyList(),
    val authorHandle: String? = null,
    val authorName: String? = null,
    val sourceUrl: String? = null,
)

sealed interface PostMedia {
    val alt: String?

    data class Image(
        val url: String,
        override val alt: String? = null,
    ) : PostMedia

    data class Video(
        val variants: List<String>,
        val url: String? = null,
        val posterUrl: String? = null,
        val duration: Double? = null,
        override val alt: String? = null,
    ) : PostMedia
}

val QuotiPost.hasMedia: Boolean
    get() = media.isNotEmpty() || relatedPost?.media?.isNotEmpty() == true
