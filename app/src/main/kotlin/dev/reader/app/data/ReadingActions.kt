package dev.reader.app.data

import dev.reader.core.model.BookId
import dev.reader.core.model.Progress
import kotlin.time.Clock

/** Marks every chapter of [bookId] read and the book itself finished. */
suspend fun ReaderGraph.markBookRead(bookId: BookId) {
    val chapters = library.chapters(bookId)
    val now = Clock.System.now()
    progress.setChaptersRead(bookId, chapters.map { it.id }, read = true, now = now)
    val last = chapters.lastOrNull { !it.isExtra } ?: chapters.lastOrNull() ?: return
    progress.update(Progress(bookId, last.id, position = 1f, updatedAt = now, finished = true))
}
