package dev.reader.app.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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
import dev.reader.app.data.ChapterLoader
import dev.reader.app.data.ReaderGraph
import dev.reader.core.model.BookId
import dev.reader.core.model.Bookmark
import dev.reader.core.model.BookmarkId
import dev.reader.core.model.ChapterId
import dev.reader.core.model.Progress
import dev.reader.core.reader.TapAction
import dev.reader.core.richtext.RichDocument
import dev.reader.core.theme.ReaderTheme
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Clock

@Composable
fun ReaderRoute(
    graph: ReaderGraph,
    bookId: BookId,
    chapterId: ChapterId,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val books by graph.library.observeBooks().collectAsState(initial = emptyList())
    val chapters by graph.library.observeChapters(bookId).collectAsState(initial = emptyList())
    val book = books.find { it.id == bookId }
    val chapter = chapters.find { it.id == chapterId }
    val preferences by graph.settings.preferences.collectAsState(initial = null)

    var document by remember(bookId, chapterId) { mutableStateOf<RichDocument?>(null) }
    var loadFailed by remember(bookId, chapterId) { mutableStateOf(false) }

    LaunchedEffect(book, chapter) {
        val b = book ?: return@LaunchedEffect
        val c = chapter ?: return@LaunchedEffect
        document = ChapterLoader.load(b, c)
        loadFailed = document == null
    }

    val prefs = preferences
    val doc = document
    when {
        doc != null && prefs != null -> {
            val theme = prefs.themePresets.find { it.name == prefs.activeThemePresetName } ?: ReaderTheme.Default
            ReaderScreen(
                document = doc,
                theme = theme,
                settings = prefs.readerSettings,
                onTapAction = { action ->
                    when (action) {
                        TapAction.OPEN_MENU -> onOpenMenu()
                        TapAction.TOGGLE_BOOKMARK -> scope.launch {
                            graph.bookmarks.add(
                                Bookmark(
                                    id = BookmarkId(UUID.randomUUID().toString()),
                                    bookId = bookId,
                                    chapterId = chapterId,
                                    position = 0f,
                                    createdAt = Clock.System.now(),
                                ),
                            )
                        }
                        else -> Unit
                    }
                },
                onPageChanged = { index, pageCount ->
                    if (pageCount > 0) {
                        scope.launch {
                            graph.progress.update(
                                Progress(
                                    bookId = bookId,
                                    chapterId = chapterId,
                                    position = index.toFloat() / pageCount,
                                    updatedAt = Clock.System.now(),
                                ),
                            )
                        }
                    }
                },
                modifier = modifier,
            )
        }
        loadFailed -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Couldn't load this chapter.")
        }
        else -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
