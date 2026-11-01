package dev.reader.app.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ZoomableSelectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun doubleTap(zoomOn: Boolean, doubleTapOn: Boolean, plain: Boolean = false): Pair<Boolean, Float?> {
        val toolbar = ReaderSelectionToolbar()
        var zoomRef: ZoomState? = null
        composeRule.setContent {
            CompositionLocalProvider(
                LocalTextToolbar provides toolbar,
                LocalTextContextMenuToolbarProvider provides toolbar,
                LocalTextContextMenuDropdownProvider provides toolbar,
            ) {
                val content: @Composable () -> Unit = {
                    zoomRef = LocalZoomState.current
                    SelectionContainer {
                        Box(Modifier.size(300.dp)) {
                            Text("Hello world today and more words here", Modifier.testTag("t"))
                        }
                    }
                }
                if (plain) {
                    Box(Modifier.size(300.dp).testTag("z")) { content() }
                } else {
                    Zoomable(zoomOn, Modifier.size(300.dp).testTag("z"), doubleTapOn) { content() }
                }
            }
        }
        composeRule.onNodeWithTag("z").performTouchInput {
            val p = Offset(30f, 20f)
            down(p)
            up()
            advanceEventTime(50)
            down(p)
            up()
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1000)
        return (toolbar.menu != null) to zoomRef?.scale
    }

    @Test
    fun `a double tap selects text without Zoomable, so the tests below are meaningful`() {
        assertTrue(doubleTap(zoomOn = true, doubleTapOn = true, plain = true).first)
    }

    @Test
    fun `a double tap zooms and does not select text`() {
        val (selected, scale) = doubleTap(zoomOn = true, doubleTapOn = true)
        assertFalse(selected)
        assertEquals(3f, scale)
    }

    @Test
    fun `a double tap does not select text when double tap zoom is off`() {
        val (selected, scale) = doubleTap(zoomOn = true, doubleTapOn = false)
        assertFalse(selected)
        assertEquals(1f, scale)
    }

    @Test
    fun `a double tap does not select text when zoom is off entirely`() {
        assertFalse(doubleTap(zoomOn = false, doubleTapOn = false).first)
    }
}
