package dev.reader.app.ui.library

import androidx.compose.ui.test.assertIsNotEnabled
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
    private var appliedExcluded: Set<Int> = setOf(-1)

    private fun setContent(currentExcludedLines: Set<Int> = emptySet()) = composeRule.setContent {
        ChapterDetectDialog(
            text,
            markdown = false,
            currentPattern = null,
            onApply = { pattern, excluded ->
                applied = pattern
                appliedExcluded = excluded
            },
            onDismiss = {},
            currentExcludedLines = currentExcludedLines,
        )
    }

    @Test
    fun `auto-detect previews the chapters it found and applies its pattern`() {
        setContent()

        composeRule.onNodeWithText("2 of 2 chapters kept").assertExists()
        composeRule.onNodeWithTag("chapter-detect-apply").performClick()

        assertEquals(ChapterSplitter.detectPattern(text, markdown = false), applied)
        assertEquals(emptySet(), appliedExcluded)
    }

    @Test
    fun `unchecking a break excludes it and re-checking restores it`() {
        setContent()
        val pattern = requireNotNull(ChapterSplitter.detectPattern(text, false))
        val secondBreakLine = ChapterSplitter.breaks(text, pattern)[1].line

        composeRule.onNodeWithTag("chapter-break-$secondBreakLine").performClick()
        composeRule.onNodeWithText("1 of 2 chapters kept").assertExists()
        composeRule.onNodeWithTag("chapter-detect-apply").performClick()
        assertEquals(setOf(secondBreakLine), appliedExcluded)
    }

    @Test
    fun `excluding every break disables apply`() {
        setContent()
        val breaks = ChapterSplitter.breaks(text, requireNotNull(ChapterSplitter.detectPattern(text, false)))

        breaks.forEach { composeRule.onNodeWithTag("chapter-break-${it.line}").performClick() }

        composeRule.onNodeWithTag("chapter-detect-apply").assertIsNotEnabled()
    }

    @Test
    fun `a custom starts-with string changes the breaks`() {
        setContent()

        composeRule.onNodeWithTag("chapter-mode-STARTS_WITH").performClick()
        composeRule.onNodeWithTag("chapter-pattern-field").performTextInput("~ ")

        composeRule.onNodeWithText("1 of 1 chapter kept").assertExists()
        composeRule.onNodeWithText("~ Interlude").assertExists()
    }

    @Test
    fun `no chapters applies null`() {
        setContent()

        composeRule.onNodeWithTag("chapter-detect-none").performClick()

        assertNull(applied)
    }
}
