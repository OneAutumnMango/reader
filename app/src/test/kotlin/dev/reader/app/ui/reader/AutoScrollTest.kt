package dev.reader.app.ui.reader

import org.junit.Test
import kotlin.test.assertEquals

class AutoScrollTest {
    private val timeout = 300L

    @Test
    fun `speed steps and clamps to the allowed range`() {
        assertEquals(45f, stepAutoScrollSpeed(40f, 1))
        assertEquals(35f, stepAutoScrollSpeed(40f, -1))
        assertEquals(AUTO_SCROLL_MIN_SPEED, stepAutoScrollSpeed(AUTO_SCROLL_MIN_SPEED, -1))
        assertEquals(AUTO_SCROLL_MAX_SPEED, stepAutoScrollSpeed(AUTO_SCROLL_MAX_SPEED, 1))
    }

    @Test
    fun `tap while running pauses`() {
        val s = AutoScrollTaps(AutoScrollMode.RUNNING).onTap(0, timeout)
        assertEquals(AutoScrollMode.PAUSED, s.mode)
    }

    @Test
    fun `tap while off does nothing`() {
        assertEquals(AutoScrollMode.OFF, AutoScrollTaps(AutoScrollMode.OFF).onTap(0, timeout).mode)
    }

    @Test
    fun `single tap while paused stays paused`() {
        val s = AutoScrollTaps(AutoScrollMode.PAUSED).onTap(0, timeout)
        assertEquals(AutoScrollMode.PAUSED, s.mode)
    }

    @Test
    fun `double tap while paused resumes`() {
        val s = AutoScrollTaps(AutoScrollMode.PAUSED).onTap(1000, timeout).onTap(1200, timeout)
        assertEquals(AutoScrollMode.RUNNING, s.mode)
    }

    @Test
    fun `slow second tap while paused does not resume`() {
        val s = AutoScrollTaps(AutoScrollMode.PAUSED).onTap(1000, timeout).onTap(1500, timeout)
        assertEquals(AutoScrollMode.PAUSED, s.mode)
    }

    @Test
    fun `the tap that paused does not count towards a resume double tap`() {
        val s = AutoScrollTaps(AutoScrollMode.RUNNING).onTap(1000, timeout).onTap(1100, timeout)
        assertEquals(AutoScrollMode.PAUSED, s.mode)
    }
}
