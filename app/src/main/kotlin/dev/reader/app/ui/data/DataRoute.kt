package dev.reader.app.ui.data

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import dev.reader.app.data.AppLog
import dev.reader.app.data.ReaderGraph
import dev.reader.app.ui.describeImportError
import dev.reader.data.export.ExportScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock

private val ANY_FILE = arrayOf("*/*")

@Composable
fun DataRoute(graph: ReaderGraph, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingScopes by remember { mutableStateOf(ExportScope.entries.toSet()) }
    var includeBooks by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var danger by remember { mutableStateOf<DangerAction?>(null) }

    fun run(done: String, block: suspend () -> Unit) {
        busy = true
        status = "Working\u2026"
        scope.launch {
            AppLog.debug("Starting data operation: $done", "DataOperations")
            status = runCatching { withContext(Dispatchers.IO) { block() } }
                .fold(
                    onSuccess = {
                        AppLog.info(done, "DataOperations")
                        done
                    },
                    onFailure = {
                        AppLog.error("Data operation failed ($done)", "DataOperations", it)
                        "Failed: ${describeImportError(it)}"
                    },
                )
            busy = false
        }
    }

    fun write(uri: Uri?, done: String, body: suspend (java.io.OutputStream) -> Unit) {
        uri ?: return
        run(done) {
            context.contentResolver.openOutputStream(uri)?.use { body(it) } ?: error("Couldn't open that file")
        }
    }

    fun read(uri: Uri?, done: String, body: suspend (java.io.InputStream) -> Unit) {
        uri ?: return
        run(done) {
            context.contentResolver.openInputStream(uri)?.use { body(it) } ?: error("Couldn't open that file")
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) {
        val scopes = pendingScopes
        write(it, "Exported ${scopes.describe()}") { out -> out.write(graph.exporter.export(scopes).toByteArray()) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        read(it, "Import complete") { input ->
            graph.exporter.import(input.readBytes().decodeToString())
            graph.refreshMissingCovers()
        }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        val withBooks = includeBooks
        write(it, if (withBooks) "Backup created (with book files)" else "Backup created") { out ->
            graph.backups.write(out, withBooks)
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        read(it, "Backup applied") { input ->
            graph.backups.restore(input)
            graph.refreshMissingCovers()
        }
    }

    DataScreen(
        status = status,
        busy = busy,
        onExport = { scopes ->
            pendingScopes = scopes
            exportLauncher.launch("reader-${scopes.fileLabel()}-${today()}.json")
        },
        onImport = { importLauncher.launch(ANY_FILE) },
        includeBooksInBackup = includeBooks,
        onIncludeBooksInBackupChange = { includeBooks = it },
        onCreateBackup = { backupLauncher.launch("reader-backup-${today()}.zip") },
        onRestoreBackup = { confirmRestore = true },
        onDanger = { danger = it },
        modifier = modifier,
    )

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Apply backup?") },
            text = { Text("Your current library, progress, stats and settings will be replaced by the backup's.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        restoreLauncher.launch(ANY_FILE)
                    },
                    modifier = Modifier.testTag("confirm-restore"),
                ) { Text("Choose backup") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }

    danger?.let { action ->
        DangerConfirmDialog(
            action = action,
            onConfirm = {
                danger = null
                AppLog.warn("Confirmed destructive action: ${action.name}", "DataOperations")
                when (action) {
                    DangerAction.REMOVE_ALL_BOOKS -> run("Removed all books") { graph.library.removeAllBooks() }
                    DangerAction.RESET_STATS -> run("Reset all stats") { graph.stats.resetAll() }
                }
            },
            onDismiss = { danger = null },
        )
    }
}

private fun today(): String = Clock.System.now().toString().substringBefore('T')

private fun Set<ExportScope>.fileLabel(): String =
    if (size == ExportScope.entries.size) "everything" else joinToString("-") { it.name.lowercase() }

private fun Set<ExportScope>.describe(): String =
    if (size == ExportScope.entries.size) "everything" else joinToString { it.name.lowercase() }
