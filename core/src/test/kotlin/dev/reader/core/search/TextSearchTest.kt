package dev.reader.core.search

import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.Span
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextSearchTest {

    private val document = RichDocument(
        listOf(
            Block.Heading(1, listOf(Span("Chapter One"))),
            Block.Paragraph(listOf(Span("This is\tthe start. this  is the middle.\u00a0This\u00a0is the end."))),
            Block.Image("cover.png", "this is alt text"),
        ),
    )

    private fun count(query: String, options: SearchOptions = SearchOptions()) =
        TextSearch.find(document, requireNotNull(TextSearch.compile(query, options)).getOrThrow()).size

    @Test
    fun `plain search is literal and ignores case unless asked`() {
        assertEquals(1, count("this is"))
        assertEquals(0, count("this is", SearchOptions(matchCase = true)))
        assertEquals(1, count("Chapter one"))
        assertEquals(0, count("Chapter one", SearchOptions(matchCase = true)))
        assertEquals(0, count("start.*middle"))
    }

    @Test
    fun `any whitespace lets spaces in the query match tabs, runs and non-breaking spaces`() {
        assertEquals(3, count("this is", SearchOptions(anyWhitespace = true)))
        assertEquals(2, count("This is", SearchOptions(anyWhitespace = true, matchCase = true)))
    }

    @Test
    fun `regex mode supports patterns and reports invalid ones`() {
        assertEquals(1, count("start.*middle", SearchOptions(regex = true)))
        assertEquals(3, count("th\\w+[\\s\\p{Z}]+is", SearchOptions(regex = true)))
        assertTrue(requireNotNull(TextSearch.compile("(unclosed", SearchOptions(regex = true))).isFailure)
        assertNull(TextSearch.compile("", SearchOptions()))
    }

    @Test
    fun `matches report their block and a snippet around them`() {
        val regex = requireNotNull(TextSearch.compile("middle", SearchOptions())).getOrThrow()
        val match = TextSearch.find(document, regex).single()

        assertEquals(1, match.block)
        assertEquals("middle", match.snippet.text.substring(match.snippet.match.first, match.snippet.match.last + 1))
        assertTrue(match.snippet.text.startsWith("This is the start."))
    }

    @Test
    fun `empty regex matches are skipped and results stop at the limit`() {
        val regex = requireNotNull(TextSearch.compile("x*", SearchOptions(regex = true))).getOrThrow()
        assertTrue(TextSearch.find(document, regex).isEmpty())
        val any = requireNotNull(TextSearch.compile("e", SearchOptions())).getOrThrow()
        assertEquals(2, TextSearch.find(document, any, limit = 2).size)
    }
}
