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
}
