package dev.reader.app.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
private const val PRIMARY_VOLUME = "primary"
private const val RAW_PREFIX = "raw:"
private val FOLDER_TYPES = listOf(DocumentsContract.Document.MIME_TYPE_DIR, "resource/folder")

/**
 * Where a book came from on the device: a readable [label], the [item] itself (file or book folder),
 * and the [folder] holding it when it can be worked out (only on the phone's own storage / SD cards).
 */
data class BookLocation(val label: String, val item: Uri, val folder: Uri?)

object BookLocations {

    /** [isFolder] for books imported from a folder, whose own folder is the one worth opening. */
    fun locate(origin: String, isFolder: Boolean): BookLocation {
        val uri = Uri.parse(origin)
        val authority = uri.authority.orEmpty()
        val documentId = documentId(uri)
        if (authority == EXTERNAL_STORAGE && documentId != null && ':' in documentId) {
            val volume = documentId.substringBefore(':')
            val path = documentId.substringAfter(':')
            val root = if (volume == PRIMARY_VOLUME) "Internal storage" else "SD card ($volume)"
            val folderId = if (isFolder) documentId else "$volume:" + path.substringBeforeLast('/', "")
            return BookLocation(
                label = listOf(root, path).filter { it.isNotEmpty() }.joinToString("/"),
                item = uri,
                folder = DocumentsContract.buildDocumentUri(authority, folderId),
            )
        }
        val label = documentId?.removePrefix(RAW_PREFIX)?.takeIf { documentId.startsWith(RAW_PREFIX) }
            ?: listOfNotNull(providerName(authority), uri.lastPathSegment?.substringAfterLast('/'))
                .joinToString(" \u00b7 ")
        return BookLocation(label = label, item = uri, folder = null)
    }

    /** The document id at the end of a document or tree URI ("primary:Books/novel.epub"). */
    private fun documentId(uri: Uri): String? {
        val segments = uri.pathSegments
        val documentIndex = segments.lastIndexOf("document")
        return when {
            documentIndex >= 0 -> segments.getOrNull(documentIndex + 1)
            segments.firstOrNull() == "tree" -> segments.getOrNull(1)
            else -> null
        }
    }

    private fun providerName(authority: String): String? = when {
        authority == "com.android.providers.downloads.documents" -> "Downloads"
        authority == "com.android.providers.media.documents" -> "Media"
        authority.contains("google.android.apps.docs") -> "Google Drive"
        else -> null
    }

    /** Opens [folder] in a file manager; false if no app on the device can show it. */
    fun openFolder(context: Context, folder: Uri): Boolean = FOLDER_TYPES.any { type ->
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(folder, type)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        launch(context, intent)
    }

    /** Opens the original file (or folder book) with whatever app handles it. */
    fun openItem(context: Context, item: Uri): Boolean {
        val type = runCatching { context.contentResolver.getType(item) }.getOrNull() ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(item, type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return launch(context, Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (error: ActivityNotFoundException) {
        AppLog.warn("No app could open the book's location", "BookLocation", error)
        false
    } catch (error: SecurityException) {
        AppLog.warn("No permission to open the book's location", "BookLocation", error)
        false
    }
}
