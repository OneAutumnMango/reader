package dev.reader.app.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.reader.app.data.ReaderGraph
import dev.reader.app.ui.rememberImportActions
import dev.reader.core.model.Book
import dev.reader.core.model.Category
import dev.reader.core.model.CategoryId
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun LibraryRoute(
    graph: ReaderGraph,
    onOpenBook: (Book) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val books by graph.library.observeBooks().collectAsState(initial = emptyList())
    val categories by graph.library.observeCategories().collectAsState(initial = emptyList())
    var selectedCategory by remember { mutableStateOf<CategoryId?>(null) }
    val importActions = rememberImportActions(graph)
    var showQuickAddMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(importActions.status) {
        if (importActions.status.isNotBlank()) snackbarHostState.showSnackbar(importActions.status)
    }

    Box(modifier = modifier.fillMaxSize()) {
        LibraryScreen(
            books = books,
            categories = categories,
            selectedCategory = selectedCategory,
            progressFor = { null },
            onCategorySelected = { selectedCategory = it },
            onBookClick = onOpenBook,
            onSearchClick = onSearchClick,
            onAddCategory = { name ->
                scope.launch {
                    graph.library.upsertCategory(
                        Category(id = CategoryId(UUID.randomUUID().toString()), name = name),
                    )
                }
            },
        )

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))

        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            FloatingActionButton(onClick = { showQuickAddMenu = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add book")
            }
            DropdownMenu(expanded = showQuickAddMenu, onDismissRequest = { showQuickAddMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Add book") },
                    onClick = {
                        showQuickAddMenu = false
                        importActions.addFiles()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Add light novel") },
                    onClick = {
                        showQuickAddMenu = false
                        importActions.addFolder()
                    },
                )
            }
        }
    }
}
