package dev.reader.parser.fb2

import dev.reader.core.richtext.Block
import dev.reader.parser.ParsedChapter
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempFile
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun buildFb2(): File {
    val file = createTempFile(suffix = ".fb2").toFile()
    file.writeText(
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <FictionBook xmlns:l="http://www.w3.org/1999/xlink">
            <description>
                <title-info>
                    <book-title>My Book</book-title>
                    <author><first-name>Jane</first-name><last-name>Doe</last-name></author>
                    <coverpage><image l:href="#cover.jpg"/></coverpage>
                </title-info>
            </description>
            <body>
                <section>
                    <title><p>Chapter One</p></title>
                    <p>Hello world.</p>
                </section>
                <section>
                    <title><p>Chapter Two</p></title>
                    <p>Second chapter.</p>
                </section>
            </body>
            <binary id="cover.jpg" content-type="image/jpeg">aGVsbG8=</binary>
        </FictionBook>
        """.trimIndent(),
    )
    return file
}

class Fb2ParserTest {

    @Test
    fun `keeps embedded images and text in chapter order`() {
        val file = createTempFile(suffix = ".fb2").toFile()
        try {
            file.writeText(
                """<FictionBook><body><section><p>Before</p><image l:href="#art"/>
                <p>After</p></section></body><binary id="art">aGVsbG8=</binary></FictionBook>""",
            )
            val document = (Fb2Parser.parse(file).chapters.single() as ParsedChapter.TextChapter).document
            assertEquals(Block.Image("#art"), document.blocks[1])
            assertEquals(3, document.blocks.size)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `reads metadata and splits sections into chapters`() {
        val book = Fb2Parser.parse(buildFb2())

        assertEquals("My Book", book.title)
        assertEquals("Jane Doe", book.author)
        assertEquals("cover.jpg", book.coverPath)
        val chapters = book.chapters.map { it as ParsedChapter.TextChapter }
        assertEquals(listOf("Chapter One", "Chapter Two"), chapters.map { it.title })
        val paragraphs = chapters[0].document.blocks.filterIsInstance<Block.Paragraph>()
        assertEquals("Hello world.", paragraphs.first().spans.first().text)
    }

    @Test
    fun `parseChapter matches the full parse by index`() {
        val file = buildFb2()
        val expected = Fb2Parser.parse(file).chapters[1] as ParsedChapter.TextChapter
        assertEquals(expected, Fb2Parser.parseChapter(file, 1))
        assertNull(Fb2Parser.parseChapter(file, 5))
    }

    @Test
    fun `extracts the cover's base64 bytes`() {
        val bytes = Fb2Parser.extractCover(buildFb2(), "cover.jpg")
        assertEquals("hello", bytes?.let { String(it) })
    }

    @Test
    fun `reads zipped fb2 chapters and cover using the declared charset`() {
        val file = createTempFile(suffix = ".fb2.zip").toFile()
        try {
            val xml = """
                <?xml version="1.0" encoding="ISO-8859-1"?>
                <FictionBook><description><title-info><book-title>Café</book-title></title-info></description>
                <body><section><title><p>Chapter</p></title><p>Olá</p></section></body>
                <binary id="cover">aGVsbG8=</binary></FictionBook>
            """.trimIndent()
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("book/story.fb2"))
                zip.write(xml.toByteArray(Charset.forName("ISO-8859-1")))
                zip.closeEntry()
            }

            val book = Fb2Parser.parse(file)
            val chapter = book.chapters.single() as ParsedChapter.TextChapter
            assertEquals("Café", book.title)
            val paragraph = chapter.document.blocks.filterIsInstance<Block.Paragraph>().single()
            assertEquals("Olá", paragraph.spans.single().text)
            assertEquals(chapter, Fb2Parser.parseChapter(file, 0))
            assertEquals("hello", Fb2Parser.extractCover(file, "cover")?.let { String(it) })
        } finally {
            file.delete()
        }
    }
}
