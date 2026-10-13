package dev.reader.app.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.reader.core.model.Book

/** A Shosetsu-style library card: cover, title and a progress bar when the book is in progress. */
@Composable
fun BookCard(book: Book, progress: Float?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            BookCover(book, modifier = Modifier.fillMaxWidth().aspectRatio(COVER_ASPECT_RATIO))
            Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (book.author.isNotBlank()) Text(book.author, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private const val COVER_ASPECT_RATIO = 0.7f
