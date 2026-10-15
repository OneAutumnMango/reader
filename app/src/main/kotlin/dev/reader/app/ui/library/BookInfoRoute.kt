package dev.reader.app.ui.library

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.reader.app.data.BookLocations
import dev.reader.app.data.ReaderGraph
import dev.reader.app.ui.ScreenTopBar
import dev.reader.core.model.BookId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun BookInfoRoute(graph: ReaderGraph, bookId: BookId, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val books by graph.library.observeBooks().collectAsState(initial = emptyList())
    val book = books.find { it.id == bookId } ?: return
    val chapters by graph.library.observeChapters(bookId).collectAsState(initial = emptyList())
    val chapterProgress by graph.progress.observeChapterProgress(bookId).collectAsState(initial = emptyMap())
    val sessions by graph.stats.observeSessions(bookId).collectAsState(initial = emptyList())
    val preferences by graph.settings.preferences.collectAsState(initial = null)
    // Books from library folders imported before origins were stored are still found through the scan map.
    val origin = book.originUri
        ?: preferences?.rootEntryBooks?.entries?.firstOrNull { it.value == book.sourcePath }?.key
    val location = origin?.let { BookLocations.locate(it, isFolder = book.isLightNovel) }
    val size by produceState<Long?>(null, book.sourcePath) {
        value = withContext(Dispatchers.IO) {
            File(book.sourcePath).walkTopDown().filter { it.isFile }.sumOf { it.length() }.takeIf { it > 0 }
        }
    }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    ScreenTopBar("Details \u00b7 ${book.title}")
    BookInfoScreen(
        book = book,
        chapters = chapters,
        chapterProgress = chapterProgress,
        sessions = sessions,
        location = location,
        sizeBytes = size,
        onOpenFolder = {
            val folder = location?.folder
            if (folder == null || !BookLocations.openFolder(context, folder)) {
                toast("No file manager here can open that folder")
            }
        },
        onOpenItem = {
            if (location == null || !BookLocations.openItem(context, location.item)) toast("Couldn't open the original")
        },
        onCopyLocation = {
            location ?: return@BookInfoScreen
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("Book location", location.label))
            toast("Location copied")
        },
        modifier = modifier,
    )
}
