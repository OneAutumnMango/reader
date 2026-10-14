package dev.reader.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.reader.core.model.Book
import dev.reader.core.model.Bookmark
import dev.reader.core.model.Chapter

private val COVER_WIDTH = 120.dp
private val COVER_HEIGHT = 180.dp
private const val READ_CHAPTER_ALPHA = 0.5f
private const val TITLE_MAX_LINES = 3

/** Kept per book outside the screen, so the scroll position and book-info toggle survive opening a chapter. */
class BookDetailState {
    val listState = LazyListState()
    var showExtras by mutableStateOf(false)
}

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
    onOpenBookmarks: () -> Unit = {},
    extras: List<Chapter> = emptyList(),
    state: BookDetailState = remember { BookDetailState() },
) {
    LazyColumn(state = state.listState, modifier = modifier.fillMaxSize()) {
        item {
            BookHeader(
                book = book,
                chapterCount = chapters.size,
                currentChapterOrderIndex = currentChapterOrderIndex,
                resumeLabel = resumeLabel,
                onResume = onResume,
                bookmarkCount = bookmarks.size,
                onOpenBookmarks = onOpenBookmarks,
            )
        }
        if (extras.isNotEmpty()) {
            item { BookInfoCard(extras, state.showExtras, { state.showExtras = it }, onChapterClick) }
        }
        item {
            Text(
                "Chapters",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
            )
            HorizontalDivider()
        }
        items(chapters, key = { it.id.value }) { chapter ->
            ChapterRow(chapter, currentChapterOrderIndex) { onChapterClick(chapter) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun BookHeader(
    book: Book,
    chapterCount: Int,
    currentChapterOrderIndex: Int?,
    resumeLabel: String?,
    onResume: () -> Unit,
    bookmarkCount: Int,
    onOpenBookmarks: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        BookCover(
            book,
            modifier = Modifier
                .size(COVER_WIDTH, COVER_HEIGHT)
                .clip(RoundedCornerShape(8.dp)),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                book.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.author.isNotBlank()) {
                Text(
                    book.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            val chapterLabel = if (chapterCount == 1) "1 chapter" else "$chapterCount chapters"
            val progressLabel = currentChapterOrderIndex?.let { " \u00b7 on ${it + 1}" }.orEmpty()
            Text(
                "${book.format.name} \u00b7 $chapterLabel$progressLabel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                if (resumeLabel != null) {
                    Button(onClick = onResume, modifier = Modifier.testTag("resume-button")) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                        Text(resumeLabel, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                if (bookmarkCount > 0) {
                    OutlinedButton(onClick = onOpenBookmarks, modifier = Modifier.testTag("bookmarks-button")) {
                        Icon(Icons.Filled.Bookmark, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                        Text("$bookmarkCount", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BookInfoCard(
    extras: List<Chapter>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onChapterClick: (Chapter) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        ListItem(
            headlineContent = { Text("Book info") },
            supportingContent = {
                Text(extras.joinToString { it.title }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
            trailingContent = {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Hide book info" else "Show book info",
                )
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.testTag("book-info-toggle").clickable { onExpandedChange(!expanded) },
        )
        if (expanded) {
            extras.forEach { extra ->
                ListItem(
                    headlineContent = { Text(extra.title) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier
                        .testTag("extra-${extra.id.value}")
                        .clickable { onChapterClick(extra) }
                        .padding(start = 40.dp),
                )
            }
        }
    }
}

@Composable
private fun ChapterRow(chapter: Chapter, currentChapterOrderIndex: Int?, onClick: () -> Unit) {
    val isCurrent = chapter.orderIndex == currentChapterOrderIndex
    val isRead = currentChapterOrderIndex != null && chapter.orderIndex < currentChapterOrderIndex
    ListItem(
        headlineContent = {
            Text(
                chapter.title,
                fontWeight = if (isCurrent) FontWeight.SemiBold else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = if (isCurrent) ({ Text("Reading now") }) else null,
        leadingContent = {
            Text(
                "${chapter.orderIndex + 1}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(end = 4.dp),
            )
        },
        colors = when {
            isCurrent -> ListItemDefaults.colors(
                headlineColor = MaterialTheme.colorScheme.primary,
                supportingColor = MaterialTheme.colorScheme.primary,
                leadingIconColor = MaterialTheme.colorScheme.primary,
            )
            isRead -> ListItemDefaults.colors(
                headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = READ_CHAPTER_ALPHA),
            )
            else -> ListItemDefaults.colors()
        },
        modifier = Modifier
            .testTag("chapter-${chapter.id.value}")
            .clickable(onClick = onClick),
    )
}
