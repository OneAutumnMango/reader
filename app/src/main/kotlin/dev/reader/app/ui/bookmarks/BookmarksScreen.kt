package dev.reader.app.ui.bookmarks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.reader.core.model.Bookmark

@Composable
fun BookmarksScreen(
    bookmarks: List<Bookmark>,
    onJump: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(bookmarks, key = { it.id.value }) { bookmark ->
            ListItem(
                headlineContent = {
                    Text(if (bookmark.note.isNotBlank()) bookmark.note else "${(bookmark.position * PERCENT).toInt()}%")
                },
                modifier = Modifier.clickable { onJump(bookmark) },
                trailingContent = {
                    IconButton(onClick = { onDelete(bookmark) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove bookmark")
                    }
                },
            )
        }
    }
}

private const val PERCENT = 100
