package dev.reader.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.reader.app.ReaderApplication
import dev.reader.app.ui.appearance.AppPaletteFactory
import dev.reader.data.store.PreferencesFile

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
            )

            MaterialTheme(colorScheme = colorScheme) {
                Surface {
                    ReaderApp(graph)
                }
            }
        }
    }
}
