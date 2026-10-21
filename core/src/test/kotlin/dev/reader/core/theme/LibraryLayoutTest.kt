package dev.reader.core.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryLayoutTest {

    @Test
    fun `columns is fixed per layout`() {
        assertEquals(4, LibraryLayout.SMALL.columns)
        assertEquals(3, LibraryLayout.MEDIUM.columns)
        assertEquals(2, LibraryLayout.LARGE.columns)
    }
}
