package dev.reader.app.ui.reader

import dev.reader.core.model.BookId
import dev.reader.core.model.ChapterId
import dev.reader.core.model.Progress
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class OpenedProgressTest {

    private val book = BookId("book")
    private val last = ChapterId("last")
    private val before = Instant.fromEpochSeconds(1)
    private val now = Instant.fromEpochSeconds(2)
    private val finished = Progress(book, last, position = 0.75f, updatedAt = before, finished = true)

    @Test
    fun `reopening a finished chapter keeps it finished at the same position`() {
        assertEquals(finished.copy(updatedAt = now), openedProgress(finished, book, last, now))
    }

    @Test
    fun `opening a different chapter starts it unfinished at the top`() {
        val other = ChapterId("other")
        assertEquals(Progress(book, other, position = 0f, updatedAt = now), openedProgress(finished, book, other, now))
    }

    @Test
    fun `first open starts unfinished at the top`() {
        assertEquals(Progress(book, last, position = 0f, updatedAt = now), openedProgress(null, book, last, now))
    }
}
