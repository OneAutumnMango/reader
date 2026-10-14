package dev.reader.app.data

import android.content.Context
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.Chapter
import dev.reader.core.reader.PdfRenderMode
import dev.reader.core.richtext.RichDocument
import dev.reader.parser.ParsedBook
import dev.reader.parser.ParsedChapter
import dev.reader.parser.comic.ComicArchiveParser
import dev.reader.parser.docx.DocxParser
import dev.reader.parser.epub.EpubParser
import dev.reader.parser.fb2.Fb2Parser
import dev.reader.parser.html.HtmlParser
import dev.reader.parser.markdown.MarkdownParser
import dev.reader.parser.mobi.MobiParser
import dev.reader.parser.pdf.PdfTextParser
import dev.reader.parser.rtf.RtfParser
import dev.reader.parser.text.TxtParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val LOG_TAG = "ChapterLoader"

/** Re-parses a chapter's source file on demand; chapter content is never cached in Room. */
object ChapterLoader {

    suspend fun load(context: Context, book: Book, chapter: Chapter): ChapterContent? =
        withContext(Dispatchers.IO) {
            AppLog.debug("Loading ${book.format} chapter ${chapter.orderIndex + 1}", LOG_TAG)
            val result = runCatching {
                val sourceFile = File(if (book.isLightNovel) chapter.sourcePath else book.sourcePath)
                when (book.format) {
                    BookFormat.CBZ, BookFormat.CBR -> loadComic(sourceFile, book.format)
                    BookFormat.PDF -> loadPdf(context, sourceFile, book, chapter.orderIndex)
                    else -> loadText(sourceFile, book, chapter.orderIndex)?.let {
                        ChapterContent.Text(BookImages.resolve(context, sourceFile, book.format, it.document))
                    }
                }
            }
            result.onFailure { AppLog.error("Failed to load ${book.format} chapter", LOG_TAG, it) }
            val content = result.getOrNull()
                ?.takeUnless { it is ChapterContent.Text && it.document.blocks.isEmpty() }
            if (content == null && result.isSuccess) {
                AppLog.warn("No readable content in ${book.format} chapter ${chapter.orderIndex + 1}", LOG_TAG)
            } else if (content != null) {
                AppLog.debug("Loaded ${book.format} chapter ${chapter.orderIndex + 1}", LOG_TAG)
            }
            content
        }

    private fun loadComic(file: File, format: BookFormat): ChapterContent.Comic? {
        val parsed = ComicArchiveParser.parse(file, format)
        val pages = (parsed.chapters.firstOrNull() as? ParsedChapter.ImageChapter)?.pages ?: return null
        return ChapterContent.Comic(file, format, pages)
    }

    private fun loadPdf(context: Context, file: File, book: Book, orderIndex: Int): ChapterContent? {
        if (book.pdfRenderMode != PdfRenderMode.REFLOW_TEXT) return ChapterContent.Pdf(file)
        val chapters = parsePdf(context, file, book).chapters.filterIsInstance<ParsedChapter.TextChapter>()
        val document = if (book.isLightNovel) {
            RichDocument(chapters.flatMap { it.document.blocks })
        } else {
            chapters.getOrNull(orderIndex)?.document ?: return null
        }
        return ChapterContent.Text(document)
    }

    private data class PdfKey(val path: String, val modified: Long, val pattern: String?, val excluded: Set<Int>)

    private var pdfCache: Pair<PdfKey, ParsedBook>? = null

    /** Splitting needs the whole PDF's text, so keep the last parse for swiping between its chapters. */
    @Synchronized
    private fun parsePdf(context: Context, file: File, book: Book): ParsedBook {
        val key = PdfKey(file.path, file.lastModified(), book.patternFor(), book.excludedLinesFor())
        pdfCache?.takeIf { it.first == key }?.let { return it.second }
        return PdfTextParser(context).parse(file, key.pattern, key.excluded).also { pdfCache = key to it }
    }

    private fun loadText(file: File, book: Book, orderIndex: Int): ChapterContent.Text? {
        val isLightNovel = book.isLightNovel
        val chapterIndex = if (isLightNovel) 0 else orderIndex
        when (book.format) {
            BookFormat.EPUB -> return EpubParser.parseChapter(file, chapterIndex)
                ?.let { ChapterContent.Text(it.document) }
            BookFormat.FB2 -> return Fb2Parser.parseChapter(file, chapterIndex)
                ?.let { ChapterContent.Text(it.document) }
            else -> Unit
        }
        val parsed = parseWholeBook(file, book)
        val chapter = parsed.chapters.getOrNull(chapterIndex)
        return (chapter as? ParsedChapter.TextChapter)?.let { ChapterContent.Text(it.document) }
    }

    private fun parseWholeBook(file: File, book: Book) = when (book.format) {
        BookFormat.TXT -> TxtParser.parse(file, book.patternFor(), book.excludedLinesFor())
        BookFormat.MARKDOWN -> MarkdownParser.parse(file, book.patternFor(), book.excludedLinesFor())
        BookFormat.HTML -> HtmlParser.parse(file)
        BookFormat.MOBI -> MobiParser.parse(file)
        BookFormat.RTF -> RtfParser.parse(file)
        BookFormat.DOCX -> DocxParser.parse(file)
        else -> error("${book.format} has no text loader")
    }

    private fun Book.patternFor(): String? = chapterPattern.takeUnless { isLightNovel }
    private fun Book.excludedLinesFor(): Set<Int> = chapterExcludedLines.takeUnless { isLightNovel }.orEmpty()
}
