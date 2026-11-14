package dev.reader.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.reader.core.model.Book

private val CARD_SHAPE = RoundedCornerShape(LIBRARY_CARD_CORNER_RADIUS)

/** A library tile for a series: a 2x2 grid of its first four covers (empty slots if fewer), a count badge and the name. */
@Composable
fun SeriesCard(name: String, books: List<Book>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth()
            .clip(CARD_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .testTag("series-card-$name"),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.7f)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                books.take(4).let { it + List(4 - it.size) { null } }.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        pair.forEach { book ->
                            val cell = Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(4.dp))
                            if (book != null) {
                                BookCover(book, modifier = cell)
                            } else {
                                Box(cell.background(MaterialTheme.colorScheme.surfaceContainerHighest))
                            }
                        }
                    }
                }
            }
            Text(
                "${books.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .testTag("series-count-badge"),
            )
        }
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(4.dp),
        )
    }
}
