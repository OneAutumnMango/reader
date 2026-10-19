package dev.reader.app.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.TextLayoutResult
import dev.reader.core.richtext.RichDocument

/** Tracks each rendered block's on-screen layout so a selection can be pinpointed to an exact character. */
internal class BlockAnchorRegistry {
    private val anchors = mutableMapOf<Int, Pair<LayoutCoordinates, TextLayoutResult>>()

    fun record(index: Int, coordinates: LayoutCoordinates, layout: TextLayoutResult) {
        anchors[index] = coordinates to layout
    }

    /**
     * Resolves [rect] (the selection's bounds, in [root]'s coordinate space) to a precise fraction
     * of the way through [document]'s text, or null if no tracked block's layout covers it (e.g.
     * paged mode, where block layouts aren't tracked).
     */
    fun resolveFraction(document: RichDocument, root: LayoutCoordinates, rect: Rect): Float? {
        val point = rect.topLeft
        for ((index, pair) in anchors) {
            val (coordinates, layout) = pair
            if (!coordinates.isAttached) continue
            val topLeftInRoot = root.localPositionOf(coordinates, Offset.Zero)
            val size = coordinates.size
            val bottomInRoot = topLeftInRoot.y + size.height
            if (point.y < topLeftInRoot.y || point.y > bottomInRoot) continue
            val localPoint = coordinates.localPositionOf(root, point)
            val charOffset = layout.getOffsetForPosition(localPoint)
            val docText = document.toDocumentText()
            val globalOffset = (docText.starts.getOrNull(index) ?: 0) + charOffset
            return docText.fractionOf(globalOffset)
        }
        return null
    }
}
