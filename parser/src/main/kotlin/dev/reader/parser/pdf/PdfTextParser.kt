package dev.reader.parser.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dev.reader.core.model.BookFormat
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.Span
import dev.reader.parser.ParsedBook
import dev.reader.parser.ParsedChapter
import java.io.File

/** Reflow mode: extracts plain text so it can be themed like any other format. */
class PdfTextParser(context: Context) {

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    fun parse(file: File): ParsedBook {
        val text = PDDocument.load(file).use { PDFTextStripper().getText(it) }
        val paragraphs = text
            .split(Regex("\n\\s*\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { Block.Paragraph(listOf(Span(it))) }

        return ParsedBook(
            title = file.nameWithoutExtension,
            author = "",
            coverPath = null,
            format = BookFormat.PDF,
            chapters = listOf(ParsedChapter.TextChapter(file.nameWithoutExtension, RichDocument(paragraphs))),
        )
    }
}
