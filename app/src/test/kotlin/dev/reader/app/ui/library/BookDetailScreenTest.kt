package dev.reader.app.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.model.Bookmark
import dev.reader.core.model.BookmarkId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.core.model.Progress
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val LONG_PRESS_MILLIS = 600L

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
    fun `chapters are numbered by their place in the book, not counting book info`() {
        val cover = Chapter(ChapterId("cover"), book.id, "Cover", "", 0, isExtra = true)
        val shifted = chapters.map { it.copy(orderIndex = it.orderIndex + 1) }
        composeRule.setContent {
            BookDetailScreen(book, shifted, emptyList(), onChapterClick = {}, extras = listOf(cover))
        }

        composeRule.onNodeWithTag("chapter-ch-1").assert(hasText("1"))
        composeRule.onNodeWithTag("chapter-ch-2").assert(hasText("2"))
    }

    @Test
    fun `sorting last to first reverses the list but keeps numbers`() {
        composeRule.setContent { BookDetailScreen(book, chapters, emptyList(), onChapterClick = {}) }

        composeRule.onNodeWithTag("chapter-sort").performClick()
        composeRule.onNodeWithTag("chapter-sort-DESCENDING").performClick()

        val first = composeRule.onNodeWithTag("chapter-ch-1").getUnclippedBoundsInRoot()
        val second = composeRule.onNodeWithTag("chapter-ch-2").getUnclippedBoundsInRoot()
        assertTrue(second.top < first.top)
        composeRule.onNodeWithTag("chapter-ch-2").assert(hasText("2"))
    }

    @Test
    fun `only finished chapters count as read and partial ones show their progress`() {
        val progress = mapOf(
            chapters[0].id to Progress(book.id, chapters[0].id, 1f, Instant.parse("2026-01-01T00:00:00Z"), true),
            chapters[1].id to Progress(book.id, chapters[1].id, 0.4f, Instant.parse("2026-01-01T00:00:00Z")),
        )
        composeRule.setContent {
            BookDetailScreen(book, chapters, emptyList(), onChapterClick = {}, chapterProgress = progress)
        }

        composeRule.onNodeWithText("1 / 2 read", substring = true).assertExists()
        composeRule.onNodeWithTag("chapter-ch-1", useUnmergedTree = true)
            .onChildren().filter(hasContentDescription("Read")).assertCountEquals(1)
        composeRule.onNodeWithText("40% read").assertExists()
    }

    @Test
    fun `long-pressing starts selection, taps add to it, and actions mark the chosen chapters`() {
        var change: Pair<List<Chapter>, Boolean>? = null
        composeRule.setContent {
            BookDetailScreen(book, chapters, emptyList(), onChapterClick = {}, onSetRead = { c, r -> change = c to r })
        }

        composeRule.onNodeWithTag("chapter-ch-2").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MILLIS)
            up()
        }
        composeRule.onNodeWithText("1 selected").assertExists()
        composeRule.onNodeWithTag("chapter-ch-1").performClick()
        composeRule.onNodeWithText("2 selected").assertExists()
        composeRule.onNodeWithTag("chapter-selection-MARK_READ").performClick()

        assertEquals(chapters to true, change)
        composeRule.onNodeWithTag("chapter-selection-bar").assertDoesNotExist()
    }

    @Test
    fun `select all, invert and close change only the selection`() {
        var marked = false
        composeRule.setContent {
            BookDetailScreen(book, chapters, emptyList(), onChapterClick = {}, onSetRead = { _, _ -> marked = true })
        }

        composeRule.onNodeWithTag("chapter-ch-1").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MILLIS)
            up()
        }
        composeRule.onNodeWithTag("chapter-selection-SELECT_ALL").performClick()
        composeRule.onNodeWithText("2 selected").assertExists()
        composeRule.onNodeWithTag("chapter-selection-INVERT").performClick()
        composeRule.onNodeWithTag("chapter-selection-bar").assertDoesNotExist()
        assertFalse(marked)
    }

    @Test
    fun `range actions cover everything up to or from the selection`() {
        val five = (1..5).map { Chapter(ChapterId("c$it"), book.id, "C$it", "", it) }

        val (c2, c3, c4) = listOf("c2", "c3", "c4").map(::ChapterId)
        assertEquals(five.take(3), ChapterSelectionAction.READ_UP_TO.targets(five, setOf(c2, c3)))
        assertEquals(five.drop(1), ChapterSelectionAction.UNREAD_FROM.targets(five, setOf(c2, c4)))
        assertEquals(listOf(five[3]), ChapterSelectionAction.MARK_UNREAD.targets(five, setOf(ChapterId("c4"))))
        assertNull(ChapterSelectionAction.INVERT.targets(five, emptySet()))
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
