package dev.reader.data.store

import dev.reader.core.model.BookId
import dev.reader.core.search.SearchOptions
import kotlinx.serialization.Serializable

/**
 * A reindex of the library, kept in the settings so it survives the app being closed or killed:
 * [pending] is the books still to do, in order, and a book only leaves it once it has been indexed.
 */
@Serializable
data class SearchIndexJob(
    val pending: List<String> = emptyList(),
    val total: Int = 0,
    val paused: Boolean = false,
    val unreadableChapters: Int = 0,
) {
    val done: Int get() = total - pending.size
    val running: Boolean get() = pending.isNotEmpty() && !paused
    val isPaused: Boolean get() = pending.isNotEmpty() && paused
    val finished: Boolean get() = total > 0 && pending.isEmpty()
}

@Serializable
data class SearchSettings(
    val inTextByDefault: Boolean = false,
    val defaultMatchCase: Boolean = false,
    val defaultRegex: Boolean = false,
    val defaultAnyWhitespace: Boolean = false,
    /** Whether book text is kept in an index at all; off means in-text search reads each book directly. */
    val indexEnabled: Boolean = true,
    val indexOnImport: Boolean = true,
    /** Books the user chose to keep out of the index. */
    val indexExcludedBooks: Set<String> = emptySet(),
    val indexJob: SearchIndexJob = SearchIndexJob(),
) {
    val defaultOptions: SearchOptions
        get() = SearchOptions(defaultMatchCase, defaultRegex, defaultAnyWhitespace)

    /** Whether [bookId] belongs in the index. */
    fun indexes(bookId: BookId): Boolean = indexEnabled && bookId.value !in indexExcludedBooks
}
