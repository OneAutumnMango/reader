package dev.reader.app.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.reader.app.data.ReaderGraph
import dev.reader.core.model.BookId
import dev.reader.core.model.Chapter

@Composable
fun BookDetailRoute(
    graph: ReaderGraph,
    bookId: BookId,
    onOpenChapter: (Chapter) -> Unit,
    onOpenBookmarks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val books by graph.library.observeBooks().collectAsState(initial = emptyList())
    val book = books.find { it.id == bookId } ?: return
    val chapters by graph.library.observeChapters(bookId).collectAsState(initial = emptyList())
    val bookmarks by graph.bookmarks.observe(bookId).collectAsState(initial = emptyList())
    val progress by graph.progress.observe(bookId).collectAsState(initial = null)
    val currentChapter = chapters.find { it.id == progress?.chapterId }

    Column(modifier = modifier) {
        if (bookmarks.isNotEmpty()) {
            TextButton(onClick = onOpenBookmarks) { Text("Bookmarks (${bookmarks.size})") }
        }
        BookDetailScreen(
            book = book,
            chapters = chapters,
            bookmarks = bookmarks,
            onChapterClick = onOpenChapter,
            currentChapterOrderIndex = currentChapter?.orderIndex,
            resumeLabel = if (currentChapter != null) "Resume" else "Start reading",
            onResume = { (currentChapter ?: chapters.firstOrNull())?.let(onOpenChapter) },
        )
    }
}
