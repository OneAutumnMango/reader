package dev.reader.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class CoverCandidate(val previewUrl: String, val fullUrl: String, val label: String)

private const val USER_AGENT = "ReaderApp/1.0 (personal ebook reader; cover lookup)"

/** Finds candidate cover images via the Open Library covers API, which needs no API key. */
class CoverSearchService {

    suspend fun search(title: String, author: String): List<CoverCandidate> = withContext(Dispatchers.IO) {
        runCatching {
            val query = buildString {
                append("title=").append(URLEncoder.encode(title, "UTF-8"))
                if (author.isNotBlank()) append("&author=").append(URLEncoder.encode(author, "UTF-8"))
                append("&fields=cover_i,title&limit=12")
            }
            parseCoverCandidates(fetchText("https://openlibrary.org/search.json?$query"), title)
        }.onFailure { AppLog.warn("Cover search failed", "CoverSearch", it) }.getOrDefault(emptyList())
    }

    suspend fun download(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { connect(url).inputStream.use { it.readBytes() } }
            .onFailure { AppLog.warn("Cover download failed: $url", "CoverSearch", it) }
            .getOrNull()
    }

    fun saveToCache(context: Context, bytes: ByteArray): File {
        val target = File(File(context.cacheDir, "covers"), "${System.currentTimeMillis()}_online_cover.jpg")
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
        return target
    }

    private fun fetchText(url: String): String = connect(url).inputStream.bufferedReader().use { it.readText() }

    internal fun parseCoverCandidates(json: String, fallbackTitle: String): List<CoverCandidate> {
        val docs = JSONObject(json).optJSONArray("docs") ?: JSONArray()
        return (0 until docs.length()).mapNotNull { i ->
            val doc = docs.getJSONObject(i)
            val coverId = doc.optInt("cover_i", -1)
            if (coverId <= 0) return@mapNotNull null
            CoverCandidate(
                previewUrl = "https://covers.openlibrary.org/b/id/$coverId-M.jpg",
                fullUrl = "https://covers.openlibrary.org/b/id/$coverId-L.jpg",
                label = doc.optString("title", fallbackTitle),
            )
        }.distinctBy { it.fullUrl }.take(8)
    }

    private fun connect(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
}
