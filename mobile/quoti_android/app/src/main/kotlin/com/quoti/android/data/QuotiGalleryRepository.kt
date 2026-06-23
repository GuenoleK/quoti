package com.quoti.android.data

import android.content.Context
import com.quoti.android.core.model.PostMedia
import com.quoti.android.core.model.QuotiPost
import com.quoti.android.core.model.RelatedPost
import com.quoti.android.core.model.SocialPlatform
import org.json.JSONArray
import org.json.JSONObject

class QuotiGalleryRepository(
    context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            "quoti_gallery",
            Context.MODE_PRIVATE,
        )

    fun loadPosts(): List<QuotiPost> {
        val raw = preferences.getString(KeyPosts, null)
        if (raw == null) {
            clearLegacyFixtureSeedFlag()
            return emptyList()
        }

        val storedPosts = runCatching {
            JSONArray(raw).toQuotiPostList()
        }.getOrDefault(emptyList())

        val migratedPosts = storedPosts.withoutDevelopmentFixtures()
        if (migratedPosts.size != storedPosts.size || preferences.contains(LegacyKeyFixturesSeeded)) {
            return writePosts(migratedPosts)
        }

        return migratedPosts
    }

    fun savePost(post: QuotiPost): List<QuotiPost> {
        val nextPosts =
            listOf(post) +
                loadPosts().filterNot { savedPost -> savedPost.galleryStorageKey == post.galleryStorageKey }
        return writePosts(nextPosts)
    }

    private fun writePosts(posts: List<QuotiPost>): List<QuotiPost> {
        val payload = JSONArray()
        val distinctPosts =
            posts
                .withoutDevelopmentFixtures()
                .distinctBy { post -> post.galleryStorageKey }
        distinctPosts.forEach { post -> payload.put(post.toJsonObject()) }

        preferences
            .edit()
            .putString(KeyPosts, payload.toString())
            .remove(LegacyKeyFixturesSeeded)
            .commit()

        return distinctPosts
    }

    fun deletePosts(keys: Set<String>): List<QuotiPost> {
        if (keys.isEmpty()) {
            return loadPosts()
        }

        return writePosts(
            loadPosts().filterNot { post -> post.galleryStorageKey in keys },
        )
    }

    fun loadLayoutModeName(): String? {
        return preferences.getString(KeyLayoutMode, null)
    }

    fun saveLayoutModeName(value: String) {
        preferences
            .edit()
            .putString(KeyLayoutMode, value)
            .apply()
    }

    private fun clearLegacyFixtureSeedFlag() {
        if (preferences.contains(LegacyKeyFixturesSeeded)) {
            preferences.edit().remove(LegacyKeyFixturesSeeded).apply()
        }
    }

    private companion object {
        const val KeyPosts = "posts"
        const val KeyLayoutMode = "layout_mode"
        const val LegacyKeyFixturesSeeded = "fixtures_seeded"
    }
}

private val QuotiPost.galleryStorageKey: String
    get() = sourceUrl ?: id

private fun List<QuotiPost>.withoutDevelopmentFixtures(): List<QuotiPost> {
    return filterNot { post -> post.id.startsWith("fixture-") }
}

private fun JSONArray.toQuotiPostList(): List<QuotiPost> {
    return List(length()) { index -> getJSONObject(index).toQuotiPost() }
}

private fun JSONObject.toQuotiPost(): QuotiPost {
    return QuotiPost(
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
}

private fun JSONObject.toRelatedPost(): RelatedPost {
    return RelatedPost(
        authorName = optionalString("authorName"),
        authorHandle = optionalString("authorHandle"),
        authorAvatarUrl = optionalString("authorAvatarUrl"),
        content = getString("content"),
        sourceUrl = optionalString("sourceUrl"),
        media = optionalArray("media").toPostMediaList(),
    )
}

private fun JSONArray?.toPostMediaList(): List<PostMedia> {
    if (this == null) {
        return emptyList()
    }

    return List(length()) { index -> getJSONObject(index).toPostMedia() }
}

private fun JSONObject.toPostMedia(): PostMedia {
    return when (val type = getString("type")) {
        "image" ->
            PostMedia.Image(
                url = getString("url"),
                variants = optionalArray("variants").toStringList(),
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

private fun QuotiPost.toJsonObject(): JSONObject {
    return JSONObject()
        .put("id", id)
        .put("platform", platform.wireName)
        .put("authorName", authorName)
        .put("authorHandle", authorHandle)
        .putOptional("authorAvatarUrl", authorAvatarUrl)
        .put("content", content)
        .putOptional("relatedPost", relatedPost?.toJsonObject())
        .putOptional("publishedAt", publishedAt)
        .putOptional("sourceUrl", sourceUrl)
        .put("media", media.toJsonArray())
        .put("capturedAt", capturedAt)
}

private fun RelatedPost.toJsonObject(): JSONObject {
    return JSONObject()
        .put("content", content)
        .put("media", media.toJsonArray())
        .putOptional("authorHandle", authorHandle)
        .putOptional("authorName", authorName)
        .putOptional("authorAvatarUrl", authorAvatarUrl)
        .putOptional("sourceUrl", sourceUrl)
}

private fun List<PostMedia>.toJsonArray(): JSONArray {
    val payload = JSONArray()
    forEach { media -> payload.put(media.toJsonObject()) }
    return payload
}

private fun PostMedia.toJsonObject(): JSONObject {
    return when (this) {
        is PostMedia.Image ->
            JSONObject()
                .put("type", "image")
                .put("url", url)
                .put("variants", variants.toStringJsonArray())
                .putOptional("alt", alt)

        is PostMedia.Video ->
            JSONObject()
                .put("type", "video")
                .putOptional("url", url)
                .putOptional("posterUrl", posterUrl)
                .put("variants", variants.toStringJsonArray())
                .putOptional("alt", alt)
                .putOptional("duration", duration)
    }
}

private fun List<String>.toStringJsonArray(): JSONArray {
    val payload = JSONArray()
    forEach { value -> payload.put(value) }
    return payload
}

private fun JSONObject.putOptional(
    name: String,
    value: Any?,
): JSONObject {
    if (value != null) {
        put(name, value)
    }
    return this
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
