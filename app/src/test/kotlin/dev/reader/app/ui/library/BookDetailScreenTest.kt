package dev.reader.app.ui.library

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class BookDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val book = Book(
        id = BookId("book-1"),
        title = "My Book",
        format = BookFormat.TXT,
        sourcePath = "/books/my-book.txt",
        addedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
    private val chapters = listOf(
        Chapter(ChapterId("ch-1"), book.id, "One", "", 0),
        Chapter(ChapterId("ch-2"), book.id, "Two", "", 1),
    )

    @Test
    fun `clicking a chapter reports it`() {
        var clicked: Chapter? = null
        composeRule.setContent {
            BookDetailScreen(book, chapters, emptyList(), onChapterClick = { clicked = it })
        }

        composeRule.onNodeWithText("Two").performClick()

        assertEquals(chapters[1], clicked)
    }
}
