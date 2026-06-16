package com.quoti.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quoti.android.core.model.SocialPlatform
import com.quoti.android.share.IncomingShareDraft
import com.quoti.android.share.IncomingShareMissingField
import com.quoti.android.share.IncomingShareEnricher
import com.quoti.android.share.IncomingShareNormalizer
import com.quoti.android.share.IncomingShareReader
import com.quoti.android.ui.QuotiApp
import com.quoti.android.ui.QuotiShareState
import com.quoti.android.ui.theme.QuotiTheme
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val shareNormalizer = IncomingShareNormalizer()
    private val shareEnricher = IncomingShareEnricher()
    private var shareState by mutableStateOf<QuotiShareState>(QuotiShareState.Empty)
    private var activeShareKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingShare(intent)

        setContent {
            QuotiTheme {
                QuotiApp(
                    shareState = shareState,
                    onClear = ::clearShare,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShare(intent)
    }

    private fun handleIncomingShare(intent: Intent?) {
        val payload = IncomingShareReader.fromIntent(intent)
        if (payload == null) {
            if (intent?.action == Intent.ACTION_MAIN) {
                clearShare()
            }
            return
        }

        val draft = shareNormalizer.normalize(payload) ?: return
        val shareKey = draft.post.sourceUrl ?: draft.rawText
        activeShareKey = shareKey

        shareState =
            if (draft.shouldWaitForEnrichment()) {
                QuotiShareState.Loading(sourceUrl = draft.post.sourceUrl)
            } else {
                draft.toShareState()
            }
        enrichIncomingShare(draft)
    }

    private fun enrichIncomingShare(draft: IncomingShareDraft) {
        val shareKey = draft.post.sourceUrl ?: draft.rawText
        lifecycleScope.launch {
            val enrichedDraft = shareEnricher.enrich(draft)
            if (activeShareKey == shareKey) {
                shareState = enrichedDraft.toShareState()
            }
        }
    }

    private fun clearShare() {
        activeShareKey = null
        shareState = QuotiShareState.Empty
        setIntent(
            Intent(Intent.ACTION_MAIN)
                .setClass(this, MainActivity::class.java),
        )
    }
}

private fun IncomingShareDraft.shouldWaitForEnrichment(): Boolean {
    return post.platform == SocialPlatform.X &&
        (missingFields.contains(IncomingShareMissingField.Content) || post.sourceUrl.isXStatusUrl())
}

private fun IncomingShareDraft.toShareState(): QuotiShareState {
    return if (hasRenderableContent()) {
        QuotiShareState.Ready(this)
    } else {
        QuotiShareState.Empty
    }
}

private fun IncomingShareDraft.hasRenderableContent(): Boolean {
    val sourceUrl = post.sourceUrl
    return post.content.isNotBlank() &&
        !missingFields.contains(IncomingShareMissingField.Content) &&
        (sourceUrl == null || post.content != sourceUrl)
}

private fun String?.isXStatusUrl(): Boolean {
    if (isNullOrBlank()) {
        return false
    }

    val uri = runCatching { URI(this) }.getOrNull() ?: return false
    val host = uri.host?.lowercase(Locale.US).orEmpty()
    val segments = uri.path.split("/").filter { it.isNotBlank() }

    return (host == "x.com" || host.endsWith(".x.com") || host == "twitter.com" || host.endsWith(".twitter.com")) &&
        segments.size >= 3 &&
        segments[1] == "status"
}
