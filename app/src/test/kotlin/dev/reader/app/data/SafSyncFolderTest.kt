package dev.reader.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import androidx.test.core.app.ApplicationProvider
import dev.reader.data.sync.MAX_SYNC_FILE_BYTES
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private const val AUTHORITY = "dev.reader.test.documents"
private const val ROOT = "root"
private const val NAME = "reader-sync-phone-abc123.json"

class FakeDocument(val name: String, val file: File, val modified: Long)

/** One flat folder. It reports no sizes, and renames a new file if told that the name is taken. */
class FakeDocumentsProvider : DocumentsProvider() {
    val documents = linkedMapOf<String, FakeDocument>()
    var failure: Exception? = null
    var missing = false
    var rootType = Document.MIME_TYPE_DIR
    var noCursor = false

    /** Appears just before the next create, as if another writer made it. */
    var appearsOnCreate: String? = null
    private val directory = createTempDirectory("saf").toFile()
    private var nextId = 0

    fun add(name: String, bytes: ByteArray = ByteArray(0), modified: Long = 0): String {
        val id = "doc${nextId++}"
        documents[id] = FakeDocument(name, File(directory, id).apply { writeBytes(bytes) }, modified)
        return id
    }

    fun contents() = documents.values.map { it.name to it.file.readText() }

    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(arrayOf("root_id"))

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor? {
        failure?.let { throw it }
        if (noCursor) return null
        val cursor = MatrixCursor(projection!!)
        if (documentId == ROOT && !missing) cursor.addRow(arrayOf<Any>(rootType))
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(projection!!)
        documents.forEach { (id, document) ->
            cursor.addRow(arrayOf<Any?>(id, document.name, "application/json", null, document.modified))
        }
        return cursor
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String) = true

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        appearsOnCreate?.let { add(it, "theirs".toByteArray()) }
        appearsOnCreate = null
        val taken = documents.values.any { it.name == displayName }
        return add(if (taken) displayName.replace(".json", " (1).json") else displayName)
    }

    override fun deleteDocument(documentId: String) {
        documents.remove(documentId)
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(documents.getValue(documentId).file, ParcelFileDescriptor.parseMode(mode))
}

@RunWith(RobolectricTestRunner::class)
class SafSyncFolderTest {

    private lateinit var provider: FakeDocumentsProvider
    private lateinit var folder: SafSyncFolder
    private val treeUri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT)

    @Before fun setUp() {
        val info = ProviderInfo().apply {
            authority = AUTHORITY
            exported = true
            grantUriPermissions = true
            readPermission = android.Manifest.permission.MANAGE_DOCUMENTS
            writePermission = android.Manifest.permission.MANAGE_DOCUMENTS
        }
        provider = Robolectric.buildContentProvider(FakeDocumentsProvider::class.java).create(info).get()
        folder = SafSyncFolder(ApplicationProvider.getApplicationContext(), treeUri)
    }

    @Test fun `write creates then overwrites without leaving old bytes`() = runBlocking<Unit> {
        folder.write(NAME, "a long first version".toByteArray())
        folder.write(NAME, "short".toByteArray())

        assertEquals(listOf(NAME to "short"), provider.contents())
        assertContentEquals("short".toByteArray(), folder.read(NAME))
    }

    @Test fun `read stops one byte past the limit when no size is reported`() = runBlocking<Unit> {
        provider.add(NAME, ByteArray(MAX_SYNC_FILE_BYTES + 100))

        assertEquals(0, folder.list().single().size)
        assertEquals(MAX_SYNC_FILE_BYTES + 1, folder.read(NAME).size)
    }

    @Test fun `duplicate names are all listed and neither is deleted`() = runBlocking<Unit> {
        provider.add(NAME, "old".toByteArray(), modified = 1)
        provider.add(NAME, "new".toByteArray(), modified = 2)

        val names = folder.list().map { it.name }

        assertEquals(listOf(NAME, "reader-sync-phone-abc123.sync-conflict-duplicate-1.json"), names)
        assertEquals(listOf("new", "old"), names.map { folder.read(it).decodeToString() })
        folder.write(NAME, "newer".toByteArray())
        assertEquals(listOf(NAME to "old", NAME to "newer"), provider.contents())
        assertFailsWith<IOException> { folder.delete(NAME) }
        assertEquals(2, provider.documents.size)
    }

    @Test fun `a renamed new file is removed and the existing one written`() = runBlocking<Unit> {
        provider.appearsOnCreate = NAME

        folder.write(NAME, "mine".toByteArray())

        assertEquals(listOf(NAME to "mine"), provider.contents())
    }

    @Test fun `provider failures and a missing folder become IOException`() = runBlocking<Unit> {
        provider.failure = SecurityException("revoked")
        assertFailsWith<IOException> { folder.list() }
        assertFailsWith<IOException> { folder.write(NAME, ByteArray(1)) }

        provider.failure = IllegalStateException("broken")
        assertFailsWith<IOException> { folder.list() }

        provider.failure = null
        provider.missing = true
        assertFailsWith<IOException> { folder.list() }
    }

    private fun open() = SafSyncFolder.open(ApplicationProvider.getApplicationContext(), treeUri.toString())

    private fun grant() = ApplicationProvider.getApplicationContext<Context>().contentResolver
        .takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )

    @Test fun `open needs a persisted permission`() {
        assertNull(open())
        grant()
        assertNotNull(open())
    }

    @Test fun `open reports only a definitely lost folder as missing`() {
        grant()
        for (lost in listOf(SecurityException("revoked"), IllegalArgumentException("deleted"))) {
            provider.failure = lost
            assertNull(open(), "$lost")
        }
        provider.failure = null
        provider.missing = true
        assertNull(open())
        provider.missing = false
        provider.rootType = "text/plain"
        assertNull(open())
    }

    @Test fun `open throws for an error that may pass`() {
        grant()
        for (passing in listOf(IllegalStateException("busy"), UnsupportedOperationException("starting"))) {
            provider.failure = passing
            assertFailsWith<IOException>("$passing") { open() }
        }
        provider.failure = null
        provider.noCursor = true
        assertFailsWith<IOException> { open() }
    }
}
