package dev.reader.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.reader.core.model.Book

/** Library search: filters by title/author as the query changes. */
@Composable
fun SearchScreen(
    books: List<Book>,
    query: String,
    onQueryChange: (String) -> Unit,
    onBookClick: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    val results = filterBooksByQuery(books, query)

    Column(modifier = modifier) {
        OutlinedTextField(value = query, onValueChange = onQueryChange, label = { Text("Search") })
        LazyColumn {
            items(results, key = { it.id.value }) { book ->
                ListItem(
                    headlineContent = { Text(book.title) },
                    supportingContent = { Text(book.author) },
                    modifier = Modifier.clickable { onBookClick(book) },
                )
            }
        }
    }
}
