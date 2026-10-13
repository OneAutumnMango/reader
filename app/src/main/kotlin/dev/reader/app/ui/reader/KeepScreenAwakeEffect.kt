package dev.reader.app.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import dev.reader.core.reader.KeepScreenAwake

/** Applies the keep-awake setting to the window while this composable is on screen. */
@Composable
fun KeepScreenAwakeEffect(setting: KeepScreenAwake) {
    val view = LocalView.current
    DisposableEffect(setting) {
        view.keepScreenOn = setting != KeepScreenAwake.NEVER
        onDispose { view.keepScreenOn = false }
    }
}
