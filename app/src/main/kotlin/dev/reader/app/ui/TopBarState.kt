package dev.reader.app.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** What the current screen wants in the app's top bar, overriding the destination's default title. */
class TopBarState {
    var title by mutableStateOf<String?>(null)
    var actions by mutableStateOf<(@Composable RowScope.() -> Unit)?>(null)
}

val LocalTopBar = staticCompositionLocalOf<TopBarState?> { null }

/** Puts [title] and [actions] in the top bar while this screen is shown. */
@Composable
fun ScreenTopBar(title: String?, actions: (@Composable RowScope.() -> Unit)? = null) {
    val bar = LocalTopBar.current ?: return
    val currentActions by rememberUpdatedState(actions)
    DisposableEffect(bar) {
        bar.actions = { currentActions?.invoke(this) }
        onDispose {
            bar.title = null
            bar.actions = null
        }
    }
    SideEffect { bar.title = title }
}
