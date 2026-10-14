package dev.reader.app.ui.library

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.reader.parser.text.ChapterSplitter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class ChapterDetectDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val text = "Chapter 1\n\nOne.\n\nChapter 2\n\nTwo.\n\n~ Interlude\n\nThree."
    private var applied: String? = "unset"

    private fun setContent() = composeRule.setContent {
        ChapterDetectDialog(text, markdown = false, currentPattern = null, onApply = { applied = it }, onDismiss = {})
    }

    @Test
    fun `auto-detect previews the chapters it found and applies its pattern`() {
        setContent()

        composeRule.onNodeWithText("2 chapters found").assertExists()
        composeRule.onNodeWithTag("chapter-detect-apply").performClick()

        assertEquals(ChapterSplitter.detectPattern(text, markdown = false), applied)
    }

    @Test
    fun `a custom starts-with string changes the breaks`() {
        setContent()

        composeRule.onNodeWithTag("chapter-mode-STARTS_WITH").performClick()
        composeRule.onNodeWithTag("chapter-pattern-field").performTextInput("~ ")

        composeRule.onNodeWithText("1 chapter found").assertExists()
        composeRule.onNodeWithText("~ Interlude").assertExists()
    }

    @Test
    fun `no chapters applies null`() {
        setContent()

        composeRule.onNodeWithTag("chapter-detect-none").performClick()

        assertNull(applied)
    }
}
