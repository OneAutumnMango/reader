package dev.reader.core.theme

import kotlinx.serialization.Serializable

/** Controls how big book covers appear in the library grid. */
@Serializable
enum class LibraryLayout {
    SMALL,
    MEDIUM,
    LARGE,
    ;

    val gridColumnMinWidthDp: Int
        get() = when (this) {
            SMALL -> 90
            MEDIUM -> 120
            LARGE -> 160
        }
}
