package dev.reader.app.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.reader.app.data.ContentFileCopier
import dev.reader.app.data.ReaderGraph
import dev.reader.core.model.Book
import dev.reader.core.model.CategoryId
import kotlinx.coroutines.launch

@Composable
fun LibraryRoute(graph: ReaderGraph, onOpenBook: (Book) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val books by graph.library.observeBooks().collectAsState(initial = emptyList())
    val categories by graph.library.observeCategories().collectAsState(initial = emptyList())
    var selectedCategory by remember { mutableStateOf<CategoryId?>(null) }
    var status by remember { mutableStateOf("") }

    val addFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            var imported = 0
            uris.forEach { uri ->
                val file = ContentFileCopier.copyToCache(context, uri) ?: return@forEach
                if (runCatching { graph.importer.importFile(file) }.isSuccess) imported++
            }
            status = "Imported $imported of ${uris.size} file(s)"
        }
    }
    val addFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val folder = ContentFileCopier.copyTreeToCache(context, treeUri)
            val imported = folder?.let { runCatching { graph.importer.importLightNovelFolder(it) }.isSuccess } == true
            status = if (imported) "Imported light novel" else "Could not import that folder"
        }
    }

    Column(modifier = modifier) {
        Row {
            Button(onClick = { addFilesLauncher.launch(arrayOf("*/*")) }) { Text("Add files") }
            Button(onClick = { addFolderLauncher.launch(null) }) { Text("Add light novel folder") }
        }
        if (status.isNotBlank()) Text(status)
        LibraryScreen(
            books = books,
            categories = categories,
            selectedCategory = selectedCategory,
            progressFor = { null },
            onCategorySelected = { selectedCategory = it },
            onBookClick = onOpenBook,
        )
    }
}
