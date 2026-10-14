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
private const val MOBI_HEADER_OFFSET = 16
private const val COMPRESSION_NONE = 1
private const val COMPRESSION_PALMDOC = 2
private const val ENCRYPTION_NONE = 0
private const val UTF8_CODE = 65001L
private const val EXTH_FLAG_BIT = 0x40
private const val EXTH_AUTHOR = 100
private const val EXTH_UPDATED_TITLE = 503

/** Thrown for Kindle/Mobipocket DRM, which this reader deliberately doesn't attempt to defeat. */
class DrmProtectedException(message: String) : Exception(message)

/**
 * Reads Mobipocket/Kindle `.mobi`/`.azw`/`.azw3` files: the PalmDB container holds a PalmDOC- or
 * plain-text-compressed HTML book. `.azw3` (KF8) files keep this classic copy for backward
 * compatibility; a KF8-only file (no classic copy) will fail with "no readable content found".
 */
object MobiParser {

    fun parse(file: File): ParsedBook {
        val bytes = file.readBytes()
        val numRecords = bytes.readU16(NUM_RECORDS_OFFSET)
        val offsets = (0 until numRecords).map { bytes.readU32(PALMDB_HEADER_SIZE + it * RECORD_INFO_SIZE).toInt() }

        fun record(index: Int): ByteArray {
            val start = offsets[index]
            val end = offsets.getOrNull(index + 1) ?: bytes.size
            return bytes.copyOfRange(start, end)
        }

        val record0 = record(0)
        val compression = record0.readU16(0)
        val textLength = record0.readU32(4)
        val textRecordCount = record0.readU16(8)
        val encryptionType = record0.readU16(12)
        if (encryptionType != ENCRYPTION_NONE) {
            throw DrmProtectedException("This book is DRM-protected and can't be opened")
        }
        check(compression == COMPRESSION_NONE || compression == COMPRESSION_PALMDOC) {
            "Unsupported MOBI compression; only plain and PalmDOC text is supported"
        }

        val mobiHeaderLength = record0.readU32(MOBI_HEADER_OFFSET + 4).toInt()
        val textEncoding = record0.readU32(MOBI_HEADER_OFFSET + 12)
        val charset = if (textEncoding == UTF8_CODE) Charsets.UTF_8 else Charset.forName("windows-1252")
        val exthFlags = record0.readU32(MOBI_HEADER_OFFSET + 112)
        val fullNameOffset = record0.readU32(MOBI_HEADER_OFFSET + 84).toInt()
        val fullNameLength = record0.readU32(MOBI_HEADER_OFFSET + 88).toInt()

        val exth = if (exthFlags.toInt() and EXTH_FLAG_BIT != 0) {
            readExth(record0, MOBI_HEADER_OFFSET + mobiHeaderLength, charset)
        } else {
            emptyMap()
        }
        val fallbackTitle = if (fullNameOffset + fullNameLength <= record0.size) {
            runCatching { String(record0, fullNameOffset, fullNameLength, charset) }.getOrNull()
        } else {
            null
        }
        val title = exth[EXTH_UPDATED_TITLE] ?: fallbackTitle?.ifBlank { null } ?: file.nameWithoutExtension
        val author = exth[EXTH_AUTHOR].orEmpty()

        val rawText = ByteArrayOutputStream().apply {
            for (i in 1..textRecordCount) {
                val data = record(i)
                write(if (compression == COMPRESSION_PALMDOC) decompressPalmDoc(data) else data)
            }
        }.toByteArray()
        val text = String(rawText, 0, minOf(textLength.toInt(), rawText.size), charset)
        val document = toRichDocument(text)

        return ParsedBook(
            title = title,
            author = author,
            coverPath = null,
            format = BookFormat.MOBI,
            chapters = listOf(ParsedChapter.TextChapter(title, document)),
        )
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
        if (offset + 12 > record0.size || String(record0, offset, 4, Charsets.US_ASCII) != "EXTH") return emptyMap()
        val count = record0.readU32(offset + 8).toInt()
        val result = mutableMapOf<Int, String>()
        var pos = offset + 12
        repeat(count) {
            if (pos + 8 > record0.size) return@repeat
            val type = record0.readU32(pos).toInt()
            val length = record0.readU32(pos + 4).toInt()
            val dataStart = pos + 8
            val dataLength = (length - 8).coerceAtLeast(0)
            if ((type == EXTH_AUTHOR || type == EXTH_UPDATED_TITLE) && dataStart + dataLength <= record0.size) {
                result.putIfAbsent(type, String(record0, dataStart, dataLength, charset))
            }
            pos += length
        }
        return result
    }

    /** The classic PalmDOC LZ77-style compression used by `compression == 2`. */
    private fun decompressPalmDoc(data: ByteArray): ByteArray {
        val out = ArrayList<Byte>(data.size * 2)
        var i = 0
        while (i < data.size) {
            val c = data[i].toInt() and 0xFF
            i++
            when {
                c in 1..8 -> {
                    repeat(c) { out.add(data[i + it]) }
                    i += c
                }
                c <= 0x7f -> out.add(c.toByte())
                c >= 0xc0 -> {
                    out.add(' '.code.toByte())
                    out.add((c xor 0x80).toByte())
                }
                else -> {
                    val c2 = data[i].toInt() and 0xFF
                    i++
                    val combined = (c shl 8) or c2
                    val distance = (combined shr 3) and 0x7FF
                    val length = (combined and 0x07) + 3
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

private fun ByteArray.readU16(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

private fun ByteArray.readU32(offset: Int): Long =
    ((this[offset].toLong() and 0xFF) shl 24) or
        ((this[offset + 1].toLong() and 0xFF) shl 16) or
        ((this[offset + 2].toLong() and 0xFF) shl 8) or
        (this[offset + 3].toLong() and 0xFF)
