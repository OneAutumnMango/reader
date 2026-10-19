package dev.reader.app.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle

/**
 * Like [Text] but detects taps on spans that carry a "bookmark" string annotation.
 * When the tap lands on such a span, the event is consumed (preventing the tap-zone
 * overlay from firing) and [onBookmarkTap] is invoked with the bookmark ID.
 * Falls back to plain [Text] when [onBookmarkTap] is null.
 */
@Composable
internal fun BookmarkAwareText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onBookmarkTap: ((String) -> Unit)? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    if (onBookmarkTap == null) {
        Text(text = text, style = style, modifier = modifier, maxLines = maxLines)
        return
    }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        style = style,
        maxLines = maxLines,
        modifier = modifier.pointerInput(text) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var up: PointerInputChange? = null
                while (up == null) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    up = event.changes.firstOrNull { it.id == down.id && !it.pressed }
                    // Bail out if something else (e.g. SelectionContainer) consumed the gesture.
                    if (up == null && event.changes.any { it.isConsumed }) return@awaitEachGesture
                }
                val layout = textLayout ?: return@awaitEachGesture
                val charOffset = layout.getOffsetForPosition(up.position)
                val annotations = text.getStringAnnotations("bookmark", charOffset, charOffset)
                if (annotations.isNotEmpty()) {
                    up.consume()
                    onBookmarkTap(annotations.first().item)
                }
            }
        },
        onTextLayout = { textLayout = it },
    )
}
