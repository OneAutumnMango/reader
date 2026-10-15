package dev.reader.app.ui.library

import android.net.Uri
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.reader.app.data.BookLocation
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.core.model.Progress
import dev.reader.core.stats.ReadingSessionEvent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class BookInfoScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val at = Instant.parse("2026-01-01T10:00:00Z")
    private val book = Book(BookId("b"), "Dune", "Herbert", format = BookFormat.EPUB, sourcePath = "/x", addedAt = at)
    private val chapters = listOf(
        Chapter(ChapterId("cover"), book.id, "Cover", "", 0, wordCount = 10, isExtra = true),
        Chapter(ChapterId("c1"), book.id, "One", "", 1, wordCount = 1_200),
        Chapter(ChapterId("c2"), book.id, "Two", "", 2, wordCount = 800),
    )
    private val progress = mapOf(ChapterId("c1") to Progress(book.id, ChapterId("c1"), 1f, at, finished = true))
    private val sessions = listOf(
        ReadingSessionEvent(book.id, ChapterId("c1"), at, Instant.parse("2026-01-01T10:30:00Z"), 1_200, 5),
    )

    private fun setContent(location: BookLocation?, onOpenFolder: () -> Unit = {}) = composeRule.setContent {
        BookInfoScreen(book, chapters, progress, sessions, location = location, onOpenFolder = onOpenFolder)
    }

    @Test
    fun `shows word counts, reading stats and progress`() {
        setContent(location = null)

        composeRule.onNodeWithTag("book-info-words").assertExists()
        composeRule.onNodeWithText("2,010").assertExists()
        composeRule.onNodeWithText("2 (+1 book info)").assertExists()
        composeRule.onNodeWithText("1 of 2").performScrollTo().assertExists()
        composeRule.onNodeWithText("30m").performScrollTo().assertExists()
        composeRule.onNodeWithText("50%").performScrollTo().assertExists()
    }

    @Test
    fun `the per-chapter breakdown stays tucked away until asked for`() {
        setContent(location = null)

        composeRule.onNodeWithTag("book-info-chapter-c1").assertDoesNotExist()
        composeRule.onNodeWithTag("book-info-breakdown").performScrollTo().performClick()
        composeRule.onNodeWithTag("book-info-chapter-c1").performScrollTo().assertExists()
        composeRule.onNodeWithText("800").assertExists()
    }

    @Test
    fun `location shows where the book is and opens its folder when known`() {
        var opened = false
        val uri = Uri.parse("content://x/document/primary%3ABooks%2FDune.epub")
        setContent(BookLocation("Internal storage/Books/Dune.epub", uri, folder = uri)) { opened = true }

        composeRule.onNodeWithText("Internal storage/Books/Dune.epub").performScrollTo().assertExists()
        composeRule.onNodeWithTag("book-open-folder").performScrollTo().performClick()
        assertTrue(opened)
    }

    @Test
    fun `no folder button when the folder can't be worked out`() {
        val uri = Uri.parse("content://drive/document/1")
        setContent(BookLocation("Google Drive \u00b7 1", uri, folder = null))

        composeRule.onNodeWithTag("book-open-item").performScrollTo().assertExists()
        composeRule.onNodeWithTag("book-open-folder").assertDoesNotExist()
    }
}
