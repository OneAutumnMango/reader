package dev.reader.app.ui.appearance

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.reader.core.theme.AppThemeMode

private val PRESET_WIDTH = 72.dp
private val PRESET_HEIGHT = 96.dp
private val PRESET_SHAPE = RoundedCornerShape(12.dp)
private val ROW_SHAPE = RoundedCornerShape(4.dp)
private val SLIVER_SHAPE = RoundedCornerShape(2.dp)
private val SELECTED_BORDER_WIDTH = 2.dp
private val UNSELECTED_BORDER_WIDTH = 1.dp

/** A tiny mock page preview rendered in the seed colour's derived scheme: two blocks, a bar and an accent sliver. */
@Composable
fun ThemePreviewTile(
    seedColor: Long,
    mode: AppThemeMode,
    systemInDarkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = remember(seedColor, mode, systemInDarkTheme) {
        AppPaletteFactory.colorScheme(seedColor, mode, systemInDarkTheme)
    }
    Column(
        modifier = modifier
            .width(PRESET_WIDTH)
            .height(PRESET_HEIGHT)
            .clip(PRESET_SHAPE)
            .background(scheme.background)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp)
                    .clip(ROW_SHAPE)
                    .background(scheme.surfaceContainer),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp)
                    .clip(ROW_SHAPE)
                    .background(scheme.surfaceContainer),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(ROW_SHAPE)
                .background(scheme.surfaceContainer),
        )
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(4.dp)
                .clip(SLIVER_SHAPE)
                .background(scheme.primary),
        )
    }
}

/** A selectable preset card: a [ThemePreviewTile] in a bordered frame, tagged by its seed colour. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThemePresetCard(
    seedColor: Long,
    mode: AppThemeMode,
    systemInDarkTheme: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Theme preview",
    testTag: String = "seed-swatch-${"%08X".format(seedColor)}",
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .clip(PRESET_SHAPE)
            .border(
                width = if (selected) SELECTED_BORDER_WIDTH else UNSELECTED_BORDER_WIDTH,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = PRESET_SHAPE,
            )
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                this.contentDescription = contentDescription
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag(testTag),
    ) {
        ThemePreviewTile(seedColor = seedColor, mode = mode, systemInDarkTheme = systemInDarkTheme)
    }
}

/** The trailing "+" card that opens the custom theme editor. */
@Composable
fun AddCustomThemeCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(PRESET_WIDTH)
            .height(PRESET_HEIGHT)
            .clip(PRESET_SHAPE)
            .border(
                width = UNSELECTED_BORDER_WIDTH,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = PRESET_SHAPE,
            )
            .clickable(onClick = onClick)
            .testTag("add-custom-theme"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Add, contentDescription = "Create custom theme")
    }
}
