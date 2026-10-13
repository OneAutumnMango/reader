package dev.reader.parser.html

import dev.reader.core.richtext.Block
import dev.reader.parser.ParsedChapter
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HtmlParserTest {

    @Test
    fun `converts headings, paragraphs and inline formatting`() {
        val file = createTempFile(suffix = ".html").apply {
            writeText(
                """
                <html><body>
                <h1>Title</h1>
                <p>Plain and <b>bold</b> and <i>italic</i>.</p>
                <ul><li>One</li><li>Two</li></ul>
                </body></html>
                """.trimIndent(),
            )
        }.toFile()

        val document = (HtmlParser.parse(file).chapters.single() as ParsedChapter.TextChapter).document

        val heading = document.blocks[0] as Block.Heading
        assertEquals(1, heading.level)
        assertEquals("Title", heading.spans.single().text)

        val paragraph = document.blocks[1] as Block.Paragraph
        assertTrue(paragraph.spans.any { it.bold && it.text == "bold" })
        assertTrue(paragraph.spans.any { it.italic && it.text == "italic" })

        val items = document.blocks.filterIsInstance<Block.ListItem>()
        assertEquals(listOf("One", "Two"), items.map { it.spans.single().text })
    }
}
