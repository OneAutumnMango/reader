package dev.reader.parser.odt

import dev.reader.core.model.BookFormat
import dev.reader.parser.ParsedBook
import dev.reader.parser.ParsedChapter
import dev.reader.parser.html.parseHtmlToRichDocument
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.Base64
import java.util.zip.ZipFile

object OdtParser {

    fun parse(file: File): ParsedBook = if (file.isZip()) parseZip(file) else parseFlat(file)

    private fun parseZip(file: File): ParsedBook = ZipFile(file).use { zip ->
        val contentEntry = zip.getEntry("content.xml") ?: error("Not a valid ODT: missing content.xml")
        val metadata = zip.readDocument("meta.xml")
        val stylesDocument = zip.readDocument("styles.xml")
        val manifest = zip.readDocument("META-INF/manifest.xml")
        require(
            manifest?.getElementsByTag("manifest:file-entry")?.none { entry ->
                entry.getElementsByTag("manifest:encryption-data").isNotEmpty()
            } != false,
        ) { "Encrypted ODT entries are not supported" }

        zip.getInputStream(contentEntry).use { input ->
            parseContent(input, metadata, stylesDocument, file.nameWithoutExtension) { path ->
                path.toArchiveEntryPath()?.let { entryPath -> zip.readEntry(entryPath) }
            }
        }
    }

    private fun parseFlat(file: File): ParsedBook = file.inputStream().use { input ->
        parseContent(input, null, null, file.nameWithoutExtension) { null }
    }

    private fun parseContent(
        input: InputStream,
        metadata: org.jsoup.nodes.Document?,
        stylesDocument: org.jsoup.nodes.Document?,
        fallbackTitle: String,
        imageBytes: (String) -> ByteArray?,
    ): ParsedBook {
        val document = Jsoup.parse(input, null, "", Parser.xmlParser())
        val officeText = document.getElementsByTag("office:body").firstOrNull()
            ?.getElementsByTag("office:text")?.firstOrNull()
            ?: error("Not a valid ODT: missing office:body/office:text")
        val styles = OdtStyles(document, stylesDocument)
        val html = StringBuilder()
        renderBlocks(officeText, html, styles, imageBytes)
        val title = metadata?.getElementsByTag("dc:title")?.firstOrNull()?.text()?.trim()
            ?.takeIf(String::isNotBlank)
            ?: document.getElementsByTag("dc:title").firstOrNull()?.text()?.trim()?.takeIf(String::isNotBlank)
            ?: fallbackTitle
        val author = metadata?.getElementsByTag("dc:creator")?.firstOrNull()?.text()?.trim()
            ?: document.getElementsByTag("dc:creator").firstOrNull()?.text()?.trim().orEmpty()

        return ParsedBook(
            title = title,
            author = author,
            coverPath = null,
            format = BookFormat.ODT,
            chapters = listOf(ParsedChapter.TextChapter(title, parseHtmlToRichDocument(html.toString()))),
        )
    }

    private fun renderBlocks(
        root: Element,
        html: StringBuilder,
        styles: OdtStyles,
        imageBytes: (String) -> ByteArray?,
    ) {
        root.children().forEach { child ->
            when (child.tagName()) {
                "text:p" -> renderParagraph(child, "p", html, styles, imageBytes)
                "text:h" -> {
                    val level = child.attr("text:outline-level").toIntOrNull()
                        ?.coerceIn(MIN_HEADING_LEVEL, MAX_HEADING_LEVEL) ?: DEFAULT_HEADING_LEVEL
                    renderParagraph(child, "h$level", html, styles, imageBytes)
                }
                "text:list" -> renderList(child, html, styles, imageBytes)
                else -> renderBlocks(child, html, styles, imageBytes)
            }
        }
    }

    private fun renderParagraph(
        paragraph: Element,
        tag: String,
        html: StringBuilder,
        styles: OdtStyles,
        imageBytes: (String) -> ByteArray?,
    ) {
        html.append('<').append(tag).append('>')
        appendInline(paragraph, html, styles.resolve(paragraph.attr("text:style-name")), styles, imageBytes)
        html.append("</").append(tag).append('>')
    }

    private fun renderList(
        list: Element,
        html: StringBuilder,
        styles: OdtStyles,
        imageBytes: (String) -> ByteArray?,
    ) {
        val tag = if (styles.isOrderedList(list.attr("text:style-name"))) "ol" else "ul"
        html.append('<').append(tag).append('>')
        list.children().filter { it.tagName() == "text:list-item" }.forEach { item ->
            html.append("<li>")
            item.children().forEach { child ->
                when (child.tagName()) {
                    "text:p" -> renderParagraph(child, "span", html, styles, imageBytes)
                    "text:h" -> renderParagraph(child, "span", html, styles, imageBytes)
                    "text:list" -> renderList(child, html, styles, imageBytes)
                    else -> renderBlocks(child, html, styles, imageBytes)
                }
            }
            html.append("</li>")
        }
        html.append("</").append(tag).append('>')
    }

