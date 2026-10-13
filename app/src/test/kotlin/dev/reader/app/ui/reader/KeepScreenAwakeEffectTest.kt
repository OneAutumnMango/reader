package dev.reader.app.ui.reader

import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.reader.core.reader.KeepScreenAwake
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class KeepScreenAwakeEffectTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `while-reading keeps the screen on`() {
        var view: android.view.View? = null
        composeRule.setContent {
            view = LocalView.current
            KeepScreenAwakeEffect(KeepScreenAwake.WHILE_READING)
        }

        composeRule.waitForIdle()
        assertTrue(view!!.keepScreenOn)
    }

    @Test
    fun `never leaves the screen timeout as normal`() {
        var view: android.view.View? = null
        composeRule.setContent {
            view = LocalView.current
            KeepScreenAwakeEffect(KeepScreenAwake.NEVER)
        }

        composeRule.waitForIdle()
        assertFalse(view!!.keepScreenOn)
    }
}
