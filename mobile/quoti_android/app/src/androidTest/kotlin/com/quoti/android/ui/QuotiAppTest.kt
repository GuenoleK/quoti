package com.quoti.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quoti.android.core.model.QuotiPost
import com.quoti.android.core.model.SocialPlatform
import com.quoti.android.share.IncomingShareDraft
import com.quoti.android.ui.theme.QuotiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuotiAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsEmptyStateWhenNoPostIsCaptured() {
        composeRule.setContent {
            QuotiTheme {
                QuotiApp(incomingDraft = null)
            }
        }

        composeRule.onAllNodesWithText("Quoti").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("No post captured").assertIsDisplayed()
        composeRule.onAllNodesWithText("Light").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Copy image").assertCountEquals(0)
    }

    @Test
    fun showsProcessingStateWithoutInterimUrlCard() {
        composeRule.setContent {
            QuotiTheme {
                QuotiApp(
                    shareState =
                        QuotiShareState.Loading(
                            sourceUrl = "https://x.com/i/status/123",
                        ),
                )
            }
        }

        composeRule.onNodeWithText("Preparing post").assertIsDisplayed()
        composeRule.onAllNodesWithText("https://x.com/i/status/123").assertCountEquals(0)
        composeRule.onAllNodesWithText("Shared X post").assertCountEquals(0)
    }

    @Test
    fun showsExportAndShareControlsForCapturedPost() {
        composeRule.setContent {
            QuotiTheme {
                QuotiApp(
                    shareState =
                        QuotiShareState.Ready(
                            capturedDraft(),
                        ),
                )
            }
        }

        composeRule.onAllNodesWithText("Quoti").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("Light").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Copy image").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("More actions").performClick()
        composeRule.onNodeWithText("Share image").assertIsDisplayed()
    }

    @Test
    fun incomingShareDisplaysCapturedPayloadWithoutEditor() {
        composeRule.setContent {
            QuotiTheme {
                QuotiApp(
                    shareState =
                        QuotiShareState.Ready(
                            capturedDraft(
                                content = "Captured text",
                            ),
                        ),
                )
            }
        }

        composeRule.onAllNodesWithText("Captured text").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("Edit card").assertCountEquals(0)
        composeRule.onAllNodesWithText("Post text").assertCountEquals(0)
    }

    private fun capturedDraft(content: String = "Captured text"): IncomingShareDraft {
        return IncomingShareDraft(
            rawText = "https://x.com/maya_laurent/status/123",
            post =
                QuotiPost(
                    id = "incoming-test",
                    platform = SocialPlatform.X,
                    authorName = "maya_laurent",
                    authorHandle = "@maya_laurent",
                    content = content,
                    sourceUrl = "https://x.com/maya_laurent/status/123",
                    capturedAt = "2026-06-15T10:00:00Z",
                ),
        )
    }
}
