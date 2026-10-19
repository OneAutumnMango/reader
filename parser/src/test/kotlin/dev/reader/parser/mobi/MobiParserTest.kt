package dev.reader.parser.mobi

import dev.reader.core.model.BookFormat
import dev.reader.core.richtext.Block
import dev.reader.parser.ParsedChapter
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import kotlin.io.path.createTempFile
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Builds a minimal, real MOBI file byte-for-byte so [MobiParser] can be tested without fixtures. */
private class MobiBuilder {
    private var compression = 1
    private var encryptionType = 0
    private var mobiVersion = 6
    private var textEncoding = 65001L
    private var exth: Map<Int, String> = emptyMap()
    private var fullName = "Fallback Title"
    private var textRecords: List<ByteArray> = emptyList()
    private var extraRecords: List<ByteArray> = emptyList()
    private var declaredTextLength: Int? = null

    fun compression(value: Int) = apply { compression = value }
    fun encryptionType(value: Int) = apply { encryptionType = value }
    fun mobiVersion(value: Int) = apply { mobiVersion = value }
    fun fullName(value: String) = apply { fullName = value }
    fun declaredTextLength(value: Int) = apply { declaredTextLength = value }
    fun exth(values: Map<Int, String>) = apply { exth = values }
    fun textRecords(vararg records: ByteArray) = apply { textRecords = records.toList() }
    fun extraRecords(vararg records: ByteArray) = apply { extraRecords = records.toList() }

    fun build(): ByteArray {
        val exthBytes = buildExth()
        val fullNameBytes = fullName.toByteArray(Charsets.UTF_8)
        val textLength = declaredTextLength ?: textRecords.sumOf { it.size }

        val record0 = ByteArrayOutputStream().apply {
            writeU16(compression)
            writeU16(0)
            writeU32(textLength.toLong())
            writeU16(textRecords.size)
            writeU16(4096)
            writeU16(encryptionType)
            writeU16(0)
            writeBytes("MOBI".toByteArray())
            val headerLength = 232 // arbitrary header size, must be >= all fields used below
            writeU32(headerLength.toLong())
            writeU32(2) // mobi type
            writeU32(textEncoding)
            writeU32(0) // unique id
            writeU32(mobiVersion.toLong())
            repeat(40) { write(0) } // reserved, offsets 28..67 (relative to MOBI header start)
            writeU32(0) // first non-book index (68..71 wait adjust below)
            // Pad up to fullNameOffset (84 relative to MOBI header start == offset 100 absolute)
            while (size() < 16 + 84) write(0)
            val fullNameOffset = 16 + headerLength + exthBytes.size
            writeU32(fullNameOffset.toLong())
            writeU32(fullNameBytes.size.toLong())
            while (size() < 16 + 112) write(0)
            writeU32(if (exthBytes.isNotEmpty()) 0x40L else 0L)
            while (size() < 16 + headerLength) write(0)
            write(exthBytes)
            write(fullNameBytes)
        }.toByteArray()

        val records = listOf(record0) + textRecords + extraRecords
        val headerSize = 78
        val recordInfoSize = 8
        var offset = headerSize + records.size * recordInfoSize
        val recordOffsets = records.map {
            val o = offset
            offset += it.size
            o
        }

        return ByteArrayOutputStream().apply {
            repeat(32) { write(0) } // name
            writeU16(0) // attributes
            writeU16(0) // version
            repeat(4) { writeU32(0) } // 4 dates/numbers (creation, modification, lastBackup, modNum) = 16 bytes
            writeU32(0) // appInfoID
            writeU32(0) // sortInfoID
            writeBytes("BOOK".toByteArray())
            writeBytes("MOBI".toByteArray())
            writeU32(0) // uniqueIDseed
            writeU32(0) // nextRecordListID
            writeU16(records.size)
            records.indices.forEach { i ->
                writeU32(recordOffsets[i].toLong())
                writeU32(0)
            }
            records.forEach { write(it) }
        }.toByteArray()
    }

    private fun buildExth(): ByteArray {
        if (exth.isEmpty()) return ByteArray(0)
        val body = ByteArrayOutputStream().apply {
            exth.forEach { (type, value) ->
                val bytes = value.toByteArray(Charsets.UTF_8)
                writeU32(type.toLong())
                writeU32((bytes.size + 8).toLong())
                write(bytes)
            }
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            writeBytes("EXTH".toByteArray())
            writeU32((body.size + 12).toLong())
            writeU32(exth.size.toLong())
            write(body)
        }.toByteArray()
    }
}

