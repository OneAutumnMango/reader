package dev.reader.app.ui.stats

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DurationFormatTest {

    @Test
    fun `formats hours and minutes`() {
        assertEquals("2h 15m", 135.minutes.toReadableDuration())
    }

    @Test
    fun `formats minutes only when under an hour`() {
        assertEquals("45m", 45.minutes.toReadableDuration())
    }

    @Test
    fun `formats zero as 0m`() {
        assertEquals("0m", 0.minutes.toReadableDuration())
    }

    @Test
    fun `formats sub-minute reading as under a minute`() {
        assertEquals("<1m", 30.seconds.toReadableDuration())
    }
}
