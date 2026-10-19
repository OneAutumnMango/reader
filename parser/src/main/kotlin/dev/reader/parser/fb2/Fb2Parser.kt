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
import java.io.InputStream
import java.util.Base64
import java.util.zip.ZipFile

/** FictionBook 2 reader: each top-level `<section>` in the main `<body>` becomes a chapter. */
object Fb2Parser {

    fun parse(file: File): ParsedBook = withFb2Stream(file) { input ->
        val fallbackTitle = file.nameWithoutExtension.let { name ->
            if (name.endsWith(FB2_EXTENSION, true)) name.dropLast(FB2_EXTENSION.length) else name
        }
        parse(input, fallbackTitle)
    }

    fun parseChapter(file: File, orderIndex: Int): ParsedChapter.TextChapter? =
        parse(file).chapters.getOrNull(orderIndex) as? ParsedChapter.TextChapter

    fun extractCover(file: File, binaryId: String): ByteArray? = runCatching {
        withFb2Stream(file) { input ->
            val doc = Jsoup.parse(input, null, "", Parser.xmlParser())
            val binary = doc.select("binary[id=$binaryId]").firstOrNull() ?: return@withFb2Stream null
            Base64.getMimeDecoder().decode(binary.text().trim())
        }
    }.getOrNull()

    private fun <T> withFb2Stream(file: File, action: (InputStream) -> T): T {
        val signature = file.inputStream().use { it.readNBytes(ZIP_SIGNATURE.length) }
        val isZip = signature.contentEquals(ZIP_SIGNATURE.encodeToByteArray())
        if (!isZip) return file.inputStream().use(action)

        return ZipFile(file).use { zip ->
            val entry = zip.entries().asSequence()
                .firstOrNull { !it.isDirectory && it.name.endsWith(FB2_EXTENSION, true) }
                ?: error("Not a valid FB2 archive: missing .fb2 entry")
            zip.getInputStream(entry).use(action)
        }
    }

    private fun parse(input: InputStream, fallbackTitle: String): ParsedBook {
        val doc = Jsoup.parse(input, null, "", Parser.xmlParser())
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

    private const val FB2_EXTENSION = ".fb2"
    private const val ZIP_SIGNATURE = "PK"
}
