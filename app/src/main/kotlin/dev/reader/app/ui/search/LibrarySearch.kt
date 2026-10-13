package dev.reader.app.ui.search

import dev.reader.core.model.Book

/** Matches a book's title or author against the query, case-insensitively. */
fun filterBooksByQuery(books: List<Book>, query: String): List<Book> {
    if (query.isBlank()) return books
    return books.filter { it.title.contains(query, ignoreCase = true) || it.author.contains(query, ignoreCase = true) }
}
