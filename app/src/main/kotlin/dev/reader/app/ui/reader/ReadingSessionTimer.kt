package dev.reader.app.ui.reader

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

internal data class ReadingSessionInterval(val startedAt: Instant, val endedAt: Instant) {
    val duration: Duration get() = endedAt - startedAt
}

internal class ReadingSessionTimer {
    private var startedAt: Instant? = null
    private var elapsedStartedAtNanos: Long? = null

    fun setActive(active: Boolean, elapsedRealtimeNanos: Long, wallTime: Instant): ReadingSessionInterval? {
        if (active) {
            if (startedAt == null) {
                startedAt = wallTime
                elapsedStartedAtNanos = elapsedRealtimeNanos
            }
            return null
        }

        val start = startedAt ?: return null
        val elapsedStart = elapsedStartedAtNanos ?: return null
        val duration = (elapsedRealtimeNanos - elapsedStart).coerceAtLeast(0).nanoseconds
        startedAt = null
        elapsedStartedAtNanos = null
        return ReadingSessionInterval(start, start + duration)
    }
}
