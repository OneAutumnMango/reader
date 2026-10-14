package dev.reader.parser.fb2

import dev.reader.core.model.BookFormat
import dev.reader.core.richtext.RichDocument
import dev.reader.parser.ParsedBook
import dev.reader.parser.ParsedChapter
import dev.reader.parser.html.parseHtmlToRichDocument
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.util.Base64

/** FictionBook 2 reader: each top-level `<section>` in the main `<body>` becomes a chapter. */
object Fb2Parser {

    fun parse(file: File): ParsedBook = parse(file.readText(), file.nameWithoutExtension)

    fun parseChapter(file: File, orderIndex: Int): ParsedChapter.TextChapter? =
        parse(file).chapters.getOrNull(orderIndex) as? ParsedChapter.TextChapter

    fun extractCover(file: File, binaryId: String): ByteArray? = runCatching {
        val doc = Jsoup.parse(file.readText(), "", Parser.xmlParser())
        val binary = doc.select("binary[id=$binaryId]").firstOrNull() ?: return null
        Base64.getMimeDecoder().decode(binary.text().trim())
    }.getOrNull()

    private fun parse(xml: String, fallbackTitle: String): ParsedBook {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val titleInfo = doc.selectFirst("description > title-info")
        val bookTitle: String? = titleInfo?.selectFirst("book-title")?.text()?.trim()
        val title = bookTitle.orEmpty().ifBlank { fallbackTitle }
        val author = titleInfo?.selectFirst("author")?.let { a ->
            val first: String = a.selectFirst("first-name")?.text()?.trim().orEmpty()
            val last: String = a.selectFirst("last-name")?.text()?.trim().orEmpty()
            listOf(first, last).filter { it.isNotEmpty() }.joinToString(" ")
        }.orEmpty()
        val coverId = titleInfo?.selectFirst("coverpage > image")?.let { image ->
            val href: String = image.attr("l:href").ifBlank { image.attr("xlink:href") }
            href.removePrefix("#").ifBlank { null }
        }

        val body = doc.select("body").firstOrNull { it.attr("name").isBlank() } ?: doc.selectFirst("body")
        val sections = body?.children()?.filter { it.tagName() == "section" }.orEmpty()
        val chapters = sections.mapIndexed { index, section -> section.toChapter(index) }
            .ifEmpty { listOf(ParsedChapter.TextChapter(title, body?.toDocument() ?: RichDocument(emptyList()))) }

        return ParsedBook(
            title = title,
            author = author,
            coverPath = coverId,
            format = BookFormat.FB2,
            chapters = chapters,
        )
    }

    private fun Element.toChapter(index: Int): ParsedChapter.TextChapter {
        val headingText: String? = selectFirst("title")?.text()?.trim()
        val title = headingText.orEmpty().ifBlank { "Chapter ${index + 1}" }
        return ParsedChapter.TextChapter(title, toDocument())
    }

    private fun Element.toDocument(): RichDocument {
        val content = clone()
        content.select("title").remove()
        content.select("image").forEach { image ->
            val href = image.attr("l:href").ifBlank { image.attr("xlink:href") }
            image.tagName("img").attr("src", href)
        }
        return parseHtmlToRichDocument(content.html())
    }
}
