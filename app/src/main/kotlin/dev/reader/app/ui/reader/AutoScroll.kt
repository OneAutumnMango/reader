package dev.reader.app.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

const val AUTO_SCROLL_MIN_SPEED = 10f
const val AUTO_SCROLL_MAX_SPEED = 300f
const val AUTO_SCROLL_STEP = 5f

/** OFF until the user presses the menu's play button; then RUNNING or PAUSED until stopped. */
enum class AutoScrollMode { OFF, RUNNING, PAUSED }

fun stepAutoScrollSpeed(current: Float, steps: Int): Float =
    (current + steps * AUTO_SCROLL_STEP).coerceIn(AUTO_SCROLL_MIN_SPEED, AUTO_SCROLL_MAX_SPEED)

/** Tap handling: a tap pauses a running scroll; two quick taps while paused resume it. */
data class AutoScrollTaps(val mode: AutoScrollMode, val pausedTapAt: Long? = null) {
    fun onTap(now: Long, doubleTapTimeout: Long): AutoScrollTaps = when (mode) {
        AutoScrollMode.OFF -> this
        // The pausing tap is deliberately not the first half of a resume double tap.
        AutoScrollMode.RUNNING -> AutoScrollTaps(AutoScrollMode.PAUSED)
        AutoScrollMode.PAUSED ->
            if (pausedTapAt != null && now - pausedTapAt < doubleTapTimeout) {
                AutoScrollTaps(AutoScrollMode.RUNNING)
            } else {
                copy(pausedTapAt = now)
            }
    }
}

/** Set by the reader while auto-scroll runs so MainActivity can route the volume keys to it. */
object AutoScrollVolumeKeys {
    // shortcut: one global handler, fine while there is a single reader screen.
    var onStep: ((Int) -> Unit)? = null
}

/**
 * Watches touches on the Initial pass without consuming them. A still finger reports
 * `onHold(true)` until release (a drag clears it at once, since the list itself then scrolls and
 * auto-scroll carries on); a quick still press-and-release reports `onTap`.
 */
fun Modifier.autoScrollTouch(
    enabled: Boolean,
    onDown: () -> Unit,
    onHold: (Boolean) -> Unit,
    onTap: (uptimeMillis: Long) -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onDown()
            onHold(true)
            var moved = false
            var upTime = down.uptimeMillis
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.first()
                upTime = change.uptimeMillis
                val dragged = (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                if (!moved && (event.changes.size > 1 || dragged)) {
                    moved = true
                    onHold(false)
                }
            } while (event.changes.any { it.pressed })
            onHold(false)
            if (!moved && upTime - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) onTap(upTime)
        }
    }
}
