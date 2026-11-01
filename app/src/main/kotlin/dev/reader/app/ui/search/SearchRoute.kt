package dev.reader.app.ui.search

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.app.data.ReaderGraph
import dev.reader.app.ui.ScreenTopBar
import dev.reader.core.model.Book
import dev.reader.core.model.Chapter
import dev.reader.core.search.TextSearch
import dev.reader.core.theme.LibraryLayout

@Composable
fun SearchRoute(
    graph: ReaderGraph,
    state: SearchState,
    books: List<Book>,
    onBookClick: (Book) -> Unit,
    onOpenChapter: (Book, Chapter, block: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val allChapters by graph.library.observeAllChapters().collectAsState(initial = emptyList())
    val preferences by graph.settings.preferences.collectAsState(initial = null)
    val progressByBook by graph.progress.observeAll().collectAsState(initial = emptyMap())
    val fractionByBook by graph.progress.observeBookFractions().collectAsState(initial = emptyMap())
    val unreadByBook by graph.progress.observeUnreadChapterCounts().collectAsState(initial = emptyMap())

    val chaptersByBook = remember(allChapters) {
        allChapters.groupBy { it.bookId }.mapValues { (_, list) -> list.sortedBy { it.orderIndex } }
    }
    val latestBooks by rememberUpdatedState(books)
    val latestChapters by rememberUpdatedState(chaptersByBook)

    ScreenTopBar(title = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("In text", style = MaterialTheme.typography.labelLarge)
            Switch(
                checked = state.inText,
                onCheckedChange = { state.inText = it },
                modifier = Modifier.padding(horizontal = 8.dp).testTag("search-in-text-toggle"),
            )
        }
    }

    val compiled = remember(state.query, state.options) { TextSearch.compile(state.query.trim(), state.options) }
    LaunchedEffect(state.searchRequest) {
        if (state.searchRequest == state.completedRequest) return@LaunchedEffect
        val regex = TextSearch.compile(state.submittedQuery, state.submittedOptions)?.getOrNull()
        if (regex == null) {
            state.clearResults()
            return@LaunchedEffect
        }
        state.searchLibraryText(context, latestBooks, { latestChapters[it].orEmpty() }, regex)
    }

    SearchScreen(
        books = books,
        query = state.query,
        onQueryChange = { state.query = it },
        onBookClick = onBookClick,
        modifier = modifier,
        chapterMatches = remember(books, allChapters, state.query) {
            matchChaptersByTitle(books, allChapters, state.query)
        },
        onChapterClick = { book, chapter -> onOpenChapter(book, chapter, null) },
        layout = preferences?.libraryLayout ?: LibraryLayout.MEDIUM,
        progressFor = { id ->
            fractionByBook[id]?.takeIf { it > 0f && it < 1f && progressByBook[id]?.finished != true }
        },
        finishedFor = { id -> progressByBook[id]?.finished == true || (fractionByBook[id] ?: 0f) >= 1f },
        unreadChaptersFor = { id -> unreadByBook[id] ?: 0 },
        gridState = state.gridState,
        inText = state.inText,
        options = state.options,
        onOptionsChange = { state.options = it },
        invalidQuery = compiled?.isFailure == true,
        onSearch = state::submit,
        resultsQuery = state.submittedQuery,
        textResults = state.results,
        searchProgress = if (state.searching) state.booksSearched to state.booksToSearch else null,
        collapsed = state.collapsed,
        onCollapsedChange = { state.collapsed = it },
        onMatchClick = { book, chapter, block -> onOpenChapter(book, chapter, block) },
        listState = state.listState,
    )
}
