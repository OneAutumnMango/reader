package dev.reader.app.ui.library

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class CategoryChoiceRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `clicking the row triggers its callback`() {
        var clicked = false
        composeRule.setContent {
            CategoryChoiceRow(label = "Reading", checked = false, tag = "choice-reading", onClick = { clicked = true })
        }

        composeRule.onNodeWithTag("choice-reading").performClick()

        assertTrue(clicked)
    }
}
