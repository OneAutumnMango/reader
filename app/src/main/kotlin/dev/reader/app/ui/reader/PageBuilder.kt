package dev.reader.app.ui.reader

/**
 * Greedily groups block indices into pages that fit within [maxHeight], measured by
 * [heights]. A single block taller than [maxHeight] still gets its own page rather than
 * being dropped or causing an infinite loop.
 */
fun buildPages(heights: List<Float>, maxHeight: Float): List<IntRange> {
    if (heights.isEmpty()) return listOf(IntRange.EMPTY)

    val pages = mutableListOf<IntRange>()
    var pageStart = 0
    var pageHeight = 0f

    heights.forEachIndexed { index, height ->
        val wouldOverflow = pageHeight > 0f && pageHeight + height > maxHeight
        if (wouldOverflow) {
            pages += pageStart until index
            pageStart = index
            pageHeight = 0f
        }
        pageHeight += height
    }
    pages += pageStart until heights.size
    return pages
}
