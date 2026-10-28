package dev.reader.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Copies SAF content:// Uris into local files so the rest of the app can work with plain [File]s.
 * These are a book's permanent copy (its [Book.sourcePath][dev.reader.core.model.Book.sourcePath]
 * forever points here), so they go under [Context.getFilesDir], not [Context.getCacheDir] — the
 * system can and does clear the cache dir on its own to free up space.
 */
object ContentFileCopier {

    fun copyToCache(context: Context, uri: Uri): File? {
        val name = displayName(context, uri) ?: uri.lastPathSegment ?: "import"
        val target = File(File(context.filesDir, "imports"), "${System.currentTimeMillis()}_$name")
        target.parentFile?.mkdirs()
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        return target
    }

    /** Copies every file directly inside a picked folder (non-recursive) into a local temp folder. */
    fun copyTreeToCache(context: Context, treeUri: Uri): File? {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        return copyFolderToCache(context, tree)
    }

    /** Copies every file directly inside a [DocumentFile] folder (non-recursive) into a local temp folder. */
    fun copyFolderToCache(context: Context, folder: DocumentFile): File {
        val target = File(context.filesDir, "imports/${System.currentTimeMillis()}_${folder.name ?: "book"}")
        target.mkdirs()
        folder.listFiles().filter { it.isFile }.forEach { doc ->
            val name = doc.name
            if (name == null) {
                AppLog.warn("Skipped a folder entry without a file name", "FileCopy")
                return@forEach
            }
            val destination = File(target, name)
            val input = context.contentResolver.openInputStream(doc.uri)
            if (input == null) {
                AppLog.warn("Skipped an unreadable folder entry", "FileCopy")
                return@forEach
            }
            input.use { source ->
                destination.outputStream().use { source.copyTo(it) }
            }
        }
        return target
    }

    /** Copies a single [DocumentFile] (not a tree/folder) into a local temp file. */
    fun copyDocumentToCache(context: Context, document: DocumentFile): File? {
        val name = document.name ?: "import"
        val target = File(File(context.filesDir, "imports"), "${System.currentTimeMillis()}_$name")
        target.parentFile?.mkdirs()
        val input = context.contentResolver.openInputStream(document.uri) ?: return null
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        return target
    }

    private fun displayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
        }
        return null
    }
}
