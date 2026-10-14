package dev.reader.app.data

import android.content.Context
import android.util.Log
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.Chapter
import dev.reader.core.reader.PdfRenderMode
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
            val result = runCatching {
                val sourceFile = File(if (book.isLightNovel) chapter.sourcePath else book.sourcePath)
                when (book.format) {
                    BookFormat.CBZ, BookFormat.CBR -> loadComic(sourceFile, book.format)
                    BookFormat.PDF -> loadPdf(context, sourceFile, book.pdfRenderMode)
                    else -> loadText(sourceFile, book, chapter.orderIndex)?.let {
                        ChapterContent.Text(BookImages.resolve(context, sourceFile, book.format, it.document))
                    }
                }
            }
            result.onFailure { Log.e(LOG_TAG, "Failed to load chapter '${chapter.title}'", it) }
            val content = result.getOrNull()
                ?.takeUnless { it is ChapterContent.Text && it.document.blocks.isEmpty() }
            if (content == null) {
                val reason = result.exceptionOrNull()?.message ?: "no readable content was found"
                AppLog.record("Couldn't load '${chapter.title}': $reason")
            }
            content
        }

    private fun loadComic(file: File, format: BookFormat): ChapterContent.Comic? {
        val parsed = ComicArchiveParser.parse(file, format)
        val pages = (parsed.chapters.firstOrNull() as? ParsedChapter.ImageChapter)?.pages ?: return null
        return ChapterContent.Comic(file, format, pages)
    }

    private fun loadPdf(context: Context, file: File, renderMode: PdfRenderMode?): ChapterContent? =
        if (renderMode != PdfRenderMode.REFLOW_TEXT) {
            ChapterContent.Pdf(file)
        } else {
            val parsed = PdfTextParser(context).parse(file)
            (parsed.chapters.firstOrNull() as? ParsedChapter.TextChapter)?.let { ChapterContent.Text(it.document) }
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
