package dev.reader.app.data

import android.content.Context
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.Chapter
import dev.reader.core.reader.PdfRenderMode
import dev.reader.parser.ParsedChapter
import dev.reader.parser.comic.ComicArchiveParser
import dev.reader.parser.epub.EpubParser
import dev.reader.parser.html.HtmlParser
import dev.reader.parser.markdown.MarkdownParser
import dev.reader.parser.pdf.PdfTextParser
import dev.reader.parser.text.TxtParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Re-parses a chapter's source file on demand; chapter content is never cached in Room. */
object ChapterLoader {

    suspend fun load(context: Context, book: Book, chapter: Chapter): ChapterContent? =
        withContext(Dispatchers.IO) {
            runCatching {
                val sourceFile = File(if (book.isLightNovel) chapter.sourcePath else book.sourcePath)
                when (book.format) {
                    BookFormat.CBZ, BookFormat.CBR -> loadComic(sourceFile, book.format)
                    BookFormat.PDF -> loadPdf(context, sourceFile, book.pdfRenderMode)
                    else -> loadText(sourceFile, book.format, book.isLightNovel, chapter.orderIndex)
                }
            }.getOrNull()
        }

    private fun loadComic(file: File, format: BookFormat): ChapterContent.Comic? {
        val parsed = ComicArchiveParser.parse(file, format)
        val pages = (parsed.chapters.firstOrNull() as? ParsedChapter.ImageChapter)?.pages ?: return null
        return ChapterContent.Comic(file, format, pages)
    }

    private fun loadPdf(context: Context, file: File, renderMode: PdfRenderMode?): ChapterContent? =
        if (renderMode == PdfRenderMode.FIXED_PAGE) {
            ChapterContent.Pdf(file)
        } else {
            val parsed = PdfTextParser(context).parse(file)
            (parsed.chapters.firstOrNull() as? ParsedChapter.TextChapter)?.let { ChapterContent.Text(it.document) }
        }

    private fun loadText(
        file: File,
        format: BookFormat,
        isLightNovel: Boolean,
        orderIndex: Int,
    ): ChapterContent.Text? {
        val parsed = when (format) {
            BookFormat.TXT -> TxtParser.parse(file)
            BookFormat.MARKDOWN -> MarkdownParser.parse(file)
            BookFormat.HTML -> HtmlParser.parse(file)
            BookFormat.EPUB -> EpubParser.parse(file)
            else -> error("$format has no text loader")
        }
        val chapter = if (isLightNovel) parsed.chapters.firstOrNull() else parsed.chapters.getOrNull(orderIndex)
        return (chapter as? ParsedChapter.TextChapter)?.let { ChapterContent.Text(it.document) }
    }
}
