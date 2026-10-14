package dev.reader.data.importer

import java.io.File
import java.security.MessageDigest

/** Content fingerprints that survive renames: identical bytes always give the same hash. */
internal object ContentHash {

    private const val BUFFER_SIZE = 64 * 1024

    fun ofFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    /** Order-independent hash of every file in [folder], so renaming the folder or its files doesn't matter. */
    fun ofFolder(folder: File): String {
        val fileHashes = (folder.listFiles { f -> f.isFile } ?: emptyArray()).map { ofFile(it) }.sorted()
        return MessageDigest.getInstance("SHA-256").digest(fileHashes.joinToString("\n").toByteArray()).toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
