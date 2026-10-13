package dev.reader.parser.markdown

import dev.reader.core.model.BookFormat
import dev.reader.parser.ParsedBook
import dev.reader.parser.ParsedChapter
import dev.reader.parser.html.parseHtmlToRichDocument
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.File

object MarkdownParser {

    private val parser = Parser.builder().build()
    private val renderer = HtmlRenderer.builder().build()

    fun parse(file: File): ParsedBook {
        val node = parser.parse(file.readText())
        val document = parseHtmlToRichDocument(renderer.render(node))
        return ParsedBook(
            title = file.nameWithoutExtension,
            author = "",
            coverPath = null,
            format = BookFormat.MARKDOWN,
            chapters = listOf(ParsedChapter.TextChapter(file.nameWithoutExtension, document)),
        )
    }
}
