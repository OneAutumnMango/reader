package dev.reader.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.reader.app.data.AppLog
import dev.reader.app.data.LogEntry
import dev.reader.app.data.LogLevel

@Composable
fun LogHistoryRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var minimumLevel by remember { mutableStateOf(LogLevel.WARN) }
    var pendingExport by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val written = runCatching {
            requireNotNull(context.contentResolver.openOutputStream(uri)) { "Couldn't open log export" }.use {
                it.write(AppLog.format(pendingExport).toByteArray())
            }
        }.onSuccess {
            AppLog.info("Exported ${pendingExport.size} filtered log entries", "LogHistory")
        }.onFailure {
            AppLog.error("Couldn't export log", "LogHistory", it)
        }.isSuccess
        Toast.makeText(context, if (written) "Log exported" else "Couldn't export log", Toast.LENGTH_SHORT).show()
    }

    LogHistoryScreen(
        entries = AppLog.entries,
        minimumLevel = minimumLevel,
        onMinimumLevelChange = { minimumLevel = it },
        onClear = { AppLog.clear() },
        onCopy = { filteredEntries ->
            runCatching {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("Reader log", AppLog.format(filteredEntries)))
            }.onSuccess {
                AppLog.info("Copied ${filteredEntries.size} filtered log entries", "LogHistory")
                Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
            }.onFailure {
                AppLog.error("Couldn't copy log", "LogHistory", it)
                Toast.makeText(context, "Couldn't copy log", Toast.LENGTH_SHORT).show()
            }
        },
        onExport = {
            pendingExport = it
            exportLauncher.launch("reader-log.txt")
        },
        modifier = modifier,
    )
}
