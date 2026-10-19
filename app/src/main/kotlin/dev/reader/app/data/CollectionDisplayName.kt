package dev.reader.app.data

import android.net.Uri
import android.provider.DocumentsContract

private const val VOLUME_ID_PART_LENGTH = 4
private const val HEX_RADIX = 16

internal fun collectionDisplayName(uri: Uri, savedName: String? = null, providerName: String? = null): String {
    savedName?.takeIf { it.isNotBlank() }?.let { return it }

    val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        ?: uri.lastPathSegment
    val fallback = fallbackCollectionName(documentId)
    return providerName
        ?.takeIf { it.isNotBlank() && !isRawProviderName(it, documentId) }
        ?: fallback
}

private fun fallbackCollectionName(documentId: String?): String {
    val id = documentId?.trim().orEmpty()
    if (id.isEmpty()) return "Collection"

    val separator = id.indexOf(':')
    if (separator >= 0) {
        val volumeId = id.substring(0, separator)
        val path = id.substring(separator + 1).trim('/')
        if (path.isNotEmpty()) return path.substringAfterLast('/').ifBlank { "Collection" }
        return if (volumeId.equals("primary", ignoreCase = true)) "Internal storage" else "SD card"
    }

    return when {
        id.equals("primary", ignoreCase = true) -> "Internal storage"
        isVolumeId(id) -> "SD card"
        else -> id.substringAfterLast('/').ifBlank { "Collection" }
    }
}

private fun isRawProviderName(providerName: String, documentId: String?): Boolean {
    val provider = providerName.trim()
    val id = documentId?.trim().orEmpty()
    val volumeId = id.substringBefore(':', id)
    return provider.equals(id, ignoreCase = true) ||
        provider.equals(volumeId, ignoreCase = true) ||
        isVolumeId(provider)
}

private fun isVolumeId(value: String): Boolean {
    if (value.equals("primary", ignoreCase = true)) return true
    val parts = value.split('-')
    return parts.size == 2 && parts.all { part ->
        part.length == VOLUME_ID_PART_LENGTH && part.all { it.digitToIntOrNull(HEX_RADIX) != null }
    }
}
