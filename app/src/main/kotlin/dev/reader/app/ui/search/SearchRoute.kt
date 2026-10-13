package dev.reader.app.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.reader.app.data.ReaderGraph
import dev.reader.core.model.Book

@Composable
fun SearchRoute(graph: ReaderGraph, onBookClick: (Book) -> Unit, modifier: Modifier = Modifier) {
    val books by graph.library.observeBooks().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }

    SearchScreen(
        books = books,
        query = query,
        onQueryChange = { query = it },
        onBookClick = onBookClick,
        modifier = modifier,
    )
}
