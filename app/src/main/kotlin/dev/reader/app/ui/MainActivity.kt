package dev.reader.app.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import dev.reader.app.ReaderApplication
import dev.reader.app.ui.appearance.AppPaletteFactory
import dev.reader.data.store.PreferencesFile

private const val HALF = 0.5f

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val graph = (application as ReaderApplication).graph

        setContent {
            val preferences by graph.settings.preferences.collectAsState(initial = PreferencesFile())
            val colorScheme = AppPaletteFactory.colorScheme(
                preferences.appTheme.seedColor,
                preferences.appTheme.mode,
                isSystemInDarkTheme(),
                preferences.appTheme.overrides,
            )
            val background = colorScheme.background.toArgb()
            val dark = colorScheme.background.luminance() < HALF
            DisposableEffect(background, dark) {
                val bars = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                window.setBackgroundDrawable(ColorDrawable(background))
                onDispose {}
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(color = colorScheme.background) {
                    ReaderApp(graph)
                }
            }
        }
    }
}
