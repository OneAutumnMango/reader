package dev.reader.app.ui.reader

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ReadingSessionTimerTest {

    @Test
    fun `disposal after pause cannot record the same interval or locked time twice`() {
        val timer = ReadingSessionTimer()
        val start = Instant.parse("2026-10-19T10:00:00Z")
        timer.setActive(true, 0L, start)
        assertEquals(10.seconds, timer.setActive(false, 10.seconds.inWholeNanoseconds, start)?.duration)
        assertNull(timer.setActive(false, 24.hours.inWholeNanoseconds, start + 24.hours))
        timer.setActive(true, 24.hours.inWholeNanoseconds, start + 24.hours)
        assertEquals(
            8.seconds,
            timer.setActive(false, (24.hours + 8.seconds).inWholeNanoseconds, start + 24.hours)?.duration,
        )
    }

    @Test
    fun `inactive prefetched chapter does not start a session`() {
        val timer = ReadingSessionTimer()
        val now = Instant.parse("2026-10-19T10:00:00Z")

        assertNull(timer.setActive(false, 0L, now))
        timer.setActive(true, 10.seconds.inWholeNanoseconds, now)
        val interval = timer.setActive(false, 12.seconds.inWholeNanoseconds, now)

        assertEquals(2.seconds, interval?.duration)
    }

    @Test
    fun `pause flushes elapsed time without counting the paused interval or wall clock jump`() {
        val timer = ReadingSessionTimer()
        val start = Instant.parse("2026-10-19T10:00:00Z")
        timer.setActive(true, 0L, start)

        val first = timer.setActive(false, 8.seconds.inWholeNanoseconds, start + 24.hours)
        timer.setActive(true, 24.hours.inWholeNanoseconds, start + 24.hours)
        val second = timer.setActive(
            false,
            24.hours.inWholeNanoseconds + 6.seconds.inWholeNanoseconds,
            start + 48.hours,
        )

        assertEquals(8.seconds, first?.duration)
        assertEquals(start + 8.seconds, first?.endedAt)
        assertEquals(6.seconds, second?.duration)
        assertEquals(start + 24.hours, second?.startedAt)
    }
}
