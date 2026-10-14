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

    @Test
    fun `importing the same file twice does not create a duplicate book`() = runBlocking {
        val file = File(tempDir, "My Story.txt").apply { writeText("First paragraph.") }

        val first = importer.importFile(file)
        val second = importer.importFile(file)

        assertEquals(first.id, second.id)
        val allBooks = LibraryRepository(database).observeBooks().first()
        assertEquals(1, allBooks.size)
    }

    @Test
    fun `a renamed copy of the same file is recognised as the same book and keeps its chapters`() = runBlocking {
        val original = File(tempDir, "My Story.txt").apply { writeText("First paragraph.") }
        val renamed = File(tempDir, "Totally Different Name.txt").apply { writeText("First paragraph.") }
        val library = LibraryRepository(database)

        val first = importer.importFile(original)
        val firstChapters = library.chapters(first.id)
        val second = importer.importFile(renamed)

        assertEquals(first.id, second.id)
        assertEquals("My Story", second.title)
        assertEquals(1, library.observeBooks().first().size)
        assertEquals(firstChapters.map { it.id }, library.chapters(second.id).map { it.id })
    }

    @Test
    fun `applying a chapter pattern re-splits the book and null restores a single chapter`() = runBlocking {
        val library = LibraryRepository(database)
        val file = File(tempDir, "Saga.txt").apply { writeText("Chapter 1\n\nOne.\n\nChapter 2\n\nTwo.") }
        val book = importer.importFile(file)

        val split = importer.applyChapterPattern(book, "^Chapter")
        assertEquals(listOf("Chapter 1", "Chapter 2"), library.chapters(split.id).map { it.title })
        assertEquals("^Chapter", split.chapterPattern)

        val single = importer.applyChapterPattern(split, null)
        assertEquals(1, library.chapters(single.id).size)
    }

    @Test
    fun `excluded lines are remembered and reapplied when the book is re-imported`() = runBlocking {
        val library = LibraryRepository(database)
        val file = File(tempDir, "Saga.txt").apply {
            writeText("Chapter 1\n\nOne.\n\nChapter 2\n\nTwo.\n\nChapter 3\n\nThree.")
        }
        val book = importer.importFile(file)
        val secondBreakLine = file.readText().lines().indexOf("Chapter 2")

        val split = importer.applyChapterPattern(book, "^Chapter", setOf(secondBreakLine))

        assertEquals(setOf(secondBreakLine), split.chapterExcludedLines)
        val titles = library.chapters(split.id).map { it.title }
        assertEquals(listOf("Chapter 1", "Chapter 3"), titles)

        val reimported = importer.importFile(file)
        assertEquals(listOf("Chapter 1", "Chapter 3"), library.chapters(reimported.id).map { it.title })
    }

    @Test
    fun `new books land in the default category`() = runBlocking {
        val library = LibraryRepository(database)
        val manga = dev.reader.core.model.Category(dev.reader.core.model.CategoryId("manga"), "Manga")
        library.upsertCategory(manga)
        library.setDefaultCategory(manga.id)

        val book = importer.importFile(File(tempDir, "a.txt").apply { writeText("hi") })

        assertEquals(setOf(manga.id), book.categoryIds)
    }

    @Test
    fun `a renamed light novel folder is recognised as the same book`() = runBlocking {
        val folder = File(tempDir, "Novel").apply { mkdirs() }
        File(folder, "1.txt").writeText("one")
        val renamed = File(tempDir, "Novel Renamed").apply { mkdirs() }
        File(renamed, "chapter-1.txt").writeText("one")

        val first = importer.importLightNovelFolder(folder)
        val second = importer.importLightNovelFolder(renamed)

        assertEquals(first.id, second.id)
        assertEquals(1, LibraryRepository(database).observeBooks().first().size)
    }
}
