package dev.reader.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.reader.app.data.ReaderGraph
import dev.reader.app.ui.rememberImportActions

@Composable
fun SettingsRoute(graph: ReaderGraph, modifier: Modifier = Modifier) {
    val actions = rememberImportActions(graph)
    SettingsScreen(
        status = actions.status,
        onAddFiles = actions.addFiles,
        onAddFolder = actions.addFolder,
        modifier = modifier,
    )
}
