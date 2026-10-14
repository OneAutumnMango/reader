package dev.reader.parser.epub

import dev.reader.core.model.BookFormat
import dev.reader.core.richtext.Block
import dev.reader.parser.ParsedBook
import dev.reader.parser.ParsedChapter
import dev.reader.parser.html.parseHtmlToRichDocument
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipFile

object EpubParser {

    fun parseChapter(file: File, orderIndex: Int): ParsedChapter.TextChapter? = ZipFile(file).use { zip ->
        val container = Jsoup.parse(zip.readEntryText("META-INF/container.xml"), "", Parser.xmlParser())
        val opfPath = container.select("rootfile").first()?.attr("full-path")
            ?: error("EPUB container.xml has no rootfile")
        val opf = Jsoup.parse(zip.readEntryText(opfPath), "", Parser.xmlParser())
        val manifest = opf.select("manifest > item").associateBy { it.attr("id") }
        val entries = opf.select("spine > itemref").mapNotNull { manifest[it.attr("idref")] }
        val entry = entries.getOrNull(orderIndex) ?: return@use null
        val document = parseHtmlToRichDocument(zip.readEntryText(entry.resolve(opfPath.substringBeforeLast('/', ""))))
        val title = (document.blocks.firstOrNull() as? Block.Heading)
            ?.spans?.joinToString("") { it.text }?.ifBlank { null } ?: "Chapter ${orderIndex + 1}"
        ParsedChapter.TextChapter(title, document)
    }

    fun parse(file: File): ParsedBook {
        ZipFile(file).use { zip ->
            val containerXml = zip.readEntryText("META-INF/container.xml")
            val opfPath = Jsoup.parse(containerXml, "", Parser.xmlParser())
                .select("rootfile").first()?.attr("full-path")
                ?: error("EPUB container.xml has no rootfile")

            val opf = Jsoup.parse(zip.readEntryText(opfPath), "", Parser.xmlParser())
            val baseDir = opfPath.substringBeforeLast('/', "")

            val manifest = opf.select("manifest > item").associateBy(
                keySelector = { it.attr("id") },
                valueTransform = { it.resolve(baseDir) },
            )
            val spineIds = opf.select("spine > itemref").map { it.attr("idref") }

            val chapters = spineIds.mapIndexed { index, id ->
                val entryName = manifest[id] ?: return@mapIndexed null
                val document = parseHtmlToRichDocument(zip.readEntryText(entryName))
                val title = (document.blocks.firstOrNull() as? Block.Heading)
                    ?.spans?.joinToString("") { it.text }
                    ?.ifBlank { null }
                    ?: "Chapter ${index + 1}"
                ParsedChapter.TextChapter(title, document)
            }.filterNotNull()

            return ParsedBook(
                title = opf.select("metadata > title").text().ifBlank { file.nameWithoutExtension },
                author = opf.select("metadata > creator").text(),
                coverPath = findCoverEntry(opf, manifest, baseDir),
                format = BookFormat.EPUB,
                chapters = chapters,
            )
        }
    }

    /** Reads the cover image's raw bytes out of the epub, by its manifest-resolved entry name. */
    fun extractCover(file: File, entryName: String): ByteArray? = runCatching {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry(entryName) ?: return@use null
            zip.getInputStream(entry).use { it.readBytes() }
        }
    }.getOrNull()

    private fun findCoverEntry(
        opf: org.jsoup.nodes.Document,
        manifest: Map<String, String>,
        baseDir: String,
    ): String? {
        val coverId = opf.select("metadata > meta[name=cover]").attr("content")
        manifest[coverId]?.let { return it }
        return opf.select("manifest > item[properties~=cover-image]").first()?.resolve(baseDir)
    }

    private fun org.jsoup.nodes.Element.resolve(baseDir: String): String {
        val href = java.net.URLDecoder.decode(attr("href"), "UTF-8")
        return if (baseDir.isEmpty()) href else "$baseDir/$href"
    }

    private fun ZipFile.readEntryText(name: String): String {
        val entry = getEntry(name) ?: error("EPUB is missing entry: $name")
        return getInputStream(entry).bufferedReader().use { it.readText() }
    }
}
