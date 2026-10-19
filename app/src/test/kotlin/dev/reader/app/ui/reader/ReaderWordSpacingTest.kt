package dev.reader.app.ui.reader

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import dev.reader.core.richtext.Span
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ReaderWordSpacingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `spacing changes word width without changing text or formatting boundaries`() {
        val spans = listOf(Span("one two"), Span("three four", bold = true))
        val original = spans.toAnnotatedString()
        val spaced = spans.toAnnotatedString(wordSpacingSp = 8f)
        assertEquals("one twothree four", spaced.text)
        assertEquals(2, spaced.spanStyles.count { it.item.letterSpacing == 8.sp })
        var originalWidth = 0
        var spacedWidth = 0
        composeRule.setContent {
            val measurer = rememberTextMeasurer()
            originalWidth = measurer.measure(original, TextStyle(fontSize = 20.sp)).size.width
            spacedWidth = measurer.measure(spaced, TextStyle(fontSize = 20.sp)).size.width
        }
        composeRule.runOnIdle { assertTrue(spacedWidth > originalWidth, "$spacedWidth should exceed $originalWidth") }
    }
}
