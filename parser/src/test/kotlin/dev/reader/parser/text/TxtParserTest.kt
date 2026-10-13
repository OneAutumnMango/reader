package dev.reader.parser.text

import dev.reader.core.richtext.Block
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.assertEquals

class TxtParserTest {

    @Test
    fun `splits blank-line separated paragraphs`() {
        val file = createTempFile(suffix = ".txt").apply {
            writeText("First paragraph.\n\nSecond paragraph,\nstill second.\n\nThird.")
        }.toFile()

        val book = TxtParser.parse(file)

        val document = (book.chapters.single() as dev.reader.parser.ParsedChapter.TextChapter).document
        assertEquals(3, document.blocks.size)
        val first = document.blocks[0] as Block.Paragraph
        assertEquals("First paragraph.", first.spans.single().text)
        val second = document.blocks[1] as Block.Paragraph
        assertEquals("Second paragraph,\nstill second.", second.spans.single().text)
    }
}
