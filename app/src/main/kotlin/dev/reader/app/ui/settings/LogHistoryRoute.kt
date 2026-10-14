package dev.reader.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.reader.app.data.AppLog

@Composable
fun LogHistoryRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val written = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(AppLog.format(AppLog.entries).toByteArray()) }
        }.isSuccess
        Toast.makeText(context, if (written) "Log exported" else "Couldn't export log", Toast.LENGTH_SHORT).show()
    }

    LogHistoryScreen(
        entries = AppLog.entries,
        onClear = { AppLog.clear() },
        onCopy = {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("Reader log", AppLog.format(AppLog.entries)))
            Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
        },
        onExport = { exportLauncher.launch("reader-log.txt") },
        modifier = modifier,
    )
}
