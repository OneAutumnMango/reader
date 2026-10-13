package dev.reader.parser.comic

import com.github.junrar.Archive
import dev.reader.core.model.BookFormat
import dev.reader.core.util.NaturalSortComparator
import dev.reader.parser.ParsedBook
import dev.reader.parser.ParsedChapter
import java.io.File
import java.util.zip.ZipFile

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp")

object ComicArchiveParser {

    fun parse(file: File, format: BookFormat): ParsedBook {
        require(format == BookFormat.CBZ || format == BookFormat.CBR) { "not a comic archive: $format" }
        val pages = when (format) {
            BookFormat.CBZ -> listCbzPages(file)
            else -> listCbrPages(file)
        }.sortedWith(NaturalSortComparator)

        return ParsedBook(
            title = file.nameWithoutExtension,
            author = "",
            coverPath = pages.firstOrNull(),
            format = format,
            chapters = listOf(ParsedChapter.ImageChapter(file.nameWithoutExtension, pages)),
        )
    }

    private fun listCbzPages(file: File): List<String> =
        ZipFile(file).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { it.name }
                .filter { it.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
                .toList()
        }

    private fun listCbrPages(file: File): List<String> =
        Archive(file).use { archive ->
            archive.fileHeaders
                .filterNot { it.isDirectory }
                .map { it.fileName }
                .filter { it.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
        }

    /** Reads one page's raw image bytes out of the archive, by its entry/file name. */
    fun readPage(file: File, format: BookFormat, pageName: String): ByteArray = when (format) {
        BookFormat.CBZ -> ZipFile(file).use { zip ->
            val entry = requireNotNull(zip.getEntry(pageName)) { "no such page: $pageName" }
            zip.getInputStream(entry).use { it.readBytes() }
        }
        BookFormat.CBR -> Archive(file).use { archive ->
            val header = requireNotNull(archive.fileHeaders.firstOrNull { it.fileName == pageName }) {
                "no such page: $pageName"
            }
            val output = java.io.ByteArrayOutputStream()
            archive.extractFile(header, output)
            output.toByteArray()
        }
        else -> error("not a comic archive: $format")
    }
}
