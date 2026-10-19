package dev.reader.app.ui.reader

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var isResumed by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    val books by graph.library.observeBooks().collectAsState(initial = emptyList())
    val allChapters by graph.library.observeChapters(bookId).collectAsState(initial = emptyList())
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            isResumed = event.targetState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Extras (title page, copyright…) open on their own; otherwise only real chapters are swipeable.
    val chapters = remember(allChapters, chapterId, books) {
        val pdfPages = books.find { it.id == bookId }
            ?.let { it.format == BookFormat.PDF && it.pdfRenderMode != PdfRenderMode.REFLOW_TEXT } == true
        val opened = allChapters.find { it.id == chapterId }
        when {
            // Chapters can start mid-page, so page view always shows the whole PDF.
            pdfPages -> allChapters.take(1)
            opened?.isExtra == true -> listOf(opened)
            else -> allChapters.filterNot { it.isExtra }.ifEmpty { allChapters }
        }
    }
    val book = books.find { it.id == bookId }
    val preferences by graph.settings.preferences.collectAsState(initial = null)
    val prefs = preferences

    var showMenu by remember(bookId) { mutableStateOf(false) }
    var menuExpanded by remember(bookId) { mutableStateOf(false) }
    // Per chapter page, so the label is right the moment a chapter is swiped in.
    val positions = remember(bookId) { mutableStateMapOf<Int, Pair<Int, Int>>() }

    if (book == null || chapters.isEmpty() || prefs == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val readingMode = prefs.readerSettings.readingMode
    val initialPage = remember(bookId) { chapters.indexOfFirst { it.id == chapterId }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialPage) { chapters.size }
    val currentChapter = chapters.getOrNull(pagerState.currentPage) ?: chapters[initialPage]
    val theme = prefs.themePresets.find { it.name == prefs.activeThemePresetName } ?: ReaderTheme.Default
    val pdfRenderMode = if (book.format == BookFormat.PDF) book.pdfRenderMode ?: PdfRenderMode.FIXED_PAGE else null
    val pageBased = book.format == BookFormat.CBZ || book.format == BookFormat.CBR ||
        pdfRenderMode == PdfRenderMode.FIXED_PAGE
    val search = rememberBookSearch(book, chapters)

    Box(modifier = modifier.fillMaxSize()) {
        // In horizontal paged modes the inner page pager scrolls first; once it hits its last/first
        // page the leftover swipe nests up into this pager and turns the chapter.
        HorizontalPager(
            state = pagerState,
            reverseLayout = readingMode == ReadingMode.PAGED_HORIZONTAL_RTL,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ChapterPage(
                graph = graph,
                scope = scope,
                book = book,
                chapter = chapters[page],
                isActive = page == pagerState.settledPage && !pagerState.isScrollInProgress && isResumed,
                settings = prefs.readerSettings,
                theme = theme,
                onToggleMenu = { showMenu = !showMenu },
                onPositionChanged = { index, total -> positions[page] = index to total },
                highlight = search.highlight,
                jump = search.jumpFor(page),
            )
        }

        if (showMenu) {
            val position = positions[pagerState.currentPage] ?: (0 to 0)
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
                onSearch = if (pageBased) {
                    null
                } else {
                    {
                        search.open = !search.open
                        menuExpanded = false
                    }
                },
                searchPanel = if (search.open && !pageBased) {
                    {
                        ReaderSearchPanel(
                            state = search.ui,
                            onQueryChange = { search.query = it },
                            onOptionsChange = { search.options = it },
                            onSelect = { index ->
                                search.select(index)?.let { scope.launch { pagerState.scrollToPage(it) } }
                            },
                            onClose = { search.open = false },
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    LaunchedEffect(currentChapter.id, pagerState.settledPage, pagerState.isScrollInProgress, isResumed) {
        if (pagerState.currentPage != pagerState.settledPage || pagerState.isScrollInProgress || !isResumed) {
            return@LaunchedEffect
        }
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
    isActive: Boolean,
    settings: ReaderSettings,
    theme: ReaderTheme,
    onToggleMenu: () -> Unit,
    onPositionChanged: (Int, Int) -> Unit,
    highlight: Regex?,
    jump: BlockJump?,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val bookmarks by graph.bookmarks.observe(book.id).collectAsState(initial = emptyList())
    var content by remember(chapter.id, book.pdfRenderMode) { mutableStateOf<ChapterContent?>(null) }
    var loadFailed by remember(chapter.id, book.pdfRenderMode) { mutableStateOf(false) }
    var initialPositionFraction by remember(chapter.id) { mutableStateOf<Float?>(null) }
    var finished by remember(chapter.id) { mutableStateOf(false) }
    var lastFraction by remember(chapter.id) { mutableStateOf(0f) }
    var lastIndex by remember(chapter.id) { mutableStateOf<Int?>(null) }
    var pagesTurned by remember(chapter.id) { mutableStateOf(0) }
    var recordedWords by remember(chapter.id) { mutableStateOf(0) }
    var recordedPages by remember(chapter.id) { mutableStateOf(0) }
    var noteSelection by remember(chapter.id) { mutableStateOf<String?>(null) }
    var noteDraft by remember(chapter.id) { mutableStateOf("") }
    val sessionTimer = remember(chapter.id) { ReadingSessionTimer() }
    val latestFraction by rememberUpdatedState(lastFraction)
    val latestPagesTurned by rememberUpdatedState(pagesTurned)
    val latestRecordedWords by rememberUpdatedState(recordedWords)
    val latestRecordedPages by rememberUpdatedState(recordedPages)

    val loaded = content
    val startFraction = initialPositionFraction
    val sessionActive = isActive && loaded != null && startFraction != null

    fun flushSession() {
        val interval = sessionTimer.setActive(false, SystemClock.elapsedRealtimeNanos(), Clock.System.now()) ?: return
        if (interval.duration < MIN_SESSION) return
        val words = (chapter.wordCount * latestFraction).toInt()
        val wordsRead = (words - latestRecordedWords).coerceAtLeast(0)
        val pagesRead = (latestPagesTurned - latestRecordedPages).coerceAtLeast(0)
        recordedWords = maxOf(latestRecordedWords, words)
        recordedPages = maxOf(latestRecordedPages, latestPagesTurned)
        graph.recordReadingSession(
            ReadingSessionEvent(
                bookId = book.id,
                chapterId = chapter.id,
                startedAt = interval.startedAt,
                endedAt = interval.endedAt,
                wordsRead = wordsRead,
                pagesTurned = pagesRead,
            ),
        )
    }

    DisposableEffect(sessionActive, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) flushSession()
        }
        lifecycle.addObserver(observer)
        if (sessionActive && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            sessionTimer.setActive(true, SystemClock.elapsedRealtimeNanos(), Clock.System.now())
            recordedWords = maxOf(recordedWords, (chapter.wordCount * lastFraction).toInt())
            recordedPages = pagesTurned
            val isFinished = finished || lastFraction >= 1f
            finished = isFinished
            graph.saveChapterProgress(
                Progress(
                    bookId = book.id,
                    chapterId = chapter.id,
                    position = lastFraction,
                    updatedAt = Clock.System.now(),
                    finished = isFinished,
                ),
            )
        }
        onDispose {
            lifecycle.removeObserver(observer)
            flushSession()
        }
    }

    LaunchedEffect(chapter.id, book.pdfRenderMode) {
        content = ChapterLoader.load(context, book, chapter)
        loadFailed = content == null
    }

    LaunchedEffect(chapter.id) {
        val saved = graph.progress.observeChapterProgress(book.id, chapter.id).first()
        finished = false
        // A finished chapter is being re-read, so start it from the top.
        initialPositionFraction = if (saved?.finished == true) 0f else saved?.position ?: 0f
        lastFraction = initialPositionFraction ?: 0f
        recordedWords = (chapter.wordCount * lastFraction).toInt()
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
            val atEnd = index >= pageCount - 1
            lastFraction = if (atEnd) 1f else index.toFloat() / pageCount
            if (isActive) {
                if (atEnd) finished = true
                graph.saveChapterProgress(
                    Progress(
                        bookId = book.id,
                        chapterId = chapter.id,
                        position = lastFraction,
                        updatedAt = Clock.System.now(),
                        finished = finished,
                    ),
                )
            }
        }
    }

    fun saveSelectionBookmark(action: SelectionAction, selectedText: String, note: String = "") {
        scope.launch {
            graph.bookmarks.add(
                Bookmark(
                    id = BookmarkId(UUID.randomUUID().toString()),
                    bookId = book.id,
                    chapterId = chapter.id,
                    position = lastFraction,
                    note = selectionBookmarkNote(action, selectedText, note),
                    createdAt = Clock.System.now(),
                ),
            )
        }
    }

    fun onSelectionAction(action: SelectionAction, selectedText: String) {
        when (action) {
            SelectionAction.TRANSLATE, SelectionAction.SEARCH, SelectionAction.DICTIONARY ->
                launchSelectionIntent(context, action, selectedText)
            SelectionAction.NOTE -> {
                noteSelection = selectedText
                noteDraft = ""
            }
            SelectionAction.HIGHLIGHT -> saveSelectionBookmark(action, selectedText)
            SelectionAction.COPY -> Unit
        }
    }

    var tappedBookmark by remember(chapter.id) { mutableStateOf<Bookmark?>(null) }

    fun onBookmarkTap(bookmarkId: String) {
        tappedBookmark = bookmarks.firstOrNull { it.id.value == bookmarkId }
    }

    when {
        loaded is ChapterContent.Text && startFraction != null -> ReaderScreen(
            document = remember(loaded.document, bookmarks, chapter.id) {
                loaded.document.withSavedHighlights(bookmarks.filter { it.chapterId == chapter.id })
            },
            theme = theme,
            settings = settings,
            initialPositionFraction = startFraction,
            onTapAction = ::onTapAction,
            onPageChanged = ::onPageChanged,
            modifier = Modifier.fillMaxSize(),
            highlight = highlight,
            jump = jump,
            onSelectionAction = ::onSelectionAction,
            onBookmarkTap = ::onBookmarkTap,
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

    noteSelection?.let { selectedText ->
        AlertDialog(
            onDismissRequest = { noteSelection = null },
            title = { Text("Add note") },
            text = {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    label = { Text("Note") },
                    supportingText = { Text(selectedText) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        saveSelectionBookmark(SelectionAction.NOTE, selectedText, noteDraft)
                        noteSelection = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { noteSelection = null }) { Text("Cancel") }
            },
        )
    }

    tappedBookmark?.let { bookmark ->
        val isNote = bookmark.note.startsWith("[Note]\n")
        val quote = when {
            isNote -> bookmark.note.removePrefix("[Note]\n").substringBefore("\n\n").trim()
            bookmark.note.startsWith("[Highlight]\n") -> bookmark.note.removePrefix("[Highlight]\n").trim()
            else -> bookmark.note
        }
        val noteContent = if (isNote) bookmark.note.substringAfter("\n\n", "").trim() else ""
        AlertDialog(
            onDismissRequest = { tappedBookmark = null },
            title = { Text(if (isNote) "Note" else "Highlight") },
            text = {
                Column {
                    Text(
                        quote,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                    )
                    if (noteContent.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(noteContent)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { graph.bookmarks.remove(bookmark) }
                        tappedBookmark = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { tappedBookmark = null }) { Text("Close") }
            },
        )
    }
}
