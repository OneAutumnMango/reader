package dev.reader.app.ui.reader

import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.geometry.Rect
import androidx.test.core.app.ApplicationProvider
import dev.reader.core.model.BookId
import dev.reader.core.model.Bookmark
import dev.reader.core.model.BookmarkId
import dev.reader.core.model.ChapterId
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.Span
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class ReaderSelectionActionsTest {

    @Test
    fun `saved highlights cross formatting and paragraphs without losing text`() {
        val document = RichDocument(
            listOf(
                Block.Paragraph(listOf(Span("hello "), Span("world", bold = true))),
                Block.Paragraph(listOf(Span("next paragraph"))),
            ),
        )
        val bookmark = Bookmark(
            BookmarkId("highlight"), BookId("book"), ChapterId("chapter"), 0f,
            "[Highlight]\nworld\nnext", Instant.fromEpochSeconds(0),
        )
        val highlighted = document.withSavedHighlights(listOf(bookmark))
        val first = (highlighted.blocks[0] as Block.Paragraph).spans
        val second = (highlighted.blocks[1] as Block.Paragraph).spans
        assertEquals("hello world", first.joinToString("") { it.text })
        assertEquals("next paragraph", second.joinToString("") { it.text })
        assertTrue(first.last().highlighted && first.last().bold)
        assertTrue(second.first().highlighted)
        assertEquals("next", second.first().text)
        assertTrue(!second.last().highlighted)
        val (head, tail) = splitSpans(first, 8)
        assertTrue(head.last().highlighted && tail.first().highlighted)
    }

    @Test
    fun `saved highlight picks the occurrence nearest its recorded position, not the first match`() {
        val document = RichDocument(
            listOf(Block.Paragraph(listOf(Span("you have it when you do this")))),
        )
        // "you" appears at char offset 0 and 17; a position recorded near the second occurrence
        // should resolve there, not fall back to the first occurrence in the text.
        val secondYouOffset = 17
        val position = secondYouOffset.toFloat() / document.toDocumentText().text.length
        val bookmark = Bookmark(
            BookmarkId("highlight"), BookId("book"), ChapterId("chapter"), position,
            "[Highlight]\nyou", Instant.fromEpochSeconds(0),
        )
        val highlighted = document.withSavedHighlights(listOf(bookmark))
        val spans = (highlighted.blocks[0] as Block.Paragraph).spans
        val highlightedText = spans.filter { it.highlighted }.joinToString("") { it.text }
        assertEquals("you", highlightedText)
        val fullText = spans.joinToString("") { it.text }
        val highlightStart = spans.takeWhile { !it.highlighted }.sumOf { it.text.length }
        assertEquals(secondYouOffset, highlightStart)
        assertEquals("you have it when you do this", fullText)
    }

    @Test
    fun `selection action waits for an asynchronous copy`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("old", "previous"))
        val toolbar = ReaderSelectionToolbar()
        var copyLater: (() -> Unit)? = null
        var received: String? = null
        toolbar.showMenu(Rect.Zero, {
            copyLater = { toolbar.captureSelection("new quote") }
        }, null, null, null)
        toolbar.dispatchSelectionAction(SelectionAction.HIGHLIGHT) { _, text, _ -> received = text }
        assertNull(received)
        copyLater?.invoke()
        assertEquals("new quote", received)
        assertEquals("previous", clipboard.primaryClip?.getItemAt(0)?.text)
    }

    @Test
    fun `external selection actions create supported Android intents`() {
        val translation = assertNotNull(selectionIntent(SelectionAction.TRANSLATE, "bonjour"))
        assertEquals(Intent.ACTION_PROCESS_TEXT, translation.action)
        assertEquals("bonjour", translation.getStringExtra(Intent.EXTRA_PROCESS_TEXT))
        assertTrue(translation.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false))

        val search = assertNotNull(selectionIntent(SelectionAction.SEARCH, "hello world"))
        assertEquals(Intent.ACTION_WEB_SEARCH, search.action)
        assertEquals("hello world", search.getStringExtra(SearchManager.QUERY))

        val dictionary = assertNotNull(selectionIntent(SelectionAction.DICTIONARY, "reader"))
        assertEquals(Intent.ACTION_DEFINE, dictionary.action)
        assertEquals("reader", dictionary.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun `external selection actions have browser fallbacks`() {
        val translation = selectionFallbackIntent(SelectionAction.TRANSLATE, "hello world")!!.data!!
        assertEquals("translate.google.com", translation.host)
        assertEquals("hello world", translation.getQueryParameter("text"))
        val search = selectionFallbackIntent(SelectionAction.SEARCH, "hello world")!!.data!!
        assertEquals("hello world", search.getQueryParameter("q"))
        val dictionary = selectionFallbackIntent(SelectionAction.DICTIONARY, "reader")!!.data!!
        assertEquals("define reader", dictionary.getQueryParameter("q"))
        assertNull(selectionIntent(SelectionAction.COPY, "hello"))
        assertNull(selectionFallbackIntent(SelectionAction.NOTE, "hello"))
    }

    @Test
    fun `notes and highlights persist their selected text and note category`() {
        assertEquals(
            "[Highlight]\nselected words",
            selectionBookmarkNote(SelectionAction.HIGHLIGHT, "selected words"),
        )
        assertEquals(
            "[Note]\nselected words\n\nmargin thought",
            selectionBookmarkNote(SelectionAction.NOTE, "selected words", "margin thought"),
        )
        assertEquals("[Note]\nselected words", selectionBookmarkNote(SelectionAction.NOTE, "selected words"))
    }

    @Test
    fun `copy keeps the quote while other actions restore the prior clipboard`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("old", "existing clipboard"))
        val toolbar = ReaderSelectionToolbar()
        var received: Pair<SelectionAction, String>? = null
        toolbar.showMenu(
            Rect.Zero,
            { toolbar.captureSelection("selected quote") },
            null,
            null,
            null,
        )

        toolbar.dispatchSelectionAction(SelectionAction.NOTE) { action, text, _ ->
            received = action to text
        }

        assertEquals(SelectionAction.NOTE to "selected quote", received)
        assertEquals("existing clipboard", clipboard.primaryClip?.getItemAt(0)?.text)

        toolbar.showMenu(
            Rect.Zero,
            { clipboard.setPrimaryClip(ClipData.newPlainText("selection", "copied quote")) },
            null,
            null,
            null,
        )
        toolbar.dispatchSelectionAction(SelectionAction.COPY) { _, _, _ ->
            error("Copy is handled by the toolbar")
        }

        assertEquals("copied quote", clipboard.primaryClip?.getItemAt(0)?.text)
    }
}
