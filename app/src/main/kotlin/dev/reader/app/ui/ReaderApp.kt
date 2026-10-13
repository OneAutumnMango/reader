package dev.reader.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.reader.app.data.ReaderGraph
import dev.reader.app.ui.appearance.AppearanceRoute
import dev.reader.app.ui.bookmarks.BookmarksRoute
import dev.reader.app.ui.export.ImportExportRoute
import dev.reader.app.ui.library.BookDetailRoute
import dev.reader.app.ui.library.LibraryRoute
import dev.reader.app.ui.reader.ReaderRoute
import dev.reader.app.ui.search.SearchRoute
import dev.reader.app.ui.settings.SettingsRoute
import dev.reader.app.ui.stats.StatsRoute
import kotlinx.coroutines.launch

private val DRAWER_DESTINATIONS = listOf(
    Destination.Library to ("Library" to Icons.Filled.Menu),
    Destination.Appearance to ("Appearance" to Icons.Filled.Palette),
    Destination.Stats to ("Stats" to Icons.Filled.QueryStats),
    Destination.ImportExport to ("Import / export" to Icons.Filled.ImportExport),
    Destination.Settings to ("Settings" to Icons.Filled.Settings),
)

private val CONTENT_MARGIN = 16.dp

private fun titleFor(destination: Destination): String = when (destination) {
    Destination.Library -> "Library"
    Destination.Search -> "Search"
    Destination.Stats -> "Stats"
    Destination.Appearance -> "Appearance"
    Destination.ImportExport -> "Import / export"
    Destination.Settings -> "Settings"
    is Destination.BookDetail -> "Book"
    is Destination.Bookmarks -> "Bookmarks"
    is Destination.Reader -> ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderApp(graph: ReaderGraph) {
    val navigator = remember { Navigator() }

    BackHandler(enabled = navigator.canGoBack) { navigator.pop() }

    if (navigator.current is Destination.Reader) {
        ReaderDestinationContent(graph, navigator)
        return
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
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
                    title = { Text(titleFor(navigator.current)) },
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
            val content = Modifier
                .padding(padding)
                .padding(horizontal = CONTENT_MARGIN)
            when (val destination = navigator.current) {
                Destination.Library -> LibraryRoute(
                    graph,
                    onOpenBook = { navigator.push(Destination.BookDetail(it.id)) },
                    onSearchClick = { navigator.push(Destination.Search) },
                    modifier = content,
                )
                Destination.Search -> SearchRoute(
                    graph,
                    onBookClick = { navigator.push(Destination.BookDetail(it.id)) },
                    modifier = content,
                )
                Destination.Stats -> StatsRoute(graph, content)
                Destination.Appearance -> AppearanceRoute(graph, content)
                Destination.ImportExport -> ImportExportRoute(graph, content)
                Destination.Settings -> SettingsRoute(graph, content)
                is Destination.BookDetail -> BookDetailRoute(
                    graph,
                    destination.bookId,
                    onOpenChapter = { navigator.push(Destination.Reader(destination.bookId, it.id)) },
                    onOpenBookmarks = { navigator.push(Destination.Bookmarks(destination.bookId)) },
                    modifier = content,
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
