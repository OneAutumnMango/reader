package dev.reader.parser.text

import dev.reader.core.model.BookFormat
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.Span
import dev.reader.parser.ParsedBook
import dev.reader.parser.ParsedChapter
import java.io.File

object TxtParser {

    fun parse(file: File): ParsedBook {
        val paragraphs = file.readText()
            .split(Regex("\n\\s*\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { Block.Paragraph(listOf(Span(it))) }

        return ParsedBook(
            title = file.nameWithoutExtension,
            author = "",
            coverPath = null,
            format = BookFormat.TXT,
            chapters = listOf(ParsedChapter.TextChapter(file.nameWithoutExtension, RichDocument(paragraphs))),
        )
    }
}
