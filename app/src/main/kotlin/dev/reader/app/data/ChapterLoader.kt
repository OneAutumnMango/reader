package dev.reader.app.data

import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.Chapter
import dev.reader.core.richtext.RichDocument
import dev.reader.parser.ParsedChapter
import dev.reader.parser.epub.EpubParser
import dev.reader.parser.html.HtmlParser
import dev.reader.parser.markdown.MarkdownParser
import dev.reader.parser.text.TxtParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Re-parses a chapter's source file on demand; chapter content is never cached in Room. */
object ChapterLoader {

    suspend fun load(book: Book, chapter: Chapter): RichDocument? = withContext(Dispatchers.IO) {
        runCatching {
            if (book.isLightNovel) {
                val parsed = parseFile(File(chapter.sourcePath), book.format)
                (parsed.chapters.firstOrNull() as? ParsedChapter.TextChapter)?.document
            } else {
                val parsed = parseFile(File(book.sourcePath), book.format)
                (parsed.chapters.getOrNull(chapter.orderIndex) as? ParsedChapter.TextChapter)?.document
            }
        }.getOrNull()
    }

    private fun parseFile(file: File, format: BookFormat) = when (format) {
        BookFormat.TXT -> TxtParser.parse(file)
        BookFormat.MARKDOWN -> MarkdownParser.parse(file)
        BookFormat.HTML -> HtmlParser.parse(file)
        BookFormat.EPUB -> EpubParser.parse(file)
        BookFormat.PDF, BookFormat.CBZ, BookFormat.CBR -> error("$format isn't wired into the text reader yet")
    }
}
