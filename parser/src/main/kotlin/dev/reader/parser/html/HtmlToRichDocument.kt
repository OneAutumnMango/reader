package dev.reader.parser.html

import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.Span
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

private val BOLD_TAGS = setOf("b", "strong")
private val ITALIC_TAGS = setOf("i", "em")
private val IMAGE_TAGS = setOf("img", "image")
private val CONTAINER_TAGS = setOf(
    "div", "section", "article", "main", "header", "footer", "body", "html", "blockquote",
)
private val HEADING_PATTERN = Regex("h[1-6]")
private val TITLE_CLASS_PATTERN = Regex("(?i)title|chapter|heading|chap-?num|ch-?head")
private const val MAX_TITLE_LENGTH = 120

/** Many EPUBs mark chapter titles with a class on a `<p>`/`<div>` instead of using `<h1>`-`<h6>`. */
private fun Element.looksLikeTitle(): Boolean {
    if (tagName() != "p" && tagName() != "div") return false
    val text = text().trim()
    return text.isNotEmpty() && text.length <= MAX_TITLE_LENGTH && TITLE_CLASS_PATTERN.containsMatchIn(className())
}

fun parseHtmlToRichDocument(html: String): RichDocument {
    val document = Jsoup.parse(html)
    val classRules = parseStylesheetClassRules(document)
    val blocks = mutableListOf<Block>()
    walkBlocks(document.body(), classRules, blocks)
    return RichDocument(blocks)
}

private fun walkBlocks(root: Element, classRules: Map<String, CssStyle>, blocks: MutableList<Block>) {
    for (element in root.children()) {
        val tag = element.tagName()
        val textAlign = styleOf(element, classRules).textAlign
        when {
            tag in IMAGE_TAGS -> {
                if (!element.isSvgFallbackImage()) blocks += element.imageBlock()
            }
            tag.matches(HEADING_PATTERN) -> {
                appendTextBlocks(element, classRules, blocks) { Block.Heading(tag.drop(1).toInt(), it, textAlign) }
            }
            element.looksLikeTitle() -> appendTextBlocks(element, classRules, blocks) {
                Block.Heading(2, it, textAlign)
            }
            tag == "ul" -> element.children().forEachIndexed { i, li ->
                appendTextBlocks(li, classRules, blocks) { Block.ListItem(it, ordered = false, index = i) }
            }
            tag == "ol" -> element.children().forEachIndexed { i, li ->
                appendTextBlocks(li, classRules, blocks) { Block.ListItem(it, ordered = true, index = i) }
            }
            tag in CONTAINER_TAGS -> walkBlocks(element, classRules, blocks)
            element.text().isNotBlank() -> appendTextBlocks(element, classRules, blocks) {
                Block.Paragraph(it, textAlign)
            }
            else -> walkBlocks(element, classRules, blocks)
        }
    }
}

private fun Element.imageBlock() = Block.Image(resolvedImageHref(), attr("alt"))

private fun Element.isSvgFallbackImage(): Boolean {
    if (tagName() != "img") return false
    // Calibre/Kindlegen pairs a full-size heading image with a tiny ~120px one for old mobi7
    // e-ink devices, marking the mobi7-only copy with data-amznremoved-m8; we're not mobi7, so
    // showing both just renders the real image next to a blurry upscaled duplicate.
    if (attr("data-amznremoved-m8").equals("true", ignoreCase = true)) return true
    // Inside an SVG that also has an <image> element (e.g. <svg><switch><image/><foreignObject><img/></foreignObject></switch></svg>)
    if (parents().any { ancestor -> ancestor.tagName() == "svg" && ancestor.selectFirst("image") != null }) return true
    // Directly before or after a sibling SVG that contains an <image> — fallback order varies by toolchain
    if (adjacentElementSiblings().any { it.tagName() == "svg" && it.selectFirst("image") != null }) return true
    // Not siblings, but both wrapped a level or two up (e.g. two <p>/<div> wrappers next to each
    // other) and pointing at the same resource — still the SVG/<img> fallback pair, just nested.
    val href = resolvedImageHref()
    if (href.isBlank()) return false
    var scope = parent()
    repeat(2) {
        val match = scope?.select("svg image")?.any { it.resolvedImageHref() == href } == true
        if (match) return true
        scope = scope?.parent()
    }
    return false
}

private fun Element.adjacentElementSiblings(): List<Element> =
    listOfNotNull(previousElementSibling(), nextElementSibling())

private fun Element.resolvedImageHref(): String =
    attr("src").ifBlank { attr("href") }.ifBlank { attr("xlink:href") }

private fun appendTextBlocks(
    element: Element,
    classRules: Map<String, CssStyle>,
    blocks: MutableList<Block>,
    textBlock: (List<Span>) -> Block,
) {
    val spans = mutableListOf<Span>()
    fun flush() {
        if (spans.isNotEmpty()) blocks += textBlock(spans.toList())
        spans.clear()
    }
    collectSpans(element, CssStyle(), TagEmphasis(), classRules, spans) {
        flush()
        blocks += it
    }
    flush()
}

/** Tag-based emphasis, tracked independently of CSS so a bare `<b>` still works with no CSS at all. */
private data class TagEmphasis(val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false)

/**
 * [tagEmphasis] accumulates independently of [inheritedCss], so an explicit `font-weight: normal`
 * doesn't get overridden back to bold by a `<b>` ancestor, and a plain `<b>` still applies when
 * no CSS is present at all.
 */
private fun collectSpans(
    element: Element,
    inheritedCss: CssStyle,
    tagEmphasis: TagEmphasis,
    classRules: Map<String, CssStyle>,
    spans: MutableList<Span>,
    onImage: (Block.Image) -> Unit,
) {
    val css = styleOf(element, classRules).mergeOver(inheritedCss)
    val tag = element.tagName()
    val resolvedBold = css.bold ?: (tagEmphasis.bold || tag in BOLD_TAGS)
    val resolvedItalic = css.italic ?: (tagEmphasis.italic || tag in ITALIC_TAGS)
    val resolvedUnderline = css.underline ?: (tagEmphasis.underline || tag == "u")
    val resolvedEmphasis = TagEmphasis(resolvedBold, resolvedItalic, resolvedUnderline)

    for (node in element.childNodes()) {
        when (node) {
            is TextNode -> {
                val text = node.text()
                if (text.isNotBlank()) {
                    spans += Span(text, resolvedBold, resolvedItalic, resolvedUnderline, css.color)
                }
            }
            is Element -> when {
                node.isSvgFallbackImage() -> Unit
                node.tagName() in IMAGE_TAGS -> onImage(node.imageBlock())
                else -> collectSpans(node, css, resolvedEmphasis, classRules, spans, onImage)
            }
        }
    }
}
