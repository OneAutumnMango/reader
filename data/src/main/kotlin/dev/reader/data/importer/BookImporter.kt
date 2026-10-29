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
import dev.reader.parser.TextBookParser
import dev.reader.parser.comic.ComicArchiveParser
import dev.reader.parser.docx.DocxParser
import dev.reader.parser.epub.EpubParser
import dev.reader.parser.fb2.Fb2Parser
import dev.reader.parser.pdf.PdfTextParser
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID
import kotlin.time.Clock

/** Imports a single file, or a light-novel folder where each file becomes a chapter. */
class BookImporter(
    private val library: LibraryRepository,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> kotlin.time.Instant = { Clock.System.now() },
) {

    suspend fun importFile(
        file: File,
        categoryIds: Set<CategoryId> = emptySet(),
        pdfContext: Context? = null,
        target: Book? = null,
        originUri: String? = null,
    ): Book {
        val format = requireNotNull(BookFormat.fromFileName(file.name)) {
            "unsupported extension: ${file.extension}"
        }
        val hash = ContentHash.ofFile(file)
        // An explicit target wins: duplicates imported before hashing existed can share a hash.
        val existing = target ?: library.findByContentHash(hash) ?: library.findBySourcePath(file.path)
        val parsed = parse(file, format, pdfContext, existing?.chapterPattern, existing?.chapterExcludedLines.orEmpty())
        val bookId = existing?.id ?: BookId(newId())
        val book = Book(
            id = bookId,
            title = existing?.title ?: cleanTitle(parsed.title),
            author = parsed.author,
            coverPath = existing?.coverPath?.takeIf(::isReadableFile)
                ?: materializeCover(file, format, bookId, parsed.coverPath),
            format = format,
            sourcePath = file.path,
            categoryIds = existing?.categoryIds ?: categoryIds.ifEmpty { library.defaultCategoryIds() },
            pdfRenderMode = existing?.pdfRenderMode,
            addedAt = existing?.addedAt ?: now(),
            contentHash = hash,
            chapterPattern = existing?.chapterPattern,
            chapterExcludedLines = existing?.chapterExcludedLines.orEmpty(),
            originUri = originUri ?: existing?.originUri,
        )
        val chapterIds = existingChapterIds(existing)
        val chapters = parsed.chapters.mapIndexed { index, chapter ->
            chapter.toChapterModel(bookId, index, chapterIds[index])
        }
        library.upsertBook(book, chapters)
        return book
    }

    /** Rebuilds missing covers without changing book metadata, chapters, or reading progress. */
    suspend fun refreshMissingCovers(
        pdfContext: Context?,
        sourceFile: (Book) -> File? = { File(it.sourcePath) },
    ): Int {
        var refreshed = 0
        library.observeBooks().first().forEach { book ->
            if (book.coverPath?.let(::isReadableFile) == true || book.format !in COVER_FORMATS) return@forEach
            runCatching {
                val source = sourceFile(book)?.takeIf { it.isFile && it.canRead() } ?: return@runCatching
                val parsed = parse(source, book.format, pdfContext)
                val coverPath = materializeCover(source, book.format, book.id, parsed.coverPath)
                    ?: return@runCatching
                library.updateBookDetails(book.copy(coverPath = coverPath))
                refreshed++
            }
        }
        return refreshed
    }

    /** Re-parses a book's source file in place, replacing its chapter list (e.g. after a parser fix). */
    suspend fun reimport(book: Book, pdfContext: Context? = null): Book = if (book.isLightNovel) {
        importLightNovelFolder(File(book.sourcePath), originUri = book.originUri)
    } else {
        importFile(File(book.sourcePath), pdfContext = pdfContext, target = book)
    }

    /** Re-splits a text-based [book] on [pattern] (null = its default chapters), skipping [excludedLines]. */
    suspend fun applyChapterPattern(
        book: Book,
        pattern: String?,
        excludedLines: Set<Int> = emptySet(),
        pdfContext: Context? = null,
    ): Book {
        val updated = book.copy(chapterPattern = pattern, chapterExcludedLines = excludedLines)
        library.updateBookDetails(updated)
        return importFile(File(book.sourcePath), pdfContext = pdfContext, target = updated)
    }

    suspend fun importLightNovelFolder(
        folder: File,
        categoryIds: Set<CategoryId> = emptySet(),
        originUri: String? = null,
    ): Book {
        val files = (folder.listFiles { f -> f.isFile } ?: emptyArray())
            .sortedWith(compareBy(NaturalSortComparator) { it.name })

        val hash = ContentHash.ofFolder(folder)
        val existing = library.findByContentHash(hash) ?: library.findBySourcePath(folder.path)
        val bookId = existing?.id ?: BookId(newId())
        val chapterIds = existingChapterIds(existing)
        val chapters = files.mapIndexedNotNull { index, file ->
            val format = BookFormat.fromFileName(file.name) ?: return@mapIndexedNotNull null
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
                ?.let { BookFormat.fromFileName(File(it.sourcePath).name) }
                ?: BookFormat.TXT,
            sourcePath = folder.path,
            isLightNovel = true,
            categoryIds = existing?.categoryIds ?: categoryIds.ifEmpty { library.defaultCategoryIds() },
            addedAt = existing?.addedAt ?: now(),
            contentHash = hash,
            originUri = originUri ?: existing?.originUri,
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
        chapterExcludedLines: Set<Int> = emptySet(),
    ): ParsedBook = when (format) {
        BookFormat.DOCX -> DocxParser.parse(file, chapterPattern, chapterExcludedLines)
        BookFormat.TXT, BookFormat.MARKDOWN, BookFormat.HTML, BookFormat.MOBI, BookFormat.RTF, BookFormat.ODT,
        ->
            TextBookParser.parse(file, format, chapterPattern, chapterExcludedLines)
        BookFormat.EPUB -> EpubParser.parse(file)
        BookFormat.CBZ, BookFormat.CBR -> ComicArchiveParser.parse(file, format)
        BookFormat.PDF -> PdfTextParser(requireNotNull(pdfContext) { "PDF import needs a Context" })
            .parse(file, chapterPattern, chapterExcludedLines)
        BookFormat.FB2 -> Fb2Parser.parse(file)
    }

    /** Extracts an archive-internal cover image (epub/cbz/cbr) to a real file next to the source. */
    private fun materializeCover(file: File, format: BookFormat, bookId: BookId, entryName: String?): String? {
        entryName ?: return null
        val bytes = when (format) {
            BookFormat.EPUB -> EpubParser.extractCover(file, entryName)
            BookFormat.CBZ, BookFormat.CBR -> runCatching { ComicArchiveParser.readPage(file, format, entryName) }
                .getOrNull()
            BookFormat.FB2 -> Fb2Parser.extractCover(file, entryName)
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

private val COVER_FORMATS = setOf(BookFormat.EPUB, BookFormat.CBZ, BookFormat.CBR, BookFormat.FB2)

private fun isReadableFile(path: String): Boolean = File(path).let { it.isFile && it.canRead() }

private val LEADING_ID_PATTERN = Regex("^[0-9]{3,}[.\\-_\\s]+")

/** Strips a leading numeric id (e.g. a download/site id) like "1791901.....-" off a raw file name. */
internal fun cleanTitle(raw: String): String {
    val stripped = raw.replace(LEADING_ID_PATTERN, "")
    return stripped.replace('_', ' ').trim().ifBlank { raw }
}
