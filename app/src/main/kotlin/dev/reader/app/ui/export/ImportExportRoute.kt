package dev.reader.app.ui.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.reader.app.data.ReaderGraph
import kotlinx.coroutines.launch

@Composable
fun ImportExportRoute(graph: ReaderGraph, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = graph.exporter.export()
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            status = "Exported library"
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            if (text != null && runCatching { graph.exporter.import(text) }.isSuccess) {
                status = "Imported library"
            } else {
                status = "Could not read that file"
            }
        }
    }

    ImportExportScreen(
        status = status,
        onExport = { exportLauncher.launch("reader-export.json") },
        onImport = { importLauncher.launch(arrayOf("application/json")) },
        modifier = modifier,
    )
}
