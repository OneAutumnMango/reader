package dev.reader.parser.markdown

import dev.reader.core.richtext.Block
import dev.reader.parser.ParsedChapter
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownParserTest {

    @Test
    fun `converts headings, emphasis and lists`() {
        val file = createTempFile(suffix = ".md").apply {
            writeText(
                """
                # Title

                Plain and **bold** and *italic*.

                - One
                - Two
                """.trimIndent(),
            )
        }.toFile()

        val document = (MarkdownParser.parse(file).chapters.single() as ParsedChapter.TextChapter).document

        val heading = document.blocks[0] as Block.Heading
        assertEquals(1, heading.level)

        val paragraph = document.blocks[1] as Block.Paragraph
        assertTrue(paragraph.spans.any { it.bold && it.text == "bold" })
        assertTrue(paragraph.spans.any { it.italic && it.text == "italic" })

        val items = document.blocks.filterIsInstance<Block.ListItem>()
        assertEquals(listOf("One", "Two"), items.map { it.spans.single().text })
    }
}
