package dev.reader.core.theme

import kotlin.test.Test
import kotlin.test.assertTrue

class LibraryLayoutTest {

    @Test
    fun `larger layouts have wider grid columns`() {
        assertTrue(LibraryLayout.SMALL.gridColumnMinWidthDp < LibraryLayout.MEDIUM.gridColumnMinWidthDp)
        assertTrue(LibraryLayout.MEDIUM.gridColumnMinWidthDp < LibraryLayout.LARGE.gridColumnMinWidthDp)
    }
}
