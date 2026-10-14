package dev.reader.app.data

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class AppLogTest {

    @Test
    fun `formats oldest first with timestamps`() {
        val entries = listOf(
            LogEntry(Instant.parse("2026-01-02T00:00:00Z"), "second"),
            LogEntry(Instant.parse("2026-01-01T00:00:00Z"), "first"),
        )

        assertEquals(
            "2026-01-01T00:00:00Z  [INFO] Reader: first\n2026-01-02T00:00:00Z  [INFO] Reader: second",
            AppLog.format(entries),
        )
    }

    @Test
    fun `warn threshold includes warnings and errors only`() {
        val entries = LogLevel.entries.map { LogEntry(Instant.parse("2026-01-01T00:00:00Z"), it.label, it) }
        assertEquals(listOf(LogLevel.WARN, LogLevel.ERROR), AppLog.filter(entries, LogLevel.WARN).map { it.level })
        assertEquals(entries, AppLog.filter(entries, LogLevel.DEBUG))
        assertEquals(listOf(LogLevel.ERROR), AppLog.filter(entries, LogLevel.ERROR).map { it.level })
    }

    @Test
    fun `export includes severity source and failure details`() {
        val entry = LogEntry(
            Instant.parse("2026-01-01T00:00:00Z"), "Failed", LogLevel.ERROR, "Import", "Stack trace",
        )
        assertTrue(AppLog.format(listOf(entry)).contains("[ERROR] Import: Failed\nStack trace"))
    }
}
