package dev.reader.app.ui.categories

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import dev.reader.core.model.Category
import dev.reader.core.model.CategoryId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
class CategoriesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val novels = Category(CategoryId("novels"), "Novels", orderIndex = 0)
    private val comics = Category(CategoryId("comics"), "Comics", orderIndex = 1)

    private var showAll = true
    private var added: String? = null
    private var deleted: Category? = null
    private var moved: Pair<Int, Int>? = null
    private var default: CategoryId? = CategoryId("unset")
    private var singleOnly = false

    private fun setContent() = composeRule.setContent {
        CategoriesScreen(
            categories = listOf(novels, comics),
            showAllCategory = true,
            onShowAllCategoryChange = { showAll = it },
            onAdd = { added = it },
            onDelete = { deleted = it },
            onMove = { from, to -> moved = from to to },
            onDefaultChange = { default = it },
            singleCategoryPerBook = singleOnly,
            onSingleCategoryPerBookChange = { singleOnly = it },
        )
    }

    @Test
    fun `toggles the All tab`() {
        setContent()
        composeRule.onNodeWithTag("show-all-toggle").performClick()
        assertFalse(showAll)
    }

    @Test
    fun `adds, reorders and deletes categories`() {
        setContent()

        composeRule.onNodeWithTag("single-category-toggle").performClick()
        assertEquals(true, singleOnly)

        composeRule.onNodeWithTag("drag-category-novels").performTouchInput {
            swipe(start = center, end = center + Offset(0f, 120f), durationMillis = 300)
        }
        assertEquals(0 to 1, moved)

        composeRule.onNodeWithTag("delete-comics").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm-delete").performClick()
        assertEquals(comics, deleted)

        composeRule.onNodeWithText("Add category").performScrollTo().performClick()
        composeRule.onNodeWithTag("category_name_field").performTextInput("Manga")
        composeRule.onNodeWithText("Add").performClick()
        assertEquals("Manga", added)
    }

    @Test
    fun `sets the default category`() {
        setContent()
        composeRule.onNodeWithTag("default-comics").performScrollTo().performClick()
        assertEquals(comics.id, default)
    }
}
