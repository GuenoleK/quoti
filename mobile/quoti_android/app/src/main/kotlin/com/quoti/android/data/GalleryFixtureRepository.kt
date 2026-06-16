package com.quoti.android.data

import android.content.res.AssetManager
import com.quoti.android.core.model.PostMedia
import com.quoti.android.core.model.QuotiPost
import com.quoti.android.core.model.RelatedPost
import com.quoti.android.core.model.SocialPlatform
import org.json.JSONArray
import org.json.JSONObject

class GalleryFixtureRepository(
    private val assets: AssetManager,
) {
    fun loadPosts(): List<QuotiPost> {
        return fixturePaths.map { path ->
            assets.open(path).bufferedReader().use { reader ->
                JSONObject(reader.readText()).toQuotiPost()
            }
        }
    }

    private companion object {
        val fixturePaths =
            listOf(
                "fixtures/posts/short-text.json",
                "fixtures/posts/long-text.json",
                "fixtures/posts/reply-context.json",
                "fixtures/posts/image-post.json",
                "fixtures/posts/video-poster.json",
                "fixtures/posts/missing-author-data.json",
            )
    }
}

private fun JSONObject.toQuotiPost(): QuotiPost {
    return QuotiPost(
        id = getString("id"),
        platform = SocialPlatform.fromJson(getString("platform")),
        authorName = getString("authorName"),
        authorHandle = getString("authorHandle"),
        content = getString("content"),
        relatedPost = optionalObject("relatedPost")?.toRelatedPost(),
        publishedAt = optionalString("publishedAt"),
        sourceUrl = optionalString("sourceUrl"),
        media = optionalArray("media").toPostMediaList(),
        capturedAt = getString("capturedAt"),
    )
}

private fun JSONObject.toRelatedPost(): RelatedPost {
    return RelatedPost(
        authorName = optionalString("authorName"),
        authorHandle = optionalString("authorHandle"),
        content = getString("content"),
        sourceUrl = optionalString("sourceUrl"),
        media = optionalArray("media").toPostMediaList(),
    )
}

private fun JSONArray?.toPostMediaList(): List<PostMedia> {
    if (this == null) {
        return emptyList()
    }

    return List(length()) { index ->
        getJSONObject(index).toPostMedia()
    }
}

private fun JSONObject.toPostMedia(): PostMedia {
    return when (val type = getString("type")) {
        "image" ->
            PostMedia.Image(
                url = getString("url"),
                alt = optionalString("alt"),
            )

        "video" ->
            PostMedia.Video(
                url = optionalString("url"),
                posterUrl = optionalString("posterUrl"),
                variants = optionalArray("variants").toStringList(),
                alt = optionalString("alt"),
                duration = optionalDouble("duration"),
            )

        else -> error("Unsupported media type: $type")
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) {
        return emptyList()
    }

    return List(length()) { index -> getString(index) }
}

private fun JSONObject.optionalObject(name: String): JSONObject? {
    return if (has(name) && !isNull(name)) getJSONObject(name) else null
}

private fun JSONObject.optionalArray(name: String): JSONArray? {
    return if (has(name) && !isNull(name)) getJSONArray(name) else null
}

private fun JSONObject.optionalString(name: String): String? {
    return if (has(name) && !isNull(name)) getString(name).takeIf { it.isNotBlank() } else null
}

private fun JSONObject.optionalDouble(name: String): Double? {
    return if (has(name) && !isNull(name)) getDouble(name) else null
}
