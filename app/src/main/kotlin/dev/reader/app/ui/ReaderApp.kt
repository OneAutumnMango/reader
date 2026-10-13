package dev.reader.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.reader.app.data.ReaderGraph
import dev.reader.app.ui.appearance.AppearanceRoute
import dev.reader.app.ui.bookmarks.BookmarksRoute
import dev.reader.app.ui.export.ImportExportRoute
import dev.reader.app.ui.library.BookDetailRoute
import dev.reader.app.ui.library.LibraryRoute
import dev.reader.app.ui.reader.ReaderRoute
import dev.reader.app.ui.search.SearchRoute
import dev.reader.app.ui.stats.StatsRoute

private val ROOT_DESTINATIONS = listOf(
    Destination.Library to ("Library" to Icons.Filled.Menu),
    Destination.Search to ("Search" to Icons.Filled.Search),
    Destination.Stats to ("Stats" to Icons.Filled.QueryStats),
    Destination.Appearance to ("Appearance" to Icons.Filled.Palette),
    Destination.ImportExport to ("Import/export" to Icons.Filled.ImportExport),
)

@Composable
fun ReaderApp(graph: ReaderGraph) {
    val navigator = remember { Navigator() }

    BackHandler(enabled = navigator.canGoBack) { navigator.pop() }

    Scaffold(
        bottomBar = {
            if (!navigator.canGoBack) {
                NavigationBar {
                    ROOT_DESTINATIONS.forEach { (destination, labelAndIcon) ->
                        val (label, icon) = labelAndIcon
                        NavigationBarItem(
                            selected = navigator.current == destination,
                            onClick = { navigator.switchRoot(destination) },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val content = Modifier.padding(padding)
        when (val destination = navigator.current) {
            Destination.Library -> LibraryRoute(
                graph,
                onOpenBook = { navigator.push(Destination.BookDetail(it.id)) },
                content,
            )
            Destination.Search -> SearchRoute(
                graph,
                onBookClick = { navigator.push(Destination.BookDetail(it.id)) },
                content,
            )
            Destination.Stats -> StatsRoute(graph, content)
            Destination.Appearance -> AppearanceRoute(graph, content)
            Destination.ImportExport -> ImportExportRoute(graph, content)
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
            is Destination.Reader -> ReaderRoute(
                graph,
                destination.bookId,
                destination.chapterId,
                onOpenMenu = { navigator.pop() },
                modifier = content,
            )
        }
    }
}
