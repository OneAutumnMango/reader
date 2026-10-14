package dev.reader.parser.docx

import dev.reader.core.model.BookFormat
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.Span
import dev.reader.parser.ParsedBook
import dev.reader.parser.ParsedChapter
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipFile

/** Reads the paragraph text out of a .docx's `word/document.xml`; run formatting isn't preserved. */
object DocxParser {

    fun parse(file: File): ParsedBook = ZipFile(file).use { zip ->
        val documentXml = zip.getEntry("word/document.xml")?.let { entry ->
            zip.getInputStream(entry).bufferedReader().use { it.readText() }
        } ?: error("Not a valid .docx: missing word/document.xml")

        val body = Jsoup.parse(documentXml, "", Parser.xmlParser())
        val paragraphs = body.select("w\\:p").mapNotNull { paragraph ->
            paragraph.select("w\\:t").joinToString("") { it.wholeText() }
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.let { Block.Paragraph(listOf(Span(it))) }
        }

        val core = zip.getEntry("docProps/core.xml")
            ?.let { entry -> zip.getInputStream(entry).bufferedReader().use { it.readText() } }
            ?.let { Jsoup.parse(it, "", Parser.xmlParser()) }
        val title = core?.select("dc\\:title")?.text()?.trim()?.ifBlank { null } ?: file.nameWithoutExtension
        val author = core?.select("dc\\:creator")?.text().orEmpty()

        ParsedBook(
            title = title,
            author = author,
            coverPath = null,
            format = BookFormat.DOCX,
            chapters = listOf(ParsedChapter.TextChapter(title, RichDocument(paragraphs))),
        )
    }
}
