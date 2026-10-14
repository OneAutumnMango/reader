package dev.reader.parser.markdown

import dev.reader.core.model.BookFormat
import dev.reader.parser.ParsedBook
import dev.reader.parser.html.parseHtmlToRichDocument
import dev.reader.parser.text.ChapterSplitter
import dev.reader.parser.text.TextChunk
import dev.reader.parser.text.toChapters
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.File

object MarkdownParser {

    private val parser = Parser.builder().build()
    private val renderer = HtmlRenderer.builder().build()

    fun parse(file: File, chapterPattern: String? = null): ParsedBook {
        val text = file.readText()
        val chunks = chapterPattern?.let { ChapterSplitter.split(text, it) } ?: listOf(TextChunk(null, text))
        return ParsedBook(
            title = file.nameWithoutExtension,
            author = "",
            coverPath = null,
            format = BookFormat.MARKDOWN,
            chapters = chunks.toChapters(file.nameWithoutExtension) {
                parseHtmlToRichDocument(renderer.render(parser.parse(it)))
            },
        )
    }
}
