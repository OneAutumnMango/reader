package dev.reader.parser.docx

import dev.reader.core.richtext.Block
import dev.reader.parser.ParsedChapter
import org.junit.jupiter.api.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempFile
import kotlin.test.assertEquals

private fun buildDocx(title: String = "", author: String = ""): File {
    val file = createTempFile(suffix = ".docx").toFile()
    ZipOutputStream(file.outputStream()).use { zip ->
        fun entry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray())
            zip.closeEntry()
        }
        entry(
            "word/document.xml",
            """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p><w:r><w:t>First</w:t></w:r><w:r><w:t> paragraph.</w:t></w:r></w:p>
                    <w:p><w:r><w:t>Second paragraph.</w:t></w:r></w:p>
                    <w:p></w:p>
                </w:body>
            </w:document>
            """.trimIndent(),
        )
        entry(
            "docProps/core.xml",
            """
            <cp:coreProperties xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>$title</dc:title>
                <dc:creator>$author</dc:creator>
            </cp:coreProperties>
            """.trimIndent(),
        )
    }
    return file
}

class DocxParserTest {

    @Test
    fun `joins runs within a paragraph and skips empty ones`() {
        val document = (DocxParser.parse(buildDocx()).chapters.single() as ParsedChapter.TextChapter).document

        assertEquals(
            listOf("First paragraph.", "Second paragraph."),
            document.blocks.map { (it as Block.Paragraph).spans.single().text },
        )
    }

    @Test
    fun `reads title and author from core properties`() {
        val book = DocxParser.parse(buildDocx(title = "My Doc", author = "Jane Doe"))

        assertEquals("My Doc", book.title)
        assertEquals("Jane Doe", book.author)
    }
}
