package dev.reader.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

private val CHOICE_SHAPE = RoundedCornerShape(8.dp)

/** One selectable row in a category picker: checkbox/radio, label, tinted background when selected. */
@Composable
internal fun CategoryChoiceRow(
    label: String,
    checked: Boolean,
    tag: String,
    single: Boolean = false,
    onClick: () -> Unit,
) {
    val background = if (checked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CHOICE_SHAPE)
            .background(background)
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (single) {
            RadioButton(selected = checked, onClick = null)
        } else {
            Checkbox(checked = checked, onCheckedChange = null)
        }
        Text(label, modifier = Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}
