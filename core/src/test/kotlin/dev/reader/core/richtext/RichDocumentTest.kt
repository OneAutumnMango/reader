package dev.reader.core.richtext

import kotlin.test.Test
import kotlin.test.assertEquals

class RichDocumentTest {

    @Test
    fun `counts words across paragraphs, headings and list items, ignoring images`() {
        val document = RichDocument(
            listOf(
                Block.Heading(1, listOf(Span("Chapter One"))),
                Block.Paragraph(listOf(Span("The quick brown fox"))),
                Block.ListItem(listOf(Span("jumps over")), ordered = false, index = 0),
                Block.Image("cover.jpg"),
            ),
        )

        assertEquals(8, document.wordCount())
    }

    @Test
    fun `an empty document has zero words`() {
        assertEquals(0, RichDocument(emptyList()).wordCount())
    }
}
