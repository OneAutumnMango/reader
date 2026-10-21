package dev.reader.core.theme

import kotlinx.serialization.Serializable

@Serializable
enum class Justification {
    START,
    JUSTIFY,
    END,
}

/** A saveable, loadable set of reader typography and spacing options. */
@Serializable
data class ReaderTheme(
    val name: String,
    val fontFamily: String = "serif",
    val fontSizeSp: Float = 18f,
    val textColor: Long = 0xFF000000,
    val backgroundColor: Long = 0xFFFFFFFF,
    val lineSpacing: Float = 1.4f,
    val wordSpacingSp: Float = 0f,
    val letterSpacingSp: Float = 0f,
    val paragraphSpacingSp: Float = 12f,
    val justification: Justification = Justification.START,
) {
    companion object {
        val Default = ReaderTheme(name = "Default")
        val Sepia = ReaderTheme(
            name = "Sepia",
            textColor = 0xFF5B4636,
            backgroundColor = 0xFFF4ECD8,
        )
        /** AMOLED-friendly: true black background, so OLED screens turn those pixels off. */
        val Night = ReaderTheme(
            name = "Night",
            textColor = 0xFFFFFFFF,
            backgroundColor = 0xFF000000,
        )

        val builtIns = listOf(Default, Sepia, Night)
    }
}
