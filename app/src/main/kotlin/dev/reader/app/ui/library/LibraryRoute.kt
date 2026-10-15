package dev.reader.app.ui.library

import androidx.activity.compose.BackHandler
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
import dev.reader.app.data.markBookRead
import dev.reader.app.data.markBookUnread
import dev.reader.app.ui.ScreenTopBar
import dev.reader.app.ui.rememberImportActions
import dev.reader.core.model.Book
import dev.reader.core.model.BookId
import dev.reader.core.model.Category
import dev.reader.core.model.CategoryId
import dev.reader.core.theme.LibraryLayout
import dev.reader.data.store.LibrarySort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun LibraryRoute(
    graph: ReaderGraph,
    onOpenBook: (Book) -> Unit,
    onSearchClick: () -> Unit,
    onOpenDetails: (Book) -> Unit = {},
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
    val fractionByBook by graph.progress.observeBookFractions().collectAsState(initial = emptyMap())
    val importActions = rememberImportActions(graph)
    var showQuickAddMenu by remember { mutableStateOf(false) }
    var longPressedBook by remember { mutableStateOf<Book?>(null) }
    var selectedBooks by remember { mutableStateOf<Set<BookId>>(emptySet()) }
    val selecting = selectedBooks.isNotEmpty()
    BackHandler(enabled = selecting) { selectedBooks = emptySet() }
    var chapterDetectBook by remember { mutableStateOf<Book?>(null) }
    var coverTargetBook by remember { mutableStateOf<Book?>(null) }
    var showCategoryPickerForSelection by remember { mutableStateOf(false) }
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

    val sort = preferences?.librarySort ?: LibrarySort.ADDED
    val descending = preferences?.librarySortDescending ?: true
    val sortedBooks = remember(books, sort, descending, progressByBook, fractionByBook) {
        sortBooks(books, sort, descending, { progressByBook[it]?.updatedAt }, { fractionByBook[it] })
    }
    ScreenTopBar(title = null) {
        LibrarySortMenu(sort, descending) { newSort, newDescending ->
            scope.launch {
                graph.settings.update { it.copy(librarySort = newSort, librarySortDescending = newDescending) }
            }
        }
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
                books = sortedBooks,
                categories = categories,
                selectedCategory = selectedCategory,
                progressFor = { id ->
                    fractionByBook[id]?.takeIf { it > 0f && it < 1f && progressByBook[id]?.finished != true }
                },
                finishedFor = { id -> progressByBook[id]?.finished == true || (fractionByBook[id] ?: 0f) >= 1f },
                onCategorySelected = onCategorySelected,
                onBookClick = { book ->
                    if (selecting) {
                        selectedBooks = if (book.id in selectedBooks) selectedBooks - book.id
                        else selectedBooks + book.id
                    } else {
                        onOpenBook(book)
                    }
                },
                onBookLongClick = { book ->
                    selectedBooks = selectedBooks + book.id
                },
                selectedBookIds = selectedBooks,
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
            if (!selecting) {
                BookContextMenu(
                    book = book,
                    categories = categories,
                    allRead = (fractionByBook[book.id] ?: 0f) >= 1f,
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
                    onMarkAllRead = { scope.launch { graph.markBookRead(book.id) } },
                    onMarkAllUnread = { scope.launch { graph.markBookUnread(book.id) } },
                    onRemove = { scope.launch { graph.library.deleteBook(book.id) } },
                    onDismiss = { longPressedBook = null },
                    onShowDetails = { onOpenDetails(book) },
                    onDetectChapters = if (book.supportsChapterDetection()) ({ chapterDetectBook = book }) else null,
                )
            }
        }

        chapterDetectBook?.let { book ->
            ChapterDetectHost(graph, book, onDismiss = { chapterDetectBook = null })
        }

        if (selecting) {
            val selectedList = sortedBooks.filter { it.id in selectedBooks }

            if (showCategoryPickerForSelection) {
                val commonCategories = selectedList.map { it.categoryIds }.reduceOrNull { a, b -> a intersect b }.orEmpty()
                CategoryPickerForBooks(
                    categories = categories,
                    initial = commonCategories,
                    onConfirm = { ids ->
                        scope.launch {
                            selectedList.forEach { graph.library.updateCategories(it.id, ids) }
                        }
                        showCategoryPickerForSelection = false
                        selectedBooks = emptySet()
                    },
                    onDismiss = { showCategoryPickerForSelection = false },
                )
            }

            BookSelectionBar(
                selectedCount = selectedBooks.size,
                totalCount = sortedBooks.size,
                onSelectAll = { selectedBooks = sortedBooks.map { it.id }.toSet() },
                onClose = { selectedBooks = emptySet() },
                onChangeCategory = { showCategoryPickerForSelection = true },
                onMarkRead = {
                    scope.launch { selectedList.forEach { graph.markBookRead(it.id) } }
                    selectedBooks = emptySet()
                },
                onMarkUnread = {
                    scope.launch {
                        selectedList.forEach { book ->
                            val existing = graph.progress.observe(book.id).first()
                            if (existing != null) {
                                graph.progress.update(existing.copy(finished = false))
                            }
                        }
                    }
                    selectedBooks = emptySet()
                },
                onRemove = {
                    scope.launch { selectedList.forEach { graph.library.deleteBook(it.id) } }
                    selectedBooks = emptySet()
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))

        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            if (!selecting) {
                FloatingActionButton(onClick = { showQuickAddMenu = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add book")
                }
                DropdownMenu(expanded = showQuickAddMenu, onDismissRequest = { showQuickAddMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Add book (file)") },
                        onClick = {
                            showQuickAddMenu = false
                            importActions.addFiles()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Add book (folder)") },
                        onClick = {
                            showQuickAddMenu = false
                            importActions.addFolder()
                        },
                    )
                }
            }
        }
    }
}
