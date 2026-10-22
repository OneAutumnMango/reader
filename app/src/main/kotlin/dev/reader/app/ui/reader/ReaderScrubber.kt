package dev.reader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.core.reader.ReadingMode
import kotlin.math.roundToInt

// The thumb's own fixed size; when the caller's modifier doesn't pin a width (as in the real
// reader, which only constrains height), a BoxWithConstraints with no fillMax* wraps to this.
private val THUMB_WIDTH = 28.dp
private val THUMB_HEIGHT = 48.dp

/**
 * A vertical drag track for jumping around within the current chapter page while reading in
 * continuous-scroll mode. [fraction] is the externally-known read position (0f..1f); while the
 * user is actively dragging, the thumb instead tracks the drag position directly so it doesn't
 * visually snap back to [fraction] until the drag completes and a fresh position is reported back
 * through [onScrub].
 */
@Composable
fun ReaderScrubber(fraction: Float, onScrub: (Float) -> Unit, modifier: Modifier = Modifier) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    // pointerInput(Unit) launches its gesture coroutine once; without this, the coroutine's
    // detectDragGestures lambdas would keep calling whatever onScrub closure existed at that
    // first launch, even after ReaderRoute recomposes with a new one (e.g. a different page).
    val currentOnScrub by rememberUpdatedState(onScrub)

    BoxWithConstraints(
        modifier = modifier
            .testTag("reader-scrubber-track")
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.y / size.height).coerceIn(0f, 1f)
                    },
                    onDrag = { change, _ ->
                        dragFraction = (change.position.y / size.height).coerceIn(0f, 1f)
                        currentOnScrub(dragFraction)
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                )
            },
    ) {
        val displayedFraction = if (isDragging) dragFraction else fraction
        val offsetY = (maxHeight - THUMB_HEIGHT) * displayedFraction

        Box(
            modifier = Modifier
                .testTag("reader-scrubber-thumb")
                .offset(y = offsetY)
                .width(THUMB_WIDTH)
                .height(THUMB_HEIGHT)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** Whether the continuous-scroll scrubber should currently be shown. */
internal fun scrubberVisible(showMenu: Boolean, menuExpanded: Boolean, readingMode: ReadingMode): Boolean =
    showMenu && !menuExpanded && readingMode == ReadingMode.CONTINUOUS_SCROLL

/**
 * The read-position fraction (0f..1f) represented by a `(index, total)` position, if known.
 * Divides by `total - 1` (the last valid index), not `total`, so the thumb actually reaches the
 * bottom of the track when the last block is showing, rather than maxing out just short of it.
 */
internal fun scrubberFraction(position: Pair<Int, Int>?): Float {
    val (index, total) = position ?: return 0f
    if (total <= 1) return 0f
    return index.toFloat() / (total - 1)
}

/**
 * The [BlockJump] that would bring [fraction] of the way through a [total]-block chapter into
 * view. The inverse of [scrubberFraction]: multiplies by `total - 1` and rounds, so a drag to the
 * very bottom of the track resolves to the last block, not one short of it.
 */
internal fun nextScrubJump(fraction: Float, total: Int, nonce: Int): BlockJump {
    val maxBlock = (total - 1).coerceAtLeast(0)
    val block = (fraction * maxBlock).roundToInt().coerceIn(0, maxBlock)
    return BlockJump(block = block, id = nonce)
}
