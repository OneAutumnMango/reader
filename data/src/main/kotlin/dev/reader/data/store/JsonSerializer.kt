package dev.reader.data.store

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.okio.OkioSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource

/**
 * Reads and writes one file as JSON. A file that fails to parse is reported as corrupt so
 * DataStore can fall back to [defaultValue] rather than leaving the app unable to start.
 */
internal class JsonSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
    private val json: Json,
) : OkioSerializer<T> {

    override suspend fun readFrom(source: BufferedSource): T {
        val text = source.readUtf8()
        if (text.isBlank()) return defaultValue
        return try {
            json.decodeFromString(serializer, text)
        } catch (e: SerializationException) {
            throw CorruptionException("Could not parse stored JSON", e)
        }
    }

    override suspend fun writeTo(t: T, sink: BufferedSink) {
        sink.writeUtf8(json.encodeToString(serializer, t))
    }
}
