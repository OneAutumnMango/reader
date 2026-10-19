package dev.reader.parser

import dev.reader.core.model.BookFormat
import dev.reader.core.richtext.RichDocument
import dev.reader.parser.docx.DocxParser
import dev.reader.parser.html.HtmlParser
import dev.reader.parser.markdown.MarkdownParser
import dev.reader.parser.mobi.MobiParser
import dev.reader.parser.odt.OdtParser
import dev.reader.parser.rtf.RtfParser
import dev.reader.parser.text.TxtParser
import java.io.File

/** Shared parse and chapter-detection entry point for standalone text-based book formats. */
object TextBookParser {

    fun parse(
        file: File,
        format: BookFormat,
        pattern: String? = null,
        excludedLines: Set<Int> = emptySet(),
    ): ParsedBook {
        if (format == BookFormat.TXT) return TxtParser.parse(file, pattern, excludedLines)
        if (format == BookFormat.MARKDOWN) return MarkdownParser.parse(file, pattern, excludedLines)

        val parsed = parseBase(file, format)
        if (pattern == null) return parsed
        val blocks = parsed.chapters.filterIsInstance<ParsedChapter.TextChapter>()
            .flatMap { it.document.blocks }
        val document = RichDocument(blocks)
        return parsed.copy(chapters = document.splitChapters(file.nameWithoutExtension, pattern, excludedLines))
    }

    /** Returns the exact canonical lines used by chapter detection and custom splitting. */
    fun detectionText(file: File, format: BookFormat): String = when (format) {
        BookFormat.TXT, BookFormat.MARKDOWN -> file.readText()
        else -> {
            val blocks = parseBase(file, format).chapters.filterIsInstance<ParsedChapter.TextChapter>()
                .flatMap { it.document.blocks }
            RichDocument(blocks).chapterDetectionText()
        }
    }

    private fun parseBase(file: File, format: BookFormat): ParsedBook = when (format) {
        BookFormat.HTML -> HtmlParser.parse(file)
        BookFormat.MOBI -> MobiParser.parse(file)
        BookFormat.RTF -> RtfParser.parse(file)
        BookFormat.DOCX -> DocxParser.parse(file)
        BookFormat.ODT -> OdtParser.parse(file)
        else -> error("${format.name} is not supported by TextBookParser")
    }
}
