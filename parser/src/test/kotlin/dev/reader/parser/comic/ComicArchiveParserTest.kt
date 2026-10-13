package dev.reader.parser.comic

import dev.reader.core.model.BookFormat
import dev.reader.parser.ParsedChapter
import org.junit.jupiter.api.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempFile
import kotlin.test.assertEquals

private fun buildCbz(): File {
    val file = createTempFile(suffix = ".cbz").toFile()
    ZipOutputStream(file.outputStream()).use { zip ->
        for (name in listOf("page10.jpg", "page2.jpg", "page1.jpg", "notes.txt")) {
            zip.putNextEntry(ZipEntry(name))
            zip.write("x".toByteArray())
            zip.closeEntry()
        }
    }
    return file
}

class ComicArchiveParserTest {

    @Test
    fun `lists image pages in natural order, ignoring non-image entries`() {
        val book = ComicArchiveParser.parse(buildCbz(), BookFormat.CBZ)

        val chapter = book.chapters.single() as ParsedChapter.ImageChapter
        assertEquals(listOf("page1.jpg", "page2.jpg", "page10.jpg"), chapter.pages)
        assertEquals("page1.jpg", book.coverPath)
    }
}
