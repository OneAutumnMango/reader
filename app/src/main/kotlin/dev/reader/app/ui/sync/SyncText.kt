package dev.reader.app.ui.sync

import dev.reader.core.sync.CategoriesValue
import dev.reader.core.sync.ItemKey
import dev.reader.core.sync.ItemKind
import dev.reader.core.sync.ItemValue
import dev.reader.core.sync.PositionValue
import dev.reader.data.sync.UndoResult
import kotlin.math.roundToInt

private const val PERCENT = 100

fun describeValue(value: ItemValue?): String = when (value) {
    null -> "No position"
    is PositionValue -> {
        val progress = if (value.finished) "finished" else "${(value.position * PERCENT).roundToInt()}%"
        "Ch. ${value.chapter + 1}, $progress"
    }
    is CategoriesValue -> value.names.joinToString(limit = 20).ifEmpty { "No categories" }
}

internal fun describeItem(key: ItemKey): String = when (key.kind) {
    ItemKind.BOOK_POSITION -> "Reading position"
    ItemKind.CHAPTER -> "Chapter ${key.chapter + 1}"
    ItemKind.CATEGORIES -> "Categories"
}

/** A failed undo restores and skips nothing, and gives the reason as the publish error. */
internal fun describeUndo(result: UndoResult): String {
    val failed = result.restored.isEmpty() && result.skipped.isEmpty()
    if (failed && result.publishError != null) return "Could not undo: ${result.publishError}"
    val counts = "Restored: ${result.restored.size}. Skipped (changed since): ${result.skipped.size}."
    return result.publishError?.let { "$counts Not published: $it" } ?: counts
}
