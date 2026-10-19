@file:Suppress("MagicNumber") // field offsets and bit masks from the published MOBI/PalmDOC binary format.

package dev.reader.parser.mobi

import dev.reader.core.model.BookFormat
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.Span
import dev.reader.parser.ParsedBook
import dev.reader.parser.ParsedChapter
import dev.reader.parser.html.parseHtmlToRichDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset

private const val PALMDB_HEADER_SIZE = 78
private const val RECORD_INFO_SIZE = 8
private const val NUM_RECORDS_OFFSET = 76
private const val DATABASE_TYPE_OFFSET = 60
private const val MOBI_HEADER_OFFSET = 16
private const val COMPRESSION_NONE = 1
private const val COMPRESSION_PALMDOC = 2
private const val COMPRESSION_HUFF_CDIC = 0x4448
private const val ENCRYPTION_NONE = 0
private const val UTF8_CODE = 65001L
private const val EXTH_FLAG_BIT = 0x40
private const val EXTH_AUTHOR = 100
private const val EXTH_UPDATED_TITLE = 503
private const val MOBI_VERSION_OFFSET = MOBI_HEADER_OFFSET + 20

/** Thrown for Kindle/Mobipocket DRM, which this reader deliberately doesn't attempt to defeat. */
class DrmProtectedException(message: String) : Exception(message)

/**
 * Reads classic PalmDOC and Mobipocket files using uncompressed or PalmDOC-compressed text.
 * KF8-only and HUFF/CDIC-compressed files are not supported.
 */
object MobiParser {

    fun parse(file: File): ParsedBook {
        val palmDb = readPalmDb(file.readBytes())
        val record0 = palmDb.record(0)
        val header = readPalmDocHeader(palmDb, record0)
        if (!palmDb.isPalmDoc) validateMobi(palmDb, record0, header)
        val rawText = decodeText(palmDb, header)
        require(header.textLength <= rawText.size) {
            "Invalid PalmDB: text records are shorter than the declared text length"
        }
        val metadata = readBookMetadata(palmDb, record0, file.nameWithoutExtension)
        val text = String(rawText, 0, header.textLength, metadata.charset)
        val document = toRichDocument(text)

        return ParsedBook(
            title = metadata.title,
            author = metadata.author,
            coverPath = null,
            format = BookFormat.MOBI,
            chapters = listOf(ParsedChapter.TextChapter(metadata.title, document)),
        )
    }

    private data class PalmDb(
        val bytes: ByteArray,
        val offsets: List<Int>,
        val title: String,
        val isPalmDoc: Boolean,
    ) {
        fun record(index: Int): ByteArray {
            require(index in offsets.indices) { "Invalid PalmDB: missing record $index" }
            val end = offsets.getOrNull(index + 1) ?: bytes.size
            return bytes.copyOfRange(offsets[index], end)
        }
    }

    private data class PalmDocHeader(
        val compression: Int,
        val textLength: Int,
        val textRecordCount: Int,
    )

    private data class BookMetadata(
        val title: String,
        val author: String,
        val charset: Charset,
    )

    private fun readPalmDb(bytes: ByteArray): PalmDb {
        require(bytes.size >= PALMDB_HEADER_SIZE) { "Invalid PalmDB: header is truncated" }
        val numRecords = bytes.readU16(NUM_RECORDS_OFFSET)
        require(numRecords > 0) { "Invalid PalmDB: no records" }
        val recordsEnd = PALMDB_HEADER_SIZE.toLong() + numRecords.toLong() * RECORD_INFO_SIZE
        require(recordsEnd <= bytes.size) { "Invalid PalmDB: record table is truncated" }
        val offsets = (0 until numRecords).map { index ->
            val offset = bytes.readU32(PALMDB_HEADER_SIZE + index * RECORD_INFO_SIZE)
            require(offset >= recordsEnd && offset <= bytes.size.toLong()) {
                "Invalid PalmDB: record $index offset is out of bounds"
            }
            if (index > 0) {
                require(offset >= bytes.readU32(PALMDB_HEADER_SIZE + (index - 1) * RECORD_INFO_SIZE)) {
                    "Invalid PalmDB: record offsets are decreasing"
                }
            }
            offset.toInt()
        }
        val databaseType = String(bytes, DATABASE_TYPE_OFFSET, 8, Charsets.US_ASCII)
        val isPalmDoc = databaseType == "TEXtREAd"
        require(isPalmDoc || databaseType == "BOOKMOBI") {
            "Unsupported PalmDB type '$databaseType'; expected TEXt/REAd PalmDOC or BOOK/MOBI"
        }
        val palmDbTitle = String(bytes, 0, 32, Charset.forName("windows-1252"))
            .substringBefore('\u0000').trim()
        return PalmDb(bytes, offsets, palmDbTitle, isPalmDoc)
    }

