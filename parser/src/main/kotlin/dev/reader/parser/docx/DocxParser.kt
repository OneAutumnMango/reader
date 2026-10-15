package dev.reader.parser.docx

import dev.reader.core.model.BookFormat
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.Span
import dev.reader.parser.ParsedBook
import dev.reader.parser.ParsedChapter
import dev.reader.parser.text.ChapterSplitter
import dev.reader.parser.text.toChapters
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipFile

private val HEADING_STYLE = Regex("^(heading[1-9]|title|subtitle|chapter)$", RegexOption.IGNORE_CASE)

object DocxParser {

    /**
     * Plain-text view for chapter-detection. Blank lines separate paragraphs; heading-style
     * paragraphs are wrapped in **…** so bold/standalone detector strategies can find them.
     */
    fun text(file: File): String = ZipFile(file).use { zip ->
        val body = Jsoup.parse(zip.readEntry("word/document.xml"), "", Parser.xmlParser())
        body.select("w\\:p")
            .mapNotNull { para ->
                val text = para.select("w\\:t").joinToString("") { it.wholeText() }.trim()
                    .takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                if (HEADING_STYLE.matches(para.pStyle())) "**$text**" else text
            }
            .joinToString("\n\n")
    }

    fun parse(file: File, chapterPattern: String? = null, excludedLines: Set<Int> = emptySet()): ParsedBook =
        ZipFile(file).use { zip ->
            val body = Jsoup.parse(zip.readEntry("word/document.xml"), "", Parser.xmlParser())
            val core = zip.getEntry("docProps/core.xml")
                ?.let { Jsoup.parse(zip.getInputStream(it).bufferedReader().readText(), "", Parser.xmlParser()) }
            val title = core?.select("dc\\:title")?.text()?.trim()?.ifBlank { null } ?: file.nameWithoutExtension
            val author = core?.select("dc\\:creator")?.text().orEmpty()
            val blocks = body.select("w\\:p").mapNotNull { it.toBlock() }

            val chapters = if (chapterPattern != null) {
                val fullText = text(file)
                ChapterSplitter.split(fullText, chapterPattern, excludedLines)
                    .toChapters(title) { raw ->
                        RichDocument(raw.lines().filter { it.isNotBlank() }.map { line ->
                            Block.Paragraph(listOf(Span(line.trim().trim('*'))))
                        })
                    }
            } else {
                listOf(ParsedChapter.TextChapter(title, RichDocument(blocks)))
            }

            ParsedBook(title = title, author = author, coverPath = null, format = BookFormat.DOCX, chapters = chapters)
        }

    private fun ZipFile.readEntry(name: String): String =
        getEntry(name)?.let { getInputStream(it).bufferedReader().readText() }
            ?: error("Not a valid .docx: missing $name")

    private fun Element.toBlock(): Block? {
        val level = headingLevel(pStyle())
        val spans = select("w\\:r").mapNotNull { run ->
            val text = run.select("w\\:t").joinToString("") { it.wholeText() }
            if (text.isEmpty()) return@mapNotNull null
            val rPr = run.selectFirst("w\\:rPr")
            Span(
                text = text,
                bold = rPr?.ooxmlFlag("w\\:b") ?: false,
                italic = rPr?.ooxmlFlag("w\\:i") ?: false,
                underline = rPr?.selectFirst("w\\:u")?.let { it.attr("w:val") != "none" } ?: false,
            )
        }
        if (spans.isEmpty()) return null
        return if (level != null) Block.Heading(level, spans) else Block.Paragraph(spans)
    }

    /** True when an OOXML toggle property element is present and not explicitly negated. */
    private fun Element.ooxmlFlag(selector: String): Boolean {
        val el = selectFirst(selector) ?: return false
        val v = el.attr("w:val")
        return v.isEmpty() || v == "true" || v == "1" || v == "on"
    }

    private fun Element.pStyle(): String =
        selectFirst("w\\:pPr > w\\:pStyle")?.let { it.attr("w:val").ifEmpty { it.attr("val") } }.orEmpty()

    private fun headingLevel(style: String): Int? = when (style.lowercase()) {
        "heading1" -> 1; "heading2" -> 2; "heading3" -> 3
        "heading4" -> 4; "heading5" -> 5; "heading6" -> 6
        "title", "chapter" -> 1
        "subtitle" -> 2
        else -> null
    }
}

