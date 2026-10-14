package dev.reader.app.ui.reader

/**
 * Greedily lays [items] out into pages of [pageHeight], with [spacing] between items on a page.
 *
 * - An item that doesn't fit is [split] at the remaining space when possible (e.g. a long paragraph
 *   continues on the next page), so text is never clipped and pages fill up.
 * - Items for which [keepWithNext] is true (headings) are never left at the bottom of a page: they
 *   move to the next page together with whatever follows them.
 * - An item taller than a whole page that can't be split still gets a page of its own.
 */
fun <T> paginate(
    items: List<T>,
    pageHeight: Float,
    spacing: Float,
    measure: (T) -> Float,
    split: (T, available: Float) -> Pair<T, T>? = { _, _ -> null },
    keepWithNext: (T) -> Boolean = { false },
): List<List<T>> {
    val pages = mutableListOf<List<T>>()
    val queue = ArrayDeque(items)
    var page = mutableListOf<T>()
    var used = 0f

    fun closePage() {
        pages += page
        page = mutableListOf()
        used = 0f
    }

    while (queue.isNotEmpty()) {
        val item = queue.removeFirst()
        val gap = if (page.isEmpty()) 0f else spacing
        val height = measure(item)
        if (used + gap + height <= pageHeight) {
            page += item
            used += gap + height
            continue
        }
        val parts = split(item, pageHeight - used - gap)
        when {
            parts != null -> {
                page += parts.first
                queue.addFirst(parts.second)
                closePage()
            }
            page.isEmpty() -> {
                page += item
                closePage()
            }
            else -> {
                val carried = page.takeLastWhile(keepWithNext).takeIf { it.size < page.size }.orEmpty()
                repeat(carried.size) { page.removeAt(page.lastIndex) }
                closePage()
                queue.addFirst(item)
                carried.asReversed().forEach(queue::addFirst)
            }
        }
    }
    if (page.isNotEmpty() || pages.isEmpty()) pages += page
    return pages
}
