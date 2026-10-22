package dev.reader.app.ui.reader

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.reader.core.reader.ReadingMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ReaderScrubberTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `dragging to a point on the track reports the matching fraction`() {
        var reported = -1f
        composeRule.setContent {
            ReaderScrubber(fraction = 0f, onScrub = { reported = it }, modifier = Modifier.size(24.dp, 200.dp))
        }
        composeRule.onNodeWithTag("reader-scrubber-track").performTouchInput {
            down(topLeft)
            moveTo(Offset(topLeft.x, topLeft.y + 100f))
            up()
        }
        assertEquals(0.5f, reported, 0.05f)
    }

    @Test
    fun `dragging above the top of the track clamps to 0`() {
        var reported = -1f
        composeRule.setContent {
            ReaderScrubber(fraction = 0.5f, onScrub = { reported = it }, modifier = Modifier.size(24.dp, 200.dp))
        }
        composeRule.onNodeWithTag("reader-scrubber-track").performTouchInput {
            down(center)
            moveTo(Offset(center.x, -500f))
            up()
        }
        assertEquals(0f, reported)
    }

    @Test
    fun `dragging below the bottom of the track clamps to 1`() {
        var reported = -1f
        composeRule.setContent {
            ReaderScrubber(fraction = 0.5f, onScrub = { reported = it }, modifier = Modifier.size(24.dp, 200.dp))
        }
        composeRule.onNodeWithTag("reader-scrubber-track").performTouchInput {
            down(center)
            moveTo(Offset(center.x, 5_000f))
            up()
        }
        assertEquals(1f, reported)
    }

    @Test
    fun `while dragging, the displayed position follows the drag rather than the fraction prop`() {
        var fraction by mutableStateOf(0f)
        composeRule.setContent {
            ReaderScrubber(fraction = fraction, onScrub = { }, modifier = Modifier.size(24.dp, 200.dp))
        }
        composeRule.onNodeWithTag("reader-scrubber-track").performTouchInput {
            down(topLeft)
            moveTo(Offset(topLeft.x, topLeft.y + 150f))
        }
        fraction = 0f // external truth says "top", but a drag is in progress
        // SemanticsNode.positionInRoot can read stale layout immediately after a snapshot write in
        // this BoxWithConstraints + Robolectric combination, so read the live LayoutCoordinates
        // directly rather than the semantics convenience property.
        val draggedTop = composeRule.onNodeWithTag("reader-scrubber-thumb")
            .fetchSemanticsNode().layoutInfo.coordinates.positionInRoot().y
        composeRule.onNodeWithTag("reader-scrubber-track").performTouchInput { up() }

        assertTrue(draggedTop > 50f) // thumb stayed near the drag point, not snapped to fraction=0
    }
}

class ReaderScrubberHelpersTest {

    @Test
    fun `scrubberVisible requires the bar up, not expanded, and continuous-scroll mode`() {
        assertTrue(scrubberVisible(showMenu = true, menuExpanded = false, readingMode = ReadingMode.CONTINUOUS_SCROLL))
        assertEquals(
            false,
            scrubberVisible(showMenu = false, menuExpanded = false, readingMode = ReadingMode.CONTINUOUS_SCROLL),
        )
        assertEquals(
            false,
            scrubberVisible(showMenu = true, menuExpanded = true, readingMode = ReadingMode.CONTINUOUS_SCROLL),
        )
        assertEquals(
            false,
            scrubberVisible(showMenu = true, menuExpanded = false, readingMode = ReadingMode.PAGED_VERTICAL),
        )
    }

    @Test
    fun `scrubberFraction is 0 for null position or zero total, otherwise index over total`() {
        assertEquals(0f, scrubberFraction(null))
        assertEquals(0f, scrubberFraction(5 to 0))
        assertEquals(0.5f, scrubberFraction(5 to 10))
    }

    @Test
    fun `nextScrubJump clamps to a valid block index for degenerate totals`() {
        assertEquals(0, nextScrubJump(fraction = 0.5f, total = 0, nonce = 1).block)
        assertEquals(0, nextScrubJump(fraction = 0.5f, total = 1, nonce = 1).block)
        assertEquals(5, nextScrubJump(fraction = 0.5f, total = 10, nonce = 1).block)
    }

    @Test
    fun `nextScrubJump carries the given nonce so repeated jumps to the same block are distinct`() {
        val first = nextScrubJump(fraction = 0.5f, total = 10, nonce = 1)
        val second = nextScrubJump(fraction = 0.5f, total = 10, nonce = 2)
        assertEquals(first.block, second.block)
        assertTrue(first.id != second.id)
    }
}
