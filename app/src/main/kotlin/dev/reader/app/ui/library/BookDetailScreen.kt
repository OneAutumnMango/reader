package dev.reader.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.core.model.Book
import dev.reader.core.model.Bookmark
import dev.reader.core.model.Chapter

private val COVER_MAX_HEIGHT = 220.dp
private const val READ_CHAPTER_ALPHA = 0.5f

@Composable
fun BookDetailScreen(
    book: Book,
    chapters: List<Chapter>,
    bookmarks: List<Bookmark>,
    onChapterClick: (Chapter) -> Unit,
    modifier: Modifier = Modifier,
    currentChapterOrderIndex: Int? = null,
    resumeLabel: String? = null,
    onResume: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    extras: List<Chapter> = emptyList(),
) {
    var showExtras by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
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
            if (extras.isNotEmpty()) {
                item {
                    ListItem(
                        headlineContent = { Text("Book info") },
                        supportingContent = { Text(extras.joinToString { it.title }, maxLines = 1) },
                        leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                        trailingContent = {
                            Icon(
                                if (showExtras) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (showExtras) "Hide book info" else "Show book info",
                            )
                        },
                        modifier = Modifier.testTag("book-info-toggle").clickable { showExtras = !showExtras },
                    )
                }
                if (showExtras) {
                    items(extras, key = { it.id.value }) { extra ->
                        ListItem(
                            headlineContent = { Text(extra.title) },
                            modifier = Modifier
                                .padding(start = 24.dp)
                                .testTag("extra-${extra.id.value}")
                                .clickable { onChapterClick(extra) },
                        )
                    }
                }
                item { Text("Chapters (${chapters.size})", style = MaterialTheme.typography.titleSmall) }
            }
            items(chapters, key = { it.id.value }) { chapter ->
                val isRead = currentChapterOrderIndex != null && chapter.orderIndex < currentChapterOrderIndex
                ListItem(
                    headlineContent = { Text(chapter.title) },
                    colors = if (isRead) {
                        ListItemDefaults.colors(
                            headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = READ_CHAPTER_ALPHA),
                        )
                    } else {
                        ListItemDefaults.colors()
                    },
                    modifier = Modifier
                        .testTag("chapter-${chapter.id.value}")
                        .clickable { onChapterClick(chapter) },
                )
            }
        }

        if (resumeLabel != null) {
            ExtendedFloatingActionButton(
                onClick = onResume,
                icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                text = { Text(resumeLabel) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("resume-button"),
            )
        }

        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .testTag("book-detail-menu"),
        ) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Book options")
        }
    }
}
