package dev.reader.app.ui.data

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import dev.reader.app.data.AppLog
import dev.reader.app.data.AutomaticBackupScheduler
import dev.reader.app.data.ReaderGraph
import dev.reader.app.ui.describeImportError
import dev.reader.data.export.ExportScope
import dev.reader.data.store.PreferencesFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock

private val ANY_FILE = arrayOf("*/*")

/** A picked backup file, read far enough to show what restoring it would change. */
private data class PendingRestore(val uri: Uri, val preview: RestorePreview)

@Composable
fun DataRoute(graph: ReaderGraph, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences by graph.settings.preferences.collectAsState(initial = PreferencesFile())
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingScopes by remember { mutableStateOf(ExportScope.entries.toSet()) }
    var includeBooks by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<PendingRestore?>(null) }
    var danger by remember { mutableStateOf<DangerAction?>(null) }

    LaunchedEffect(
        preferences.automaticBackupsEnabled,
        preferences.automaticBackupFolderUri,
        preferences.automaticBackupFrequency,
    ) {
        AutomaticBackupScheduler.update(context, preferences)
    }

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
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        status = "Reading backup…"
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val backupBooks = requireNotNull(context.contentResolver.openInputStream(uri)) {
                        "Couldn't open that file"
                    }.use { graph.backups.preview(it) }
                    val current = graph.library.observeBooks().first()
                    PendingRestore(uri, restorePreview(current, backupBooks))
                }
            }
            busy = false
            outcome.fold(
                onSuccess = {
                    status = ""
                    pendingRestore = it
                },
                onFailure = { status = "Failed: ${describeImportError(it)}" },
            )
        }
    }
    val backupFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            scope.launch { graph.settings.update { it.copy(automaticBackupFolderUri = uri.toString()) } }
        }
    }
    val backupFolderName = preferences.automaticBackupFolderUri?.let { value ->
        runCatching { DocumentFile.fromTreeUri(context, Uri.parse(value))?.name }.getOrNull()
            ?.takeIf(String::isNotBlank)
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
        onRestoreBackup = { restoreLauncher.launch(ANY_FILE) },
        onDanger = { danger = it },
        modifier = modifier,
        automaticBackupsEnabled = preferences.automaticBackupsEnabled,
        onAutomaticBackupsEnabledChange = { enabled ->
            scope.launch { graph.settings.update { it.copy(automaticBackupsEnabled = enabled) } }
        },
        automaticBackupFolderName = backupFolderName,
        onChooseAutomaticBackupFolder = { backupFolderLauncher.launch(null) },
        automaticBackupFrequency = preferences.automaticBackupFrequency,
        onAutomaticBackupFrequencyChange = { frequency ->
            scope.launch { graph.settings.update { it.copy(automaticBackupFrequency = frequency) } }
        },
        automaticBackupIncludeBooks = preferences.automaticBackupIncludeBooks,
        onAutomaticBackupIncludeBooksChange = { include ->
            scope.launch { graph.settings.update { it.copy(automaticBackupIncludeBooks = include) } }
        },
    )

    pendingRestore?.let { pending ->
        RestorePreviewDialog(
            preview = pending.preview,
            onMerge = {
                pendingRestore = null
                read(pending.uri, "Backup merged") { input ->
                    graph.backups.restore(input, merge = true)
                    graph.refreshMissingCovers()
                }
            },
            onReplace = {
                pendingRestore = null
                read(pending.uri, "Backup applied") { input ->
                    graph.backups.restore(input)
                    graph.refreshMissingCovers()
                }
            },
            onDismiss = { pendingRestore = null },
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
