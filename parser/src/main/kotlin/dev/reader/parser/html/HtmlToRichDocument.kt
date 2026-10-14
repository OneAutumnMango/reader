package dev.reader.parser.html

import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.Span
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

private val BOLD_TAGS = setOf("b", "strong")
private val ITALIC_TAGS = setOf("i", "em")
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
            tag == "img" -> blocks += Block.Image(element.attr("src"), element.attr("alt"))
            tag.matches(HEADING_PATTERN) -> {
                blocks += Block.Heading(tag.drop(1).toInt(), spansOf(element, classRules), textAlign)
            }
            element.looksLikeTitle() -> blocks += Block.Heading(2, spansOf(element, classRules), textAlign)
            tag == "ul" -> element.children().forEachIndexed { i, li ->
                blocks += Block.ListItem(spansOf(li, classRules), ordered = false, index = i)
            }
            tag == "ol" -> element.children().forEachIndexed { i, li ->
                blocks += Block.ListItem(spansOf(li, classRules), ordered = true, index = i)
            }
            tag in CONTAINER_TAGS -> walkBlocks(element, classRules, blocks)
            element.text().isNotBlank() -> blocks += Block.Paragraph(spansOf(element, classRules), textAlign)
            else -> walkBlocks(element, classRules, blocks)
        }
    }
}

private fun spansOf(element: Element, classRules: Map<String, CssStyle>): List<Span> {
    val spans = mutableListOf<Span>()
    collectSpans(element, CssStyle(), TagEmphasis(), classRules, spans)
    return spans
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
            is Element -> collectSpans(node, css, resolvedEmphasis, classRules, spans)
        }
    }
}
