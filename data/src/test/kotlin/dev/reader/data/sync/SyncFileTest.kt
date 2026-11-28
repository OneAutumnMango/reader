package dev.reader.data.sync

import dev.reader.core.sync.CategoriesValue
import dev.reader.core.sync.MAX_VERSION_COUNT
import dev.reader.core.sync.PositionValue
import dev.reader.core.sync.Versioned
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncFileTest {

    private fun item(position: Float = 0.5f, chapter: Int = 2, updatedAt: Long = 10, count: Long = 1) =
        Versioned(PositionValue(chapter, position, false, updatedAt), mapOf("a3f9c2ffff" to count))

    private val categories = Versioned(CategoriesValue(listOf("Fantasy")), mapOf("a3f9c2ffff" to 1L))

    private val book = SyncBook(
        contentHash = "hash",
        title = "Title",
        signature = "sig",
        position = item(),
        chapters = listOf(SyncChapter(0, item()), SyncChapter(1, Versioned(null))),
        categories = categories,
    )

    private val file = SyncFile(
        deviceId = "a3f9c2ffff",
        deviceName = "Tab",
        devices = mapOf("a3f9c2ffff" to "Tab"),
        publishedAt = 100,
        books = listOf(book, book.copy(contentHash = "other", categories = null)),
    )

    private fun text(value: SyncFile) = SyncFileCodec.encode(value).decodeToString()

    private fun assertRejected(bytes: ByteArray) {
        assertThrows(SyncFileException::class.java) { SyncFileCodec.decode(bytes) }
    }

    private fun assertRejected(text: String) = assertRejected(text.encodeToByteArray())

    private fun assertRejected(value: SyncFile) = assertRejected(SyncFileCodec.encode(value))

    private fun assertRejected(value: SyncBook) = assertRejected(file.copy(books = listOf(value)))

    /** Replaces the first match, failing if there is none so a test cannot pass on an unchanged file. */
    private fun edited(old: String, new: String): String {
        val original = text(file)
        assertTrue(old, original.contains(old))
        return original.replaceFirst(old, new)
    }

    @Test
    fun roundTrips() {
        assertEquals(file, SyncFileCodec.decode(SyncFileCodec.encode(file)))
    }

    @Test
    fun outputIsIndentedWithAFormatAndNoLocalPaths() {
        val output = text(file)
        assertTrue(output.contains("\n"))
        assertTrue(output.startsWith("{\n \"format\": 1,"))
        assertFalse(output.contains("sourcePath"))
    }

    @Test
    fun rejectsUnreadableFiles() {
        assertRejected(text(file).dropLast(10))
        assertRejected("")
        assertRejected("null")
        assertRejected(byteArrayOf(0xC3.toByte(), 0x28))
        assertRejected(edited("\"Title\"", "\"Title\", \"sourcePath\": \"/x\""))
        assertRejected("[".repeat(1_000_000))
        assertRejected("{\"a\":".repeat(500_000))
        assertRejected(edited("\"value\": {", "\"value\": {\"x\": " + "[".repeat(1_000_000)))
    }

    @Test
    fun rejectsAnOversizedFileThatWouldOtherwiseParse() {
        val valid = SyncFileCodec.encode(file)
        val padded = valid + ByteArray(MAX_SYNC_FILE_BYTES - valid.size + 1) { ' '.code.toByte() }
        assertRejected(padded)
        assertEquals(file, SyncFileCodec.decode(padded.copyOf(MAX_SYNC_FILE_BYTES)))
    }

    @Test
    fun rejectsUnknownOrMissingFormatAndBlankDeviceId() {
        assertRejected(file.copy(format = 2))
        assertRejected(edited("\"format\": 1,", ""))
        assertRejected(file.copy(deviceId = " "))
    }

    @Test
    fun rejectsBadNumbers() {
        assertRejected(edited("\"position\": 0.5", "\"position\": NaN"))
        assertRejected(edited("\"position\": 0.5", "\"position\": \"NaN\""))
        assertRejected(edited("\"position\": 0.5", "\"position\": 1e999"))
        assertRejected(book.copy(position = item(position = 1.5f)))
        assertRejected(book.copy(position = item(position = -0.1f)))
        assertRejected(book.copy(position = item(chapter = -1)))
        assertRejected(book.copy(chapters = listOf(SyncChapter(-1, item()))))
        assertRejected(book.copy(chapters = listOf(SyncChapter(0, item(position = 1.5f)))))
        assertRejected(book.copy(position = item(count = -1)))
        assertRejected(book.copy(chapters = listOf(SyncChapter(0, item(count = -1)))))
        assertRejected(book.copy(categories = categories.copy(version = mapOf("a" to -1L))))
        assertRejected(book.copy(position = item(updatedAt = -1)))
        assertRejected(file.copy(publishedAt = -1))
    }

    @Test
    fun acceptsChangeCountsOnlyUpToTheMaximum() {
        val atMaximum = file.copy(books = listOf(book.copy(position = item(count = MAX_VERSION_COUNT))))
        assertEquals(atMaximum, SyncFileCodec.decode(SyncFileCodec.encode(atMaximum)))
        val over = item(count = MAX_VERSION_COUNT + 1)
        assertRejected(book.copy(position = over))
        assertRejected(book.copy(chapters = listOf(SyncChapter(0, over))))
        assertRejected(book.copy(categories = categories.copy(version = over.version)))
    }

    /** Pins the type tags and property names that other installs depend on. */
    @Test
    fun decodesALiteralFile() {
        val literal = """
            {"format":1,"deviceId":"a3f9c2ffff","deviceName":"Tab","devices":{"a3f9c2ffff":"Tab"},"publishedAt":100,
             "books":[{"contentHash":"hash","title":"Title","signature":"sig",
              "position":{"value":{"type":"position","chapter":2,"position":0.5,"finished":false,"updatedAt":10},
               "version":{"a3f9c2ffff":1},"rewound":true},
              "chapters":[{"index":0,"item":{"value":null}}],
              "categories":{"value":{"type":"categories","names":["Fantasy"]},"version":{"a3f9c2ffff":1}}}]}
        """
        val expected = file.copy(
            books = listOf(
                book.copy(position = item().copy(rewound = true), chapters = listOf(SyncChapter(0, Versioned(null)))),
            ),
        )
        assertEquals(expected, SyncFileCodec.decode(literal.encodeToByteArray()))
    }

    @Test
    fun errorMessagesAreShortAndWithoutControlCharacters() {
        val hostile = edited("\"Title\"", "\"Title\", \"\\u001b\u001b\u0007" + "k".repeat(1000) + "\": 1")
        val error = assertThrows(SyncFileException::class.java) { SyncFileCodec.decode(hostile.encodeToByteArray()) }
        val message = error.message.orEmpty()
        assertTrue(message, message.length <= 200)
        assertTrue(message, message.none { it.isISOControl() })
    }

    @Test
    fun rejectsDuplicateEntries() {
        assertRejected(file.copy(books = listOf(book, book)))
        assertRejected(book.copy(chapters = listOf(SyncChapter(0, item()), SyncChapter(0, item()))))
    }

    @Test
    fun rejectsValuesOfTheWrongKind() {
        assertRejected(book.copy(position = categories))
        assertRejected(book.copy(chapters = listOf(SyncChapter(0, categories))))
        assertRejected(book.copy(categories = item()))
    }

    @Test
    fun sanitizesDeviceNamesOnDecode() {
        val hostile = " \u0000bad\n" + "x".repeat(100)
        val decoded = SyncFileCodec.decode(
            SyncFileCodec.encode(file.copy(deviceName = hostile, devices = mapOf("a" to hostile, "b" to "\t "))),
        )
        val clean = "bad" + "x".repeat(37)
        assertEquals(clean, decoded.deviceName)
        assertEquals(mapOf("a" to clean, "b" to "Device"), decoded.devices)
    }

    @Test
    fun sanitizingDoesNotSplitASurrogatePair() {
        assertEquals("x".repeat(39), sanitizeDeviceName("x".repeat(39) + "😀"))
    }

    @Test
    fun buildsAndRecognisesFileNames() {
        assertEquals("reader-sync-pixel-8-x-a3f9c2.json", syncFileName("Pixel 8/../x", "a3f9c2ffff"))
        assertEquals("reader-sync-device-a3f9c2.json", syncFileName("", "a3f9c2ffff"))
        assertTrue(isSyncFileName("reader-sync-tab-a3f9c2.sync-conflict-20261128-101010-ABC.json"))
        assertFalse(isSyncFileName(".syncthing.reader-sync-tab-a3f9c2.json.tmp"))
        assertFalse(isSyncFileName("reader-sync-tab-a3f9c2.json.tmp"))
    }

    private fun decodedNames(vararg names: String): CategoriesValue {
        val categories = Versioned(CategoriesValue(names.toList()), mapOf("d" to 3L))
        val bytes = SyncFileCodec.encode(file.copy(books = listOf(book.copy(categories = categories))))
        val decoded = SyncFileCodec.decode(bytes).books.single().categories!!
        assertEquals(categories.version, decoded.version)
        return decoded.value as CategoriesValue
    }

    @Test
    fun `decode drops invisible and repeated category names`() {
        assertEquals(CategoriesValue(listOf("A")), decodedNames("", "A", " a "))
        assertEquals(CategoriesValue(emptyList()), decodedNames("", "  ", "\u0000", "\u200B"))
    }

    @Test
    fun `decode strips control characters and caps category names`() {
        val long = "x".repeat(99)

        assertEquals(CategoriesValue(listOf("AB", "${long}y")), decodedNames(" A\u0000\nB ", "${long}yz"))
        // Neither a trailing space nor half a surrogate pair survives the cut.
        assertEquals(CategoriesValue(listOf(long)), decodedNames("$long z"))
        assertEquals(CategoriesValue(listOf(long)), decodedNames("$long\uD83D\uDE00"))
    }
}
