package dev.reader.core.theme

import kotlinx.serialization.Serializable

/** Controls how big book covers appear in the library grid. */
@Serializable
enum class LibraryLayout {
    SMALL,
    MEDIUM,
    LARGE,
    ;

    val columns: Int
        get() = when (this) {
            SMALL -> 4
            MEDIUM -> 3
            LARGE -> 2
        }
}
