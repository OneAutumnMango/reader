package dev.reader.parser.epub

import dev.reader.core.model.BookFormat
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.wordCount
import dev.reader.parser.ParsedBook
import dev.reader.parser.ParsedChapter
import dev.reader.parser.html.parseHtmlToRichDocument
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

private const val EPUB_TYPE_SCAN_DEPTH = 4

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

            val items = opf.select("manifest > item")
            val manifest = items.associateBy(
                keySelector = { it.attr("id") },
                valueTransform = { it.resolve(baseDir) },
            )
            val navEntries = items.filter { "nav" in it.attr("properties").split(' ') }.map { it.resolve(baseDir) }
            val toc = readToc(zip, items, baseDir)
            val guideTypes = opf.select("guide > reference")
                .associate { it.resolve(baseDir) to it.attr("type").lowercase() }
            val spine = opf.select("spine > itemref").mapNotNull { ref ->
                manifest[ref.attr("idref")]?.let { it to (ref.attr("linear") != "no") }
            }
            val firstTocIndex = spine.indexOfFirst { (entry, _) -> entry in toc }
            val bookTitle = opf.select("metadata > title").text().ifBlank { file.nameWithoutExtension }

            var chapterNumber = 0
            val chapters = spine.mapIndexed { index, (entryName, linear) ->
                val html = zip.readEntryText(entryName)
                val document = parseHtmlToRichDocument(html)
                val heading = document.headingText()
                val extraLabel = EpubExtras.classify(
                    SpineItem(
                        entryName = entryName,
                        tocLabel = toc[entryName],
                        heading = heading,
                        semanticTypes = epubTypes(html) + listOfNotNull(guideTypes[entryName]),
                        isNav = entryName in navEntries,
                        linear = linear,
                        wordCount = document.wordCount(),
                        beforeFirstTocEntry = index < firstTocIndex,
                        headingIsBookTitle = heading != null && heading.equals(bookTitle, ignoreCase = true),
                    ),
                )
                val title = if (extraLabel != null) {
                    toc[entryName] ?: extraLabel
                } else {
                    chapterNumber++
                    toc[entryName] ?: heading ?: "Chapter $chapterNumber"
                }
                ParsedChapter.TextChapter(title, document, isExtra = extraLabel != null)
            }

            return ParsedBook(
                title = bookTitle,
                author = opf.select("metadata > creator").text(),
                coverPath = findCoverEntry(opf, manifest, baseDir),
                format = BookFormat.EPUB,
                chapters = chapters,
            )
        }
    }

    /** Entry name -> label from the EPUB 3 nav document, falling back to the EPUB 2 NCX. */
    private fun readToc(zip: ZipFile, items: List<Element>, baseDir: String): Map<String, String> {
        val nav = items.firstOrNull { "nav" in it.attr("properties").split(' ') }?.resolve(baseDir)
        val ncx = items.firstOrNull { it.attr("media-type") == "application/x-dtbncx+xml" }?.resolve(baseDir)
        val toc = LinkedHashMap<String, String>()
        runCatching {
            if (nav != null) {
                val doc = Jsoup.parse(zip.readEntryText(nav))
                val tocNav = doc.select("nav").firstOrNull { it.attr("epub:type") == "toc" } ?: doc.selectFirst("nav")
                val navDir = nav.substringBeforeLast('/', "")
                tocNav?.select("a[href]")?.forEach { link ->
                    toc.putIfAbsent(resolvePath(navDir, link.attr("href")), link.text().trim())
                }
            }
            if (toc.isEmpty() && ncx != null) {
                Jsoup.parse(zip.readEntryText(ncx), "", Parser.xmlParser()).select("navPoint").forEach { point ->
                    val src = point.selectFirst("content")?.attr("src") ?: return@forEach
                    val label = point.selectFirst("navLabel > text")?.text()?.trim().orEmpty()
                    toc.putIfAbsent(resolvePath(ncx.substringBeforeLast('/', ""), src), label)
                }
            }
        }
        return toc.filterValues { it.isNotBlank() }
    }

    private fun epubTypes(html: String): Set<String> =
        Jsoup.parse(html).body().allElements.take(EPUB_TYPE_SCAN_DEPTH)
            .flatMap { it.attr("epub:type").lowercase().split(' ') }
            .filter { it.isNotBlank() }
            .toSet()

    private fun RichDocument.headingText(): String? =
        (blocks.firstOrNull() as? Block.Heading)?.spans?.joinToString("") { it.text }?.trim()?.ifBlank { null }

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

    private fun Element.resolve(baseDir: String): String = resolvePath(baseDir, attr("href"))

    /** Resolves an href relative to [baseDir] into a zip entry name, dropping any #fragment and `..` segments. */
    private fun resolvePath(baseDir: String, href: String): String {
        val decoded = URLDecoder.decode(href.substringBefore('#'), "UTF-8")
        val segments = ArrayDeque<String>()
        (if (baseDir.isEmpty()) decoded else "$baseDir/$decoded").split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> segments.removeLastOrNull()
                else -> segments.addLast(segment)
            }
        }
        return segments.joinToString("/")
    }

    private fun ZipFile.readEntryText(name: String): String {
        val entry = getEntry(name) ?: error("EPUB is missing entry: $name")
        return getInputStream(entry).bufferedReader().use { it.readText() }
    }
}
