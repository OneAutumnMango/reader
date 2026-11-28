package dev.reader.app.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
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
import androidx.lifecycle.lifecycleScope
import dev.reader.app.ReaderApplication
import dev.reader.app.data.ContentFileCopier
import dev.reader.app.data.SyncPublishScheduler
import dev.reader.app.ui.appearance.AppPaletteFactory
import dev.reader.app.ui.reader.AutoScrollVolumeKeys
import dev.reader.data.store.PreferencesFile
import kotlinx.coroutines.launch

private const val HALF = 0.5f

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)

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

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) SyncPublishScheduler.schedule(this)
    }

    private val swallowedVolumeKeys = mutableSetOf<Int>()

    // Volume keys change the auto-scroll speed while it runs; otherwise they behave as usual.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val step = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> 1
            KeyEvent.KEYCODE_VOLUME_DOWN -> -1
            else -> return super.onKeyDown(keyCode, event)
        }
        val handler = AutoScrollVolumeKeys.onStep ?: return super.onKeyDown(keyCode, event)
        handler(step)
        swallowedVolumeKeys += keyCode
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // Only a key whose press we swallowed has its release swallowed too.
        return if (swallowedVolumeKeys.remove(keyCode)) true else super.onKeyUp(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        val data = intent.data ?: return
        if (intent.action != Intent.ACTION_VIEW) return
        lifecycleScope.launch {
            val file = ContentFileCopier.copyToCache(this@MainActivity, data) ?: return@launch
            val graph = (application as ReaderApplication).graph
            runCatching {
                graph.importer.importFile(file, pdfContext = this@MainActivity, originUri = data.toString())
            }.onSuccess {
                Toast.makeText(this@MainActivity, "Imported ${file.name}", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@MainActivity, "Couldn’t import ${file.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
