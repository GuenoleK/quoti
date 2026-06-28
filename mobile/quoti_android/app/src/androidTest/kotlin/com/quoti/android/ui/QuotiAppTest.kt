package com.quoti.android.ui

import android.content.Context
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quoti.android.core.model.PostMedia
import com.quoti.android.core.model.QuotiPost
import com.quoti.android.core.model.RelatedPost
import com.quoti.android.core.model.SocialPlatform
import com.quoti.android.data.QuotiGalleryRepository
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
        composeRule.onNodeWithContentDescription("Gallery").assertIsDisplayed()
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
        composeRule.onNodeWithContentDescription("More actions").performClick()
        composeRule.onNodeWithText("Copy image").assertIsDisplayed()
        composeRule.onAllNodesWithText("Share image").assertCountEquals(1)
        composeRule.onAllNodesWithText("Open source").assertCountEquals(1)
        composeRule.onAllNodesWithText("Copy source link").assertCountEquals(0)
        composeRule.onAllNodesWithText("Copy text").assertCountEquals(0)
        composeRule.onAllNodesWithText("Refresh preview").assertCountEquals(0)
    }

    @Test
    fun showsVideoExportControlsForVideoPost() {
        composeRule.setContent {
            QuotiTheme {
                QuotiApp(
                    shareState =
                        QuotiShareState.Ready(
                            capturedDraft(
                                post =
                                    capturedPost(
                                        relatedPost =
                                            RelatedPost(
                                                authorName = "ABDOU",
                                                authorHandle = "@MarshallFCB",
                                                content = "Parent post",
                                                media =
                                                    listOf(
                                                        PostMedia.Video(
                                                            url = "https://video.twimg.com/ext_tw_video/123/pu/vid/avc1/720x1280/video.mp4",
                                                            posterUrl = "https://pbs.twimg.com/ext_tw_video_thumb/123/pu/img/poster.jpg",
                                                            variants =
                                                                listOf(
                                                                    "https://video.twimg.com/ext_tw_video/123/pu/vid/avc1/720x1280/video.mp4",
                                                                ),
                                                        ),
                                                    ),
                                            ),
                                    ),
                            ),
                        ),
                )
            }
        }

        composeRule.onNodeWithContentDescription("More actions").performClick()
        composeRule.onNodeWithText("Download video").assertIsDisplayed()
        composeRule.onNodeWithText("Copy image").assertIsDisplayed()
        composeRule.onAllNodesWithText("Download PNG").assertCountEquals(1)
        composeRule.onAllNodesWithText("Open source").assertCountEquals(1)
        composeRule.onAllNodesWithText("Copy source link").assertCountEquals(0)
        composeRule.onAllNodesWithText("Copy text").assertCountEquals(0)
        composeRule.onAllNodesWithText("Refresh preview").assertCountEquals(0)
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

    @Test
    fun quotedPostCanBeHiddenFromPreview() {
        composeRule.setContent {
            QuotiTheme {
                QuotiApp(
                    shareState =
                        QuotiShareState.Ready(
                            capturedDraft(
                                post =
                                    capturedPost(
                                        content = "Main captured text",
                                        relatedPost =
                                            RelatedPost(
                                                authorName = "Source author",
                                                authorHandle = "@source",
                                                content = "Quoted source text",
                                            ),
                                    ),
                            ),
                        ),
                )
            }
        }

        composeRule.onNodeWithText("Quoted source text").assertIsDisplayed()
        composeRule.onNodeWithText("Main only").performScrollTo().performClick()

        composeRule.onAllNodesWithText("Quoted source text").assertCountEquals(0)
        composeRule.onNodeWithText("Main captured text").assertIsDisplayed()
    }

    @Test
    fun clearPostReturnsToEmptyState() {
        composeRule.setContent {
            var shareState by mutableStateOf<QuotiShareState>(
                QuotiShareState.Ready(capturedDraft()),
            )
            QuotiTheme {
                QuotiApp(
                    shareState = shareState,
                    onClear = { shareState = QuotiShareState.Empty },
                )
            }
        }

        composeRule.onNodeWithContentDescription("More actions").performClick()
        composeRule.onNodeWithText("Clear post").performClick()

        composeRule.onNodeWithText("No post captured").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Copy image").assertCountEquals(0)
    }

    @Test
    fun gallerySelectionShowsDeleteSheetAndHidesSearch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context
            .getSharedPreferences("quoti_gallery", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        QuotiGalleryRepository(context).savePost(
            capturedPost(
                id = "saved-first",
                content = "First saved text",
                sourceUrl = "https://x.com/maya_laurent/status/111",
            ),
        )
        QuotiGalleryRepository(context).savePost(
            capturedPost(
                id = "saved-second",
                content = "Second saved text",
                sourceUrl = "https://x.com/maya_laurent/status/222",
            ),
        )

        composeRule.setContent {
            QuotiTheme {
                QuotiApp(incomingDraft = null)
            }
        }

        composeRule.onNodeWithContentDescription("Gallery").performClick()
        composeRule.onNodeWithText("Second saved text").performTouchInput { longClick() }

        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
        composeRule.onAllNodesWithText("Search text or URL").assertCountEquals(0)

        composeRule.onNodeWithText("First saved text").performTouchInput { click() }

        composeRule.onNodeWithText("2 selected").assertIsDisplayed()
    }

    private fun capturedDraft(
        content: String = "Captured text",
        post: QuotiPost = capturedPost(content = content),
    ): IncomingShareDraft {
        return IncomingShareDraft(
            rawText = "https://x.com/maya_laurent/status/123",
            post = post,
        )
    }

    private fun capturedPost(
        id: String = "incoming-test",
        content: String = "Captured text",
        relatedPost: RelatedPost? = null,
        sourceUrl: String = "https://x.com/maya_laurent/status/123",
    ): QuotiPost {
        return QuotiPost(
            id = id,
            platform = SocialPlatform.X,
            authorName = "maya_laurent",
            authorHandle = "@maya_laurent",
            content = content,
            relatedPost = relatedPost,
            sourceUrl = sourceUrl,
            capturedAt = "2026-06-15T10:00:00Z",
        )
    }
}
