package dev.reader.app.data

import androidx.compose.runtime.mutableStateListOf
import kotlin.time.Clock
import kotlin.time.Instant

data class LogEntry(val timestamp: Instant, val message: String)

private const val MAX_ENTRIES = 200

/** In-memory, newest-first history of errors surfaced to the user, viewable from Settings. */
object AppLog {
    val entries = mutableStateListOf<LogEntry>()

    fun record(message: String) {
        entries.add(0, LogEntry(Clock.System.now(), message))
        while (entries.size > MAX_ENTRIES) entries.removeAt(entries.lastIndex)
    }

    fun clear() = entries.clear()
}
