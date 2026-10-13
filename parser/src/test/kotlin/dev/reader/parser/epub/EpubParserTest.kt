package dev.reader.parser.epub

import dev.reader.core.richtext.Block
import dev.reader.parser.ParsedChapter
import org.junit.jupiter.api.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempFile
import kotlin.test.assertEquals

private fun buildEpub(): File {
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
                    <item id="cover" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                </manifest>
                <spine>
                    <itemref idref="ch1"/>
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
}
