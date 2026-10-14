package dev.reader.parser.text

import dev.reader.core.model.BookFormat
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.Span
import dev.reader.parser.ParsedBook
import java.io.File

object TxtParser {

    /** One chapter for the whole file, or one per heading matched by [chapterPattern] (see [ChapterSplitter]). */
    fun parse(file: File, chapterPattern: String? = null, excludedLines: Set<Int> = emptySet()): ParsedBook {
        val text = file.readText()
        val chunks = chapterPattern?.let { ChapterSplitter.split(text, it, excludedLines) }
            ?: listOf(TextChunk(null, text))
        return ParsedBook(
            title = file.nameWithoutExtension,
            author = "",
            coverPath = null,
            format = BookFormat.TXT,
            chapters = chunks.toChapters(file.nameWithoutExtension) { RichDocument(paragraphs(it)) },
        )
    }

    private fun paragraphs(text: String): List<Block> = text
        .split(Regex("\n\\s*\n"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { Block.Paragraph(listOf(Span(it))) }
}
