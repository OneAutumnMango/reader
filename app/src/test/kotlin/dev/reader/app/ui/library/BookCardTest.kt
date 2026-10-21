package dev.reader.app.ui.library

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class BookCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val book = Book(
        id = BookId("book-1"),
        title = "My Book",
        author = "Jane Doe",
        format = BookFormat.EPUB,
        sourcePath = "/books/my-book.epub",
        addedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    @Test
    fun `shows title and author`() {
        composeRule.setContent { BookCard(book = book, progress = null, onClick = {}) }

        composeRule.onNodeWithText("My Book").assertExists()
        composeRule.onNodeWithText("Jane Doe").assertExists()
    }

    @Test
    fun `clicking the card triggers the callback`() {
        var clicked = false
        composeRule.setContent { BookCard(book = book, progress = 0.5f, onClick = { clicked = true }) }

        composeRule.onNodeWithText("My Book").performClick()

        assertTrue(clicked)
    }

    @Test
    fun `long-pressing the card triggers the long-click callback`() {
        var longClicked = false
        composeRule.setContent {
            BookCard(book = book, progress = null, onClick = {}, onLongClick = { longClicked = true })
        }

        composeRule.onNodeWithText("My Book").performTouchInput {
            down(center)
            advanceEventTime(600L)
            up()
        }

        assertTrue(longClicked)
    }

    @Test
    fun `hides author when showAuthor is false`() {
        composeRule.setContent { BookCard(book = book, progress = null, onClick = {}, showAuthor = false) }

        composeRule.onNodeWithText("My Book").assertExists()
        composeRule.onAllNodesWithText("Jane Doe").assertCountEquals(0)
    }

    @Test
    fun `shows the progress bar only when progress is not null`() {
        composeRule.setContent { BookCard(book = book, progress = 0.5f, onClick = {}) }
        composeRule.onNodeWithTag("book-progress-bar", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `hides the progress bar when progress is null`() {
        composeRule.setContent { BookCard(book = book, progress = null, onClick = {}) }
        composeRule.onNodeWithTag("book-progress-bar").assertDoesNotExist()
    }

    @Test
    fun `shows the progress bar at zero progress`() {
        composeRule.setContent { BookCard(book = book, progress = 0f, onClick = {}) }
        composeRule.onNodeWithTag("book-progress-bar", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `shows the progress bar at full progress`() {
        composeRule.setContent { BookCard(book = book, progress = 1f, onClick = {}) }
        composeRule.onNodeWithTag("book-progress-bar", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `progress bar reports its value for accessibility`() {
        composeRule.setContent { BookCard(book = book, progress = 0.5f, onClick = {}) }

        val merged = composeRule.onNodeWithTag("book-card-${book.id.value}").fetchSemanticsNode()
        val info = merged.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)

        assertEquals(0.5f, info?.current)
    }
}
