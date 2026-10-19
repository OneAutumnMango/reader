package dev.reader.app.data

import dev.reader.core.model.BookId
import dev.reader.core.model.Progress
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/** Marks every chapter of [bookId] read and the book itself finished. */
suspend fun ReaderGraph.markBookRead(bookId: BookId) {
    val chapters = library.chapters(bookId)
    val now = Clock.System.now()
    progress.setChaptersRead(bookId, chapters.map { it.id }, read = true, now = now)
    val last = chapters.lastOrNull { !it.isExtra } ?: chapters.lastOrNull() ?: return
    progress.update(Progress(bookId, last.id, position = 1f, updatedAt = now, finished = true))
}

/** Marks every chapter of [bookId] unread and clears the finished flag. */
suspend fun ReaderGraph.markBookUnread(bookId: BookId) {
    val chapters = library.chapters(bookId)
    val now = Clock.System.now()
    progress.setChaptersRead(bookId, chapters.map { it.id }, read = false, now = now)
    progress.observe(bookId).first()?.let { progress.update(it.copy(finished = false)) }
}
