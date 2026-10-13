package dev.reader.data.importer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.reader.data.repo.LibraryRepository
import dev.reader.data.room.ReaderDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BookImporterTest {

    private lateinit var database: ReaderDatabase
    private lateinit var importer: BookImporter
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReaderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        var counter = 0
        importer = BookImporter(LibraryRepository(database), newId = { "id-${counter++}" })
        tempDir = File.createTempFile("importer", "").apply {
            delete()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        database.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun `importing a txt file creates a book with one chapter`() = runBlocking {
        val file = File(tempDir, "My Story.txt").apply { writeText("First paragraph.\n\nSecond paragraph.") }

        val book = importer.importFile(file)

        assertEquals("My Story", book.title)
        val chapters = LibraryRepository(database).observeChapters(book.id).first()
        assertEquals(1, chapters.size)
        assertEquals(4, chapters.single().wordCount)
    }

    @Test
    fun `importing a light novel folder sorts files naturally into chapters`() = runBlocking {
        val folder = File(tempDir, "My Light Novel").apply { mkdirs() }
        File(folder, "Chapter 10.txt").writeText("ten")
        File(folder, "Chapter 2.txt").writeText("two")
        File(folder, "Chapter 1.txt").writeText("one")

        val book = importer.importLightNovelFolder(folder)

        assertTrue(book.isLightNovel)
        val chapters = LibraryRepository(database).observeChapters(book.id).first()
        assertEquals(listOf("Chapter 1", "Chapter 2", "Chapter 10"), chapters.map { it.title })
    }

    @Test
    fun `importing a light novel folder strips a leading numeric id from the title`() = runBlocking {
        val folder = File(tempDir, "1791901.....-Mother of Learning").apply { mkdirs() }
        File(folder, "Chapter 1.txt").writeText("one")

        val book = importer.importLightNovelFolder(folder)

        assertEquals("Mother of Learning", book.title)
    }

    @Test
    fun `importing an epub materializes its cover to a real file`() = runBlocking {
        val file = File(tempDir, "book.epub")
        java.util.zip.ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(name: String, content: ByteArray) {
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
            entry(
                "META-INF/container.xml",
                """
                <?xml version="1.0"?>
                <container><rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles></container>
                """.trimIndent().toByteArray(),
            )
            entry(
                "OEBPS/content.opf",
                """
                <?xml version="1.0"?>
                <package>
                    <metadata><title>Covered</title></metadata>
                    <manifest>
                        <item id="ch1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="cover" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                    </manifest>
                    <spine><itemref idref="ch1"/></spine>
                </package>
                """.trimIndent().toByteArray(),
            )
            entry("OEBPS/chapter1.xhtml", "<html><body><p>Hi.</p></body></html>".toByteArray())
            entry("OEBPS/cover.jpg", byteArrayOf(1, 2, 3, 4))
        }

        val book = importer.importFile(file)

        val coverPath = requireNotNull(book.coverPath)
        assertTrue(File(coverPath).exists())
        assertEquals(listOf<Byte>(1, 2, 3, 4), File(coverPath).readBytes().toList())
    }

    @Test
    fun `cleanTitle strips a leading numeric id but keeps ordinary titles`() {
        assertEquals("Mother of Learning", cleanTitle("1791901.....-Mother of Learning"))
        assertEquals("Mother of Learning", cleanTitle("1791901_-_Mother_of_Learning"))
        assertEquals("My Story", cleanTitle("My Story"))
        assertEquals("01 - Chapter One", cleanTitle("01 - Chapter One"))
    }
}
