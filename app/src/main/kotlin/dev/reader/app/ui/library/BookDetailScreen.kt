package dev.reader.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.reader.core.model.Book
import dev.reader.core.model.Bookmark
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.core.model.Progress

private val COVER_WIDTH = 120.dp
private val COVER_HEIGHT = 180.dp
private val FAB_CLEARANCE = 88.dp
private const val READ_CHAPTER_ALPHA = 0.5f
private const val TITLE_MAX_LINES = 3
private const val PERCENT = 100

enum class ChapterSort(val label: String) {
    ASCENDING("First to last"),
    DESCENDING("Last to first"),
    UNREAD_FIRST("Unread first"),
}

/** Kept per book outside the screen, so scroll position, book-info toggle and sort survive opening a chapter. */
class BookDetailState {
    val listState = LazyListState()
    var showExtras by mutableStateOf(false)
    var sort by mutableStateOf(ChapterSort.ASCENDING)
    var selected by mutableStateOf<Set<ChapterId>>(emptySet())
}

/** A chapter's place in the book (1-based, extras excluded), whatever order the list is sorted in. */
private data class NumberedChapter(val number: Int, val chapter: Chapter)

@Composable
fun BookDetailScreen(
    book: Book,
    chapters: List<Chapter>,
    bookmarks: List<Bookmark>,
    onChapterClick: (Chapter) -> Unit,
    modifier: Modifier = Modifier,
    currentChapterId: ChapterId? = null,
    chapterProgress: Map<ChapterId, Progress> = emptyMap(),
    resumeLabel: String? = null,
    onResume: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    onSetRead: (chapters: List<Chapter>, read: Boolean) -> Unit = { _, _ -> },
    extras: List<Chapter> = emptyList(),
    state: BookDetailState = remember { BookDetailState() },
) {
    val numbered = chapters.mapIndexed { index, chapter -> NumberedChapter(index + 1, chapter) }
    val isFinished = { chapter: Chapter -> chapterProgress[chapter.id]?.finished == true }
    val sorted = when (state.sort) {
        ChapterSort.ASCENDING -> numbered
        ChapterSort.DESCENDING -> numbered.asReversed()
        ChapterSort.UNREAD_FIRST -> numbered.sortedBy { isFinished(it.chapter) }
    }
    val current = numbered.find { it.chapter.id == currentChapterId }
    val selecting = state.selected.isNotEmpty()
    fun toggle(chapter: Chapter) {
        state.selected = if (chapter.id in state.selected) state.selected - chapter.id else state.selected + chapter.id
    }
    fun act(action: ChapterSelectionAction) {
        val all = chapters.map { it.id }.toSet()
        when (action) {
            ChapterSelectionAction.SELECT_ALL -> state.selected = all
            ChapterSelectionAction.INVERT -> state.selected = all - state.selected
            else -> {
                val read = action == ChapterSelectionAction.MARK_READ || action == ChapterSelectionAction.READ_UP_TO
                action.targets(chapters, state.selected)?.let { onSetRead(it, read) }
                state.selected = emptySet()
            }
        }
    }
    BackHandler(enabled = selecting) { state.selected = emptySet() }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(state = state.listState, modifier = Modifier.fillMaxSize()) {
            item {
                BookHeader(book, chapters.size, current?.number, bookmarks.size, onOpenBookmarks)
            }
            if (extras.isNotEmpty()) {
                item { BookInfoCard(extras, state.showExtras, { state.showExtras = it }, onChapterClick) }
            }
            item {
                ChaptersHeader(
                    count = chapters.size,
                    readCount = chapters.count(isFinished),
                    sort = state.sort,
                    onSortChange = { state.sort = it },
                )
            }
            items(sorted, key = { it.chapter.id.value }) { (number, chapter) ->
                ChapterRow(
                    number = number,
                    chapter = chapter,
                    isCurrent = chapter.id == currentChapterId,
                    progress = chapterProgress[chapter.id],
                    selection = if (selecting) chapter.id in state.selected else null,
                    onClick = { if (selecting) toggle(chapter) else onChapterClick(chapter) },
                    onLongClick = { toggle(chapter) },
                )
            }
            item { Spacer(Modifier.height(FAB_CLEARANCE)) }
        }

        if (selecting) {
            ChapterSelectionBar(
                selectedCount = state.selected.size,
                onAction = ::act,
                onClose = { state.selected = emptySet() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else if (resumeLabel != null) {
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
    }
}

@Composable
private fun BookHeader(
    book: Book,
    chapterCount: Int,
    currentNumber: Int?,
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
            val progressLabel = currentNumber?.let { " \u00b7 on $it" }.orEmpty()
            Text(
                "${book.format.name} \u00b7 $chapterLabel$progressLabel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (bookmarkCount > 0) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
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
private fun ChaptersHeader(count: Int, readCount: Int, sort: ChapterSort, onSortChange: (ChapterSort) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        Text(
            "Chapters",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "  $readCount / $count read",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("chapter-sort")) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort chapters")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                ChapterSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        leadingIcon = { if (option == sort) Icon(Icons.Filled.Check, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onSortChange(option)
                        },
                        modifier = Modifier.testTag("chapter-sort-${option.name}"),
                    )
                }
            }
        }
    }
    HorizontalDivider()
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterRow(
    number: Int,
    chapter: Chapter,
    isCurrent: Boolean,
    progress: Progress?,
    selection: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isRead = progress?.finished == true
    val partial = progress?.position?.takeIf { !isRead && it > 0f }
    ListItem(
        headlineContent = {
            Text(
                chapter.title,
                fontWeight = if (isCurrent) FontWeight.SemiBold else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = if (isCurrent || partial != null) {
            { ChapterProgressLine(isCurrent, partial) }
        } else {
            null
        },
        leadingContent = {
            if (selection != null) {
                Checkbox(checked = selection, onCheckedChange = null, modifier = Modifier.size(24.dp))
            } else {
                Text("$number", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 4.dp))
            }
        },
        trailingContent = if (isRead) {
            { Icon(Icons.Filled.CheckCircle, contentDescription = "Read", Modifier.size(18.dp)) }
        } else {
            null
        },
        colors = when {
            selection == true -> ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                headlineColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            isCurrent -> ListItemDefaults.colors(
                headlineColor = MaterialTheme.colorScheme.primary,
                supportingColor = MaterialTheme.colorScheme.primary,
                leadingIconColor = MaterialTheme.colorScheme.primary,
            )
            isRead -> ListItemDefaults.colors(
                headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = READ_CHAPTER_ALPHA),
                leadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = READ_CHAPTER_ALPHA),
                trailingIconColor = MaterialTheme.colorScheme.primary.copy(alpha = READ_CHAPTER_ALPHA),
            )
            else -> ListItemDefaults.colors()
        },
        modifier = Modifier
            .testTag("chapter-${chapter.id.value}")
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

@Composable
private fun ChapterProgressLine(isCurrent: Boolean, partial: Float?) {
    Column {
        val label = listOfNotNull(
            "Reading now".takeIf { isCurrent },
            partial?.let { "${(it * PERCENT).toInt()}% read" },
        ).joinToString(" \u00b7 ")
        Text(label)
        if (partial != null) {
            LinearProgressIndicator(
                progress = { partial },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("chapter-progress"),
            )
        }
    }
}
