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

    @Test
    fun `splits into chapters with a short preamble as front matter`() {
        val file = createTempFile(suffix = ".txt").apply {
            writeText("Title\n\nChapter 1\n\nOne.\n\nChapter 2\n\nTwo.")
        }.toFile()

        val chapters = TxtParser.parse(file, ChapterSplitter.prefixPattern("Chapter"))
            .chapters.map { it as dev.reader.parser.ParsedChapter.TextChapter }

        assertEquals(listOf("Front matter", "Chapter 1", "Chapter 2"), chapters.map { it.title })
        assertEquals(listOf(true, false, false), chapters.map { it.isExtra })
        val heading = chapters[1].document.blocks.first() as Block.Heading
        assertEquals("Chapter 1", heading.spans.single().text)
    }
}
