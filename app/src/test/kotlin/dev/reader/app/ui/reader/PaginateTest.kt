package dev.reader.app.ui.reader

import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.Span
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PaginateTest {

    @Test
    fun `groups blocks into fixed-size pages`() {
        val blocks = (1..13).map { Block.Paragraph(listOf(Span("p$it"))) }
        val document = RichDocument(blocks)

        val pages = document.paginate(blocksPerPage = 5)

        assertEquals(3, pages.size)
        assertEquals(5, pages[0].size)
        assertEquals(5, pages[1].size)
        assertEquals(3, pages[2].size)
    }

    @Test
    fun `an empty document still produces one empty page`() {
        assertEquals(listOf(emptyList()), RichDocument(emptyList()).paginate())
    }
}
