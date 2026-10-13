package dev.reader.parser.html

import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.Span
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

private val BOLD_TAGS = setOf("b", "strong")
private val ITALIC_TAGS = setOf("i", "em")
private val CONTAINER_TAGS = setOf("div", "section", "article", "body", "html", "blockquote")
private val HEADING_PATTERN = Regex("h[1-6]")

fun parseHtmlToRichDocument(html: String): RichDocument {
    val body = Jsoup.parse(html).body()
    val blocks = mutableListOf<Block>()
    walkBlocks(body, blocks)
    return RichDocument(blocks)
}

private fun walkBlocks(root: Element, blocks: MutableList<Block>) {
    for (element in root.children()) {
        val tag = element.tagName()
        when {
            tag == "img" -> blocks += Block.Image(element.attr("src"), element.attr("alt"))
            tag.matches(HEADING_PATTERN) -> blocks += Block.Heading(tag.drop(1).toInt(), spansOf(element))
            tag == "ul" -> element.children().forEachIndexed { i, li ->
                blocks += Block.ListItem(spansOf(li), ordered = false, index = i)
            }
            tag == "ol" -> element.children().forEachIndexed { i, li ->
                blocks += Block.ListItem(spansOf(li), ordered = true, index = i)
            }
            tag in CONTAINER_TAGS -> walkBlocks(element, blocks)
            element.text().isNotBlank() -> blocks += Block.Paragraph(spansOf(element))
            else -> walkBlocks(element, blocks)
        }
    }
}

private fun spansOf(element: Element): List<Span> {
    val spans = mutableListOf<Span>()
    collectSpans(element, bold = false, italic = false, underline = false, spans = spans)
    return spans
}

private fun collectSpans(
    element: Element,
    bold: Boolean,
    italic: Boolean,
    underline: Boolean,
    spans: MutableList<Span>,
) {
    for (node in element.childNodes()) {
        when (node) {
            is TextNode -> {
                val text = node.text()
                if (text.isNotBlank()) spans += Span(text, bold, italic, underline)
            }
            is Element -> {
                val tag = node.tagName()
                collectSpans(
                    element = node,
                    bold = bold || tag in BOLD_TAGS,
                    italic = italic || tag in ITALIC_TAGS,
                    underline = underline || tag == "u",
                    spans = spans,
                )
            }
        }
    }
}
