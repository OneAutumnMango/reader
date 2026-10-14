package dev.reader.app.ui.stats

import kotlin.time.Duration

private const val MINUTES_PER_HOUR = 60

/** Formats a reading-time duration as "2h 15m" (or just "15m" / "0m" when under an hour). */
fun Duration.toReadableDuration(): String {
    val totalMinutes = inWholeMinutes
    if (totalMinutes == 0L && isPositive()) return "<1m"
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
