package dev.reader.app.ui.reader

import dev.reader.core.richtext.Span
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PageBuilderTest {

    private fun pages(heights: List<Float>, page: Float = 100f, spacing: Float = 0f) =
        paginate(heights, page, spacing, measure = { it })

    @Test
    fun `groups items until the page would overflow, counting spacing`() {
        assertEquals(listOf(listOf(40f, 40f), listOf(40f, 40f)), pages(listOf(40f, 40f, 40f, 40f)))
        assertEquals(listOf(listOf(45f), listOf(45f)), pages(listOf(45f, 45f), spacing = 20f))
    }

    @Test
    fun `an item taller than a page that can't be split gets its own page`() {
        assertEquals(listOf(listOf(200f), listOf(10f)), pages(listOf(200f, 10f)))
    }

    @Test
    fun `an empty document produces one empty page`() {
        assertEquals(listOf(emptyList()), pages(emptyList()))
    }

    @Test
    fun `items that don't fit are split so the page fills up`() {
        // Items are "line counts" of 10px each; splitting keeps whole lines.
        val result = paginate(
            items = listOf(5, 8),
            pageHeight = 100f,
            spacing = 0f,
            measure = { it * 10f },
            split = { lines, available ->
                (available / 10).toInt().takeIf { it in 1 until lines }?.let { it to lines - it }
            },
        )

        assertEquals(listOf(listOf(5, 5), listOf(3)), result)
    }

    @Test
    fun `a heading is never left alone at the bottom of a page`() {
        val result = paginate(
            items = listOf("p", "H", "p"),
            pageHeight = 100f,
            spacing = 0f,
            measure = { if (it == "H") 10f else 60f },
            keepWithNext = { it == "H" },
        )

        assertEquals(listOf(listOf("p"), listOf("H", "p")), result)
    }

    @Test
    fun `spans split at a character offset, across span boundaries and separators`() {
        val spans = listOf(Span("Hello "), Span("brave", bold = true), Span(" new world"))

        val (head, tail) = splitSpans(spans, offset = 8)

        assertEquals(listOf("Hello ", "br"), head.map { it.text })
        assertEquals(listOf("ave", " new world"), tail.map { it.text })
        assertEquals(true, tail.first().bold)

        val (headSep, tailSep) = splitSpans(listOf(Span("ab"), Span("cd")), offset = 3, separator = 1)
        assertEquals(listOf("ab"), headSep.map { it.text })
        assertEquals(listOf("cd"), tailSep.map { it.text })
    }
}
