package dev.reader.app.data

import android.content.Context
import dev.reader.core.model.BookFormat
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.parser.epub.EpubParser
import dev.reader.parser.fb2.Fb2Parser
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Base64

internal object BookImages {

    fun resolve(context: Context, source: File, format: BookFormat, document: RichDocument): RichDocument =
        RichDocument(
            document.blocks.map { block ->
                if (block is Block.Image) {
                    block.copy(
                        path = runCatching { resolvePath(context, source, format, block.path) }
                            .onFailure { AppLog.warn("Illustration resolution failed for $format", "BookImages", it) }
                            .getOrDefault(block.path),
                    )
                } else {
                    block
                }
            },
        )

    private fun resolvePath(context: Context, source: File, format: BookFormat, path: String): String {
        if (path.isBlank()) return path
        val embedded = path.startsWith("data:") || format == BookFormat.EPUB || format == BookFormat.FB2
        if (!embedded) return source.toURI().resolve(URI(path)).toString()
        val key = "${source.absolutePath}:${source.lastModified()}:${source.length()}:$path"
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }
        val target = File(context.cacheDir, "book-images/$name")
        if (target.exists() && target.length() > 0) return target.absolutePath
        val bytes = when {
            path.startsWith("data:") -> decodeDataUri(path)
            format == BookFormat.EPUB -> EpubParser.extractCover(source, path)
            format == BookFormat.FB2 -> Fb2Parser.extractCover(source, path.removePrefix("#"))
            else -> null
        }
        if (bytes == null) {
            AppLog.warn("Embedded illustration not found in $format", "BookImages")
            return path
        }
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
        return target.absolutePath
    }

    private fun decodeDataUri(path: String): ByteArray {
        val data = path.substringAfter(',')
        return if (path.substringBefore(',').endsWith(";base64")) {
            Base64.getMimeDecoder().decode(data)
        } else {
            URI("file:/$data").path.removePrefix("/").toByteArray()
        }
    }
}
