package dev.reader.app.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.model.Bookmark
import dev.reader.core.model.BookmarkId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
    fun `extras are tucked into an expandable book info section`() {
        val copyright = Chapter(ChapterId("ex-1"), book.id, "Copyright", "", 2, isExtra = true)
        var clicked: Chapter? = null
        composeRule.setContent {
            BookDetailScreen(book, chapters, emptyList(), onChapterClick = { clicked = it }, extras = listOf(copyright))
        }

        composeRule.onNodeWithTag("book-info-toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("extra-ex-1").performScrollTo().performClick()

        assertEquals(copyright, clicked)
    }

    @Test
    fun `clicking a chapter reports it`() {
        var clicked: Chapter? = null
        composeRule.setContent {
            BookDetailScreen(book, chapters, emptyList(), onChapterClick = { clicked = it })
        }

        composeRule.onNodeWithText("Two").performScrollTo().performClick()

        assertEquals(chapters[1], clicked)
    }

    @Test
    fun `clicking resume reports it`() {
        var resumed = false
        composeRule.setContent {
            BookDetailScreen(
                book,
                chapters,
                emptyList(),
                onChapterClick = {},
                resumeLabel = "Resume",
                onResume = { resumed = true },
            )
        }

        composeRule.onNodeWithTag("resume-button").performClick()

        assertTrue(resumed)
    }

    @Test
    fun `book info stays open after leaving the screen and coming back`() {
        val copyright = Chapter(ChapterId("ex-1"), book.id, "Copyright", "", 2, isExtra = true)
        val state = BookDetailState()
        var shown by mutableStateOf(true)
        composeRule.setContent {
            if (shown) {
                BookDetailScreen(
                    book,
                    chapters,
                    emptyList(),
                    onChapterClick = {},
                    extras = listOf(copyright),
                    state = state,
                )
            }
        }

        composeRule.onNodeWithTag("book-info-toggle").performScrollTo().performClick()
        shown = false
        composeRule.waitForIdle()
        shown = true

        composeRule.onNodeWithTag("extra-ex-1").performScrollTo().assertExists()
    }

    @Test
    fun `bookmarks button appears with a count and opens them`() {
        var opened = false
        val bookmark = Bookmark(
            BookmarkId("b"),
            book.id,
            chapters[0].id,
            0f,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        composeRule.setContent {
            BookDetailScreen(book, chapters, listOf(bookmark), onChapterClick = {}, onOpenBookmarks = { opened = true })
        }

        composeRule.onNodeWithTag("bookmarks-button").performClick()

        assertTrue(opened)
    }
}
