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
        val hash = ContentHash.ofFile(file)
        val existing = library.findByContentHash(hash) ?: library.findBySourcePath(file.path)
        val parsed = parse(file, format, pdfContext, existing?.chapterPattern)
        val bookId = existing?.id ?: BookId(newId())
        val book = Book(
            id = bookId,
            title = existing?.title ?: cleanTitle(parsed.title),
            author = parsed.author,
            coverPath = existing?.coverPath ?: materializeCover(file, format, bookId, parsed.coverPath),
            format = format,
            sourcePath = file.path,
            categoryIds = existing?.categoryIds ?: categoryIds.ifEmpty { library.defaultCategoryIds() },
            pdfRenderMode = existing?.pdfRenderMode,
            addedAt = existing?.addedAt ?: now(),
            contentHash = hash,
            chapterPattern = existing?.chapterPattern,
        )
        val chapterIds = existingChapterIds(existing)
        val chapters = parsed.chapters.mapIndexed { index, chapter ->
            chapter.toChapterModel(bookId, index, chapterIds[index])
        }
        library.upsertBook(book, chapters)
        return book
    }

    /** Re-splits a txt/markdown [book] on [pattern] (null = back to a single chapter). */
    suspend fun applyChapterPattern(book: Book, pattern: String?): Book {
        library.updateBookDetails(book.copy(chapterPattern = pattern))
        return importFile(File(book.sourcePath))
    }

    suspend fun importLightNovelFolder(folder: File, categoryIds: Set<CategoryId> = emptySet()): Book {
        val files = (folder.listFiles { f -> f.isFile } ?: emptyArray())
            .sortedWith(compareBy(NaturalSortComparator) { it.name })

        val hash = ContentHash.ofFolder(folder)
        val existing = library.findByContentHash(hash) ?: library.findBySourcePath(folder.path)
        val bookId = existing?.id ?: BookId(newId())
        val chapterIds = existingChapterIds(existing)
        val chapters = files.mapIndexedNotNull { index, file ->
            val format = BookFormat.fromExtension(file.extension) ?: return@mapIndexedNotNull null
            val parsed = parse(file, format, pdfContext = null)
            val textChapters = parsed.chapters.filterIsInstance<ParsedChapter.TextChapter>()
            Chapter(
                id = chapterIds[index] ?: ChapterId(newId()),
                bookId = bookId,
                title = file.nameWithoutExtension,
                sourcePath = file.path,
                orderIndex = index,
                wordCount = textChapters.sumOf { it.document.wordCount() },
            )
        }

        val book = Book(
            id = bookId,
            title = existing?.title ?: cleanTitle(folder.name),
            coverPath = existing?.coverPath,
            format = chapters.firstOrNull()
                ?.let { BookFormat.fromExtension(File(it.sourcePath).extension) }
                ?: BookFormat.TXT,
            sourcePath = folder.path,
            isLightNovel = true,
            categoryIds = existing?.categoryIds ?: categoryIds.ifEmpty { library.defaultCategoryIds() },
            addedAt = existing?.addedAt ?: now(),
            contentHash = hash,
        )
        library.upsertBook(book, chapters)
        return book
    }

    /**
     * Re-imports books added before content hashing (and front-matter detection) existed, in place,
     * so they get a hash for duplicate checks. Books whose source file is gone are left untouched.
     */
    suspend fun refreshLegacyBooks(pdfContext: Context?) {
        library.booksMissingContentHash().forEach { book ->
            val source = File(book.sourcePath)
            if (!source.exists()) return@forEach
            runCatching {
                if (book.isLightNovel) importLightNovelFolder(source) else importFile(source, pdfContext = pdfContext)
            }
        }
    }

    /** Reuses chapter ids by position on re-import so reading progress and bookmarks stay attached. */
    private suspend fun existingChapterIds(existing: Book?): Map<Int, ChapterId> =
        existing?.let { book -> library.chapters(book.id).associate { it.orderIndex to it.id } }.orEmpty()

    private fun parse(
        file: File,
        format: BookFormat,
        pdfContext: Context?,
        chapterPattern: String? = null,
    ): ParsedBook = when (format) {
        BookFormat.TXT -> TxtParser.parse(file, chapterPattern)
        BookFormat.MARKDOWN -> MarkdownParser.parse(file, chapterPattern)
        BookFormat.HTML -> HtmlParser.parse(file)
        BookFormat.EPUB -> EpubParser.parse(file)
        BookFormat.CBZ, BookFormat.CBR -> ComicArchiveParser.parse(file, format)
        BookFormat.PDF -> PdfTextParser(requireNotNull(pdfContext) { "PDF import needs a Context" }).parse(file)
    }

    /** Extracts an archive-internal cover image (epub/cbz/cbr) to a real file next to the source. */
    private fun materializeCover(file: File, format: BookFormat, bookId: BookId, entryName: String?): String? {
        entryName ?: return null
        val bytes = when (format) {
            BookFormat.EPUB -> EpubParser.extractCover(file, entryName)
            BookFormat.CBZ, BookFormat.CBR -> runCatching { ComicArchiveParser.readPage(file, format, entryName) }
                .getOrNull()
            else -> null
        } ?: return null
        val extension = entryName.substringAfterLast('.', "jpg")
        val coverFile = File(file.parentFile ?: file, "${bookId.value}_cover.$extension")
        coverFile.writeBytes(bytes)
        return coverFile.path
    }

    private fun ParsedChapter.toChapterModel(bookId: BookId, orderIndex: Int, existingId: ChapterId?) = Chapter(
        id = existingId ?: ChapterId(newId()),
        bookId = bookId,
        title = title,
        sourcePath = "",
        orderIndex = orderIndex,
        wordCount = (this as? ParsedChapter.TextChapter)?.document?.wordCount() ?: 0,
        isExtra = (this as? ParsedChapter.TextChapter)?.isExtra ?: false,
    )
}

private val LEADING_ID_PATTERN = Regex("^[0-9]{3,}[.\\-_\\s]+")

/** Strips a leading numeric id (e.g. a download/site id) like "1791901.....-" off a raw file name. */
internal fun cleanTitle(raw: String): String {
    val stripped = raw.replace(LEADING_ID_PATTERN, "")
    return stripped.replace('_', ' ').trim().ifBlank { raw }
}
