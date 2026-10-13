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
}
