package dev.reader.parser.text

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChapterDetectorTest {

    private val sentence = "The wind moved slowly across the quiet hills while the travellers walked on."

    /** A realistic-sized book: each heading followed by several prose paragraphs ending in full stops. */
    private fun book(headings: List<String>, paragraphsPerChapter: Int = 8, extra: String = ""): String =
        headings.joinToString("\n\n") { heading ->
            val prose = List(paragraphsPerChapter) { List(4) { sentence }.joinToString(" ") }.joinToString("\n\n")
            "$heading\n\n$extra$prose"
        }

    private fun detectedTitles(text: String, markdown: Boolean = false): List<String> {
        val pattern = requireNotNull(ChapterSplitter.detectPattern(text, markdown)) { "nothing detected" }
        return ChapterSplitter.breaks(text, pattern).map { it.title }
    }

    @Test
    fun `finds standalone title lines with no closing punctuation`() {
        val titles = listOf("The Arrival", "A Long Night", "Into the Woods", "Homecoming")

        assertEquals(titles, detectedTitles(book(titles)))
    }

    @Test
    fun `finds standalone bold lines in markdown`() {
        val titles = listOf("**The Arrival**", "**A Long Night**", "**Homecoming**")
        val text = book(titles, extra = "Some **bold** words mid-sentence.\n\n")

        val pattern = requireNotNull(ChapterSplitter.detectPattern(text, markdown = true))

        val detected = ChapterSplitter.breaks(text, pattern).map { it.title }
        assertEquals(listOf("The Arrival", "A Long Night", "Homecoming"), detected)
    }

    @Test
    fun `learns a recurring prefix from the book itself`() {
        val titles = (1..5).map { "Log Entry #$it" }

        assertEquals(titles, detectedTitles(book(titles)))
    }

    @Test
    fun `handles cjk, bracketed and non-english chapter headings`() {
        val cjk = listOf("第一章 开始", "第二章 旅程", "第三章 结局")
        assertEquals(cjk, detectedTitles(book(cjk)))

        val bracketed = listOf("[Chapter 1] Start", "[Chapter 2] Middle", "[Chapter 3] End")
        assertEquals(bracketed, detectedTitles(book(bracketed)))

        val spanish = listOf("Capítulo 1", "Capítulo 2", "Capítulo 3")
        assertEquals(spanish, detectedTitles(book(spanish)))
    }

    @Test
    fun `ignores scene breaks and short dialogue inside chapters`() {
        val titles = listOf("Chapter One", "Chapter Two", "Chapter Three")
        val noise = "\"Wait\"\n\n* * *\n\nHmm\n\n"

        assertEquals(titles, detectedTitles(book(titles, extra = noise)))
    }

    @Test
    fun `numbered headings that count up beat a weaker structural match`() {
        val titles = (1..6).map { "$it" }

        assertEquals(titles, detectedTitles(book(titles)))
    }

    @Test
    fun `plain prose produces no suggestion`() {
        assertNull(ChapterSplitter.detectPattern(book(listOf("It was a calm day.")), markdown = false))
    }

    @Test
    fun `offers alternative splits, best first`() {
        val text = book(listOf("# Part One", "## The Arrival", "## The Storm", "# Part Two", "## The Return"))

        val suggestions = ChapterSplitter.suggestions(text, markdown = true)

        assertTrue(suggestions.size >= 2)
        assertTrue(suggestions.map { it.count }.toSet().size >= 2, "alternatives should split differently")
    }

    @Test
    fun `reads chapter numbers in many notations`() {
        assertEquals(12, ChapterNumbers.of("Chapter 12"))
        assertEquals(4, ChapterNumbers.of("CHAPTER IV"))
        assertEquals(21, ChapterNumbers.of("Part Twenty-One"))
        assertEquals(12, ChapterNumbers.of("第十二章"))
        assertNull(ChapterNumbers.of("Homecoming"))
    }
}
