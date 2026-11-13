package dev.reader.app.ui.reader

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import dev.reader.core.reader.ReaderSettings
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.Span
import dev.reader.core.theme.ReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ContinuousReaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders book illustrations instead of skipping them`() {
        composeRule.setContent {
            ContinuousReader(
                RichDocument(listOf(Block.Image("/missing.png", "Chapter illustration"))),
                ReaderTheme.Default,
                ReaderSettings(),
            )
        }
        composeRule.onNodeWithContentDescription("Chapter illustration").assertIsDisplayed()
    }

    private val document = RichDocument(
        listOf(
            Block.Heading(1, listOf(Span("Chapter One"))),
            Block.Paragraph(listOf(Span("Hello world."))),
        ),
    )

    @Test
    fun `renders every block as text`() {
        composeRule.setContent {
            ContinuousReader(document, ReaderTheme.Default, ReaderSettings())
        }

        composeRule.onNodeWithText("Chapter One").assertIsDisplayed()
        composeRule.onNodeWithText("Hello world.").assertIsDisplayed()
    }

    @Test
    fun `reports the first visible block until the end is reached`() {
        val long = RichDocument((1..60).map { Block.Paragraph(listOf(Span("Paragraph $it"))) })
        var reported: Pair<Int, Int>? = null
        composeRule.setContent {
            ContinuousReader(
                long,
                ReaderTheme.Default,
                ReaderSettings(),
                Modifier.size(300.dp, 400.dp).testTag("reader"),
                onPositionChanged = { index, total -> reported = index to total },
            )
        }

        assertEquals(0 to 60, reported)
        composeRule.onNodeWithTag("reader").performScrollToIndex(59)
        composeRule.waitForIdle()
        assertEquals(59 to 60, reported)
    }

    @Test
    fun `a chapter that fits on screen reports its end straight away`() {
        var reported: Pair<Int, Int>? = null
        composeRule.setContent {
            ContinuousReader(
                document,
                ReaderTheme.Default,
                ReaderSettings(),
                onPositionChanged = { index, total -> reported = index to total },
            )
        }

        assertEquals(1 to 2, reported)
    }

    @Test
    fun `jumping twice to the same block with a different id scrolls both times`() {
        val long = RichDocument((1..60).map { Block.Paragraph(listOf(Span("Paragraph $it"))) })
        var jump by mutableStateOf<BlockJump?>(null)
        composeRule.setContent {
            ContinuousReader(
                long,
                ReaderTheme.Default,
                ReaderSettings(),
                Modifier.size(300.dp, 400.dp).testTag("reader"),
                jump = jump,
            )
        }

        jump = BlockJump(block = 40, id = 1)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Paragraph 60").assertDoesNotExist() // scrolled to 40, not past it

        // Scroll away so the second jump to the SAME block has somewhere real to prove it moved
        // from — without this, the assertion below would pass even if the nonce were ignored.
        composeRule.onNodeWithTag("reader").performScrollToIndex(0)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Paragraph 41").assertDoesNotExist()

        // Same block, new id — must scroll again (e.g. after a user action moved away).
        jump = BlockJump(block = 40, id = 2)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Paragraph 41").assertIsDisplayed()
    }

    // Robolectric lays text and images out ~0 high, so inter-block spacing is the only real height.
    private val tallTheme = ReaderTheme.Default.copy(paragraphSpacingSp = 100f)

    private val longDoc = RichDocument((1..30).map { Block.Paragraph(listOf(Span("Paragraph $it"))) })

    @Test
    fun `auto-scrolling moves the list on and reports the end once`() {
        var firstVisible = 0
        var ended = 0
        composeRule.mainClock.autoAdvance = false // the loop requests a frame forever
        composeRule.setContent {
            ContinuousReader(
                longDoc,
                tallTheme,
                ReaderSettings(),
                Modifier.size(300.dp, 400.dp),
                onPositionChanged = { index, _ -> firstVisible = index },
                autoScrollSpeedDp = 5000f,
                autoScrolling = true,
                onReachedEnd = { ended++ },
            )
        }
        composeRule.mainClock.advanceTimeBy(5_000)

        assert(firstVisible > 0) { "list never moved" }
        assertEquals(1, ended)
    }

    @Test
    fun `does not scroll while auto-scrolling is off`() {
        var firstVisible = 0
        composeRule.setContent {
            ContinuousReader(
                longDoc,
                tallTheme,
                ReaderSettings(),
                Modifier.size(300.dp, 400.dp),
                onPositionChanged = { index, _ -> firstVisible = index },
                autoScrollSpeedDp = 5000f,
                autoScrolling = false,
            )
        }
        composeRule.mainClock.advanceTimeBy(2_000)

        assertEquals(0, firstVisible)
    }

    @Test
    fun `auto-scrolling keeps going after the user swipes the list`() {
        var firstVisible = 0
        val doc = RichDocument((1..200).map { Block.Paragraph(listOf(Span("Paragraph $it"))) })
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ContinuousReader(
                doc,
                tallTheme,
                ReaderSettings(),
                Modifier.size(300.dp, 400.dp).testTag("list"),
                onPositionChanged = { index, _ -> firstVisible = index },
                autoScrollSpeedDp = 400f,
                autoScrolling = true,
            )
        }
        composeRule.mainClock.advanceTimeBy(500)
        val beforeSwipe = firstVisible
        composeRule.onNodeWithTag("list").performTouchInput {
            swipe(Offset(150f, 300f), Offset(150f, 200f), durationMillis = 1_000)
        }
        composeRule.mainClock.advanceTimeBy(1_000) // let any fling settle
        val afterSwipe = firstVisible
        composeRule.mainClock.advanceTimeBy(3_000)

        assert(firstVisible > afterSwipe) {
            "auto-scroll stopped after a swipe ($beforeSwipe -> $afterSwipe -> $firstVisible)"
        }
    }
}
