package dev.reader.app.ui.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.core.theme.AppTheme
import dev.reader.core.theme.AppThemeMode

private val SEED_PRESETS = listOf(
    0xFF6750A4L,
    0xFF2E7D32L,
    0xFF1565C0L,
    0xFFB71C1CL,
    0xFFEF6C00L,
)

@Composable
fun AppThemeEditor(theme: AppTheme, onChange: (AppTheme) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row {
            SEED_PRESETS.forEach { seed ->
                SeedSwatch(seed, selected = theme.seedColor == seed) { onChange(theme.copy(seedColor = seed)) }
            }
        }

        OutlinedTextField(
            value = "#%08X".format(theme.seedColor),
            onValueChange = { text ->
                text.removePrefix("#").toLongOrNull(16)?.let { onChange(theme.copy(seedColor = it)) }
            },
            label = { Text("Seed colour") },
        )

        Text("Theme mode")
        Row {
            AppThemeMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .testTag("app-theme-mode-${mode.name}")
                        .clickable { onChange(theme.copy(mode = mode)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = theme.mode == mode, onClick = null)
                    Text(mode.name)
                }
            }
        }
    }
}

private const val SWATCH_SIZE_DP = 32

@Composable
private fun SeedSwatch(seed: Long, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .testTag("seed-swatch-${"%08X".format(seed)}")
            .size(SWATCH_SIZE_DP.dp)
            .clip(CircleShape)
            .background(Color(seed))
            .clickable(onClick = onClick),
    ) {
        if (selected) Text("✓")
    }
}
