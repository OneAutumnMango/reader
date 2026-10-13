package dev.reader.app.ui.bookmarks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import dev.reader.app.data.ReaderGraph
import dev.reader.core.model.BookId
import dev.reader.core.model.Bookmark
import kotlinx.coroutines.launch

@Composable
fun BookmarksRoute(
    graph: ReaderGraph,
    bookId: BookId,
    onJump: (Bookmark) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val bookmarks by graph.bookmarks.observe(bookId).collectAsState(initial = emptyList())

    BookmarksScreen(
        bookmarks = bookmarks,
        onJump = onJump,
        onDelete = { bookmark -> scope.launch { graph.bookmarks.remove(bookmark) } },
        modifier = modifier,
    )
}
