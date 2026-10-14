package dev.reader.app.ui.appearance

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.reader.app.ui.reader.CUSTOM_FONT_FILE_PREFIX
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class FontDropdownTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `lists the system fonts and selects one`() {
        var selected = "sans-serif"
        composeRule.setContent { FontDropdown(selected, onSelect = { selected = it }) }

        composeRule.onNodeWithTag("font-dropdown").performClick()
        composeRule.onNodeWithTag("font-option-casual").assertExists()
        composeRule.onNodeWithTag("font-option-serif").performClick()

        assertEquals("serif", selected)
    }

    @Test
    fun `fonts added from files appear in the list`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val font = File(context.filesDir, "fonts/Literata.ttf").apply {
            parentFile?.mkdirs()
            writeText("not really a font")
        }
        var selected = "serif"
        composeRule.setContent { FontDropdown(selected, onSelect = { selected = it }) }

        composeRule.onNodeWithTag("font-dropdown").performClick()
        composeRule.onNodeWithTag("font-option-file-Literata.ttf").performScrollTo().performClick()

        assertEquals("$CUSTOM_FONT_FILE_PREFIX${font.path}", selected)
    }

    @Test
    fun `labels stored values readably`() {
        assertEquals("Serif (Noto Serif)", fontLabel("serif"))
        assertEquals("Literata", fontLabel("${CUSTOM_FONT_FILE_PREFIX}/x/fonts/Literata.ttf"))
        assertEquals("Georgia", fontLabel("Georgia"))
    }
}
