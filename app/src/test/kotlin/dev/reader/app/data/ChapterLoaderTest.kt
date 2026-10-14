package dev.reader.app.data

import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.core.reader.PdfRenderMode
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock

@RunWith(RobolectricTestRunner::class)
class ChapterLoaderTest {

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
}
