package dev.reader.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.File

/** Copies SAF content:// Uris into local files so the rest of the app can work with plain [File]s. */
object ContentFileCopier {

    fun copyToCache(context: Context, uri: Uri): File? {
        val name = displayName(context, uri) ?: uri.lastPathSegment ?: "import"
        val target = File(File(context.cacheDir, "imports"), "${System.currentTimeMillis()}_$name")
        target.parentFile?.mkdirs()
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        return target
    }

    /** Copies every file directly inside a picked folder (non-recursive) into a local temp folder. */
    fun copyTreeToCache(context: Context, treeUri: Uri): File? {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val folder = File(context.cacheDir, "imports/${System.currentTimeMillis()}_${tree.name ?: "book"}")
        folder.mkdirs()
        tree.listFiles().filter { it.isFile }.forEach { doc ->
            val name = doc.name ?: return@forEach
            val target = File(folder, name)
            context.contentResolver.openInputStream(doc.uri)?.use { source ->
                target.outputStream().use { source.copyTo(it) }
            }
        }
        return folder
    }

    private fun displayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
        }
        return null
    }
}
