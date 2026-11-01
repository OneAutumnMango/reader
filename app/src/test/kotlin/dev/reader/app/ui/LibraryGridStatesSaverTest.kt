package dev.reader.app.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.saveable.SaverScope
import dev.reader.core.model.CategoryId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val TEST_SAVER_SCOPE = object : SaverScope {
    override fun canBeSaved(value: Any) = true
}

@RunWith(RobolectricTestRunner::class)
class LibraryGridStatesSaverTest {

    @Test
    fun `scroll position round-trips through save and restore`() {
        val novels = CategoryId("novels")
        val original = mutableMapOf(
            novels to LazyGridState(firstVisibleItemIndex = 42, firstVisibleItemScrollOffset = 7),
            null to LazyGridState(firstVisibleItemIndex = 3, firstVisibleItemScrollOffset = 0),
        )

        val saved = requireNotNull(with(LibraryGridStatesSaver) { with(TEST_SAVER_SCOPE) { save(original) } })
        val restored = requireNotNull(LibraryGridStatesSaver.restore(saved))

        assertEquals(42, restored[novels]?.firstVisibleItemIndex)
        assertEquals(7, restored[novels]?.firstVisibleItemScrollOffset)
        assertEquals(3, restored[null]?.firstVisibleItemIndex)
        assertNull(restored[CategoryId("missing")])
    }
}
