package dev.reader.core.sync

import kotlin.math.abs

private const val POSITION_TOLERANCE = 0.01f

private fun categoryNames(value: ItemValue?): Set<String> =
    (value as? CategoriesValue)?.names.orEmpty().map { it.trim().lowercase() }.toSet()

/** Equality for sync purposes: change times, position jitter and category spelling are ignored. */
fun sameValue(a: ItemValue?, b: ItemValue?): Boolean = when {
    a is PositionValue && b is PositionValue ->
        a.chapter == b.chapter && a.finished == b.finished && abs(a.position - b.position) < POSITION_TOLERANCE
    a is CategoriesValue && b is CategoriesValue -> categoryNames(a) == categoryNames(b)
    else -> a == null && b == null
}

/** Whether moving a position from [from] to [to] loses progress. A later chapter is always forward. */
fun isBackward(from: ItemValue?, to: ItemValue?): Boolean {
    if (from == null) return false
    if (to == null) return true
    if (from !is PositionValue || to !is PositionValue) return false
    if (to.chapter != from.chapter) return to.chapter < from.chapter
    return from.position - to.position >= POSITION_TOLERANCE || (from.finished && !to.finished)
}

fun isDefaultValue(kind: ItemKind, value: ItemValue?): Boolean = value == null || when (kind) {
    ItemKind.BOOK_POSITION -> false
    ItemKind.CHAPTER -> value is PositionValue && !value.finished && value.position == 0f
    ItemKind.CATEGORIES -> value is CategoriesValue && value.names.isEmpty()
}

private fun Version.count(deviceId: String): Long = this[deviceId] ?: 0

private fun Version.bumped(deviceId: String): Version = this + (deviceId to count(deviceId) + 1)

private fun maxVersion(versions: List<Version>): Version =
    versions.flatMap { it.keys }.toSet().associateWith { id -> versions.maxOf { it.count(id) } }

private fun covers(a: Version, b: Version): Boolean = b.keys.all { a.count(it) >= b.count(it) }

private fun isLater(a: Version, b: Version): Boolean = covers(a, b) && !covers(b, a)

private fun sameVersion(a: Version, b: Version): Boolean = covers(a, b) && covers(b, a)

/** Records the local [current] value, counting a change only if it differs from [previous]. */
fun record(kind: ItemKind, previous: Versioned?, current: ItemValue?, deviceId: String): Versioned {
    if (previous == null) {
        val version: Version = if (isDefaultValue(kind, current)) emptyMap() else mapOf(deviceId to 1L)
        return Versioned(current, version)
    }
    if (sameValue(previous.value, current)) return previous
    return Versioned(current, previous.version.bumped(deviceId), previous.rewound || isBackward(previous.value, current))
}

private class Group(val members: List<Candidate>, value: ItemValue?) {
    val item = Versioned(value, maxVersion(members.map { it.item.version }), members.any { it.item.rewound })
    val deviceIds = members.map { it.deviceId }.distinct()
    val updatedAt = members.maxOf { (it.item.value as? PositionValue)?.updatedAt ?: Long.MIN_VALUE }
}

fun decide(localId: String, local: Versioned, others: List<Candidate>, oneCategory: Boolean): Decision {
    val localCandidate = Candidate(localId, local)
    val all = listOf(localCandidate) + others
    val live = all.filter { c -> all.none { isLater(it.item.version, c.item.version) } }
    // With no changes counted anywhere, every value is some form of untouched, so there is nothing to ask.
    if (live.all { c -> c.item.version.values.all { it == 0L } }) return Decision.Keep(local)

    // The local candidate is first, so when it survives its group is first and keeps the local value.
    val buckets = mutableListOf<MutableList<Candidate>>()
    for (candidate in live) {
        val bucket = buckets.find { sameValue(it.first().item.value, candidate.item.value) }
        if (bucket != null) bucket.add(candidate) else buckets.add(mutableListOf(candidate))
    }
    // Within the tolerance several buckets can match local; only the first takes the local value.
    val localIndex = buckets.indexOfFirst { sameValue(local.value, it.first().item.value) }
    val groups = buckets.mapIndexed { index, members ->
        Group(members, if (index == localIndex) local.value else members.first().item.value)
    }
    val localGroup = groups.getOrNull(localIndex)

    fun ask(reason: AskReason): Decision.Ask {
        // The local value is always offered, even when a later version has superseded it.
        val first = if (localGroup == null) {
            Choice(local, listOf(localId))
        } else {
            Choice(localGroup.item, (listOf(localId) + localGroup.deviceIds).distinct())
        }
        return Decision.Ask(reason, listOf(first) + groups.filter { it !== localGroup }.map { Choice(it.item, it.deviceIds) })
    }

    val sameVersionConflict = live.any { a ->
        live.any { b -> sameVersion(a.item.version, b.item.version) && !sameValue(a.item.value, b.item.value) }
    }
    if (sameVersionConflict) return ask(AskReason.SAME_VERSION)

    val merged = maxVersion(groups.map { it.item.version })
    fun apply(group: Group) = Decision.Apply(Versioned(group.item.value, merged), group.deviceIds.first())

    if (groups.size == 1) {
        val only = groups.single()
        if (only === localGroup) return Decision.Keep(only.item)
        // Here the local value was dropped, so the one remaining version is later than local.
        val value = only.item.value
        if (local.value is CategoriesValue || value is CategoriesValue) {
            val result = categoryNames(value)
            if (!result.containsAll(categoryNames(local.value))) return ask(AskReason.REMOVES_CATEGORY)
            if (oneCategory && result.size >= 2) return ask(AskReason.ONE_CATEGORY)
        } else if (isBackward(local.value, value)) {
            return ask(AskReason.BACKWARDS)
        }
        return apply(only)
    }

    val sharedHistory = groups.flatMap { it.item.version.keys }.any { id -> groups.all { it.item.version.count(id) > 0 } }
    if (groups.any { it.item.value is CategoriesValue }) return ask(AskReason.SEPARATE)
    if (!sharedHistory) return ask(AskReason.FIRST_CONTACT)

    val newest = groups.maxBy { it.updatedAt }
    val further = groups.filter { it !== newest }.all {
        !isBackward(it.item.value, newest.item.value) && isBackward(newest.item.value, it.item.value)
    }
    if (!further || groups.any { it.item.rewound }) return ask(AskReason.SEPARATE)
    if (newest === localGroup) return Decision.Keep(Versioned(newest.item.value, merged))
    // A superseded local value took no part above, but must still never be moved backwards silently.
    if (isBackward(local.value, newest.item.value)) return ask(AskReason.BACKWARDS)
    return apply(newest)
}

/** The version recorded after applying or choosing [chosen]; a [manual] choice is later than every input. */
fun resolve(chosen: Versioned, all: List<Versioned>, deviceId: String, manual: Boolean): Versioned {
    val version = maxVersion(all.map { it.version } + chosen.version)
    return Versioned(chosen.value, if (manual) version.bumped(deviceId) else version)
}
