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

        val texts = document.blocks.filterIsInstance<Block.Paragraph>()
            .map { it.spans.joinToString("") { s -> s.text } }
        assertEquals(listOf("First paragraph.", "Second paragraph."), texts)
    }

    @Test
    fun `preserves bold and italic run formatting`() {
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
                        <w:p>
                            <w:r><w:t>Normal </w:t></w:r>
                            <w:r><w:rPr><w:b/></w:rPr><w:t>bold</w:t></w:r>
                            <w:r><w:rPr><w:i/></w:rPr><w:t> italic</w:t></w:r>
                        </w:p>
                        <w:p>
                            <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
                            <w:r><w:t>Chapter Title</w:t></w:r>
                        </w:p>
                    </w:body>
                </w:document>
                """.trimIndent(),
            )
            entry("docProps/core.xml", "<cp:coreProperties xmlns:dc=\"http://purl.org/dc/elements/1.1/\"/>")
        }

        val blocks = (DocxParser.parse(file).chapters.single() as ParsedChapter.TextChapter).document.blocks
        val para = blocks[0] as Block.Paragraph
        assertEquals(false, para.spans[0].bold)
        assertEquals(true, para.spans[1].bold)
        assertEquals(true, para.spans[2].italic)
        val heading = blocks[1] as Block.Heading
        assertEquals(1, heading.level)
        assertEquals("Chapter Title", heading.spans.single().text)
    }

    @Test
    fun `reads title and author from core properties`() {
        val book = DocxParser.parse(buildDocx(title = "My Doc", author = "Jane Doe"))

        assertEquals("My Doc", book.title)
        assertEquals("Jane Doe", book.author)
    }

    @Test
    fun `default docx import stays as one chapter unless the user applies detection`() {
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
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <w:body>
                        <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Chapter One</w:t></w:r></w:p>
                        <w:p><w:r><w:t>First chapter text.</w:t></w:r></w:p>
                        <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Chapter Two</w:t></w:r></w:p>
                        <w:p><w:r><w:t>Second chapter text.</w:t></w:r></w:p>
                    </w:body>
                </w:document>
                """.trimIndent(),
            )
            entry(
                "docProps/core.xml",
                """
                <cp:coreProperties xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>My Doc</dc:title>
                </cp:coreProperties>
                """.trimIndent(),
            )
        }

        val chapters = DocxParser.parse(file).chapters.map { it.title }

        assertEquals(listOf("My Doc"), chapters)
    }
}
