package dev.reader.app.data

import androidx.documentfile.provider.DocumentFile
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class RootFolderScannerTest {

    @Test
    fun `file collections find books in nested folders and skip everything else`() {
        val root = createTempDirectory("library").toFile()
        try {
            fun touch(path: String) = File(root, path).apply { parentFile?.mkdirs() }.writeText("x")
            touch("top.epub")
            touch("Series/Book 1.txt")
            touch("Series/Volume 2/Book 2.pdf")
            touch("Series/cover.jpg")
            touch(".thumbnails/hidden.epub")
            touch("notes.docx.tmp")

            val found = collectBookFiles(DocumentFile.fromFile(root)).mapNotNull { it.name }.sorted()

            assertEquals(listOf("Book 1.txt", "Book 2.pdf", "top.epub"), found)
        } finally {
            root.deleteRecursively()
        }
    }
}
