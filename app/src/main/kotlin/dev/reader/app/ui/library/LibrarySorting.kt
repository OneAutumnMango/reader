package dev.reader.app.ui.library

import dev.reader.core.model.Book
import dev.reader.core.model.BookId
import dev.reader.data.store.LibrarySort
import kotlin.time.Instant

val LibrarySort.label: String
    get() = when (this) {
        LibrarySort.TITLE -> "Title"
        LibrarySort.AUTHOR -> "Author"
        LibrarySort.LAST_READ -> "Last read"
        LibrarySort.ADDED -> "Date added"
        LibrarySort.PROGRESS -> "Progress"
    }

/**
 * Orders [books] by [sort]. Books with nothing to sort on (never read, no author) always go last,
 * whichever direction is chosen; ties fall back to title.
 */
fun sortBooks(
    books: List<Book>,
    sort: LibrarySort,
    descending: Boolean,
    lastRead: (BookId) -> Instant?,
    progress: (BookId) -> Float?,
): List<Book> {
    val byTitle = compareBy<Book, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
    val (known, unknown) = books.partition { book ->
        when (sort) {
            LibrarySort.AUTHOR -> book.author.isNotBlank()
            LibrarySort.LAST_READ -> lastRead(book.id) != null
            LibrarySort.PROGRESS -> (progress(book.id) ?: 0f) > 0f
            else -> true
        }
    }
    val order: Comparator<Book> = when (sort) {
        LibrarySort.TITLE -> byTitle
        LibrarySort.AUTHOR -> compareBy<Book, String>(String.CASE_INSENSITIVE_ORDER) { it.author }.then(byTitle)
        LibrarySort.LAST_READ -> compareBy<Book> { lastRead(it.id) }.then(byTitle)
        LibrarySort.ADDED -> compareBy<Book> { it.addedAt }.then(byTitle)
        LibrarySort.PROGRESS -> compareBy<Book> { progress(it.id) }.then(byTitle)
    }
    return known.sortedWith(if (descending) order.reversed() else order) + unknown.sortedWith(byTitle)
}
