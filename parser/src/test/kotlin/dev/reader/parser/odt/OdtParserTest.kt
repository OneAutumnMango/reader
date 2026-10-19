package dev.reader.parser.odt

import dev.reader.core.model.BookFormat
import dev.reader.core.richtext.Block
import dev.reader.parser.ParsedChapter
import org.junit.jupiter.api.Test
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempFile
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OdtParserTest {

    @Test
    fun `reads zipped content styles metadata images lists and tables in order`() {
        val file = createTempFile(suffix = ".odt").toFile()
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.entry("content.xml", zippedContent)
                zip.entry("styles.xml", commonStyles)
                zip.entry("meta.xml", metadata)
                zip.putNextEntry(ZipEntry("Pictures/pixel.png"))
                zip.write("image".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("../outside.png"))
                zip.write("outside".toByteArray())
                zip.closeEntry()
            }

            val book = OdtParser.parse(file)
            val document = (book.chapters.single() as ParsedChapter.TextChapter).document

            assertEquals("ODT title", book.title)
            assertEquals("A. Author", book.author)
            assertEquals(BookFormat.ODT, book.format)
            assertEquals("Chapter", (document.blocks.first() as Block.Heading).spans.single().text)
            val styledText = document.blocks.filterIsInstance<Block.Paragraph>()
                .flatMap { it.spans }.single { it.text.contains("inherited") }
            assertTrue(styledText.bold)
            assertTrue(styledText.italic)
            val paragraphTexts = document.blocks.filterIsInstance<Block.Paragraph>()
                .map { paragraph -> paragraph.spans.joinToString("") { it.text } }
            assertTrue(paragraphTexts.any { it.contains("spaces tab line") })
            assertTrue(document.blocks.any { it == Block.Image("data:image/png;base64,aW1hZ2U=") })
            assertEquals(1, document.blocks.filterIsInstance<Block.Image>().size)
            assertIs<Block.ListItem>(document.blocks[document.blocks.indexOfFirst { it is Block.ListItem }])
            val tableText = document.blocks.filterIsInstance<Block.Paragraph>().flatMap { it.spans }
                .map { it.text }.filter { it.startsWith("Cell") }
            assertEquals(listOf("Cell A", "Cell B"), tableText)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `reads flat odt xml formatting headings embedded images and metadata`() {
        val file = createTempFile(suffix = ".fodt").toFile()
        try {
            file.writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <office:document xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                    xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
                    xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
                    xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
                    xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
                    xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <office:meta><dc:title>Flat title</dc:title><dc:creator>Flat author</dc:creator></office:meta>
                    <office:automatic-styles><style:style style:name="Bold" style:family="text">
                        <style:text-properties fo:font-weight="bold"/>
                    </style:style></office:automatic-styles>
                    <office:body><office:text>
                        <text:h text:outline-level="2"><text:span text:style-name="Bold">Flat heading</text:span></text:h>
                        <text:p>left<text:s text:c="2"/>right<draw:frame><draw:image draw:mime-type="image/png">
                            <office:binary-data>aGVsbG8=</office:binary-data>
                        </draw:image></draw:frame></text:p>
                    </office:text></office:body>
                </office:document>
                """.trimIndent(),
            )

            val book = OdtParser.parse(file)
            val document = (book.chapters.single() as ParsedChapter.TextChapter).document
            val heading = assertIs<Block.Heading>(document.blocks.first())

            assertEquals("Flat title", book.title)
            assertEquals("Flat author", book.author)
            assertEquals(2, heading.level)
            assertTrue(heading.spans.single().bold)
            assertTrue(
                document.blocks.filterIsInstance<Block.Paragraph>().any { paragraph ->
                    val text = paragraph.spans.joinToString("") { it.text }
                    text.contains("left") && text.contains("right")
                },
            )
            assertTrue(document.blocks.any { it == Block.Image("data:image/png;base64,aGVsbG8=") })
            assertEquals(1, document.blocks.filterIsInstance<Block.Image>().size)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `rejects documents without office text`() {
        val file = createTempFile(suffix = ".fodt").toFile()
        try {
            file.writeText("<office:document><office:body/></office:document>")
            val error = kotlin.test.assertFailsWith<IllegalStateException> { OdtParser.parse(file) }
            assertTrue(error.message.orEmpty().contains("office:body/office:text"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `rejects encrypted archive entries with a clear error`() {
        val file = createTempFile(suffix = ".odt").toFile()
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.entry("content.xml", "<office:document><office:body><office:text/></office:body></office:document>")
                zip.entry(
                    "META-INF/manifest.xml",
                    """
                    <manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0">
                        <manifest:file-entry manifest:full-path="content.xml">
                            <manifest:encryption-data/>
                        </manifest:file-entry>
                    </manifest:manifest>
                    """.trimIndent(),
                )
            }

            val error = kotlin.test.assertFailsWith<IllegalArgumentException> { OdtParser.parse(file) }
            assertTrue(error.message.orEmpty().contains("Encrypted ODT entries are not supported"))
        } finally {
            file.delete()
        }
    }

    private fun ZipOutputStream.entry(name: String, contents: String) {
        putNextEntry(ZipEntry(name))
        write(contents.toByteArray())
        closeEntry()
    }

    private companion object {
        val metadata = """
            <office:document-meta xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                xmlns:dc="http://purl.org/dc/elements/1.1/">
                <office:meta><dc:title>ODT title</dc:title><dc:creator>A. Author</dc:creator></office:meta>
            </office:document-meta>
        """.trimIndent()

        val commonStyles = """
            <office:document-styles xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
                xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0">
                <office:styles><style:style style:name="Emphasis" style:family="text">
                    <style:text-properties fo:font-style="italic"/>
                </style:style></office:styles>
            </office:document-styles>
        """.trimIndent()

        val zippedContent = """
            <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
                xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
                xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
                xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
                xmlns:xlink="http://www.w3.org/1999/xlink">
                <office:automatic-styles>
                    <style:style style:name="Inherited" style:family="text" style:parent-style-name="Emphasis">
                        <style:text-properties fo:font-weight="bold"/>
                    </style:style>
                </office:automatic-styles>
                <office:body><office:text>
                    <text:h text:outline-level="1">Chapter</text:h>
                    <text:p>Styled <text:span text:style-name="Inherited">inherited</text:span> end<text:s text:c="3"/>
                        spaces<text:tab/>tab<text:line-break/>line
                        <draw:frame><draw:image xlink:href="Pictures/pixel%2Epng" draw:mime-type="image/png"/></draw:frame>
                        <draw:frame><draw:image xlink:href="../outside.png" draw:mime-type="image/png"/></draw:frame>
                    </text:p>
                    <text:list><text:list-item><text:p>List item</text:p></text:list-item></text:list>
                    <table:table xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0">
                        <table:table-row><table:table-cell><text:p>Cell A</text:p></table:table-cell>
                            <table:table-cell><text:p>Cell B</text:p></table:table-cell></table:table-row>
                    </table:table>
                </office:text></office:body>
            </office:document-content>
        """.trimIndent()
    }
}
