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

private fun buildEpub(
    includeMissingChapter: Boolean = false,
    chapterHtml: String = "<html><body><h1>Chapter One</h1><p>Hello world.</p></body></html>",
): File {
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
        entry("OEBPS/chapter1.xhtml", chapterHtml)
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
    fun `uses svg image instead of its html fallback and preserves repeated images`() {
        val file = buildEpub(
            chapterHtml = """
                <html><body><h1>Chapter One</h1><figure>
                    <svg><switch>
                        <image href="images/diagram.png"/>
                        <foreignObject><img src="images/diagram-fallback.png"/></foreignObject>
                    </switch></svg>
                </figure>
                <img src="images/repeated.png"/><img src="images/repeated.png"/>
                </body></html>
            """.trimIndent(),
        )

        val chapter = EpubParser.parse(file).chapters.single() as ParsedChapter.TextChapter

        assertEquals(
            listOf(
                Block.Image("OEBPS/images/diagram.png"),
                Block.Image("OEBPS/images/repeated.png"),
                Block.Image("OEBPS/images/repeated.png"),
            ),
            chapter.document.blocks.filterIsInstance<Block.Image>(),
        )
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

    @Test
    fun `separates front matter from real chapters and uses toc titles`() {
        val file = createTempFile(suffix = ".epub").toFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }

            entry(
                "META-INF/container.xml",
                """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""",
            )
            entry(
                "OEBPS/content.opf",
                """
                <package>
                    <metadata><title>Fronted</title></metadata>
                    <manifest>
                        <item id="title" href="Text/part0000.xhtml"/>
                        <item id="copy" href="Text/copyright.xhtml"/>
                        <item id="nav" href="nav.xhtml" properties="nav"/>
                        <item id="ch1" href="Text/one.xhtml"/>
                        <item id="ch2" href="Text/two.xhtml"/>
                    </manifest>
                    <spine>
                        <itemref idref="title"/><itemref idref="copy"/><itemref idref="nav"/>
                        <itemref idref="ch1"/><itemref idref="ch2"/>
                    </spine>
                </package>
                """.trimIndent(),
            )
            entry(
                "OEBPS/nav.xhtml",
                """
                <html><body><nav epub:type="toc"><ol>
                    <li><a href="Text/one.xhtml">The Beginning</a></li>
                    <li><a href="Text/two.xhtml#start">The End</a></li>
                </ol></nav></body></html>
                """.trimIndent(),
            )
            entry("OEBPS/Text/part0000.xhtml", "<html><body><h1>Fronted</h1><p>by Jane writing as J.</p></body></html>")
            entry("OEBPS/Text/copyright.xhtml", "<html><body><p>All rights reserved.</p></body></html>")
            entry("OEBPS/Text/one.xhtml", "<html><body><p>Once upon a time.</p></body></html>")
            entry("OEBPS/Text/two.xhtml", "<html><body><h2>Two</h2><p>The end.</p></body></html>")
        }

        val chapters = EpubParser.parse(file).chapters.map { it as ParsedChapter.TextChapter }

        assertEquals(
            listOf("Title page", "Copyright", "Contents", "The Beginning", "The End"),
            chapters.map { it.title },
        )
        assertEquals(listOf(true, true, true, false, false), chapters.map { it.isExtra })
    }
}
