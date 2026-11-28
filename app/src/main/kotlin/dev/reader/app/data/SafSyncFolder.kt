package dev.reader.app.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import dev.reader.data.sync.MAX_SYNC_FILE_BYTES
import dev.reader.data.sync.SyncDocument
import dev.reader.data.sync.SyncFolder
import dev.reader.data.sync.isSyncFileName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

private const val MIME_TYPE = "application/json"
private const val ACCESS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

private class Entry(val uri: Uri, val name: String, val size: Long, val modified: Long)

/**
 * A folder picked with the system folder picker. Every failure is an [IOException].
 *
 * Uses DocumentsContract directly because DocumentFile reports a failed listing as an empty folder.
 */
class SafSyncFolder(context: Context, private val treeUri: Uri) : SyncFolder {

    private val resolver = context.applicationContext.contentResolver

    /** What the last [list] found, so that [read] opens the document that was listed under a name. */
    private var listed: Map<String, Entry> = emptyMap()

    override suspend fun list(): List<SyncDocument> = access("list the sync folder") {
        listed = named(children())
        listed.map { (name, entry) -> SyncDocument(name, entry.size) }
    }

    /** Returns at most one byte more than a sync file may hold, whatever size the provider reported. */
    override suspend fun read(name: String): ByteArray = access("read $name") {
        val entry = listed[name] ?: named(children())[name] ?: throw IOException("$name is gone")
        val input = resolver.openInputStream(entry.uri) ?: throw IOException("Could not open $name")
        input.use { readLimited(it, MAX_SYNC_FILE_BYTES + 1) }
    }

    override suspend fun write(name: String, bytes: ByteArray) = access("write $name") {
        val target = named(children())[name]?.uri ?: create(name)
        val output = resolver.openOutputStream(target, "wt") ?: throw IOException("Could not open $name")
        output.use { it.write(bytes) }
    }

    override suspend fun delete(name: String) = access("delete $name") {
        val matches = children().filter { it.name == name }
        // With two of a name there is no telling that this is the one that was read.
        if (matches.size > 1) throw IOException("Several files are named $name")
        val entry = matches.singleOrNull() ?: return@access
        if (!DocumentsContract.deleteDocument(resolver, entry.uri)) throw IOException("Could not delete $name")
    }

    private suspend fun <T> access(action: String, block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: CancellationException) { // an IllegalStateException, so it must be caught first
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) { // providers throw SecurityException, IllegalArgumentException and others
            throw IOException("Could not $action: ${e.message}", e)
        }
    }

    private fun create(name: String): Uri {
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val before = children().map { it.uri }.toSet()
        val created = DocumentsContract.createDocument(resolver, parent, MIME_TYPE, name)
            ?: throw IOException("Could not create $name")
        val after = children()
        val actual = after.find { it.uri == created }?.name
        if (actual == name) return created
        // The provider renamed it, so remove the new empty copy, never a document that was already there.
        if (created !in before && actual != null) DocumentsContract.deleteDocument(resolver, created)
        // Another writer may have made the file in the meantime.
        return named(after.filter { it.uri != created })[name]?.uri ?: throw IOException("Could not create $name")
    }

    /**
     * Providers allow several files with one name. The newest keeps the name; sync files among the others are
     * listed as conflict copies, so that they are still read and are never deleted.
     */
    private fun named(entries: List<Entry>): Map<String, Entry> = buildMap {
        for ((name, same) in entries.groupBy { it.name }) {
            val ordered = same.sortedWith(compareByDescending<Entry> { it.modified }.thenBy { it.uri.toString() })
            put(name, ordered.first())
            if (!isSyncFileName(name)) continue
            ordered.drop(1).forEachIndexed { index, entry ->
                put("${name.removeSuffix(".json")}.sync-conflict-duplicate-${index + 1}.json", entry)
            }
        }
    }

    @Suppress("ThrowsCount") // each is a different way a provider reports a folder it cannot list
    private fun children(): List<Entry> {
        val folderId = DocumentsContract.getTreeDocumentId(treeUri)
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderId)
        val columns = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
        // Some providers answer for a missing folder with an empty list.
        if (!isDirectory(resolver, treeUri)) throw IOException("Sync folder is missing")
        val cursor = resolver.query(uri, columns, null, null) ?: throw IOException("Sync folder is unavailable")
        return cursor.use {
            it.extras?.getString(DocumentsContract.EXTRA_ERROR)?.let { error -> throw IOException(error) }
            generateSequence { if (it.moveToNext()) it else null }.mapNotNull(::entry).toList()
        }
    }

    /** Null for a folder, or a row with no ID or name. */
    private fun entry(row: Cursor): Entry? {
        fun column(name: String) = row.getColumnIndexOrThrow(name)
        val id = row.getString(column(Document.COLUMN_DOCUMENT_ID))
        val name = row.getString(column(Document.COLUMN_DISPLAY_NAME))
        val directory = row.getString(column(Document.COLUMN_MIME_TYPE)) == Document.MIME_TYPE_DIR
        if (id == null || name == null || directory) return null
        val size = column(Document.COLUMN_SIZE)
        return Entry(
            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
            name = name,
            size = if (row.isNull(size)) 0 else row.getLong(size).coerceAtLeast(0),
            modified = row.getLong(column(Document.COLUMN_LAST_MODIFIED)),
        )
    }

    companion object {
        /** Keeps access to a picked folder after the app restarts. Call with the folder picker's result. */
        fun persistPermission(context: Context, treeUri: Uri) =
            context.contentResolver.takePersistableUriPermission(treeUri, ACCESS)

        /** The folder at [uri], or null if permission to read and write it is gone or it no longer exists. */
        fun open(context: Context, uri: String): SafSyncFolder? = runCatching {
            val treeUri = Uri.parse(uri)
            val resolver = context.contentResolver
            val permitted = resolver.persistedUriPermissions
                .any { it.uri == treeUri && it.isReadPermission && it.isWritePermission }
            if (permitted && isDirectory(resolver, treeUri)) SafSyncFolder(context, treeUri) else null
        }.getOrNull()
    }
}

private fun isDirectory(resolver: ContentResolver, treeUri: Uri): Boolean {
    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
    return resolver.query(uri, arrayOf(Document.COLUMN_MIME_TYPE), null, null)
        ?.use { it.moveToFirst() && it.getString(0) == Document.MIME_TYPE_DIR } == true
}

private fun readLimited(input: InputStream, limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (output.size() < limit) {
        val count = input.read(buffer, 0, minOf(buffer.size, limit - output.size()))
        if (count < 0) break
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
