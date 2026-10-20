package dev.reader.app.ui.library

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.app.data.CoverCandidate
import dev.reader.app.data.CoverSearchService
import kotlinx.coroutines.launch

/**
 * Searches for cover candidates online (via Open Library) for a book with no cover, and lets the
 * user pick one, paste an image URL they found themselves, or jump to a browser search.
 */
@Composable
fun CoverSearchDialog(
    title: String,
    author: String,
    service: CoverSearchService,
    onCoverBytesChosen: (ByteArray) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var candidates by remember { mutableStateOf<List<CoverCandidate>?>(null) }
    var manualUrl by remember { mutableStateOf("") }
    var applying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(title, author) {
        candidates = service.search(title, author)
    }

    fun apply(url: String) {
        if (url.isBlank()) return
        scope.launch {
            applying = true
            error = null
            val bytes = service.download(url)
            applying = false
            if (bytes != null) onCoverBytesChosen(bytes) else error = "Couldn't download that image."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Find cover online") },
        text = {
            Column(Modifier.testTag("cover-search-dialog")) {
                when {
                    applying || candidates == null -> Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    candidates.isNullOrEmpty() -> Text(
                        "No matches found. This source only covers books it has an entry for — " +
                            "try searching or pasting a link below instead.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(candidates.orEmpty()) { candidate ->
                            CoverCandidateThumbnail(candidate, service, onClick = { apply(candidate.fullUrl) })
                        }
                    }
                }
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        val query = Uri.encode(listOf(title, author, "book cover").filter { it.isNotBlank() }.joinToString(" "))
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?tbm=isch&q=$query"))
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.testTag("cover-search-browser"),
                ) { Text("Search for it myself") }
                OutlinedTextField(
                    value = manualUrl,
                    onValueChange = { manualUrl = it },
                    label = { Text("Or paste an image URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("cover-search-url-field"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { apply(manualUrl.trim()) },
                enabled = manualUrl.isNotBlank() && !applying,
            ) { Text("Use URL") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CoverCandidateThumbnail(candidate: CoverCandidate, service: CoverSearchService, onClick: () -> Unit) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, candidate.previewUrl) {
        value = service.download(candidate.previewUrl)?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
    }
    Box(
        modifier = Modifier
            .width(80.dp)
            .height(112.dp)
            .clickable(onClick = onClick)
            .testTag("cover-candidate"),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = candidate.label,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                contentScale = ContentScale.Crop,
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        }
    }
}
