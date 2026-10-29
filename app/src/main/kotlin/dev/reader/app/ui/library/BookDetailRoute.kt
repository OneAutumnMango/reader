package dev.reader.app.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.app.data.AppLog
import dev.reader.app.data.ContentFileCopier
import dev.reader.app.data.ReaderGraph
import dev.reader.app.data.markBookRead
import dev.reader.app.data.markBookUnread
import dev.reader.app.ui.ScreenTopBar
import dev.reader.core.model.BookId
import dev.reader.core.model.Chapter
import dev.reader.parser.epub.EpubContentMissingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock

@Composable
fun BookDetailRoute(
    graph: ReaderGraph,
    bookId: BookId,
    onOpenChapter: (Chapter) -> Unit,
    onOpenBookmarks: () -> Unit,
    modifier: Modifier = Modifier,
    state: BookDetailState = remember { BookDetailState() },
    onOpenDetails: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val books by graph.library.observeBooks().collectAsState(initial = emptyList())
    val book = books.find { it.id == bookId } ?: return
    val chapters by graph.library.observeChapters(bookId).collectAsState(initial = emptyList())
    val regularChapters = chapters.filterNot { it.isExtra }.ifEmpty { chapters }
    val extras = chapters.filter { it.isExtra && it !in regularChapters }
    val bookmarks by graph.bookmarks.observe(bookId).collectAsState(initial = emptyList())
    val progress by graph.progress.observe(bookId).collectAsState(initial = null)
    val chapterProgress by graph.progress.observeChapterProgress(bookId).collectAsState(initial = emptyMap())
    val categories by graph.library.observeCategories().collectAsState(initial = emptyList())
    val currentChapter = chapters.find { it.id == progress?.chapterId }
    var showMenu by remember { mutableStateOf(false) }
    var showChapterDetect by remember { mutableStateOf(false) }
    var showCoverSearch by remember { mutableStateOf(false) }
    var reimporting by remember { mutableStateOf(false) }
    var reimportError by remember { mutableStateOf<String?>(null) }

    val coverPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val file = ContentFileCopier.copyToCache(context, uri) ?: return@launch
            graph.library.updateBookDetails(book.copy(coverPath = file.path))
        }
    }

    ScreenTopBar(book.title) {
        IconButton(onClick = { showMenu = true }, modifier = Modifier.testTag("book-detail-menu")) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Book options")
        }
    }

    BookDetailScreen(
        book = book,
        chapters = regularChapters,
        extras = extras,
        bookmarks = bookmarks,
        onChapterClick = onOpenChapter,
        currentChapterId = currentChapter?.id,
        chapterProgress = chapterProgress,
        resumeLabel = if (currentChapter != null) "Resume" else "Start reading",
        onResume = { (currentChapter ?: regularChapters.firstOrNull())?.let(onOpenChapter) },
        onOpenBookmarks = onOpenBookmarks,
        onSetRead = { targets, read ->
            scope.launch {
                val now = Clock.System.now()
                graph.progress.setChaptersRead(book.id, targets.map { it.id }, read, now)
                progress?.takeIf { !read && it.finished }?.let { graph.progress.update(it.copy(finished = false)) }
            }
        },
        onPdfRenderModeChange = if (book.format == dev.reader.core.model.BookFormat.PDF) {
            { mode ->
                scope.launch { graph.library.updateBookDetails(book.copy(pdfRenderMode = mode)) }
            }
        } else {
            null
        },
        state = state,
        modifier = modifier,
    )

    if (showMenu) {
        val allRead = regularChapters.isNotEmpty() &&
            regularChapters.all { chapterProgress[it.id]?.finished == true }
        BookContextMenu(
            book = book,
            categories = categories,
            allRead = allRead,
            onChangeCategories = { ids ->
                scope.launch { graph.library.updateCategories(book.id, ids) }
            },
            onRename = { newTitle ->
                scope.launch { graph.library.updateBookDetails(book.copy(title = newTitle)) }
            },
            onChangeCover = { coverPickerLauncher.launch(arrayOf("image/*")) },
            onFindCoverOnline = { showCoverSearch = true },
            onMarkAllRead = { scope.launch { graph.markBookRead(book.id) } },
            onMarkAllUnread = { scope.launch { graph.markBookUnread(book.id) } },
            onRemove = { scope.launch { graph.library.deleteBook(book.id) } },
            onDismiss = { showMenu = false },
            onShowDetails = onOpenDetails,
            onDetectChapters = if (book.supportsChapterDetection()) ({ showChapterDetect = true }) else null,
            onReimport = {
                reimporting = true
                scope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching { graph.reimportBook(book) }
                            .onFailure {
                                AppLog.error("Couldn't reimport book", "BookDetail", it)
                                reimportError = it.toReimportErrorMessage(book.title)
                            }
                    }
                    reimporting = false
                }
            },
        )
    }

    if (reimporting) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Reimporting…")
                }
            },
        )
    }

    reimportError?.let { message ->
        AlertDialog(
            onDismissRequest = { reimportError = null },
            confirmButton = { TextButton(onClick = { reimportError = null }) { Text("OK") } },
            title = { Text("Couldn't reimport") },
            text = { Text(message) },
        )
    }

    if (showCoverSearch) {
        CoverSearchDialog(
            title = book.title,
            author = book.author,
            service = graph.coverSearch,
            onCoverBytesChosen = { bytes ->
                scope.launch {
                    val file = graph.coverSearch.saveToCache(context, bytes)
                    graph.library.updateBookDetails(book.copy(coverPath = file.path))
                }
                showCoverSearch = false
            },
            onDismiss = { showCoverSearch = false },
        )
    }

    if (showChapterDetect) {
        ChapterDetectHost(graph, book, onDismiss = { showChapterDetect = false })
    }
}

/** Tells apart a broken source file (needs replacing) from any other reimport failure. */
private fun Throwable.toReimportErrorMessage(title: String): String = when (this) {
    is EpubContentMissingException ->
        "“$title”'s file seems to be missing content — you may need to find a new copy of it."
    else -> "Couldn't reimport “$title”: ${message ?: this::class.simpleName}"
}
