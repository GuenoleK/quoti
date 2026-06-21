package com.quoti.android.core.model

import org.json.JSONArray
import org.json.JSONObject

fun QuotiPost.toJsonString(): String =
    JSONObject()
        .put("id", id)
        .put("platform", platform.wireName)
        .put("authorName", authorName)
        .put("authorHandle", authorHandle)
        .putOptional("authorAvatarUrl", authorAvatarUrl)
        .put("content", content)
        .putOptional("relatedPost", relatedPost?.toJson())
        .putOptional("publishedAt", publishedAt)
        .putOptional("sourceUrl", sourceUrl)
        .put("media", media.toJson())
        .put("capturedAt", capturedAt)
        .toString()

fun quotiPostFromJsonString(value: String): QuotiPost = JSONObject(value).toQuotiPost()

private fun JSONObject.toQuotiPost(): QuotiPost =
    QuotiPost(
        id = getString("id"),
        platform = SocialPlatform.fromJson(getString("platform")),
        authorName = getString("authorName"),
        authorHandle = getString("authorHandle"),
        authorAvatarUrl = optionalString("authorAvatarUrl"),
        content = getString("content"),
        relatedPost = optionalObject("relatedPost")?.toRelatedPost(),
        publishedAt = optionalString("publishedAt"),
        sourceUrl = optionalString("sourceUrl"),
        media = optionalArray("media").toPostMediaList(),
        capturedAt = getString("capturedAt"),
    )

private fun RelatedPost.toJson(): JSONObject =
    JSONObject()
        .put("content", content)
        .put("media", media.toJson())
        .putOptional("authorHandle", authorHandle)
        .putOptional("authorName", authorName)
        .putOptional("authorAvatarUrl", authorAvatarUrl)
        .putOptional("sourceUrl", sourceUrl)

private fun JSONObject.toRelatedPost(): RelatedPost =
    RelatedPost(
        content = getString("content"),
        media = optionalArray("media").toPostMediaList(),
        authorHandle = optionalString("authorHandle"),
        authorName = optionalString("authorName"),
        authorAvatarUrl = optionalString("authorAvatarUrl"),
        sourceUrl = optionalString("sourceUrl"),
    )

private fun List<PostMedia>.toJson(): JSONArray =
    JSONArray().also { array ->
        forEach { media -> array.put(media.toJson()) }
    }

private fun PostMedia.toJson(): JSONObject =
    when (this) {
        is PostMedia.Image ->
            JSONObject()
                .put("type", "image")
                .put("url", url)
                .put("variants", JSONArray(variants))
                .putOptional("alt", alt)

        is PostMedia.Video ->
            JSONObject()
                .put("type", "video")
                .put("variants", JSONArray(variants))
                .putOptional("url", url)
                .putOptional("posterUrl", posterUrl)
                .putOptional("duration", duration)
                .putOptional("alt", alt)
    }

private fun JSONArray?.toPostMediaList(): List<PostMedia> {
    if (this == null) {
        return emptyList()
    }

    return List(length()) { index -> getJSONObject(index).toPostMedia() }
}

private fun JSONObject.toPostMedia(): PostMedia =
    when (val type = getString("type")) {
        "image" ->
            PostMedia.Image(
                url = getString("url"),
                variants = optionalArray("variants").toStringList(),
                alt = optionalString("alt"),
            )

        "video" ->
            PostMedia.Video(
                variants = optionalArray("variants").toStringList(),
                url = optionalString("url"),
                posterUrl = optionalString("posterUrl"),
                duration = optionalDouble("duration"),
                alt = optionalString("alt"),
            )

        else -> error("Unsupported media type: $type")
    }

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) {
        return emptyList()
    }

    return List(length()) { index -> getString(index) }
}

private fun JSONObject.optionalObject(name: String): JSONObject? =
    if (has(name) && !isNull(name)) getJSONObject(name) else null

private fun JSONObject.optionalArray(name: String): JSONArray? =
    if (has(name) && !isNull(name)) getJSONArray(name) else null

private fun JSONObject.optionalString(name: String): String? =
    if (has(name) && !isNull(name)) getString(name).takeIf { it.isNotBlank() } else null

private fun JSONObject.optionalDouble(name: String): Double? =
    if (has(name) && !isNull(name)) getDouble(name) else null

private fun JSONObject.putOptional(
    name: String,
    value: Any?,
): JSONObject {
    if (value == null) {
        put(name, JSONObject.NULL)
    } else {
        put(name, value)
    }
    return this
}