    private fun appendInline(
        parent: Element,
        html: StringBuilder,
        inherited: TextStyle,
        styles: OdtStyles,
        imageBytes: (String) -> ByteArray?,
    ) {
        parent.childNodes().forEach { node ->
            when (node) {
                is TextNode -> appendStyledText(node.wholeText, inherited, html)
                is Element -> when (node.tagName()) {
                    "text:s" -> {
                        val count = node.attr("text:c").toIntOrNull()?.coerceIn(1, MAX_REPEATED_SPACE) ?: 1
                        appendStyledText(
                            "\u00a0".repeat(count),
                            inherited,
                            html,
                        )
                    }
                    "text:tab" -> appendStyledText("\t", inherited, html)
                    "text:line-break" -> appendStyledText(
                        "\n",
                        inherited,
                        html,
                    )
                    "draw:image" -> appendImage(node, html, imageBytes)
                    else -> {
                        val resolved = inherited.merge(styles.resolve(node.attr("text:style-name")))
                        appendInline(node, html, resolved, styles, imageBytes)
                    }
                }
            }
        }
    }

    private fun appendStyledText(text: String, style: TextStyle, html: StringBuilder) {
        if (style.bold == true) html.append("<strong>")
        if (style.italic == true) html.append("<em>")
        html.append(escapeHtml(text))
        if (style.italic == true) html.append("</em>")
        if (style.bold == true) html.append("</strong>")
    }

    private fun appendImage(element: Element, html: StringBuilder, imageBytes: (String) -> ByteArray?) {
        val binaryData = element.getElementsByTag("office:binary-data").firstOrNull()?.text()?.trim()
        val path = element.attr("xlink:href")
        val bytes = binaryData?.let { runCatching { Base64.getMimeDecoder().decode(it) }.getOrNull() }
            ?: path.takeIf(String::isNotBlank)?.let(imageBytes)
            ?: return
        val mimeType = element.attr("draw:mime-type").ifBlank { mimeType(path) }
        html.append("<img src=\"data:").append(escapeHtml(mimeType)).append(";base64,")
            .append(Base64.getEncoder().encodeToString(bytes)).append("\"/>")
    }

    private const val MIN_HEADING_LEVEL = 1
    private const val MAX_HEADING_LEVEL = 6
    private const val DEFAULT_HEADING_LEVEL = 1
    private const val MAX_REPEATED_SPACE = 1000
}

private data class TextStyle(val bold: Boolean? = null, val italic: Boolean? = null) {
    fun merge(other: TextStyle): TextStyle = TextStyle(other.bold ?: bold, other.italic ?: italic)
}

private class OdtStyles(content: org.jsoup.nodes.Document, stylesDocument: org.jsoup.nodes.Document?) {
    private val definitions = (
        stylesDocument?.getElementsByTag("style:style").orEmpty() + content.getElementsByTag("style:style")
        ).associate { element ->
        element.attr("style:name") to StyleDefinition(
            parent = element.attr("style:parent-style-name"),
            style = element.getElementsByTag("style:text-properties").firstOrNull()?.let(::textStyle)
                ?: TextStyle(),
        )
    }
    private val orderedListStyles = (
        stylesDocument?.getElementsByTag("text:list-style").orEmpty() + content.getElementsByTag("text:list-style")
        )
        .filter { listStyle -> listStyle.getElementsByTag("text:list-level-style-number").isNotEmpty() }
        .mapTo(mutableSetOf()) { it.attr("style:name") }

    fun isOrderedList(name: String): Boolean = name in orderedListStyles

    fun resolve(name: String): TextStyle = resolve(name, emptySet())

    private fun resolve(name: String, visited: Set<String>): TextStyle {
        if (name.isBlank() || name in visited) return TextStyle()
        val definition = definitions[name] ?: return TextStyle()
        return resolve(definition.parent, visited + name).merge(definition.style)
    }

    private fun textStyle(properties: Element): TextStyle = TextStyle(
        bold = properties.attr("fo:font-weight").takeIf(String::isNotBlank)?.let { weight ->
            weight == "bold" || weight.toIntOrNull()?.let { it >= MIN_BOLD_FONT_WEIGHT } == true
        },
        italic = properties.attr("fo:font-style").takeIf(String::isNotBlank)
            ?.let { it == "italic" || it == "oblique" },
    )

    private data class StyleDefinition(val parent: String, val style: TextStyle)
}

private const val MIN_BOLD_FONT_WEIGHT = 600
private fun ZipFile.readDocument(path: String): org.jsoup.nodes.Document? = getEntry(path)?.let { entry ->
    getInputStream(entry).use { Jsoup.parse(it, null, "", Parser.xmlParser()) }
}

private fun ZipFile.readEntry(path: String): ByteArray? =
    getEntry(path)?.takeUnless { it.isDirectory }?.let { entry ->
        getInputStream(entry).use(InputStream::readBytes)
    }

private fun String.toArchiveEntryPath(): String? =
    runCatching { URI(replace(" ", "%20")) }.getOrNull()?.let { uri ->
        if (uri.isAbsolute || uri.rawAuthority != null) return null
        val path = uri.path ?: return null
        if (path.startsWith('/') || '\\' in path) return null
        val segments = path.split('/')
        if (segments.any { it == ".." }) return null
        segments.filter { it.isNotEmpty() && it != "." }.joinToString("/").takeIf(String::isNotEmpty)
    }

private fun File.isZip(): Boolean = inputStream().use { input ->
    val signature = input.readNBytes(ODT_ZIP_SIGNATURE.length)
    signature.contentEquals(ODT_ZIP_SIGNATURE.encodeToByteArray())
}

private fun mimeType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "svg" -> "image/svg+xml"
    "webp" -> "image/webp"
    else -> "image/png"
}

private fun escapeHtml(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

private const val ODT_ZIP_SIGNATURE = "PK"
