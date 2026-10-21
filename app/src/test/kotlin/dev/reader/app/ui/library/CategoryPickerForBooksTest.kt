package dev.reader.app.ui.library

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.reader.core.model.Category
import dev.reader.core.model.CategoryId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class CategoryPickerForBooksTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val novels = CategoryId("novels")
    private val comics = CategoryId("comics")
    private val categories = listOf(Category(novels, "Novels"), Category(comics, "Comics"))

    @Test
    fun `multi-select mode confirms the picked categories on Save`() {
        var confirmed: Set<CategoryId>? = null
        composeRule.setContent {
            CategoryPickerForBooks(categories, initial = setOf(novels), onConfirm = { confirmed = it }, onDismiss = {})
        }

        composeRule.onNodeWithText("Comics").performClick()
        composeRule.onNodeWithText("Save").performClick()

        assertEquals(setOf(novels, comics), confirmed)
    }

    @Test
    fun `single-category mode confirms immediately on tap`() {
        var confirmed: Set<CategoryId>? = null
        composeRule.setContent {
            CategoryPickerForBooks(
                categories,
                initial = emptySet(),
                onConfirm = { confirmed = it },
                onDismiss = {},
                singleCategoryPerBook = true,
            )
        }

        composeRule.onNodeWithText("Comics").performClick()

        assertEquals(setOf(comics), confirmed)
    }
}
