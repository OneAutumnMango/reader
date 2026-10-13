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
import androidx.compose.ui.platform.LocalContext
import dev.reader.app.data.ChapterContent
import dev.reader.app.data.ChapterLoader
import dev.reader.app.data.ReaderGraph
import dev.reader.core.model.BookId
import dev.reader.core.model.Bookmark
import dev.reader.core.model.BookmarkId
import dev.reader.core.model.ChapterId
import dev.reader.core.model.Progress
import dev.reader.core.reader.TapAction
import dev.reader.core.theme.ReaderTheme
import kotlinx.coroutines.flow.first
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val books by graph.library.observeBooks().collectAsState(initial = emptyList())
    val chapters by graph.library.observeChapters(bookId).collectAsState(initial = emptyList())
    val book = books.find { it.id == bookId }
    val chapter = chapters.find { it.id == chapterId }
    val preferences by graph.settings.preferences.collectAsState(initial = null)

    var content by remember(bookId, chapterId) { mutableStateOf<ChapterContent?>(null) }
    var loadFailed by remember(bookId, chapterId) { mutableStateOf(false) }
    var showMenu by remember(bookId, chapterId) { mutableStateOf(false) }
    var position by remember(bookId, chapterId) { mutableStateOf(0 to 0) }
    var initialPositionFraction by remember(bookId, chapterId) { mutableStateOf<Float?>(null) }

    LaunchedEffect(book, chapter) {
        val b = book ?: return@LaunchedEffect
        val c = chapter ?: return@LaunchedEffect
        content = ChapterLoader.load(context, b, c)
        loadFailed = content == null
    }

    LaunchedEffect(bookId, chapterId) {
        val saved = graph.progress.observe(bookId).first()
        initialPositionFraction = saved?.takeIf { it.chapterId == chapterId }?.position ?: 0f
    }

    fun onTapAction(action: TapAction) {
        when (action) {
            TapAction.OPEN_MENU -> showMenu = !showMenu
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
    }

    fun onPageChanged(index: Int, pageCount: Int) {
        position = index to pageCount
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
    }

    val prefs = preferences
    val loaded = content
    val startFraction = initialPositionFraction

    Box(modifier = modifier.fillMaxSize()) {
        when {
            loaded is ChapterContent.Text && prefs != null && startFraction != null -> {
                val theme = prefs.themePresets.find { it.name == prefs.activeThemePresetName } ?: ReaderTheme.Default
                ReaderScreen(
                    document = loaded.document,
                    theme = theme,
                    settings = prefs.readerSettings,
                    initialPositionFraction = startFraction,
                    onTapAction = ::onTapAction,
                    onPageChanged = ::onPageChanged,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            loaded is ChapterContent.Comic && prefs != null -> ComicReaderScreen(
                file = loaded.file,
                format = loaded.format,
                pages = loaded.pages,
                tapZones = prefs.readerSettings.tapZones,
                onTapAction = ::onTapAction,
                onPageChanged = ::onPageChanged,
                modifier = Modifier.fillMaxSize(),
            )
            loaded is ChapterContent.Pdf && prefs != null -> PdfReaderScreen(
                file = loaded.file,
                tapZones = prefs.readerSettings.tapZones,
                onTapAction = ::onTapAction,
                onPageChanged = ::onPageChanged,
                modifier = Modifier.fillMaxSize(),
            )
            loadFailed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Couldn't load this chapter.")
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        if (showMenu && prefs != null) {
            val theme = prefs.themePresets.find { it.name == prefs.activeThemePresetName } ?: ReaderTheme.Default
            val readingMode = prefs.readerSettings.readingMode
            ReaderMenuOverlay(
                progressLabel = readerProgressLabel(readingMode, position.first, position.second),
                theme = theme,
                onThemeChange = { updated ->
                    scope.launch {
                        graph.settings.update { p ->
                            val merged = p.themePresets.map { if (it.name == updated.name) updated else it }
                            p.copy(themePresets = merged)
                        }
                    }
                },
                settings = prefs.readerSettings,
                onSettingsChange = { updated ->
                    scope.launch { graph.settings.update { it.copy(readerSettings = updated) } }
                },
                onBack = onOpenMenu,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
