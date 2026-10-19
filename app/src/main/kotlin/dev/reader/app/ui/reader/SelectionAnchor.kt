package dev.reader.app.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.TextLayoutResult
import dev.reader.core.richtext.RichDocument

/**
 * Tracks each rendered block's on-screen layout so a selection can be pinpointed to an exact
 * character. LazyColumn recycles its item composables (the same underlying layout node gets
 * reused for a different index as you scroll), so a stale entry left behind under its old index
 * would, after reuse, report another block's *current* coordinates alongside the *old* block's
 * text layout — a mismatch that resolves to a plausible-looking but wrong, and consistently
 * earlier, character. [remove] must be called when a block's composable leaves composition so
 * this can't happen.
 */
internal class BlockAnchorRegistry {
    private val anchors = mutableMapOf<Int, Pair<LayoutCoordinates, TextLayoutResult>>()

    fun record(index: Int, coordinates: LayoutCoordinates, layout: TextLayoutResult) {
        anchors[index] = coordinates to layout
    }

    fun remove(index: Int) {
        anchors.remove(index)
    }

    /**
     * Resolves [rect] (the selection's bounds, in [root]'s coordinate space) to a precise fraction
     * of the way through [document]'s text, or null if no tracked block's layout covers it (e.g.
     * paged mode, where block layouts aren't tracked).
     */
    fun resolveFraction(document: RichDocument, root: LayoutCoordinates, rect: Rect): Float? {
        val point = rect.topLeft
        var best: Pair<Int, Offset>? = null
        for ((index, pair) in anchors) {
            val (coordinates, layout) = pair
            if (!coordinates.isAttached) continue
            val topLeftInRoot = root.localPositionOf(coordinates, Offset.Zero)
            val size = coordinates.size
            val bottomInRoot = topLeftInRoot.y + size.height
            if (point.y < topLeftInRoot.y || point.y > bottomInRoot) continue
            // More than one tracked block can match (e.g. adjacent lines touching at a boundary);
            // prefer whichever one's top is closest to the tap instead of just the first found.
            if (best == null || topLeftInRoot.y > best.second.y) {
                best = index to topLeftInRoot
            }
        }
        val (index, topLeftInRoot) = best ?: return null
        val (coordinates, layout) = anchors[index] ?: return null
        val localPoint = Offset(point.x - topLeftInRoot.x, point.y - topLeftInRoot.y)
        val charOffset = layout.getOffsetForPosition(localPoint)
        val docText = document.toDocumentText()
        val globalOffset = (docText.starts.getOrNull(index) ?: 0) + charOffset
        return docText.fractionOf(globalOffset)
    }
}
