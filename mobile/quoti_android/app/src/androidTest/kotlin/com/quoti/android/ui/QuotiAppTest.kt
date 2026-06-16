package com.quoti.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quoti.android.ui.theme.QuotiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuotiAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsExportAndShareControls() {
        composeRule.setContent {
            QuotiTheme {
                QuotiApp(incomingDraft = null)
            }
        }

        composeRule.onAllNodesWithText("Quoti").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("Light").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Copy image").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("More actions").performClick()
        composeRule.onNodeWithText("Share image").assertIsDisplayed()
    }
}
