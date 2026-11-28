package dev.reader.app.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.reader.app.ui.MenuSubheader
import java.text.DateFormat
import java.util.Date

private const val EXPLANATION = "Online Sync uses a folder that an app such as Syncthing keeps in step " +
    "between your devices. Reader doesn't send the files itself."

private const val MAX_NAME_INPUT = 40

@Composable
fun OnlineSyncScreen(
    state: SyncUiState,
    onChooseFolder: () -> Unit,
    onDeviceName: (String) -> Unit,
    onSyncNow: () -> Unit,
    onMerge: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Held outside the list so that scrolling away keeps it. It resets only when the saved name changes.
    var name by rememberSaveable(state.deviceName) { mutableStateOf(state.deviceName) }
    var confirmingUndo by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("online-sync-list"),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text(EXPLANATION) }
        item { FolderControls(state, onChooseFolder) }
        item { NameControls(state, name, { name = it.take(MAX_NAME_INPUT) }, onDeviceName) }
        item { SyncControls(state, onSyncNow, onMerge) }
        state.error?.let { error ->
            item {
                MenuSubheader("Last error")
                Bounded(error, lines = 4, error = true)
            }
        }
        devices(state)
        lastSync(state) { confirmingUndo = true }
    }

    if (confirmingUndo && state.lastChanges.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { confirmingUndo = false },
            title = { Text("Undo last apply?") },
            text = {
                Text(
                    "Puts back the previous values (${state.lastChanges.size}). " +
                        "Items changed since are skipped. Created categories remain.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingUndo = false
                        onUndo()
                    },
                    enabled = !state.running,
                ) { Text("Undo") }
            },
            dismissButton = { TextButton(onClick = { confirmingUndo = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FolderControls(state: SyncUiState, onChooseFolder: () -> Unit) {
    Column {
        state.folderName?.let { Bounded("Folder: $it") }
        OutlinedButton(onClick = onChooseFolder, enabled = !state.running, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.folderName == null) "Choose folder" else "Change folder")
        }
    }
}

@Composable
private fun NameControls(state: SyncUiState, name: String, onName: (String) -> Unit, onSave: (String) -> Unit) {
    // Saved only on request: the controller ignores calls while busy, and tidies the name it is given.
    val canSave = !state.running && name.isNotBlank() && name.trim() != state.deviceName
    Column {
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            enabled = !state.running,
            label = { Text("Device name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(name) }),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { onSave(name) }, enabled = canSave) { Text("Save name") }
    }
}

@Composable
private fun SyncControls(state: SyncUiState, onSyncNow: () -> Unit, onMerge: () -> Unit) {
    Column {
        if (state.running) {
            LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Syncing" })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSyncNow,
                enabled = state.folderName != null && !state.running,
                modifier = Modifier.weight(1f),
            ) { Text("Sync now") }
            OutlinedButton(
                onClick = onMerge,
                enabled = state.questionCount > 0 && !state.running,
                modifier = Modifier.weight(1f),
            ) { Text("Merge (${state.questionCount})") }
        }
    }
}

private fun LazyListScope.devices(state: SyncUiState) {
    val report = state.report
    if (report.devices.isNotEmpty()) {
        item {
            MenuSubheader("Devices found")
            Text(
                "A recent publish time does not prove it is the latest version.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        // No keys: conflict copies repeat a device ID.
        items(report.devices) { device ->
            val name = if (device.id == state.deviceId) "This device (another copy)" else device.name
            Column {
                Bounded(if (device.conflictCopy) "$name (conflict copy)" else name)
                Detail("Published ${formatTime(device.publishedAt)}")
            }
        }
    }
    if (report.rejected.isNotEmpty()) {
        item { MenuSubheader("Rejected files") }
        items(report.rejected) { Bounded("${it.name}: ${it.reason}", lines = 4, error = true) }
    }
}

private fun LazyListScope.lastSync(state: SyncUiState, onUndo: () -> Unit) {
    item {
        MenuSubheader("Last sync")
        Text(state.lastSyncAt?.let { "Last synced ${formatTime(it)}" } ?: "Not synced yet")
        state.report.publishError?.let { Bounded("Not published: $it", lines = 4, error = true) }
    }
    if (state.lastChanges.isNotEmpty()) {
        item {
            MenuSubheader("Applied")
            OutlinedButton(onClick = onUndo, enabled = !state.running, modifier = Modifier.fillMaxWidth()) {
                Text("Undo last apply")
            }
        }
        items(state.lastChanges) { change ->
            Column {
                Bounded("${change.title}: ${describeItem(change.key)}")
                Detail("Was: ${describeValue(change.before)}")
                Detail("Now: ${describeValue(change.after)}")
                change.sourceDeviceId?.let { id ->
                    val source = if (id == state.deviceId) {
                        "This device (another copy)"
                    } else {
                        state.report.deviceNames[id] ?: "another device"
                    }
                    Detail("From $source")
                }
            }
        }
    }
    if (state.report.questions.isNotEmpty()) {
        item { MenuSubheader("Pending (${state.report.questions.size})") }
        items(state.report.questions) { Bounded("${it.title}: ${describeItem(it.key)}") }
    }
    if (state.report.skipped.isNotEmpty()) {
        item { MenuSubheader("Skipped") }
        items(state.report.skipped) { Bounded("${it.title}: ${it.reason}", lines = 3) }
    }
}

/** Titles, device names and file names come from other devices, so their length is bounded. */
@Composable
private fun Bounded(text: String, lines: Int = 2, error: Boolean = false) {
    Text(
        text,
        maxLines = lines,
        overflow = TextOverflow.Ellipsis,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun Detail(text: String) {
    Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
}

private fun formatTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
