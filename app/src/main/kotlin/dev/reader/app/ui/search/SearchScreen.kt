package dev.reader.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.reader.app.ui.library.BookCard
import dev.reader.app.ui.library.BookCover
import dev.reader.core.model.Book
import dev.reader.core.model.BookId
import dev.reader.core.model.Chapter
import dev.reader.core.search.Snippet
import dev.reader.core.theme.LibraryLayout

/**
 * Library search. By default it filters books by title/author as cover cards, with any chapters
 * whose title matched listed below; in text mode it shows [textResults] as a tree of books,
 * chapters and matching lines.
 */
@Composable
fun SearchScreen(
    books: List<Book>,
    query: String,
    onQueryChange: (String) -> Unit,
    onBookClick: (Book) -> Unit,
    modifier: Modifier = Modifier,
    chapterMatches: List<ChapterMatch> = emptyList(),
    onChapterClick: (Book, Chapter) -> Unit = { _, _ -> },
    layout: LibraryLayout = LibraryLayout.MEDIUM,
    progressFor: (BookId) -> Float? = { null },
    finishedFor: (BookId) -> Boolean = { false },
    unreadChaptersFor: (BookId) -> Int = { 0 },
    gridState: LazyGridState = rememberLazyGridState(),
    inText: Boolean = false,
    textResults: TextSearchResults? = null,
    searchProgress: Pair<Int, Int>? = null,
    collapsed: Set<BookId> = emptySet(),
    onCollapsedChange: (Set<BookId>) -> Unit = {},
    onMatchClick: (Book, Chapter, Int) -> Unit = { _, _, _ -> },
    listState: LazyListState = rememberLazyListState(),
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(if (inText) "Search inside books" else "Search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("search-field"),
        )
        if (inText) {
            TextResults(query, textResults, searchProgress, collapsed, onCollapsedChange, onMatchClick, listState)
        } else {
            TitleResults(
                results = filterBooksByQuery(books, query),
                chapterMatches = chapterMatches,
                onBookClick = onBookClick,
                onChapterClick = onChapterClick,
                layout = layout,
                progressFor = progressFor,
                finishedFor = finishedFor,
                unreadChaptersFor = unreadChaptersFor,
                gridState = gridState,
            )
        }
    }
}

@Composable
private fun TitleResults(
    results: List<Book>,
    chapterMatches: List<ChapterMatch>,
    onBookClick: (Book) -> Unit,
    onChapterClick: (Book, Chapter) -> Unit,
    layout: LibraryLayout,
    progressFor: (BookId) -> Float?,
    finishedFor: (BookId) -> Boolean,
    unreadChaptersFor: (BookId) -> Int,
    gridState: LazyGridState,
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(layout.columns),
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().testTag("search-results"),
    ) {
        items(results, key = { "book-${it.id.value}" }) { book ->
            BookCard(
                book = book,
                progress = progressFor(book.id),
                finished = finishedFor(book.id),
                unreadChapters = unreadChaptersFor(book.id),
                showAuthor = layout == LibraryLayout.LARGE,
                onClick = { onBookClick(book) },
            )
        }
        if (chapterMatches.isNotEmpty()) {
            item(key = "chapters-header", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Chapters",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(
                chapterMatches,
                key = { "chapter-${it.chapter.id.value}" },
                span = { GridItemSpan(maxLineSpan) },
            ) { match -> ChapterMatchRow(match, onChapterClick) }
        }
    }
}

@Composable
private fun ChapterMatchRow(match: ChapterMatch, onChapterClick: (Book, Chapter) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChapterClick(match.book, match.chapter) }
            .padding(vertical = 4.dp)
            .testTag("chapter-match-${match.chapter.id.value}"),
    ) {
        BookCover(match.book, Modifier.size(COVER_THUMB_WIDTH, COVER_THUMB_HEIGHT).clip(RoundedCornerShape(4.dp)))
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(match.chapter.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            Text(
                match.book.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go to chapter")
    }
}

private val COVER_THUMB_WIDTH = 36.dp
private val COVER_THUMB_HEIGHT = 52.dp

@Composable
private fun TextResults(
    query: String,
    results: TextSearchResults?,
    progress: Pair<Int, Int>?,
    collapsed: Set<BookId>,
    onCollapsedChange: (Set<BookId>) -> Unit,
    onMatchClick: (Book, Chapter, Int) -> Unit,
    listState: LazyListState,
) {
    if (query.isBlank() || results == null) {
        Text(
            "Type to search the text of every book in your library.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        return
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().testTag("search-text-results")) {
        item(key = "summary") {
            ResultsSummary(query, results, progress, collapsed.isNotEmpty()) {
                onCollapsedChange(if (collapsed.isNotEmpty()) emptySet() else results.books.map { it.book.id }.toSet())
            }
        }
        results.books.forEach { entry ->
            val open = entry.book.id !in collapsed
            item(key = "book-${entry.book.id.value}") {
                BookRow(entry, open) {
                    onCollapsedChange(if (open) collapsed + entry.book.id else collapsed - entry.book.id)
                }
            }
            if (open) {
                entry.chapters.forEach { chapterMatches ->
                    item(key = "chapter-${chapterMatches.chapter.id.value}") {
                        Text(
                            "${chapterMatches.chapter.title} (${chapterMatches.matches.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 40.dp, top = 6.dp, bottom = 2.dp),
                        )
                    }
                    items(
                        chapterMatches.matches.size,
                        key = { "match-${chapterMatches.chapter.id.value}-$it" },
                    ) { index ->
                        val match = chapterMatches.matches[index]
                        MatchRow(match.block, match.snippet) {
                            onMatchClick(entry.book, chapterMatches.chapter, match.block)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsSummary(
    query: String,
    results: TextSearchResults,
    progress: Pair<Int, Int>?,
    anyCollapsed: Boolean,
    onToggleAll: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        val hits = if (results.truncated) "${results.totalHits}+ hits" else "${results.totalHits} hits"
        val books = if (results.books.size == 1) "1 book" else "${results.books.size} books"
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "\"${query.trim()}\": $hits in $books",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f).testTag("search-summary"),
            )
            if (results.books.isNotEmpty()) {
                TextButton(onClick = onToggleAll, modifier = Modifier.testTag("search-toggle-all")) {
                    Text(if (anyCollapsed) "Expand all" else "Collapse all")
                }
            }
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.first.toFloat() / progress.second.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BookRow(entry: BookMatches, open: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp)
            .testTag("search-book-${entry.book.id.value}"),
    ) {
        Icon(
            if (open) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (open) "Collapse" else "Expand",
        )
        BookCover(
            entry.book,
            Modifier
                .padding(horizontal = 8.dp)
                .size(COVER_THUMB_WIDTH, COVER_THUMB_HEIGHT)
                .clip(RoundedCornerShape(4.dp)),
        )
        Text(
            entry.book.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "(${entry.total})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun MatchRow(block: Int, snippet: Snippet, onClick: () -> Unit) {
    val highlight = MaterialTheme.colorScheme.primaryContainer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 56.dp, top = 3.dp, bottom = 3.dp)
            .testTag("search-match-$block"),
    ) {
        Text(
            "¶${block + 1}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            snippet.highlighted(SpanStyle(fontWeight = FontWeight.Bold, background = highlight)),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Snippet.highlighted(style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(text)
    val start = match.first.coerceIn(0, text.length)
    val end = (match.last + 1).coerceIn(start, text.length)
    addStyle(style, start, end)
}
