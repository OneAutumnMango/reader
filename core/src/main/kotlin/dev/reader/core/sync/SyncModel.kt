package dev.reader.core.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Change count per device ID. A missing ID counts as zero. */
typealias Version = Map<String, Long>

/** Highest change count; counting stops here so a hostile file cannot make it overflow. */
const val MAX_VERSION_COUNT = 1L shl 48

@Serializable
enum class ItemKind { BOOK_POSITION, CHAPTER, CATEGORIES }

/** [chapter] is used only by [ItemKind.CHAPTER]. */
@Serializable
data class ItemKey(val bookHash: String, val kind: ItemKind, val chapter: Int = -1)

@Serializable
sealed interface ItemValue

@Serializable
@SerialName("position")
data class PositionValue(val chapter: Int, val position: Float, val finished: Boolean, val updatedAt: Long) : ItemValue

@Serializable
@SerialName("categories")
data class CategoriesValue(val names: List<String>) : ItemValue

/** [rewound] marks an unresolved backward move, which blocks automatic resolution. */
@Serializable
data class Versioned(val value: ItemValue?, val version: Version = emptyMap(), val rewound: Boolean = false)

data class Candidate(val deviceId: String, val item: Versioned)

enum class AskReason { BACKWARDS, REMOVES_CATEGORY, SEPARATE, FIRST_CONTACT, ONE_CATEGORY, SAME_VERSION }

/** One distinct value and the devices holding it, the local device first. */
data class Choice(val item: Versioned, val deviceIds: List<String>)

sealed interface Decision {
    data class Keep(val item: Versioned) : Decision
    data class Apply(val item: Versioned, val sourceDeviceId: String) : Decision
    data class Ask(val reason: AskReason, val choices: List<Choice>) : Decision
}
