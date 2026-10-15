package dev.reader.app.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.reader.data.store.LibrarySort

/** Top-bar sort button: pick what to sort books by; picking the current field again flips its direction. */
@Composable
fun LibrarySortMenu(sort: LibrarySort, descending: Boolean, onChange: (LibrarySort, Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("library-sort")) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort books")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    trailingIcon = {
                        if (option == sort) {
                            Icon(
                                if (descending) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                                contentDescription = if (descending) "Descending" else "Ascending",
                            )
                        }
                    },
                    onClick = { onChange(option, if (option == sort) !descending else option.naturallyDescending) },
                    modifier = Modifier.testTag("library-sort-${option.name}"),
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(if (descending) "Switch to ascending" else "Switch to descending") },
                onClick = { onChange(sort, !descending) },
                modifier = Modifier.testTag("library-sort-direction"),
            )
        }
    }
}

/** Recency and progress read best newest/most first; names read best A to Z. */
private val LibrarySort.naturallyDescending: Boolean
    get() = this == LibrarySort.LAST_READ || this == LibrarySort.ADDED || this == LibrarySort.PROGRESS
