package dev.reader.parser.html

import dev.reader.core.richtext.Block
import dev.reader.core.richtext.Span
import dev.reader.parser.ParsedChapter
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HtmlParserTest {

    @Test
    fun `keeps nested images between styled text blocks`() {
        val document = parseHtmlToRichDocument(
            """<p><b>Before<img src="images/map.png" alt="Map">After</b></p>""",
        )

        assertEquals(Block.Paragraph(listOf(Span("Before", bold = true))), document.blocks[0])
        assertEquals(Block.Image("images/map.png", "Map"), document.blocks[1])
        assertEquals(Block.Paragraph(listOf(Span("After", bold = true))), document.blocks[2])
    }

    @Test
    fun `keeps image only paragraphs and svg image references`() {
        val document = parseHtmlToRichDocument(
            """<p><img src="art.png"></p><svg><image xlink:href="cover.jpg" /></svg>""",
        )
        assertEquals(listOf(Block.Image("art.png"), Block.Image("cover.jpg")), document.blocks)
    }

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
