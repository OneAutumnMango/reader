package dev.reader.core.reader

enum class KeepScreenAwake {
    NEVER,
    WHILE_READING,
    ALWAYS,
}

data class ReaderSettings(
    val readingMode: ReadingMode = ReadingMode.CONTINUOUS_SCROLL,
    val tapZones: TapZoneConfig = TapZoneConfig(),
    val selectableText: Boolean = true,
    val keepScreenAwake: KeepScreenAwake = KeepScreenAwake.WHILE_READING,
    val useBookStyling: Boolean = true,
    val customCss: String = "",
)
