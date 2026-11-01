package dev.reader.app.ui.search

import dev.reader.core.model.Book
import dev.reader.core.model.Chapter

/** Matches a book's title or author against the query, case-insensitively. */
fun filterBooksByQuery(books: List<Book>, query: String): List<Book> {
    if (query.isBlank()) return books
    return books.filter { it.title.contains(query, ignoreCase = true) || it.author.contains(query, ignoreCase = true) }
}

/** A chapter whose title matched the query, with the book it belongs to. */
data class ChapterMatch(val book: Book, val chapter: Chapter)

/** Chapters whose title contains the query, grouped in [books]' order and each book's chapter order. */
fun matchChaptersByTitle(books: List<Book>, chapters: List<Chapter>, query: String): List<ChapterMatch> {
    if (query.isBlank()) return emptyList()
    val byBook = chapters.filter { it.title.contains(query, ignoreCase = true) }.groupBy { it.bookId }
    return books.flatMap { book ->
        byBook[book.id].orEmpty().sortedBy { it.orderIndex }.map { ChapterMatch(book, it) }
    }
}
