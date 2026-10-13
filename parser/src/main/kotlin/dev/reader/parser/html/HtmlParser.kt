package dev.reader.parser.html

import dev.reader.core.model.BookFormat
import dev.reader.parser.ParsedBook
import dev.reader.parser.ParsedChapter
import java.io.File

object HtmlParser {

    fun parse(file: File): ParsedBook {
        val document = parseHtmlToRichDocument(file.readText())
        return ParsedBook(
            title = file.nameWithoutExtension,
            author = "",
            coverPath = null,
            format = BookFormat.HTML,
            chapters = listOf(ParsedChapter.TextChapter(file.nameWithoutExtension, document)),
        )
    }
}
