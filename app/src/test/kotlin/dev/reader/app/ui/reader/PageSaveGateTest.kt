package dev.reader.app.ui.reader

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageSaveGateTest {

    private val gate = PageSaveGate()

    private fun report(
        index: Int,
        count: Int,
        active: Boolean = true,
        finished: Boolean = false,
        userScrolled: Boolean? = null,
    ) = gate.onReport(index, count, active, finished, userScrolled)

    @Test
    fun `first report is not saved`() {
        assertFalse(report(3, 10))
    }

    @Test
    fun `turning a page saves`() {
        report(3, 10)
        assertTrue(report(4, 10))
        assertFalse(report(4, 10))
    }

    @Test
    fun `repagination does not save`() {
        assertFalse(report(3, 10))
        assertFalse(report(4, 14))
        assertTrue(report(5, 14))
    }

    @Test
    fun `one page chapter saves once when unfinished`() {
        assertTrue(report(0, 1))
        assertFalse(report(0, 1))
    }

    @Test
    fun `finished one page chapter does not save`() {
        assertFalse(report(0, 1, finished = true))
    }

    @Test
    fun `inactive reports never save`() {
        assertFalse(report(0, 1, active = false))
        assertFalse(report(3, 10, active = false))
        assertFalse(report(4, 10, active = false))
        // The inactive reports still moved the baseline.
        assertFalse(report(4, 10))
    }

    @Test
    fun `one page chapter saves on becoming active`() {
        assertFalse(report(0, 1, active = false))
        assertTrue(report(0, 1))
    }

    @Test
    fun `continuous index change without user scroll does not save`() {
        report(3, 10, userScrolled = false)
        assertFalse(report(4, 10, userScrolled = false))
        // The baseline followed the layout shift.
        assertFalse(report(4, 10, userScrolled = true))
    }

    @Test
    fun `continuous index change after user scroll saves`() {
        report(3, 10, userScrolled = false)
        assertTrue(report(4, 10, userScrolled = true))
    }

    @Test
    fun `a single long block is not saved without a user scroll`() {
        assertFalse(report(0, 1, userScrolled = false))
    }
}
