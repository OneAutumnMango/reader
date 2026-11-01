package dev.reader.app.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import dev.reader.app.data.ReaderGraph
import dev.reader.core.model.BookId
import dev.reader.data.repo.BookIndexSize
import dev.reader.data.store.PreferencesFile
import kotlinx.coroutines.launch

@Composable
fun SearchSettingsRoute(graph: ReaderGraph, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val preferences by graph.settings.preferences.collectAsState(initial = PreferencesFile())
    val books by graph.library.observeBooks().collectAsState(initial = emptyList())
    val settings = preferences.searchSettings
    val job = settings.indexJob
    // Remeasured when a reindex starts, pauses or ends, or the index is switched on or off, not per book.
    val sizeKeys = listOf(settings.indexEnabled, job.running, job.isPaused, job.finished)
    val sizes by produceState<List<BookIndexSize>?>(null, sizeKeys) { value = graph.search.sizes() }

    SearchSettingsScreen(
        settings = settings,
        onSettingsChange = { transform ->
            scope.launch { graph.settings.update { it.copy(searchSettings = transform(it.searchSettings)) } }
        },
        books = books,
        sizes = sizes,
        onReindex = { scope.launch { graph.indexer.startReindex() } },
        onPause = { scope.launch { graph.indexer.pause() } },
        onResume = { scope.launch { graph.indexer.resume() } },
        onClearIndex = { scope.launch { graph.indexer.clearIndex() } },
        onExcludedChange = { excluded ->
            scope.launch {
                val newlyExcluded = excluded - settings.indexExcludedBooks
                graph.settings.update {
                    it.copy(searchSettings = it.searchSettings.copy(indexExcludedBooks = excluded))
                }
                newlyExcluded.forEach { graph.indexer.forget(BookId(it)) }
            }
        },
        modifier = modifier,
    )
}
