package dev.reader.app.ui.library

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
}
