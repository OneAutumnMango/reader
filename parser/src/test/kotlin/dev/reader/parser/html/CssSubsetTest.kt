package dev.reader.parser.html

import dev.reader.core.richtext.Block
import dev.reader.core.theme.Justification
import dev.reader.parser.ParsedChapter
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CssSubsetTest {

    private fun parse(html: String): List<Block> {
        val file: File = createTempFile(suffix = ".html").apply { writeText(html) }.toFile()
        return (HtmlParser.parse(file).chapters.single() as ParsedChapter.TextChapter).document.blocks
    }

    @Test
    fun `inline style colour overrides the theme`() {
        val blocks = parse("<html><body><p style=\"color:#ff0000\">Red text</p></body></html>")
        val paragraph = blocks.single() as Block.Paragraph
        assertEquals(0xFFFF0000L, paragraph.spans.single().color)
    }

    @Test
    fun `class rule from a style block applies`() {
        val html = """
            <html><head><style>.warn { color: blue; font-weight: bold; }</style></head>
            <body><p class="warn">Careful</p></body></html>
        """.trimIndent()
        val paragraph = parse(html).single() as Block.Paragraph
        val span = paragraph.spans.single()
        assertEquals(0xFF0000FFL, span.color)
        assertEquals(true, span.bold)
    }

    @Test
    fun `text-align justify is captured on the block`() {
        val blocks = parse("<html><body><p style=\"text-align: justify\">Text</p></body></html>")
        assertEquals(Justification.JUSTIFY, (blocks.single() as Block.Paragraph).textAlign)
    }

    @Test
    fun `a tag without any css has no colour override`() {
        val blocks = parse("<html><body><p><b>Bold</b> plain</p></body></html>")
        val paragraph = blocks.single() as Block.Paragraph
        assertNull(paragraph.spans.first().color)
        assertEquals(true, paragraph.spans.first().bold)
    }
}
