package dev.reader.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.reader.core.model.Book
import dev.reader.core.model.Bookmark
import dev.reader.core.model.Chapter

private val COVER_MAX_HEIGHT = 220.dp

@Composable
fun BookDetailScreen(
    book: Book,
    chapters: List<Chapter>,
    bookmarks: List<Bookmark>,
    onChapterClick: (Chapter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        item {
            BookCover(
                book,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(COVER_MAX_HEIGHT)
                    .padding(bottom = 8.dp),
            )
        }
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
