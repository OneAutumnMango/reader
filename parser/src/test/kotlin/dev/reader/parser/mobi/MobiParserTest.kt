package dev.reader.parser.mobi

import dev.reader.core.richtext.Block
import dev.reader.parser.ParsedChapter
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Builds a minimal, real MOBI file byte-for-byte so [MobiParser] can be tested without fixtures. */
private class MobiBuilder {
    private var compression = 1
    private var encryptionType = 0
    private var textEncoding = 65001L
    private var exth: Map<Int, String> = emptyMap()
    private var fullName = "Fallback Title"
    private var textRecords: List<ByteArray> = emptyList()

    fun compression(value: Int) = apply { compression = value }
    fun encryptionType(value: Int) = apply { encryptionType = value }
    fun fullName(value: String) = apply { fullName = value }
    fun exth(values: Map<Int, String>) = apply { exth = values }
    fun textRecords(vararg records: ByteArray) = apply { textRecords = records.toList() }

    fun build(): ByteArray {
        val exthBytes = buildExth()
        val fullNameBytes = fullName.toByteArray(Charsets.UTF_8)
        val textLength = textRecords.sumOf { it.size }

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
            writeU32(6) // file version
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

        val records = listOf(record0) + textRecords
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
        val bytes = MobiBuilder().compression(2).textRecords(compressed).build()

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
