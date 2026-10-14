package dev.reader.app.ui.reader

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import dev.reader.app.data.AppLog
import dev.reader.parser.pdf.PdfPageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val MIN_SHARP_ZOOM = 1.05f
private const val SETTLE_MILLIS = 150L

private class ZoomTile(val bitmap: ImageBitmap, val bounds: Rect)

private class Holder {
    var coordinates: LayoutCoordinates? = null
}

/**
 * Once a pinch or pan settles, re-renders just the on-screen part of the page at screen
 * resolution and draws it over the [content]-sized base bitmap, so zoomed text stays crisp.
 */
@Composable
internal fun SharpZoomTile(renderer: PdfPageRenderer, index: Int, content: IntSize, modifier: Modifier = Modifier) {
    val zoom = LocalZoomState.current ?: return
    val holder = remember { Holder() }
    val placements = remember { mutableIntStateOf(0) }
    var tile by remember(renderer, index) { mutableStateOf<ZoomTile?>(null) }

    LaunchedEffect(renderer, index, content) {
        snapshotFlow { Triple(zoom.scale, zoom.offset, placements.intValue) }.collectLatest { (scale) ->
            if (scale <= MIN_SHARP_ZOOM) {
                tile = null
                return@collectLatest
            }
            delay(SETTLE_MILLIS)
            val region = visibleRegion(zoom.viewport, holder.coordinates, content) ?: return@collectLatest
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    renderer.renderRegion(
                        index,
                        region.pageWidthPx,
                        region.screen.left - region.pageOrigin.x,
                        region.screen.top - region.pageOrigin.y,
                        region.screen.width.roundToInt(),
                        region.screen.height.roundToInt(),
                    )
                }.onFailure { AppLog.warn("Couldn't render zoomed PDF page ${index + 1}", "PdfReader", it) }
                    .getOrNull()
            } ?: return@collectLatest
            tile = ZoomTile(bitmap.asImageBitmap(), region.local)
        }
    }

    Spacer(
        modifier
            .onPlaced {
                holder.coordinates = it
                placements.intValue++
            }
            .drawBehind {
                val current = tile ?: return@drawBehind
                drawImage(
                    current.bitmap,
                    dstOffset = IntOffset(current.bounds.left.roundToInt(), current.bounds.top.roundToInt()),
                    dstSize = IntSize(current.bounds.width.roundToInt(), current.bounds.height.roundToInt()),
                )
            },
    )
}

/** The page's visible part: in viewport pixels ([screen]), in the page box's own space ([local]). */
private class Region(val screen: Rect, val local: Rect, val pageOrigin: Offset, val pageWidthPx: Float)

private fun visibleRegion(viewport: LayoutCoordinates?, page: LayoutCoordinates?, content: IntSize): Region? {
    if (viewport?.isAttached != true || page?.isAttached != true) return null
    val origin = Offset((page.size.width - content.width) / 2f, (page.size.height - content.height) / 2f)
    val topLeft = viewport.localPositionOf(page, origin)
    val bottomRight = viewport.localPositionOf(page, origin + Offset(content.width.toFloat(), content.height.toFloat()))
    val screen = Rect(topLeft, bottomRight).intersect(Rect(Offset.Zero, viewport.size.toSize()))
    if (screen.width < 1f || screen.height < 1f) return null
    val local = Rect(page.localPositionOf(viewport, screen.topLeft), page.localPositionOf(viewport, screen.bottomRight))
    return Region(screen, local, topLeft, bottomRight.x - topLeft.x)
}