    private fun readPalmDocHeader(palmDb: PalmDb, record0: ByteArray): PalmDocHeader {
        require(record0.size >= 16) { "Invalid PalmDB: PalmDOC header is truncated" }
        val compression = record0.readU16(0)
        val textLength = record0.readU32(4)
        val textRecordCount = record0.readU16(8)
        val encryptionType = record0.readU16(12)
        if (encryptionType != ENCRYPTION_NONE) {
            throw DrmProtectedException("This book is DRM-protected and can't be opened")
        }
        require(compression != COMPRESSION_HUFF_CDIC) {
            "Unsupported MOBI compression: HUFF/CDIC is not supported"
        }
        require(compression == COMPRESSION_NONE || compression == COMPRESSION_PALMDOC) {
            "Unsupported PalmDOC compression; only uncompressed and PalmDOC compression are supported"
        }
        require(textLength <= Int.MAX_VALUE) { "Invalid PalmDB: text length is too large" }
        require(textRecordCount < palmDb.offsets.size) {
            "Invalid PalmDB: text record count exceeds available records"
        }
        return PalmDocHeader(compression, textLength.toInt(), textRecordCount)
    }

    private fun validateMobi(palmDb: PalmDb, record0: ByteArray, header: PalmDocHeader) {
        require(
            record0.size >= MOBI_HEADER_OFFSET + 116 &&
                String(record0, MOBI_HEADER_OFFSET, 4, Charsets.US_ASCII) == "MOBI",
        ) { "Invalid MOBI: MOBI header is missing or truncated" }
        val mobiHeaderLength = record0.readU32(MOBI_HEADER_OFFSET + 4)
        require(
            mobiHeaderLength >= 116 && MOBI_HEADER_OFFSET.toLong() + mobiHeaderLength <= record0.size,
        ) { "Invalid MOBI: MOBI header length is invalid" }
        require(record0.readU32(MOBI_VERSION_OFFSET) < 8) {
            "Unsupported KF8-only MOBI; only classic MOBI content is supported"
        }
        require(
            (header.textRecordCount + 1 until palmDb.offsets.size).none { index ->
                val data = palmDb.record(index)
                data.startsWithAscii("HUFF") || data.startsWithAscii("CDIC")
            },
        ) { "Unsupported MOBI compression: HUFF/CDIC is not supported" }
    }

    private fun decodeText(palmDb: PalmDb, header: PalmDocHeader): ByteArray =
        ByteArrayOutputStream().apply {
            for (index in 1..header.textRecordCount) {
                val remaining = header.textLength - size()
                val data = palmDb.record(index)
                val decoded = if (header.compression == COMPRESSION_PALMDOC) {
                    decompressPalmDoc(data, remaining)
                } else {
                    data
                }
                require(decoded.size <= remaining) {
                    "Invalid PalmDB: decoded text exceeds the declared text length"
                }
                write(decoded)
            }
        }.toByteArray()

    private fun readBookMetadata(palmDb: PalmDb, record0: ByteArray, fallbackTitle: String): BookMetadata {
        if (palmDb.isPalmDoc) {
            return BookMetadata(
                palmDb.title.ifBlank { fallbackTitle },
                "",
                Charset.forName("windows-1252"),
            )
        }

        val mobiHeaderLength = record0.readU32(MOBI_HEADER_OFFSET + 4).toInt()
        val textEncoding = record0.readU32(MOBI_HEADER_OFFSET + 12)
        val charset = if (textEncoding == UTF8_CODE) Charsets.UTF_8 else Charset.forName("windows-1252")
        val exthFlags = record0.readU32(MOBI_HEADER_OFFSET + 112)
        val exth = if (exthFlags.toInt() and EXTH_FLAG_BIT != 0) {
            readExth(record0, MOBI_HEADER_OFFSET + mobiHeaderLength, charset)
        } else {
            emptyMap()
        }
        val title = readMobiTitle(record0, charset) ?: fallbackTitle
        return BookMetadata(
            exth[EXTH_UPDATED_TITLE] ?: title,
            exth[EXTH_AUTHOR].orEmpty(),
            charset,
        )
    }

