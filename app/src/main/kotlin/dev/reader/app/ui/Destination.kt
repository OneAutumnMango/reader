package dev.reader.app.ui

import dev.reader.core.model.BookId
import dev.reader.core.model.ChapterId

sealed interface Destination {
    data object Library : Destination
    data object Search : Destination
    data object Stats : Destination
    data object Appearance : Destination
    data object Data : Destination
    data object Settings : Destination
    data object LibrarySources : Destination
    data object Categories : Destination
    data object LogHistory : Destination
    data class BookDetail(val bookId: BookId) : Destination
    data class BookInfo(val bookId: BookId) : Destination
    data class Bookmarks(val bookId: BookId) : Destination
    data class Reader(val bookId: BookId, val chapterId: ChapterId) : Destination
}
