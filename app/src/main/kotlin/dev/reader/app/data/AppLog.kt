package dev.reader.app.data

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant

enum class LogLevel(val label: String, val priority: Int) {
    DEBUG("Debug", Log.DEBUG),
    INFO("Info", Log.INFO),
    WARN("Warn", Log.WARN),
    ERROR("Err", Log.ERROR),
}

data class LogEntry(
    val timestamp: Instant,
    val message: String,
    val level: LogLevel = LogLevel.INFO,
    val source: String = "Reader",
    val details: String? = null,
)

private const val MAX_ENTRIES = 200

object AppLog {
    private val history = mutableStateListOf<LogEntry>()

    val entries: List<LogEntry>
        @Synchronized get() = history.toList()

    @Synchronized
    fun record(message: String, level: LogLevel = LogLevel.INFO, source: String = "Reader", error: Throwable? = null) {
        if (error is CancellationException) throw error
        val details = error?.stackTraceToString()
        Log.println(level.priority, source, if (details == null) message else "$message\n$details")
        history.add(0, LogEntry(Clock.System.now(), message, level, source, details))
        while (history.size > MAX_ENTRIES) history.removeAt(history.lastIndex)
    }

    fun debug(message: String, source: String = "Reader") = record(message, LogLevel.DEBUG, source)

    fun info(message: String, source: String = "Reader") = record(message, LogLevel.INFO, source)

    fun warn(message: String, source: String = "Reader", error: Throwable? = null) =
        record(message, LogLevel.WARN, source, error)

    fun error(message: String, source: String = "Reader", error: Throwable? = null) =
        record(message, LogLevel.ERROR, source, error)

    @Synchronized
    fun clear() = history.clear()

    fun filter(entries: List<LogEntry>, minimum: LogLevel): List<LogEntry> =
        entries.filter { it.level.priority >= minimum.priority }

    fun format(entries: List<LogEntry>): String =
        entries.asReversed().joinToString("\n") {
            "${it.timestamp}  [${it.level.name}] ${it.source}: ${it.message}" +
                (it.details?.let { details -> "\n$details" } ?: "")
        }
}
