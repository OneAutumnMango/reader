package dev.reader.app.ui.library

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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

    private fun setContent(
        currentExcludedLines: Set<Int> = emptySet(),
        currentPattern: String? = null,
    ) = composeRule.setContent {
        ChapterDetectDialog(
            text,
            markdown = false,
            currentPattern = currentPattern,
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
    fun `pick lines starts from the current split and adds lines picked in the book`() {
        setContent()

        composeRule.onNodeWithTag("chapter-mode").performClick()
        composeRule.onNodeWithTag("chapter-mode-PICK_LINES").performClick()
        composeRule.onNodeWithTag("chapter-break-0").assertExists()
        composeRule.onNodeWithTag("chapter-break-4").assertExists()

        composeRule.onNodeWithTag("chapter-open-picker").performClick()
        composeRule.onNodeWithTag("chapter-picker-lines").performScrollToIndex(8)
        composeRule.onNodeWithTag("chapter-line-8").performClick()
        composeRule.onNodeWithTag("chapter-picker-done").performClick()
        composeRule.onNodeWithTag("chapter-break-4").performClick()
        composeRule.onNodeWithTag("chapter-detect-apply").performClick()

        assertEquals(ChapterSplitter.linesPattern(listOf(0, 8)), applied)
        assertEquals(emptySet(), appliedExcluded)
    }

    @Test
    fun `reopening keeps picked lines listed and highlighted until toggled off`() {
        setContent(currentPattern = ChapterSplitter.linesPattern(listOf(0, 8)))

        composeRule.onNodeWithTag("chapter-break-8").assertExists()
        composeRule.onNodeWithTag("chapter-open-picker").performClick()
        composeRule.onNodeWithText("Tap a line to start a chapter there \u00b7 2 picked").assertExists()
        composeRule.onNodeWithTag("chapter-picker-lines").performScrollToIndex(8)
        composeRule.onNodeWithTag("chapter-line-8").performClick()
        composeRule.onNodeWithTag("chapter-picker-done").performClick()
        composeRule.onNodeWithTag("chapter-detect-apply").performClick()

        assertEquals(ChapterSplitter.linesPattern(listOf(0)), applied)
    }

    @Test
    fun `a saved auto-detected split reopens as auto-detect`() {
        setContent(currentPattern = ChapterSplitter.detectPattern(text, markdown = false))

        composeRule.onNodeWithText("Auto-detect").assertExists()
        composeRule.onNodeWithText("2 of 2 chapters kept").assertExists()
    }

    @Test
    fun `excluding every break disables apply`() {
        setContent()
        val breaks = ChapterSplitter.breaks(text, requireNotNull(ChapterSplitter.detectPattern(text, false)))

        breaks.forEach { composeRule.onNodeWithTag("chapter-break-${it.line}").performClick() }

        composeRule.onNodeWithTag("chapter-detect-apply").assertIsNotEnabled()
    }

    @Test
    fun `picking another suggested split changes the preview`() {
        val markdown = "# One\n\na\n\n## A\n\nb\n\n## B\n\nc\n\n# Two\n\nd\n\n## C\n\ne"
        composeRule.setContent {
            ChapterDetectDialog(markdown, markdown = true, currentPattern = null, onApply = { _, _ -> }, onDismiss = {})
        }

        composeRule.onNodeWithText("2 of 2 chapters kept").assertExists()
        composeRule.onNodeWithTag("chapter-suggestion-1").performClick()

        composeRule.onNodeWithText("3 of 3 chapters kept").assertExists()
    }

    @Test
    fun `a custom starts-with string changes the breaks`() {
        setContent()

        composeRule.onNodeWithTag("chapter-mode").performClick()
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