    private fun readMobiTitle(record0: ByteArray, charset: Charset): String? {
        val nameOffset = record0.readU32(MOBI_HEADER_OFFSET + 84)
        val nameLength = record0.readU32(MOBI_HEADER_OFFSET + 88)
        if (nameOffset + nameLength > record0.size || nameOffset > Int.MAX_VALUE || nameLength > Int.MAX_VALUE) {
            return null
        }
        return String(record0, nameOffset.toInt(), nameLength.toInt(), charset).ifBlank { null }
    }

    private fun toRichDocument(text: String): RichDocument =
        if (text.contains("<html", ignoreCase = true) || text.contains("<p", ignoreCase = true)) {
            parseHtmlToRichDocument(text)
        } else {
            RichDocument(
                text.split(Regex("\r?\n\\s*\r?\n"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { Block.Paragraph(listOf(Span(it))) },
            )
        }

    private fun readExth(record0: ByteArray, offset: Int, charset: Charset): Map<Int, String> {
        if (offset.toLong() + 12 > record0.size || String(record0, offset, 4, Charsets.US_ASCII) != "EXTH") {
            return emptyMap()
        }
        val count = record0.readU32(offset + 8)
        val result = mutableMapOf<Int, String>()
        var pos = offset + 12
        repeat(minOf(count, ((record0.size - pos) / 8).toLong()).toInt()) {
            val type = record0.readU32(pos).toInt()
            val length = record0.readU32(pos + 4)
            if (length < 8 || pos.toLong() + length > record0.size) return result
            val dataStart = pos + 8
            val dataLength = (length - 8).toInt()
            if (type == EXTH_AUTHOR || type == EXTH_UPDATED_TITLE) {
                result.putIfAbsent(type, String(record0, dataStart, dataLength, charset))
            }
            pos += length.toInt()
        }
        return result
    }

    /** The classic PalmDOC LZ77-style compression used by `compression == 2`. */
    private fun decompressPalmDoc(data: ByteArray, maxOutputSize: Int): ByteArray {
        val out = ArrayList<Byte>(minOf(data.size, maxOutputSize, 4096))
        var i = 0
        while (i < data.size) {
            val c = data[i].toInt() and 0xFF
            i++
            when {
                c in 1..8 -> {
                    require(i + c <= data.size) { "Invalid PalmDOC compression: truncated literal run" }
                    require(out.size.toLong() + c <= maxOutputSize) {
                        "Invalid PalmDOC compression: decoded text exceeds the declared text length"
                    }
                    repeat(c) { out.add(data[i + it]) }
                    i += c
                }
                c <= 0x7f -> {
                    require(out.size < maxOutputSize) {
                        "Invalid PalmDOC compression: decoded text exceeds the declared text length"
                    }
                    out.add(c.toByte())
                }
                c >= 0xc0 -> {
                    require(out.size.toLong() + 2 <= maxOutputSize) {
                        "Invalid PalmDOC compression: decoded text exceeds the declared text length"
                    }
                    out.add(' '.code.toByte())
                    out.add((c xor 0x80).toByte())
                }
                else -> {
                    require(i < data.size) { "Invalid PalmDOC compression: truncated back-reference" }
                    val c2 = data[i].toInt() and 0xFF
                    i++
                    val combined = (c shl 8) or c2
                    val distance = (combined shr 3) and 0x7FF
                    val length = (combined and 0x07) + 3
                    require(distance > 0 && distance <= out.size) {
                        "Invalid PalmDOC compression: back-reference distance exceeds decoded text"
                    }
                    require(out.size.toLong() + length <= maxOutputSize) {
                        "Invalid PalmDOC compression: decoded text exceeds the declared text length"
                    }
                    var pos = out.size - distance
                    repeat(length) {
                        out.add(out[pos])
                        pos++
                    }
                }
            }
        }
        return out.toByteArray()
    }
}

private fun ByteArray.startsWithAscii(value: String): Boolean =
    size >= value.length && String(this, 0, value.length, Charsets.US_ASCII) == value

private fun ByteArray.readU16(offset: Int): Int {
    require(offset >= 0 && offset.toLong() + 2 <= size) { "Invalid PalmDB: truncated 16-bit field" }
    return ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)
}

private fun ByteArray.readU32(offset: Int): Long {
    require(offset >= 0 && offset.toLong() + 4 <= size) { "Invalid PalmDB: truncated 32-bit field" }
    return ((this[offset].toLong() and 0xFF) shl 24) or
        ((this[offset + 1].toLong() and 0xFF) shl 16) or
        ((this[offset + 2].toLong() and 0xFF) shl 8) or
        (this[offset + 3].toLong() and 0xFF)
}
