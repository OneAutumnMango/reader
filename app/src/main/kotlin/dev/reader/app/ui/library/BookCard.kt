package dev.reader.app.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.reader.core.model.Book

internal val LIBRARY_CARD_CORNER_RADIUS: Dp = 12.dp
private val COVER_SHAPE = RoundedCornerShape(LIBRARY_CARD_CORNER_RADIUS)

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
    showAuthor: Boolean = true,
    onLongClick: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Column(
        modifier = modifier.fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .shadow(elevation = if (pressed) 4.dp else 0.dp, shape = COVER_SHAPE)
            .clip(COVER_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .testTag("book-card-${book.id.value}"),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(COVER_ASPECT_RATIO)) {
            BookCover(book, modifier = Modifier.fillMaxSize())
            when {
                selected -> Box(
                    modifier = Modifier
                        .fillMaxSize()
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
        if (progress != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f) }
                    .testTag("book-progress-bar"),
            )
        }
        Column(modifier = Modifier.padding(4.dp)) {
            Text(
                book.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (showAuthor && book.author.isNotBlank()) {
                Text(
                    book.author,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val COVER_ASPECT_RATIO = 0.7f
