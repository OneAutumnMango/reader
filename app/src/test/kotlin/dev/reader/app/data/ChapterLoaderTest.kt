package dev.reader.app.data

import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.core.reader.PdfRenderMode
import dev.reader.parser.chapterDetectionText
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Clock

@RunWith(RobolectricTestRunner::class)
class ChapterLoaderTest {

    private val tempDir = File.createTempFile("chapter-loader", "").apply {
        delete()
        mkdirs()
    }

    @org.junit.After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `PDF defaults to original pages without extracting text`() = runBlocking {
        val book = Book(
            id = BookId("pdf"),
            title = "PDF",
            format = BookFormat.PDF,
            sourcePath = "/unused.pdf",
            addedAt = Clock.System.now(),
        )
        val chapter = Chapter(ChapterId("chapter"), book.id, "PDF", book.sourcePath, 0)
        for (mode in listOf(null, PdfRenderMode.FIXED_PAGE)) {
            val content = ChapterLoader.load(
                RuntimeEnvironment.getApplication(),
                book.copy(pdfRenderMode = mode),
                chapter,
            )
            assertEquals(File(book.sourcePath), assertIs<ChapterContent.Pdf>(content).file)
        }
    }

    @Test
    fun `custom rich text splits load the selected document`() = runBlocking {
        val files = listOf(
            File(tempDir, "story.html").apply {
                writeText("<html><body><p>Chapter 1</p><p>One.</p><p>Chapter 2</p><p>Two.</p></body></html>")
            },
            File(tempDir, "story.rtf").apply {
                writeText("{\\rtf1\\ansi Chapter 1\\par\\par One.\\par\\par Chapter 2\\par\\par Two.}")
            },
            createDocx(),
        )

        files.forEach { file ->
            val format = requireNotNull(BookFormat.fromFileName(file.name))
            val book = Book(
                id = BookId(file.name),
                title = "Story",
                format = format,
                sourcePath = file.path,
                chapterPattern = "^Chapter",
                addedAt = Clock.System.now(),
            )
            val chapter = Chapter(ChapterId("second"), book.id, "Chapter 2", file.path, 1)

            val content = assertIs<ChapterContent.Text>(
                ChapterLoader.load(RuntimeEnvironment.getApplication(), book, chapter),
            )

            assertEquals("Chapter 2\n\nTwo.", content.document.chapterDetectionText())
        }
    }

    @Test
    fun `light novel chapter loading uses each source file format and keeps all its blocks`() = runBlocking {
        val html = File(tempDir, "chapter.html").apply {
            writeText("<html><body><h1>Part One</h1><p>First.</p><h1>Part Two</h1><p>Second.</p></body></html>")
        }
        val book = Book(
            id = BookId("folder"),
            title = "Folder",
            format = BookFormat.TXT,
            sourcePath = tempDir.path,
            isLightNovel = true,
            addedAt = Clock.System.now(),
        )
        val chapter = Chapter(ChapterId("html-file"), book.id, "chapter", html.path, 0)

        val content = assertNotNull(ChapterLoader.load(RuntimeEnvironment.getApplication(), book, chapter))

        assertEquals(
            "Part One\n\nFirst.\n\nPart Two\n\nSecond.",
            assertIs<ChapterContent.Text>(content).document.chapterDetectionText(),
        )
    }

    private fun createDocx(): File = File(tempDir, "story.docx").apply {
        java.util.zip.ZipOutputStream(outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("word/document.xml"))
            zip.write(
                """
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                    <w:body>
                        <w:p><w:r><w:t>Chapter 1</w:t></w:r></w:p>
                        <w:p><w:r><w:t>One.</w:t></w:r></w:p>
                        <w:p><w:r><w:t>Chapter 2</w:t></w:r></w:p>
                        <w:p><w:r><w:t>Two.</w:t></w:r></w:p>
                    </w:body>
                </w:document>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
        }
    }
}
