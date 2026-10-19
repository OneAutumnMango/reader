package dev.reader.app.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.reader.core.model.Book

/** A Shosetsu-style library card: cover, title and a progress bar when the book is in progress. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCard(
    book: Book,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    finished: Boolean = false,
    selected: Boolean = false,
    onLongClick: () -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("book-card-${book.id.value}"),
    ) {
        Column(modifier = Modifier.padding(2.dp)) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(COVER_ASPECT_RATIO)) {
                BookCover(book, modifier = Modifier.fillMaxWidth().aspectRatio(COVER_ASPECT_RATIO))
                when {
                    selected -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(COVER_ASPECT_RATIO)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp).testTag("book-selected-badge"),
                        )
                    }
                    finished -> Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Finished",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(20.dp)
                            .testTag("book-finished-badge"),
                    )
                }
            }
            Text(
                book.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.author.isNotBlank()) {
                Text(
                    book.author,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private const val COVER_ASPECT_RATIO = 0.7f
