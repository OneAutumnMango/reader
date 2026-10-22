package dev.reader.app.ui.reader

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val THUMB_ALPHA = 0.55f
private val THUMB_HEIGHT = 32.dp

// How much of each frame's gap between the raw position and the displayed one gets closed (0..1):
// low enough to damp the jitter that comes from re-deriving the document's total height from only
// the handful of blocks currently visible (a heading and a full-page image have very different
// sizes, so that estimate swings every frame even during perfectly smooth scrolling), high enough
// that the thumb still keeps up with a deliberate scroll.
private const val POSITION_SMOOTHING = 0.25f

private class SmoothedPosition {
    var value: Float? = null
}

internal fun Modifier.readerScrollIndicator(
    color: Color,
    metrics: () -> Pair<Float, Float>,
): Modifier = composed {
    val smoothed = remember { SmoothedPosition() }
    drawWithContent {
        drawContent()
        val (position, visibleFraction) = metrics()
        if (visibleFraction >= 1f || size.height <= 0f) {
            smoothed.value = null
            return@drawWithContent
        }
        val previous = smoothed.value
        val displayedPosition = if (previous == null) {
            position
        } else {
            previous + (position - previous) * POSITION_SMOOTHING
        }
        smoothed.value = displayedPosition

        val width = 3.dp.toPx()
        val inset = 3.dp.toPx()
        val trackHeight = (size.height - inset * 2).coerceAtLeast(0f)
        val thumbHeight = THUMB_HEIGHT.toPx().coerceAtMost(trackHeight)
        drawRoundRect(
            color = color.copy(alpha = THUMB_ALPHA),
            topLeft = Offset(
                size.width - width - inset,
                inset + (trackHeight - thumbHeight) * displayedPosition.coerceIn(0f, 1f),
            ),
            size = Size(width, thumbHeight),
            cornerRadius = CornerRadius(width),
        )
    }
}
