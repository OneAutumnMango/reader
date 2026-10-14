package dev.reader.parser.text

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChapterSplitterTest {

    private val novel = """
        My Novel
        by Someone

        Chapter 1: The Start

        It began.

        Part of the problem was nobody listened.

        CHAPTER II

        It went on.

        Epilogue

        It ended.
    """.trimIndent()

    @Test
    fun `auto-detects keyword headings without matching prose that starts with a keyword`() {
        val pattern = requireNotNull(ChapterSplitter.detectPattern(novel, markdown = false))

        assertEquals(
            listOf("Chapter 1: The Start", "CHAPTER II", "Epilogue"),
            ChapterSplitter.breaks(novel, pattern).map { it.title },
        )
    }

    @Test
    fun `prefers markdown h1 headings and strips the hashes from titles`() {
        val markdown = "# The Return\n\nText.\n\n## Aside\n\nMore.\n\n# The End\n\nDone."

        val pattern = requireNotNull(ChapterSplitter.detectPattern(markdown, markdown = true))

        assertEquals(listOf("The Return", "The End"), ChapterSplitter.breaks(markdown, pattern).map { it.title })
    }

    @Test
    fun `falls back to isolated numbered lines but ignores list items`() {
        val text = "1\n\nOne.\n\n2\n\nTwo.\n- 1. not this\n3. nor this\n\n3\n\nThree."

        val pattern = requireNotNull(ChapterSplitter.detectPattern(text, markdown = false))

        assertEquals(listOf("1", "2", "3"), ChapterSplitter.breaks(text, pattern).map { it.title })
    }

    @Test
    fun `finds nothing in plain prose`() {
        assertNull(ChapterSplitter.detectPattern("Just a story.\n\nWith paragraphs.", markdown = false))
    }

    @Test
    fun `prefix patterns match literally`() {
        val text = "## One\n\na\n\n## Two\n\nb"

        val breaks = ChapterSplitter.breaks(text, ChapterSplitter.prefixPattern("## "))

        assertEquals(listOf("One", "Two"), breaks.map { it.title })
        assertEquals("a", breaks.first().preview)
    }

    @Test
    fun `split keeps a preamble chunk and each heading with its chapter`() {
        val pattern = requireNotNull(ChapterSplitter.detectPattern(novel, markdown = false))

        val chunks = ChapterSplitter.split(novel, pattern)

        assertEquals(listOf(null, "Chapter 1: The Start", "CHAPTER II", "Epilogue"), chunks.map { it.title })
        assertEquals("My Novel\nby Someone\n", chunks.first().text)
    }
}
