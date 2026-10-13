package dev.reader.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.reader.core.model.Book
import dev.reader.core.model.Bookmark
import dev.reader.core.model.Chapter

@Composable
fun BookDetailScreen(
    book: Book,
    chapters: List<Chapter>,
    bookmarks: List<Bookmark>,
    onChapterClick: (Chapter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        item { Text(book.title) }
        if (book.author.isNotBlank()) item { Text(book.author) }
        if (bookmarks.isNotEmpty()) {
            item { Text("Bookmarks (${bookmarks.size})") }
        }
        items(chapters, key = { it.id.value }) { chapter ->
            ListItem(
                headlineContent = { Text(chapter.title) },
                modifier = Modifier.clickable { onChapterClick(chapter) },
            )
        }
    }
}
