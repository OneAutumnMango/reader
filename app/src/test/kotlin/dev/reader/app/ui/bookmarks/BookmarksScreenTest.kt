package dev.reader.app.ui.bookmarks

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.reader.core.model.BookId
import dev.reader.core.model.Bookmark
import dev.reader.core.model.BookmarkId
import dev.reader.core.model.ChapterId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class BookmarksScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val bookmark = Bookmark(
        id = BookmarkId("bm-1"),
        bookId = BookId("book-1"),
        chapterId = ChapterId("ch-1"),
        position = 0.25f,
        note = "Great scene",
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    @Test
    fun `clicking a bookmark jumps to it`() {
        var jumped: Bookmark? = null
        composeRule.setContent {
            BookmarksScreen(listOf(bookmark), onJump = { jumped = it }, onDelete = {})
        }

        composeRule.onNodeWithText("Great scene").performClick()

        assertEquals(bookmark, jumped)
    }

    @Test
    fun `deleting a bookmark reports it`() {
        var deleted: Bookmark? = null
        composeRule.setContent {
            BookmarksScreen(listOf(bookmark), onJump = {}, onDelete = { deleted = it })
        }

        composeRule.onNodeWithContentDescription("Remove bookmark").performClick()

        assertEquals(bookmark, deleted)
    }
}