private class PalmDocBuilder(
    private val title: String,
    private val text: ByteArray,
    private val compression: Int = 1,
    private val encryptionType: Int = 0,
    private val declaredTextLength: Int = text.size,
    private val databaseType: String = "TEXt",
    private val databaseCreator: String = "REAd",
) {
    fun build(): ByteArray {
        val record0 = ByteArrayOutputStream().apply {
            writeU16(compression)
            writeU16(0)
            writeU32(declaredTextLength.toLong())
            writeU16(1)
            writeU16(4096)
            writeU16(encryptionType)
            writeU16(0)
        }.toByteArray()
        val records = listOf(record0, text)
        val recordOffsets = listOf(94, 94 + record0.size)
        val titleBytes = title.toByteArray(Charset.forName("windows-1252")).take(32)

        return ByteArrayOutputStream().apply {
            write(titleBytes.toByteArray())
            repeat(32 - titleBytes.size) { write(0) }
            writeU16(0)
            writeU16(0)
            repeat(4) { writeU32(0) }
            writeU32(0)
            writeU32(0)
            writeBytes(databaseType.toByteArray(Charsets.US_ASCII))
            writeBytes(databaseCreator.toByteArray(Charsets.US_ASCII))
            writeU32(0)
            writeU32(0)
            writeU16(records.size)
            records.indices.forEach { index ->
                writeU32(recordOffsets[index].toLong())
                writeU32(0)
            }
            records.forEach { write(it) }
        }.toByteArray()
    }
}

private fun ByteArrayOutputStream.writeBytes(bytes: ByteArray) = write(bytes)
private fun ByteArrayOutputStream.writeU16(value: Int) {
    write((value shr 8) and 0xFF)
    write(value and 0xFF)
}
private fun ByteArrayOutputStream.writeU32(value: Long) {
    write(((value shr 24) and 0xFF).toInt())
    write(((value shr 16) and 0xFF).toInt())
    write(((value shr 8) and 0xFF).toInt())
    write((value and 0xFF).toInt())
}

private fun tempFile(bytes: ByteArray): File = createTempFile(suffix = ".mobi").toFile().apply { writeBytes(bytes) }

class MobiParserTest {

    @Test
    fun `reads plain uncompressed text`() {
        val bytes = MobiBuilder()
            .compression(1)
            .textRecords("<html><body><p>Hello world.</p></body></html>".toByteArray())
            .build()

        val book = MobiParser.parse(tempFile(bytes))

        val text = (book.chapters.single() as ParsedChapter.TextChapter).document.blocks
            .joinToString(" ") { (it as Block.Paragraph).spans.single().text }
        assertTrue(text.contains("Hello world."))
    }

    @Test
    fun `decompresses palmdoc text across multiple records`() {
        val original = "Hello world. ".repeat(50)
        val compressed = compressPalmDocForTest(original.toByteArray())
        val bytes = MobiBuilder()
            .compression(2)
            .declaredTextLength(original.toByteArray().size)
            .textRecords(compressed)
            .build()

        val book = MobiParser.parse(tempFile(bytes))

        val text = (book.chapters.single() as ParsedChapter.TextChapter).document.blocks
            .joinToString(" ") { (it as Block.Paragraph).spans.single().text }
        assertTrue(text.contains("Hello world."))
    }

    @Test
    fun `reads title and author from EXTH, falling back to the MOBI header name`() {
        val withExth = MobiBuilder()
            .fullName("Header Title")
            .exth(mapOf(100 to "Jane Doe", 503 to "EXTH Title"))
            .textRecords("Plain text content.".toByteArray())
            .build()

        val book = MobiParser.parse(tempFile(withExth))

        assertEquals("EXTH Title", book.title)
        assertEquals("Jane Doe", book.author)
    }

    @Test
    fun `falls back to the header's full name without EXTH`() {
        val bytes = MobiBuilder().fullName("Header Title").textRecords("Text.".toByteArray()).build()

        assertEquals("Header Title", MobiParser.parse(tempFile(bytes)).title)
    }

    @Test
    fun `drm-encrypted books throw a clear error`() {
        val bytes = MobiBuilder().encryptionType(2).textRecords("Text.".toByteArray()).build()

        assertFailsWith<DrmProtectedException> { MobiParser.parse(tempFile(bytes)) }
    }

    @Test
    fun `reads uncompressed PalmDOC and uses PalmDB title`() {
        val bytes = PalmDocBuilder("Café PalmDOC", "Uncompressed text.".toByteArray(Charsets.ISO_8859_1)).build()

        val book = MobiParser.parse(tempFile(bytes))

        assertEquals(BookFormat.MOBI, book.format)
        assertEquals("Café PalmDOC", book.title)
        assertEquals("Uncompressed text.", bookText(book))
    }

