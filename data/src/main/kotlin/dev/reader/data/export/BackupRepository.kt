package dev.reader.data.export

import dev.reader.core.model.Book
import dev.reader.core.model.Chapter
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val MANIFEST = "backup.json"
private const val BOOKS_DIR = "books"
private const val COVERS_DIR = "covers"

/**
 * A full backup is a zip of the complete [ExportBundle] and optionally the book files themselves.
 * Cover images are embedded in the bundle. Restoring replaces all app data and points books at the
 * restored files.
 */
class BackupRepository(
    private val exporter: ExportRepository,
    /** Where restored book files and covers are unpacked; should be persistent app storage. */
    private val restoreRoot: File,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun write(out: OutputStream, includeBooks: Boolean) {
        val bundle = exporter.bundle()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(exporter.encode(bundle).toByteArray())
            zip.closeEntry()
            bundle.books.forEach { book -> zip.addBook(book, includeBooks) }
        }
    }

    /** The backup's books, for showing what a restore would add, update or (unless merging) remove. */
    suspend fun preview(input: InputStream): List<Book> {
        val manifest = ZipInputStream(input).use { zip -> peekManifest(zip) }
        return exporter.decode(requireNotNull(manifest) { "That file isn't a reader backup" }).books
    }

    /** Replaces everything by default; [merge] upserts the backup into the current data instead. */
    suspend fun restore(input: InputStream, merge: Boolean = false) {
        val target = File(restoreRoot, "restored/${now()}")
        val manifest = ZipInputStream(input).use { zip -> unpack(zip, target) }
        val bundle = exporter.decode(requireNotNull(manifest) { "That file isn't a reader backup" })
        val relocated = bundle.relocatedTo(target)
        if (merge) exporter.mergeAll(relocated) else exporter.replaceAll(relocated)
    }

    /** The manifest is always the first entry [write] adds, so this stops as soon as it's found. */
    private fun peekManifest(zip: ZipInputStream): String? =
        generateSequence { zip.nextEntry }.firstNotNullOfOrNull { entry ->
            if (!entry.isDirectory && entry.name == MANIFEST) zip.readBytes().decodeToString() else null
        }

    /** Extracts every file under [target] and returns the manifest text, if there was one. */
    private fun unpack(zip: ZipInputStream, target: File): String? {
        var manifest: String? = null
        generateSequence { zip.nextEntry }.filterNot { it.isDirectory }.forEach { entry ->
            if (entry.name == MANIFEST) {
                manifest = zip.readBytes().decodeToString()
                return@forEach
            }
            val file = safeTarget(target, entry.name)
            file.parentFile?.mkdirs()
            file.outputStream().use { zip.copyTo(it) }
        }
        return manifest
    }

    private fun ZipOutputStream.addBook(book: Book, includeFiles: Boolean) {
        if (includeFiles) bookFiles(book).forEach { addFile("$BOOKS_DIR/${book.id.value}", it) }
    }

    private fun bookFiles(book: Book): List<File> {
        val source = File(book.sourcePath)
        return when {
            source.isDirectory -> source.listFiles { f -> f.isFile }.orEmpty().toList()
            source.isFile -> listOf(source)
            else -> emptyList()
        }
    }

    /** Points books, light-novel chapters and covers at whatever was unpacked under [root]. */
    private fun ExportBundle.relocatedTo(root: File): ExportBundle {
        val relocatedBooks = books.map { book ->
            val bookDir = File(root, "$BOOKS_DIR/${book.id.value}")
            val cover = File(root, "$COVERS_DIR/${book.id.value}").listFiles()?.firstOrNull()
            val source = when {
                !bookDir.isDirectory -> book.sourcePath
                book.isLightNovel -> bookDir.path
                else -> File(bookDir, File(book.sourcePath).name).path
            }
            book.copy(sourcePath = source, coverPath = cover?.path ?: book.coverPath)
        }
        val restoredLightNovels = relocatedBooks.filter { it.isLightNovel && it.sourcePath.startsWith(root.path) }
            .associateBy { it.id }
        return copy(
            books = relocatedBooks,
            chapters = chapters.map { chapter -> chapter.relocated(restoredLightNovels[chapter.bookId]) },
        )
    }

    private fun Chapter.relocated(book: Book?): Chapter =
        if (book == null || sourcePath.isEmpty()) {
            this
        } else {
            copy(sourcePath = File(book.sourcePath, File(sourcePath).name).path)
        }

    /** Rejects entries like `../../x` so a crafted zip can't write outside [root] (zip slip). */
    private fun safeTarget(root: File, name: String): File {
        val file = File(root, name)
        require(file.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "Unsafe path in backup: $name" }
        return file
    }

    private fun ZipOutputStream.addFile(dir: String, file: File) {
        putNextEntry(ZipEntry("$dir/${file.name}"))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }
}
