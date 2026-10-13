package dev.reader.app.ui.library

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.reader.core.model.Category
import dev.reader.core.model.CategoryId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class CategoryTabsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val categories = listOf(Category(CategoryId("novels"), "Novels"), Category(CategoryId("comics"), "Comics"))

    @Test
    fun `selecting a category tab reports its id`() {
        var selected: CategoryId? = CategoryId("novels")
        composeRule.setContent {
            CategoryTabs(categories, selected, onSelect = { selected = it }, onAddCategory = {})
        }

        composeRule.onNodeWithText("Comics").performClick()

        assertEquals(CategoryId("comics"), selected)
    }

    @Test
    fun `selecting All reports null`() {
        var selected: CategoryId? = CategoryId("novels")
        composeRule.setContent {
            CategoryTabs(categories, selected, onSelect = { selected = it }, onAddCategory = {})
        }

        composeRule.onNodeWithText("All").performClick()

        assertEquals(null, selected)
    }

    @Test
    fun `adding a category reports its name`() {
        var added: String? = null
        composeRule.setContent {
            CategoryTabs(categories, null, onSelect = {}, onAddCategory = { added = it })
        }

        composeRule.onNodeWithContentDescription("Add category").performScrollTo().performClick()
        composeRule.onNodeWithTag("category_name_field").performTextInput("Favorites")
        composeRule.onNodeWithText("Add").performClick()

        assertEquals("Favorites", added)
    }
}
