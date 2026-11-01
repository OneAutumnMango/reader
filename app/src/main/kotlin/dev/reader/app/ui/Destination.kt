package dev.reader.app.ui

import dev.reader.core.model.BookId
import dev.reader.core.model.ChapterId
import dev.reader.core.search.SearchOptions

sealed interface Destination {
    data object Library : Destination
    data object Search : Destination
    data object Stats : Destination
    data object Appearance : Destination
    data object ReaderSettings : Destination
    data object LibrarySettings : Destination
    data object Data : Destination
    data object Settings : Destination
    data object LogHistory : Destination
    data class BookDetail(val bookId: BookId) : Destination
    data class BookInfo(val bookId: BookId) : Destination
    data class Bookmarks(val bookId: BookId) : Destination

    /**
     * [block] scrolls the chapter to that block on open, and [searchQuery] (read with [searchOptions])
     * highlights its matches, e.g. when coming from a text search result.
     */
    data class Reader(
        val bookId: BookId,
        val chapterId: ChapterId,
        val block: Int? = null,
        val searchQuery: String? = null,
        val searchOptions: SearchOptions = SearchOptions(),
    ) : Destination
}
