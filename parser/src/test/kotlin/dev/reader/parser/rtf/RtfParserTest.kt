package dev.reader.parser.rtf

import dev.reader.core.richtext.Block
import dev.reader.parser.ParsedChapter
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.test.assertEquals

class RtfParserTest {

    private fun rtf(body: String): File {
        val file = createTempFile(suffix = ".rtf").toFile()
        file.writeText(body)
        return file
    }

    @Test
    fun `strips control words and keeps paragraph breaks`() {
        val file = rtf(
            """{\rtf1\ansi{\fonttbl{\f0 Times New Roman;}}\f0 Hello \b world\b0 !\par\par Second paragraph.}""",
        )

        val document = (RtfParser.parse(file).chapters.single() as ParsedChapter.TextChapter).document
        val texts = document.blocks.map { (it as Block.Paragraph).spans.single().text }

        assertEquals(listOf("Hello world!", "Second paragraph."), texts)
    }

    @Test
    fun `decodes hex escapes and skips the unicode fallback byte`() {
        val file = rtf("""{\rtf1 Caf\'e9 \u233?!}""")

        val text = (RtfParser.parse(file).chapters.single() as ParsedChapter.TextChapter)
            .document.blocks.single().let { (it as Block.Paragraph).spans.single().text }

        assertEquals("Caf\u00e9 \u00e9!", text)
    }
}
