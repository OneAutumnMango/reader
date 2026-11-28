package dev.reader.app.ui.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.reader.app.data.AppLog
import dev.reader.app.data.ReaderGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@Composable
fun MergeRoute(graph: ReaderGraph, modifier: Modifier = Modifier) {
    val state by graph.sync.state.collectAsState()
    val preferences by graph.settings.preferences.collectAsState(initial = null)
    val categories by graph.library.observeCategories().collectAsState(initial = emptyList())
    var checked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // The controller ignores calls while busy.
        graph.sync.state.first { !it.running }
        graph.sync.check()
        graph.sync.state.first { !it.running }
        checked = true
    }

    MergeScreen(
        state,
        // Until the setting is read, assume the stricter rule.
        oneCategory = preferences?.singleCategoryPerBook ?: true,
        categoryNames = categories.map { it.name },
        checked = checked,
        loadBook = { loadBook(graph, it) },
        onApply = graph.sync::apply,
        onCheck = graph.sync::check,
        modifier = modifier,
    )
}

/** Null if the book has gone or cannot be read; the question still shows, without cover or chapter titles. */
private suspend fun loadBook(graph: ReaderGraph, hash: String): MergeBook? = try {
    graph.library.findByContentHash(hash)?.let { book ->
        MergeBook(book, graph.library.chapters(book.id).associate { it.orderIndex to it.title })
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) { // only decoration is lost
    AppLog.warn("Could not load a book to merge", "Sync", e)
    null
}
