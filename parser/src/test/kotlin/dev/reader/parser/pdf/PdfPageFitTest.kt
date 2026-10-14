package dev.reader.parser.pdf

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PdfPageFitTest {

    @Test
    fun `portrait page in a portrait screen is limited by width`() {
        assertEquals(1080 to 1528, fitWithin(pageWidth = 595, pageHeight = 842, maxWidth = 1080, maxHeight = 2200))
    }

    @Test
    fun `portrait page in a wide box is limited by height`() {
        assertEquals(707 to 1000, fitWithin(pageWidth = 595, pageHeight = 842, maxWidth = 2000, maxHeight = 1000))
    }

    @Test
    fun `landscape page keeps its ratio`() {
        assertEquals(1080 to 763, fitWithin(pageWidth = 842, pageHeight = 595, maxWidth = 1080, maxHeight = 2200))
    }
}
