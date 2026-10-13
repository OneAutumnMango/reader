package dev.reader.app.ui.reader

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PageBuilderTest {

    @Test
    fun `groups blocks until the page would overflow`() {
        val pages = buildPages(heights = listOf(40f, 40f, 40f, 40f), maxHeight = 100f)
        assertEquals(listOf(0..1, 2..3), pages)
    }

    @Test
    fun `a single block taller than the page still gets its own page`() {
        val pages = buildPages(heights = listOf(200f, 10f), maxHeight = 100f)
        assertEquals(listOf(0..0, 1..1), pages)
    }

    @Test
    fun `an empty document produces one empty page`() {
        assertEquals(listOf(IntRange.EMPTY), buildPages(heights = emptyList(), maxHeight = 100f))
    }

    @Test
    fun `everything fits on one page`() {
        val pages = buildPages(heights = listOf(10f, 10f, 10f), maxHeight = 100f)
        assertEquals(listOf(0..2), pages)
    }
}
