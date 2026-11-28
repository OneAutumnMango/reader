package dev.reader.app.ui.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.reader.app.data.AppLog
import dev.reader.app.data.ReaderGraph
import dev.reader.app.data.SafSyncFolder
import kotlinx.coroutines.flow.first

@Composable
fun OnlineSyncRoute(graph: ReaderGraph, onMerge: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by graph.sync.state.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }

    var pickedFolder by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { graph.sync.check() }

    // The controller ignores calls while busy (a check can start while the picker is open), so wait for it.
    LaunchedEffect(pickedFolder) {
        val uri = pickedFolder ?: return@LaunchedEffect
        graph.sync.state.first { !it.running }
        graph.sync.setFolder(uri)
        // Lists what the new folder has waiting.
        graph.sync.state.first { !it.running }
        graph.sync.check()
        // Last, because changing it cancels this effect.
        pickedFolder = null
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            SafSyncFolder.persistPermission(context, uri)
            pickedFolder = uri.toString()
        } catch (e: SecurityException) {
            AppLog.warn("Could not keep access to the sync folder", "Sync", e)
            message = "Could not keep access to that folder. Choose another one."
        }
    }

    OnlineSyncScreen(
        state,
        onChooseFolder = { folderPicker.launch(null) },
        onDeviceName = graph.sync::setDeviceName,
        onSyncNow = { graph.sync.syncNow() },
        onMerge = onMerge,
        onUndo = { graph.sync.undo { message = describeUndo(it) } },
        modifier = modifier,
    )

    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
}
