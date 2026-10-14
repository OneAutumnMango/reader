package dev.reader.app.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.reader.app.data.ContentFileCopier
import dev.reader.app.data.ReaderGraph
import dev.reader.core.model.BookId
import dev.reader.core.model.Chapter
import dev.reader.core.model.Progress
import kotlinx.coroutines.launch
import kotlin.time.Clock

@Composable
fun BookDetailRoute(
    graph: ReaderGraph,
    bookId: BookId,
    onOpenChapter: (Chapter) -> Unit,
    onOpenBookmarks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val books by graph.library.observeBooks().collectAsState(initial = emptyList())
    val book = books.find { it.id == bookId } ?: return
    val chapters by graph.library.observeChapters(bookId).collectAsState(initial = emptyList())
    val bookmarks by graph.bookmarks.observe(bookId).collectAsState(initial = emptyList())
    val progress by graph.progress.observe(bookId).collectAsState(initial = null)
    val categories by graph.library.observeCategories().collectAsState(initial = emptyList())
    val currentChapter = chapters.find { it.id == progress?.chapterId }
    var showMenu by remember { mutableStateOf(false) }

    val coverPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val file = ContentFileCopier.copyToCache(context, uri) ?: return@launch
            graph.library.updateBookDetails(book.copy(coverPath = file.path))
        }
    }

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
            onMenuClick = { showMenu = true },
        )
    }

    if (showMenu) {
        BookContextMenu(
            book = book,
            categories = categories,
            onChangeCategories = { ids ->
                scope.launch { graph.library.updateCategories(book.id, ids) }
            },
            onRename = { newTitle ->
                scope.launch { graph.library.updateBookDetails(book.copy(title = newTitle)) }
            },
            onChangeCover = { coverPickerLauncher.launch(arrayOf("image/*")) },
            onMarkAllRead = {
                scope.launch {
                    val lastChapter = chapters.lastOrNull()
                    if (lastChapter != null) {
                        graph.progress.update(
                            Progress(
                                bookId = book.id,
                                chapterId = lastChapter.id,
                                position = 1f,
                                updatedAt = Clock.System.now(),
                                finished = true,
                            ),
                        )
                    }
                }
            },
            onRemove = { scope.launch { graph.library.deleteBook(book.id) } },
            onDismiss = { showMenu = false },
        )
    }
}
