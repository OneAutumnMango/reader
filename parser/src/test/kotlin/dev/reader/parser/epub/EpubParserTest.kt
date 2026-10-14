package dev.reader.parser.epub

import dev.reader.core.richtext.Block
import dev.reader.parser.ParsedChapter
import org.junit.jupiter.api.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempFile
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun buildEpub(includeMissingChapter: Boolean = false): File {
    val file = createTempFile(suffix = ".epub").toFile()
    ZipOutputStream(file.outputStream()).use { zip ->
        fun entry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray())
            zip.closeEntry()
        }

        entry(
            "META-INF/container.xml",
            """
            <?xml version="1.0"?>
            <container><rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
            </rootfiles></container>
            """.trimIndent(),
        )
        entry(
            "OEBPS/content.opf",
            """
            <?xml version="1.0"?>
            <package>
                <metadata><title>My Book</title><creator>Jane Doe</creator></metadata>
                <manifest>
                    <item id="ch1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                    ${if (includeMissingChapter) "<item id=\"ch2\" href=\"missing.xhtml\"/>" else ""}
                    <item id="cover" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                </manifest>
                <spine>
                    <itemref idref="ch1"/>
                    ${if (includeMissingChapter) "<itemref idref=\"ch2\"/>" else ""}
                </spine>
            </package>
            """.trimIndent(),
        )
        entry("OEBPS/chapter1.xhtml", "<html><body><h1>Chapter One</h1><p>Hello world.</p></body></html>")
        entry("OEBPS/cover.jpg", "fake-jpeg-bytes")
    }
    return file
}

class EpubParserTest {

    @Test
    fun `loads only the requested chapter without reading other spine entries`() {
        val chapter = EpubParser.parseChapter(buildEpub(includeMissingChapter = true), 0)
        assertEquals("Chapter One", chapter?.title)
        val paragraph = chapter?.document?.blocks?.filterIsInstance<Block.Paragraph>()?.single()
        assertEquals("Hello world.", paragraph?.spans?.single()?.text)
    }

    @Test
    fun `chapter loader matches full parse and returns null outside the spine`() {
        val file = buildEpub()
        val expected = EpubParser.parse(file).chapters.single() as ParsedChapter.TextChapter
        assertEquals(expected, EpubParser.parseChapter(file, 0))
        assertNull(EpubParser.parseChapter(file, -1))
        assertNull(EpubParser.parseChapter(file, 1))
    }

    @Test
    fun `reads metadata, cover and spine order`() {
        val book = EpubParser.parse(buildEpub())

        assertEquals("My Book", book.title)
        assertEquals("Jane Doe", book.author)
        assertEquals("OEBPS/cover.jpg", book.coverPath)

        val chapter = book.chapters.single() as ParsedChapter.TextChapter
        assertEquals("Chapter One", chapter.title)
        val paragraph = chapter.document.blocks.filterIsInstance<Block.Paragraph>().single()
        assertEquals("Hello world.", paragraph.spans.single().text)
    }

    @Test
    fun `decodes percent-encoded hrefs so spine entries with spaces resolve`() {
        val file = createTempFile(suffix = ".epub").toFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }

            entry(
                "META-INF/container.xml",
                """
                <?xml version="1.0"?>
                <container><rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles></container>
                """.trimIndent(),
            )
            entry(
                "OEBPS/content.opf",
                """
                <?xml version="1.0"?>
                <package>
                    <metadata><title>Spaced</title></metadata>
                    <manifest>
                        <item id="ch1" href="Chapter%20One.xhtml" media-type="application/xhtml+xml"/>
                    </manifest>
                    <spine><itemref idref="ch1"/></spine>
                </package>
                """.trimIndent(),
            )
            entry("OEBPS/Chapter One.xhtml", "<html><body><p>Found it.</p></body></html>")
        }

        val book = EpubParser.parse(file)

        val chapter = book.chapters.single() as ParsedChapter.TextChapter
        val paragraph = chapter.document.blocks.filterIsInstance<Block.Paragraph>().single()
        assertEquals("Found it.", paragraph.spans.single().text)
        assertEquals(chapter, EpubParser.parseChapter(file, 0))
    }
}
