package dev.reader.app.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag

const val MAX_ZOOM = 5f
private const val DEFAULT_DOUBLE_TAP_ZOOM = 3f

/** Current pinch zoom of the enclosing [Zoomable], so content can re-render sharper while zoomed. */
class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    /** The unzoomed viewport, for mapping content positions to what's on screen. */
    var viewport: LayoutCoordinates? = null
}

val LocalZoomState = compositionLocalOf<ZoomState?> { null }

/**
 * Pinch-to-zoom and pan for any reader content. Gestures are inspected in the Initial pass so a
 * pinch wins over the scrollable/pager inside, while single-finger drags at 1x — or panning that
 * has already hit the zoomed edge — fall through so scrolling and page turns keep working.
 */
@Composable
fun Zoomable(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    doubleTapEnabled: Boolean = true,
    doubleTapZoomScale: Float = DEFAULT_DOUBLE_TAP_ZOOM,
    content: @Composable () -> Unit,
) {
    val zoom = remember { ZoomState() }
    var scale by zoom::scale
    var offset by zoom::offset
    LaunchedEffect(enabled) {
        if (!enabled) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    // Disabling only stops the pinch/zoom handling below; a confirmed double tap is still always
    // consumed here regardless, so it can never fall through to the text selection underneath.
    val gesture = Modifier.pointerInput(enabled, doubleTapEnabled, doubleTapZoomScale) {
        var lastTapTime = 0L
        var lastTapPosition = Offset.Zero
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Text selection starts on this second *down*, not the up, so it has to be consumed here
            // (parents see the Initial pass first) for a double tap not to select a word.
            val secondTap = down.uptimeMillis - lastTapTime < viewConfiguration.doubleTapTimeoutMillis &&
                (down.position - lastTapPosition).getDistance() < viewConfiguration.touchSlop
            if (secondTap) down.consume()
            var upChange = down
            var multiTouch = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                if (pressed > 1) multiTouch = true
                event.changes.firstOrNull { it.id == down.id }?.let { upChange = it }
                if (enabled && pressed > 0 && (multiTouch || scale > 1f)) {
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

            // Not a tap (moved too much, or a second finger showed up) — nothing more to do.
            if (multiTouch || (upChange.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                return@awaitEachGesture
            }
            val now = upChange.uptimeMillis
            val isDoubleTap = now - lastTapTime < viewConfiguration.doubleTapTimeoutMillis &&
                (down.position - lastTapPosition).getDistance() < viewConfiguration.touchSlop
            if (!isDoubleTap) {
                lastTapTime = now
                lastTapPosition = down.position
                return@awaitEachGesture
            }
            lastTapTime = 0L
            // Always consumed, even when zoom is off, so a double tap can never reach the text
            // selection underneath and select a word instead.
            // shortcut: consuming here stops the tap zone from also acting on this (second) tap, but
            // the *first* tap of the double-tap already fired before we knew a second one was coming —
            // upgrade by giving TapZoneOverlay an onDoubleTap and a shared ZoomState if that glitch matters.
            upChange.consume()
            if (!enabled || !doubleTapEnabled) return@awaitEachGesture
            val newScale = if (scale > 1f) 1f else doubleTapZoomScale.coerceIn(1f, MAX_ZOOM)
            offset = if (newScale == 1f) {
                Offset.Zero
            } else {
                val centroid = down.position - Offset(size.width / 2f, size.height / 2f)
                val maxX = size.width * (newScale - 1f) / 2f
                val maxY = size.height * (newScale - 1f) / 2f
                Offset(
                    (-centroid.x * (newScale - 1f)).coerceIn(-maxX, maxX),
                    (-centroid.y * (newScale - 1f)).coerceIn(-maxY, maxY),
                )
            }
            scale = newScale
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .testTag("zoomable")
            .then(gesture)
            .onPlaced { zoom.viewport = it }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    ) {
        CompositionLocalProvider(LocalZoomState provides zoom) { content() }
    }
}
