package dev.reader.app.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag

private const val MAX_ZOOM = 5f

/**
 * Pinch-to-zoom and pan for any reader content. Gestures are inspected in the Initial pass so a
 * pinch wins over the scrollable/pager inside, while single-finger drags at 1x — or panning that
 * has already hit the zoomed edge — fall through so scrolling and page turns keep working.
 */
@Composable
fun Zoomable(enabled: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (!enabled) {
        Box(modifier) { content() }
        return
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val gesture = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                val multiTouch = pressed > 1
                if (pressed > 0 && (multiTouch || scale > 1f)) {
                    val newScale = (scale * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                    val centroid = event.calculateCentroid() - Offset(size.width / 2f, size.height / 2f)
                    val target = centroid - (centroid - offset) * (newScale / scale) + event.calculatePan()
                    val maxX = size.width * (newScale - 1f) / 2f
                    val maxY = size.height * (newScale - 1f) / 2f
                    val newOffset = Offset(target.x.coerceIn(-maxX, maxX), target.y.coerceIn(-maxY, maxY))
                    val moved = newOffset != offset || newScale != scale
                    scale = newScale
                    offset = newOffset
                    if (multiTouch || moved) event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .testTag("zoomable")
            .then(gesture)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    ) {
        content()
    }
}
