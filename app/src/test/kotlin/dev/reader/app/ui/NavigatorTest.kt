package dev.reader.app.ui

import dev.reader.core.model.BookId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigatorTest {

    @Test
    fun `starts at the given destination with no back stack`() {
        val navigator = Navigator(Destination.Library)
        assertEquals(Destination.Library, navigator.current)
        assertFalse(navigator.canGoBack)
    }

    @Test
    fun `push adds to the stack and pop removes it`() {
        val navigator = Navigator()
        navigator.push(Destination.BookDetail(BookId("b1")))

        assertEquals(Destination.BookDetail(BookId("b1")), navigator.current)
        assertTrue(navigator.canGoBack)

        assertTrue(navigator.pop())
        assertEquals(Destination.Library, navigator.current)
        assertFalse(navigator.canGoBack)
    }

    @Test
    fun `pop on a single-item stack does nothing`() {
        val navigator = Navigator()
        assertFalse(navigator.pop())
        assertEquals(Destination.Library, navigator.current)
    }

    @Test
    fun `back from library and reader settings and logs returns to the library`() {
        val navigator = Navigator()
        navigator.switchRoot(Destination.LibrarySettings)
        assertEquals(Destination.LibrarySettings, navigator.current)
        navigator.switchRoot(Destination.LogHistory)

        assertTrue(navigator.handlesSystemBack)
        navigator.back()
        assertEquals(Destination.Library, navigator.current)
        navigator.switchRoot(Destination.ReaderSettings)
        navigator.back()
        assertEquals(Destination.Library, navigator.current)
        assertFalse(navigator.handlesSystemBack)
    }

    @Test
    fun `switchRoot clears everything pushed on top`() {
        val navigator = Navigator()
        navigator.push(Destination.BookDetail(BookId("b1")))
        navigator.push(Destination.Bookmarks(BookId("b1")))

        navigator.switchRoot(Destination.Search)

        assertEquals(Destination.Search, navigator.current)
        assertFalse(navigator.canGoBack)
    }
}
