package dev.reader.data.importer

import android.content.Context
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.model.CategoryId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.core.richtext.wordCount
import dev.reader.core.util.NaturalSortComparator
import dev.reader.data.repo.LibraryRepository
import dev.reader.parser.ParsedBook
import dev.reader.parser.ParsedChapter
import dev.reader.parser.comic.ComicArchiveParser
import dev.reader.parser.epub.EpubParser
import dev.reader.parser.html.HtmlParser
import dev.reader.parser.markdown.MarkdownParser
import dev.reader.parser.pdf.PdfTextParser
import dev.reader.parser.text.TxtParser
import java.io.File
import java.util.UUID
import kotlin.time.Clock

/** Imports a single file, or a light-novel folder where each file becomes a chapter. */
class BookImporter(
    private val library: LibraryRepository,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> kotlin.time.Instant = { Clock.System.now() },
) {

    suspend fun importFile(file: File, categoryIds: Set<CategoryId> = emptySet(), pdfContext: Context? = null): Book {
        val format = requireNotNull(BookFormat.fromExtension(file.extension)) {
            "unsupported extension: ${file.extension}"
        }
        val parsed = parse(file, format, pdfContext)
        val bookId = BookId(newId())
        val book = Book(
            id = bookId,
            title = parsed.title,
            author = parsed.author,
            coverPath = parsed.coverPath,
            format = format,
            sourcePath = file.path,
            categoryIds = categoryIds,
            addedAt = now(),
        )
        val chapters = parsed.chapters.mapIndexed { index, chapter -> chapter.toChapterModel(bookId, index) }
        library.upsertBook(book, chapters)
        return book
    }

    suspend fun importLightNovelFolder(folder: File, categoryIds: Set<CategoryId> = emptySet()): Book {
        val files = (folder.listFiles { f -> f.isFile } ?: emptyArray())
            .sortedWith(compareBy(NaturalSortComparator) { it.name })

        val bookId = BookId(newId())
        val chapters = files.mapIndexedNotNull { index, file ->
            val format = BookFormat.fromExtension(file.extension) ?: return@mapIndexedNotNull null
            val parsed = parse(file, format, pdfContext = null)
            val textChapters = parsed.chapters.filterIsInstance<ParsedChapter.TextChapter>()
            Chapter(
                id = ChapterId(newId()),
                bookId = bookId,
                title = file.nameWithoutExtension,
                sourcePath = file.path,
                orderIndex = index,
                wordCount = textChapters.sumOf { it.document.wordCount() },
            )
        }

        val book = Book(
            id = bookId,
            title = folder.name,
            format = chapters.firstOrNull()
                ?.let { BookFormat.fromExtension(File(it.sourcePath).extension) }
                ?: BookFormat.TXT,
            sourcePath = folder.path,
            isLightNovel = true,
            categoryIds = categoryIds,
            addedAt = now(),
        )
        library.upsertBook(book, chapters)
        return book
    }

    private fun parse(file: File, format: BookFormat, pdfContext: Context?): ParsedBook = when (format) {
        BookFormat.TXT -> TxtParser.parse(file)
        BookFormat.MARKDOWN -> MarkdownParser.parse(file)
        BookFormat.HTML -> HtmlParser.parse(file)
        BookFormat.EPUB -> EpubParser.parse(file)
        BookFormat.CBZ, BookFormat.CBR -> ComicArchiveParser.parse(file, format)
        BookFormat.PDF -> PdfTextParser(requireNotNull(pdfContext) { "PDF import needs a Context" }).parse(file)
    }

    private fun ParsedChapter.toChapterModel(bookId: BookId, orderIndex: Int): Chapter = Chapter(
        id = ChapterId(newId()),
        bookId = bookId,
        title = title,
        sourcePath = "",
        orderIndex = orderIndex,
        wordCount = (this as? ParsedChapter.TextChapter)?.document?.wordCount() ?: 0,
    )
}
