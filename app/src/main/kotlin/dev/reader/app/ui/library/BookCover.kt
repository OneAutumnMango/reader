package dev.reader.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import coil3.compose.AsyncImage
import dev.reader.core.model.Book

/** The book's cover image, or a lettered placeholder when none has been set. */
@Composable
fun BookCover(book: Book, modifier: Modifier = Modifier) {
    if (book.coverPath != null) {
        AsyncImage(model = book.coverPath, contentDescription = book.title, modifier = modifier)
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(book.title.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.headlineMedium)
        }
    }
}
