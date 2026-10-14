package dev.reader.app.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.reader.app.data.ContentFileCopier
import dev.reader.app.data.ReaderGraph
import dev.reader.app.ui.rememberImportActions
import dev.reader.core.model.Book
import dev.reader.core.model.Category
import dev.reader.core.model.CategoryId
import dev.reader.core.model.Progress
import dev.reader.core.theme.LibraryLayout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Clock

@Composable
fun LibraryRoute(
    graph: ReaderGraph,
    onOpenBook: (Book) -> Unit,
    onSearchClick: () -> Unit,
    gridStateFor: (CategoryId?) -> LazyGridState? = { null },
    selectedCategory: CategoryId? = null,
    onCategorySelected: (CategoryId?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val books by graph.library.observeBooks().collectAsState(initial = emptyList())
    val categories by graph.library.observeCategories().collectAsState(initial = emptyList())
    val preferences by graph.settings.preferences.collectAsState(initial = null)
    val progressByBook by graph.progress.observeAll().collectAsState(initial = emptyMap())
    val importActions = rememberImportActions(graph)
    var showQuickAddMenu by remember { mutableStateOf(false) }
    var longPressedBook by remember { mutableStateOf<Book?>(null) }
    var chapterDetectBook by remember { mutableStateOf<Book?>(null) }
    var coverTargetBook by remember { mutableStateOf<Book?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val coverPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = coverTargetBook
        coverTargetBook = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        scope.launch {
            val file = ContentFileCopier.copyToCache(context, uri) ?: return@launch
            graph.library.updateBookDetails(target.copy(coverPath = file.path))
        }
    }

    LaunchedEffect(importActions.status) {
        if (importActions.status.isNotBlank()) snackbarHostState.showSnackbar(importActions.status)
    }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch {
                    graph.rootFolderScanner.scanAll()
                    isRefreshing = false
                }
            },
        ) {
            LibraryScreen(
                books = books,
                categories = categories,
                selectedCategory = selectedCategory,
                progressFor = { id -> progressByBook[id]?.takeUnless { it.finished }?.position },
                finishedFor = { id -> progressByBook[id]?.finished ?: false },
                onCategorySelected = onCategorySelected,
                onBookClick = onOpenBook,
                onBookLongClick = { longPressedBook = it },
                onSearchClick = onSearchClick,
                onAddCategory = { name ->
                    scope.launch {
                        graph.library.upsertCategory(
                            Category(
                                id = CategoryId(UUID.randomUUID().toString()),
                                name = name.trim(),
                                orderIndex = (categories.maxOfOrNull { it.orderIndex } ?: -1) + 1,
                            ),
                        )
                    }
                },
                layout = preferences?.libraryLayout ?: LibraryLayout.MEDIUM,
                showAllCategory = preferences?.showAllCategory ?: true,
                gridStateFor = gridStateFor,
            )
        }

        longPressedBook?.let { book ->
            BookContextMenu(
                book = book,
                categories = categories,
                onChangeCategories = { ids ->
                    scope.launch { graph.library.updateCategories(book.id, ids) }
                },
                onRename = { newTitle ->
                    scope.launch { graph.library.updateBookDetails(book.copy(title = newTitle)) }
                },
                onChangeCover = {
                    coverTargetBook = book
                    coverPickerLauncher.launch(arrayOf("image/*"))
                },
                onMarkAllRead = {
                    scope.launch {
                        val lastChapter = graph.library.observeChapters(book.id).first().lastOrNull()
                        if (lastChapter != null) {
                            graph.progress.update(
                                Progress(
                                    bookId = book.id,
                                    chapterId = lastChapter.id,
                                    position = 1f,
                                    updatedAt = Clock.System.now(),
                                    finished = true,
                                ),
                            )
                        }
                    }
                },
                onRemove = { scope.launch { graph.library.deleteBook(book.id) } },
                onDismiss = { longPressedBook = null },
                onDetectChapters = if (book.supportsChapterDetection()) ({ chapterDetectBook = book }) else null,
            )
        }

        chapterDetectBook?.let { book ->
            ChapterDetectHost(graph, book, onDismiss = { chapterDetectBook = null })
        }

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
