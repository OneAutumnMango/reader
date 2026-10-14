package dev.reader.app.data

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class AppLogTest {

    @Test
    fun `formats oldest first with timestamps`() {
        val entries = listOf(
            LogEntry(Instant.parse("2026-01-02T00:00:00Z"), "second"),
            LogEntry(Instant.parse("2026-01-01T00:00:00Z"), "first"),
        )

        assertEquals(
            "2026-01-01T00:00:00Z  first\n2026-01-02T00:00:00Z  second",
            AppLog.format(entries),
        )
    }
}
