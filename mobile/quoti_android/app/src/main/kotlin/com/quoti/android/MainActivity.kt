package com.quoti.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quoti.android.share.IncomingShareDraft
import com.quoti.android.share.IncomingShareNormalizer
import com.quoti.android.share.IncomingShareReader
import com.quoti.android.ui.QuotiApp
import com.quoti.android.ui.theme.QuotiTheme

class MainActivity : ComponentActivity() {
    private val shareNormalizer = IncomingShareNormalizer()
    private var incomingDraft by mutableStateOf<IncomingShareDraft?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingDraft = normalizeIncomingShare(intent)

        setContent {
            QuotiTheme {
                QuotiApp(incomingDraft = incomingDraft)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        normalizeIncomingShare(intent)?.let { draft ->
            incomingDraft = draft
        }
    }

    private fun normalizeIncomingShare(intent: Intent?): IncomingShareDraft? {
        return IncomingShareReader.fromIntent(intent)?.let(shareNormalizer::normalize)
    }
}
