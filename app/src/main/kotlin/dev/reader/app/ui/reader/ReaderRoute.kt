package dev.reader.app.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.model.Bookmark
import dev.reader.core.model.BookmarkId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.core.model.Progress
import dev.reader.core.reader.PdfRenderMode
import dev.reader.core.reader.ReaderSettings
import dev.reader.core.reader.ReadingMode
import dev.reader.core.reader.TapAction
import dev.reader.core.stats.ReadingSessionEvent
import dev.reader.core.theme.ReaderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private val MIN_SESSION = 5.seconds

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
    val allChapters by graph.library.observeChapters(bookId).collectAsState(initial = emptyList())
    // Extras (title page, copyright…) open on their own; otherwise only real chapters are swipeable.
    val chapters = remember(allChapters, chapterId) {
        val opened = allChapters.find { it.id == chapterId }
        if (opened?.isExtra == true) listOf(opened) else allChapters.filterNot { it.isExtra }.ifEmpty { allChapters }
    }
    val book = books.find { it.id == bookId }
    val preferences by graph.settings.preferences.collectAsState(initial = null)
    val prefs = preferences

    var showMenu by remember(bookId) { mutableStateOf(false) }
    var menuExpanded by remember(bookId) { mutableStateOf(false) }
    var position by remember(bookId) { mutableStateOf(0 to 0) }

    if (book == null || chapters.isEmpty() || prefs == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val readingMode = prefs.readerSettings.readingMode
    // Horizontal-paged mode already owns horizontal drags for page-turning within a chapter,
    // so the chapter-swipe pager only accepts user swipes in the other reading modes.
    val chapterSwipeEnabled = readingMode != ReadingMode.PAGED_HORIZONTAL_LTR &&
        readingMode != ReadingMode.PAGED_HORIZONTAL_RTL
    val initialPage = remember(bookId) { chapters.indexOfFirst { it.id == chapterId }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialPage) { chapters.size }
    val currentChapter = chapters.getOrNull(pagerState.currentPage) ?: chapters[initialPage]
    val theme = prefs.themePresets.find { it.name == prefs.activeThemePresetName } ?: ReaderTheme.Default
    val pdfRenderMode = if (book.format == BookFormat.PDF) book.pdfRenderMode ?: PdfRenderMode.FIXED_PAGE else null
    val pageBased = book.format == BookFormat.CBZ || book.format == BookFormat.CBR ||
        pdfRenderMode == PdfRenderMode.FIXED_PAGE

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = chapterSwipeEnabled,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ChapterPage(
                graph = graph,
                scope = scope,
                book = book,
                chapter = chapters[page],
                settings = prefs.readerSettings,
                theme = theme,
                onToggleMenu = { showMenu = !showMenu },
                onPositionChanged = { index, total -> if (page == pagerState.currentPage) position = index to total },
            )
        }

        if (showMenu) {
            ReaderMenuOverlay(
                progressLabel = readerProgressLabel(readingMode, position.first, position.second, pageBased),
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
                expanded = menuExpanded,
                onExpandedChange = { menuExpanded = it },
                pdfRenderMode = pdfRenderMode,
                onPdfRenderModeChange = { mode ->
                    scope.launch { graph.library.updateBookDetails(book.copy(pdfRenderMode = mode)) }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    LaunchedEffect(currentChapter.id) {
        // Keep the book-level "last opened" pointer up to date as chapters are swiped through.
        val existing = graph.progress.observe(bookId).first()
        graph.progress.update(
            Progress(
                bookId = bookId,
                chapterId = currentChapter.id,
                position = existing?.takeIf { it.chapterId == currentChapter.id }?.position ?: 0f,
                updatedAt = Clock.System.now(),
            ),
        )
    }
}

@Composable
private fun ChapterPage(
    graph: ReaderGraph,
    scope: CoroutineScope,
    book: Book,
    chapter: Chapter,
    settings: ReaderSettings,
    theme: ReaderTheme,
    onToggleMenu: () -> Unit,
    onPositionChanged: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    var content by remember(chapter.id, book.pdfRenderMode) { mutableStateOf<ChapterContent?>(null) }
    var loadFailed by remember(chapter.id, book.pdfRenderMode) { mutableStateOf(false) }
    var initialPositionFraction by remember(chapter.id) { mutableStateOf<Float?>(null) }
    var lastFraction by remember(chapter.id) { mutableStateOf(0f) }
    var lastIndex by remember(chapter.id) { mutableStateOf<Int?>(null) }
    var pagesTurned by remember(chapter.id) { mutableStateOf(0) }
    val sessionStartedAt = remember(chapter.id) { Clock.System.now() }

    DisposableEffect(chapter.id) {
        onDispose {
            val endedAt = Clock.System.now()
            if (endedAt - sessionStartedAt < MIN_SESSION) return@onDispose
            scope.launch {
                graph.stats.recordSession(
                    ReadingSessionEvent(
                        bookId = book.id,
                        chapterId = chapter.id,
                        startedAt = sessionStartedAt,
                        endedAt = endedAt,
                        wordsRead = (chapter.wordCount * lastFraction).toInt(),
                        pagesTurned = pagesTurned,
                    ),
                )
            }
        }
    }

    LaunchedEffect(chapter.id, book.pdfRenderMode) {
        content = ChapterLoader.load(context, book, chapter)
        loadFailed = content == null
    }

    LaunchedEffect(chapter.id) {
        val saved = graph.progress.observeChapterProgress(book.id, chapter.id).first()
        initialPositionFraction = saved?.position ?: 0f
    }

    fun onTapAction(action: TapAction) {
        when (action) {
            TapAction.OPEN_MENU -> onToggleMenu()
            TapAction.TOGGLE_BOOKMARK -> scope.launch {
                graph.bookmarks.add(
                    Bookmark(
                        id = BookmarkId(UUID.randomUUID().toString()),
                        bookId = book.id,
                        chapterId = chapter.id,
                        position = 0f,
                        createdAt = Clock.System.now(),
                    ),
                )
            }
            else -> Unit
        }
    }

    fun onPageChanged(index: Int, pageCount: Int) {
        onPositionChanged(index, pageCount)
        if (lastIndex != null && lastIndex != index) pagesTurned++
        lastIndex = index
        if (pageCount > 0) {
            lastFraction = index.toFloat() / pageCount
            scope.launch {
                graph.progress.updateChapterProgress(
                    Progress(
                        bookId = book.id,
                        chapterId = chapter.id,
                        position = index.toFloat() / pageCount,
                        updatedAt = Clock.System.now(),
                    ),
                )
            }
        }
    }

    val loaded = content
    val startFraction = initialPositionFraction

    when {
        loaded is ChapterContent.Text && startFraction != null -> ReaderScreen(
            document = loaded.document,
            theme = theme,
            settings = settings,
            initialPositionFraction = startFraction,
            onTapAction = ::onTapAction,
            onPageChanged = ::onPageChanged,
            modifier = Modifier.fillMaxSize(),
        )
        loaded is ChapterContent.Comic && startFraction != null -> ComicReaderScreen(
            file = loaded.file,
            format = loaded.format,
            pages = loaded.pages,
            settings = settings,
            initialPositionFraction = startFraction,
            onTapAction = ::onTapAction,
            onPageChanged = ::onPageChanged,
            modifier = Modifier.fillMaxSize(),
        )
        loaded is ChapterContent.Pdf && startFraction != null -> PdfReaderScreen(
            file = loaded.file,
            settings = settings,
            initialPositionFraction = startFraction,
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
}
