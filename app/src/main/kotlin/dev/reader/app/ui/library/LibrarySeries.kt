// Named for the feature: the file also holds the functions around its one class.
@file:Suppress("MatchingDeclarationName", "Filename")

package dev.reader.app.ui.library

import dev.reader.core.model.Book
import dev.reader.core.util.NaturalSortComparator

sealed interface LibraryItem {
    data class Single(val book: Book) : LibraryItem
    data class Series(val name: String, val books: List<Book>) : LibraryItem // books natural-sorted by title
}

/** Groups books sharing a series name into one [LibraryItem.Series] at the position of its first book. */
fun groupBySeries(books: List<Book>, minBooks: Int = 2): List<LibraryItem> {
    val groups = LinkedHashMap<String, MutableList<Book>>()
    for (b in books) b.series?.takeIf { it.isNotBlank() }?.let { groups.getOrPut(it) { mutableListOf() }.add(b) }
    val emitted = mutableSetOf<String>()
    return buildList {
        for (b in books) {
            val name = b.series?.takeIf { it.isNotBlank() }
            val group = name?.let { groups.getValue(it) }
            when {
                group == null || group.size < minBooks -> add(LibraryItem.Single(b))
                emitted.add(name) ->
                    add(LibraryItem.Series(name, group.sortedWith(compareBy(NaturalSortComparator) { it.title })))
            }
        }
    }
}
