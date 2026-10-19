package dev.reader.app.ui.reader

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume

enum class SelectionAction(val label: String) {
    COPY("Copy"),
    TRANSLATE("Translate"),
    SEARCH("Search"),
    DICTIONARY("Dictionary"),
    NOTE("Note"),
    HIGHLIGHT("Highlight"),
}

internal class ReaderSelectionToolbar : TextToolbar, TextContextMenuProvider {
    private var pendingAction: ((String) -> Unit)? = null
    private var closeContextMenu: (() -> Unit)? = null
    var coordinates: LayoutCoordinates? = null
    var menu by mutableStateOf<SelectionToolbarMenu?>(null)
        private set

    override val status: TextToolbarStatus
        get() = if (menu == null) TextToolbarStatus.Hidden else TextToolbarStatus.Shown

    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        val copy = dataProvider.data().components.filterIsInstance<TextContextMenuItem>()
            .firstOrNull { it.key == TextContextMenuKeys.CopyKey } ?: return
        suspendCancellableCoroutine<Unit> { continuation ->
            val session = object : TextContextMenuSession {
                override fun close() {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            closeContextMenu = session::close
            val bounds = coordinates?.takeIf { it.isAttached }?.let { dataProvider.contentBounds(it) } ?: Rect.Zero
            showMenu(bounds, { copy.onClick(session) }, null, null, null)
            continuation.invokeOnCancellation { hide() }
        }
        hide()
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        pendingAction = null
        menu = onCopyRequested?.let { SelectionToolbarMenu(rect, it) }
    }

    override fun hide() {
        menu = null
        val close = closeContextMenu
        closeContextMenu = null
        close?.invoke()
    }

    fun dismiss() {
        pendingAction = null
        hide()
    }

    fun captureSelection(text: String): Boolean {
        val action = pendingAction ?: return false
        pendingAction = null
        if (text.isNotBlank()) action(text)
        return true
    }

    fun dispatchSelectionAction(
        action: SelectionAction,
        onAction: (SelectionAction, String, Rect) -> Unit,
    ) {
        val currentMenu = menu ?: return
        val rect = currentMenu.rect
        pendingAction = if (action == SelectionAction.COPY) null else { text -> onAction(action, text, rect) }
        currentMenu.onCopyRequested()
        hide()
    }
}

internal data class SelectionToolbarMenu(val rect: Rect, val onCopyRequested: () -> Unit)

@Composable
internal fun ReaderSelectionToolbarOverlay(
    toolbar: ReaderSelectionToolbar,
    onAction: (SelectionAction, String, Rect) -> Unit,
) {
    val menu = toolbar.menu ?: return
    val density = LocalDensity.current
    val verticalOffset = with(density) { 148.dp.roundToPx() }
    val horizontalOffset = menu.rect.left.toInt().coerceAtLeast(0)
    val verticalPosition = (menu.rect.top.toInt() - verticalOffset).coerceAtLeast(0)

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(horizontalOffset, verticalPosition),
        properties = PopupProperties(focusable = false),
        onDismissRequest = toolbar::dismiss,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = MaterialTheme.shapes.small,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
        ) {
            Column(Modifier.padding(4.dp)) {
                SelectionAction.entries.chunked(2).forEach { actions ->
                    Row(Modifier.fillMaxWidth()) {
                        actions.forEach { action ->
                            TextButton(
                                onClick = { toolbar.dispatchSelectionAction(action, onAction) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(action.label)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun selectionIntent(action: SelectionAction, text: String): Intent? = when (action) {
    SelectionAction.TRANSLATE -> Intent().apply {
        type = "text/plain"
        setAction(Intent.ACTION_PROCESS_TEXT)
        putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    }
    SelectionAction.SEARCH -> Intent(Intent.ACTION_WEB_SEARCH)
        .putExtra(SearchManager.QUERY, text)
    SelectionAction.DICTIONARY -> Intent(Intent.ACTION_DEFINE)
        .putExtra(Intent.EXTRA_TEXT, text)
    SelectionAction.COPY, SelectionAction.NOTE, SelectionAction.HIGHLIGHT -> null
}

internal fun selectionFallbackIntent(action: SelectionAction, text: String): Intent? {
    val query = when (action) {
        SelectionAction.TRANSLATE -> "https://translate.google.com/?sl=auto&text=${urlEncode(text)}"
        SelectionAction.SEARCH -> "https://www.google.com/search?q=${urlEncode(text)}"
        SelectionAction.DICTIONARY -> {
            val definitionQuery = urlEncode("define $text")
            "https://www.google.com/search?q=$definitionQuery"
        }
        SelectionAction.COPY, SelectionAction.NOTE, SelectionAction.HIGHLIGHT -> return null
    }
    return Intent(Intent.ACTION_VIEW, Uri.parse(query))
}

internal fun launchSelectionIntent(context: Context, action: SelectionAction, text: String) {
    val primary = selectionIntent(action, text) ?: return
    val fallback = selectionFallbackIntent(action, text)
    val hasTextProcessor = primary.resolveActivity(context.packageManager) != null
    val primaryIntent = if (action == SelectionAction.TRANSLATE && hasTextProcessor) {
        Intent.createChooser(primary, action.label)
    } else {
        primary
    }
    val launched = runCatching { context.startActivity(primaryIntent) }.isSuccess
    if (!launched) {
        val fallbackLaunched = fallback?.let { runCatching { context.startActivity(it) }.isSuccess } == true
        if (!fallbackLaunched) {
            Toast.makeText(
                context,
                "No app available for ${action.label.lowercase()}.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}

internal fun selectionBookmarkNote(
    action: SelectionAction,
    selectedText: String,
    note: String = "",
): String = when (action) {
    SelectionAction.HIGHLIGHT -> "[Highlight]\n$selectedText"
    SelectionAction.NOTE -> "[Note]\n$selectedText" + note.takeIf { it.isNotBlank() }
        ?.let { "\n\n$it" }.orEmpty()
    else -> error("Only notes and highlights are persisted as bookmarks")
}

private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