    @Test
    fun `reads PalmDOC-compressed records`() {
        val text = "PalmDOC compression works across a real PalmDB record."
        val compressed = compressPalmDocForTest(text.toByteArray())
        val bytes = PalmDocBuilder(
            "Compressed",
            compressed,
            compression = 2,
            declaredTextLength = text.length,
        ).build()

        val book = MobiParser.parse(tempFile(bytes))

        assertEquals(text, bookText(book))
    }

    @Test
    fun `rejects non-book PalmDB types`() {
        val bytes = PalmDocBuilder(
            "Medical data",
            "not a book".toByteArray(),
            databaseType = "DATA",
            databaseCreator = "MEDI",
        ).build()

        val error = assertFailsWith<IllegalArgumentException> { MobiParser.parse(tempFile(bytes)) }

        assertTrue(error.message.orEmpty().contains("Unsupported PalmDB type"))
    }

    @Test
    fun `rejects HUFF CDIC and KF8-only MOBI content explicitly`() {
        val huffBytes = MobiBuilder().extraRecords("HUFF".toByteArray()).build()
        val huffCompressionBytes = MobiBuilder().compression(0x4448).build()
        val kf8Bytes = MobiBuilder().mobiVersion(8).textRecords("Text.".toByteArray()).build()

        assertTrue(
            assertFailsWith<IllegalArgumentException> { MobiParser.parse(tempFile(huffBytes)) }
                .message.orEmpty().contains("HUFF/CDIC"),
        )
        assertTrue(
            assertFailsWith<IllegalArgumentException> { MobiParser.parse(tempFile(huffCompressionBytes)) }
                .message.orEmpty().contains("HUFF/CDIC"),
        )
        assertTrue(
            assertFailsWith<IllegalArgumentException> { MobiParser.parse(tempFile(kf8Bytes)) }
                .message.orEmpty().contains("KF8-only"),
        )
    }

    @Test
    fun `malformed record metadata and PalmDOC back-references fail clearly`() {
        val invalidOffset = PalmDocBuilder("Bad offset", "Text.".toByteArray()).build().apply {
            writeU32At(78, 0)
        }
        val invalidDistance = PalmDocBuilder(
            "Bad distance",
            byteArrayOf(0x80.toByte(), 0x00),
            compression = 2,
            declaredTextLength = 3,
        ).build()
        val excessiveRecordCount = PalmDocBuilder("Bad count", "Text.".toByteArray()).build().apply {
            val record0Offset = readU32At(78).toInt()
            writeU16At(record0Offset + 8, 3)
        }
        val outputExceedsDeclaredLength = PalmDocBuilder(
            "Excessive output",
            byteArrayOf(0x41, 0x80.toByte(), 0x08),
            compression = 2,
            declaredTextLength = 3,
        ).build()

        assertTrue(
            assertFailsWith<IllegalArgumentException> { MobiParser.parse(tempFile(invalidOffset)) }
                .message.orEmpty().contains("offset"),
        )
        assertTrue(
            assertFailsWith<IllegalArgumentException> { MobiParser.parse(tempFile(invalidDistance)) }
                .message.orEmpty().contains("back-reference distance"),
        )
        assertTrue(
            assertFailsWith<IllegalArgumentException> { MobiParser.parse(tempFile(excessiveRecordCount)) }
                .message.orEmpty().contains("text record count"),
        )
        assertTrue(
            assertFailsWith<IllegalArgumentException> { MobiParser.parse(tempFile(outputExceedsDeclaredLength)) }
                .message.orEmpty().contains("exceeds the declared text length"),
        )
    }
}

private fun bookText(book: dev.reader.parser.ParsedBook): String =
    (book.chapters.single() as ParsedChapter.TextChapter).document.blocks
        .joinToString(" ") { (it as Block.Paragraph).spans.single().text }

private fun ByteArray.readU32At(offset: Int): Long =
    ((this[offset].toLong() and 0xFF) shl 24) or
        ((this[offset + 1].toLong() and 0xFF) shl 16) or
        ((this[offset + 2].toLong() and 0xFF) shl 8) or
        (this[offset + 3].toLong() and 0xFF)

private fun ByteArray.writeU16At(offset: Int, value: Int) {
    this[offset] = (value shr 8).toByte()
    this[offset + 1] = value.toByte()
}

private fun ByteArray.writeU32At(offset: Int, value: Int) {
    this[offset] = (value ushr 24).toByte()
    this[offset + 1] = (value ushr 16).toByte()
    this[offset + 2] = (value ushr 8).toByte()
    this[offset + 3] = value.toByte()
}

/** A from-scratch PalmDOC compressor (literal runs only) just to produce input [MobiParser] can decompress. */
private fun compressPalmDocForTest(data: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    var i = 0
    while (i < data.size) {
        val chunk = minOf(8, data.size - i)
        out.write(chunk)
        out.write(data, i, chunk)
        i += chunk
    }
    return out.toByteArray()
}
