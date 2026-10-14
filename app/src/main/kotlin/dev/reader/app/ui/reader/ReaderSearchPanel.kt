package dev.reader.app.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.reader.core.search.SearchMatch
import dev.reader.core.search.SearchOptions

private val RESULTS_MAX_HEIGHT = 240.dp
private val TOGGLE_SIZE = 36.dp

/** A match somewhere in the book: [chapterIndex] is its position in the reader's chapter pager. */
data class SearchHit(val chapterIndex: Int, val chapterTitle: String, val match: SearchMatch)

/** What the search panel shows; [hits] is null until a query has been run. */
data class SearchUiState(
    val query: String = "",
    val options: SearchOptions = SearchOptions(),
    val hits: List<SearchHit>? = null,
    val searching: Boolean = false,
    val invalid: Boolean = false,
    val active: Int? = null,
)

/** Book-wide text search with match-case (Aa), regex (.*) and any-whitespace toggles. */
@Composable
fun ReaderSearchPanel(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onOptionsChange: (SearchOptions) -> Unit,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = state.options
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("Search book") },
                isError = state.invalid,
                trailingIcon = {
                    Row {
                        OptionToggle(options.matchCase, "Match case", "search-match-case", {
                            onOptionsChange(options.copy(matchCase = it))
                        }) { Text("Aa", fontWeight = FontWeight.Bold) }
                        OptionToggle(options.regex, "Regular expression", "search-regex", {
                            onOptionsChange(options.copy(regex = it))
                        }) { Text(".*", fontWeight = FontWeight.Bold) }
                        OptionToggle(
                            options.anyWhitespace,
                            "Match any whitespace",
                            "search-whitespace",
                            { onOptionsChange(options.copy(anyWhitespace = it)) },
                            enabled = !options.regex,
                        ) { Icon(Icons.Filled.SpaceBar, contentDescription = null) }
                    }
                },
                modifier = Modifier.weight(1f).testTag("reader-search-field"),
            )
            IconButton(onClick = onClose, modifier = Modifier.testTag("reader-search-close")) {
                Icon(Icons.Filled.Close, contentDescription = "Close search")
            }
        }
        SearchStatus(state, onSelect)
        state.hits?.takeIf { it.isNotEmpty() }?.let { hits ->
            LazyColumn(modifier = Modifier.heightIn(max = RESULTS_MAX_HEIGHT).testTag("reader-search-results")) {
                itemsIndexed(hits) { index, hit -> SearchHitRow(hit, index == state.active) { onSelect(index) } }
            }
        }
    }
}

@Composable
private fun SearchStatus(state: SearchUiState, onSelect: (Int) -> Unit) {
    val hits = state.hits.orEmpty()
    val label = when {
        state.invalid -> "That isn't a valid regular expression."
        state.searching && hits.isEmpty() -> "Searching\u2026"
        state.hits == null -> ""
        hits.isEmpty() -> "No matches"
        else -> {
            val position = state.active?.let { "${it + 1} of " }.orEmpty()
            val more = if (state.searching) " so far\u2026" else ""
            "$position${hits.size} match${if (hits.size == 1) "" else "es"}$more"
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (state.invalid) MaterialTheme.colorScheme.error else Color.Unspecified,
            modifier = Modifier.weight(1f).padding(start = 4.dp).testTag("reader-search-status"),
        )
        if (hits.isNotEmpty()) {
            val active = state.active
            IconButton(
                onClick = { onSelect(((active ?: 0) - 1 + hits.size) % hits.size) },
                modifier = Modifier.testTag("reader-search-previous"),
            ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match") }
            IconButton(
                onClick = { onSelect(active?.let { (it + 1) % hits.size } ?: 0) },
                modifier = Modifier.testTag("reader-search-next"),
            ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match") }
        }
    }
}

@Composable
private fun SearchHitRow(hit: SearchHit, active: Boolean, onClick: () -> Unit) {
    val snippet = hit.match.snippet
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .testTag("reader-search-hit"),
    ) {
        Text(
            hit.chapterTitle,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            buildAnnotatedString {
                append(snippet.text.substring(0, snippet.match.first))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = Color(0x80FFC107))) {
                    append(snippet.text.substring(snippet.match.first, snippet.match.last + 1))
                }
                append(snippet.text.substring(snippet.match.last + 1))
            },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OptionToggle(
    checked: Boolean,
    description: String,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = Modifier.size(TOGGLE_SIZE).testTag(tag).semantics { contentDescription = description },
        content = content,
    )
}
