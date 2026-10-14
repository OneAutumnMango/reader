package dev.reader.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.reader.app.data.ReaderGraph
import dev.reader.app.ui.appearance.AppearanceRoute
import dev.reader.app.ui.bookmarks.BookmarksRoute
import dev.reader.app.ui.categories.CategoriesRoute
import dev.reader.app.ui.data.DataRoute
import dev.reader.app.ui.library.BookDetailRoute
import dev.reader.app.ui.library.BookDetailState
import dev.reader.app.ui.library.LibraryRoute
import dev.reader.app.ui.reader.ReaderRoute
import dev.reader.app.ui.search.SearchRoute
import dev.reader.app.ui.settings.LogHistoryRoute
import dev.reader.app.ui.settings.SettingsRoute
import dev.reader.app.ui.stats.StatsRoute
import dev.reader.core.model.BookId
import dev.reader.core.model.CategoryId
import kotlinx.coroutines.launch

private val DRAWER_DESTINATIONS = listOf(
    Destination.Library to ("Library" to Icons.Filled.Menu),
    Destination.Appearance to ("Appearance" to Icons.Filled.Palette),
    Destination.Categories to ("Categories" to Icons.Filled.Category),
    Destination.Stats to ("Stats" to Icons.Filled.QueryStats),
    Destination.Data to ("Data" to Icons.Filled.Storage),
    Destination.Settings to ("Settings" to Icons.Filled.Settings),
)

private val CONTENT_MARGIN = 16.dp

private fun titleFor(destination: Destination): String = when (destination) {
    Destination.Library -> "Library"
    Destination.Search -> "Search"
    Destination.Stats -> "Stats"
    Destination.Appearance -> "Appearance"
    Destination.Data -> "Data"
    Destination.Settings -> "Settings"
    Destination.Categories -> "Categories"
    Destination.LogHistory -> "Log history"
    is Destination.BookDetail -> "Book"
    is Destination.Bookmarks -> "Bookmarks"
    is Destination.Reader -> ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderApp(graph: ReaderGraph) {
    val navigator = remember { Navigator() }
    // Kept here so each category tab's scroll position and the chosen tab survive opening a book.
    val libraryGridStates = remember { mutableMapOf<CategoryId?, LazyGridState>() }
    val bookDetailStates = remember { mutableMapOf<BookId, BookDetailState>() }
    val topBar = remember { TopBarState() }
    var libraryCategory by remember { mutableStateOf<CategoryId?>(null) }

    LaunchedEffect(Unit) { graph.scanRootFoldersOnLaunch() }

    BackHandler(enabled = navigator.handlesSystemBack) { navigator.back() }

    if (navigator.current is Destination.Reader) {
        ReaderDestinationContent(graph, navigator)
        return
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                DRAWER_DESTINATIONS.forEach { (destination, labelAndIcon) ->
                    val (label, icon) = labelAndIcon
                    NavigationDrawerItem(
                        selected = navigator.current == destination,
                        onClick = {
                            navigator.switchRoot(destination)
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            topBar.title ?: titleFor(navigator.current),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navigator.switchRoot(Destination.Library) },
                        )
                    },
                    actions = { topBar.actions?.invoke(this) },
                    navigationIcon = {
                        if (navigator.canGoBack) {
                            IconButton(onClick = { navigator.pop() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu")
                            }
                        }
                    },
                )
            },
        ) { padding ->
            CompositionLocalProvider(LocalTopBar provides topBar) {
                val content = Modifier
                    .padding(padding)
                    .padding(horizontal = CONTENT_MARGIN)
                when (val destination = navigator.current) {
                    Destination.Library -> LibraryRoute(
                        graph,
                        onOpenBook = { navigator.push(Destination.BookDetail(it.id)) },
                        onSearchClick = { navigator.push(Destination.Search) },
                        gridStateFor = { libraryGridStates.getOrPut(it) { LazyGridState() } },
                        selectedCategory = libraryCategory,
                        onCategorySelected = { libraryCategory = it },
                        modifier = content,
                    )
                    Destination.Search -> SearchRoute(
                        graph,
                        onBookClick = { navigator.push(Destination.BookDetail(it.id)) },
                        modifier = content,
                    )
                    Destination.Stats -> StatsRoute(graph, content)
                    Destination.Appearance -> AppearanceRoute(graph, content)
                    Destination.Categories -> CategoriesRoute(graph, content)
                    Destination.Data -> DataRoute(graph, content)
                    Destination.Settings -> SettingsRoute(
                        graph,
                        onViewLogHistory = { navigator.push(Destination.LogHistory) },
                        modifier = content,
                    )
                    Destination.LogHistory -> LogHistoryRoute(content)
                    is Destination.BookDetail -> BookDetailRoute(
                        graph,
                        destination.bookId,
                        onOpenChapter = { navigator.push(Destination.Reader(destination.bookId, it.id)) },
                        onOpenBookmarks = { navigator.push(Destination.Bookmarks(destination.bookId)) },
                        modifier = content,
                        state = bookDetailStates.getOrPut(destination.bookId) { BookDetailState() },
                    )
                    is Destination.Bookmarks -> BookmarksRoute(
                        graph,
                        destination.bookId,
                        onJump = { navigator.push(Destination.Reader(destination.bookId, it.chapterId)) },
                        modifier = content,
                    )
                    is Destination.Reader -> Unit
                }
            }
        }
    }
}

/** The reader is a full-screen, edge-to-edge experience: no drawer, top bar or margin. */
@Composable
private fun ReaderDestinationContent(graph: ReaderGraph, navigator: Navigator) {
    val destination = navigator.current as Destination.Reader
    ReaderRoute(
        graph,
        destination.bookId,
        destination.chapterId,
        onOpenMenu = { navigator.pop() },
    )
}
