@file:Suppress("TooManyFunctions") // small rules the Merge screen shares

package dev.reader.app.ui.sync

import dev.reader.core.sync.CategoriesValue
import dev.reader.core.sync.ItemKind
import dev.reader.core.sync.ItemValue
import dev.reader.core.sync.PositionValue
import dev.reader.core.sync.isBackward
import dev.reader.data.sync.ApplyResult
import dev.reader.data.sync.Pick
import dev.reader.data.sync.Question

enum class MergeFilter { ALL, POSITIONS, CATEGORIES }

data class ReviewSummary(val total: Int, val backwards: Int, val unfinished: Int, val losingCategory: Int)

fun filterQuestions(questions: List<Question>, filter: MergeFilter, search: String): List<Question> {
    val text = search.trim()
    return questions.filter {
        val categories = it.key.kind == ItemKind.CATEGORIES
        val kind = filter == MergeFilter.ALL || categories == (filter == MergeFilter.CATEGORIES)
        kind && it.title.contains(text, ignoreCase = true)
    }
}

/** The first choice is always this device's current value. */
fun keepMine(questions: List<Question>): List<Pick> = questions.map { Pick(it, it.choices.first().item.value) }

/** A device under two values (it has a conflict copy) has no single value, so that question stays pending. */
fun useDevice(questions: List<Question>, deviceId: String): List<Pick> = questions.mapNotNull { question ->
    question.choices.singleOrNull { deviceId in it.deviceIds }?.let { Pick(question, it.item.value) }
}

private val FURTHEST = nullsFirst(compareBy<PositionValue>({ it.chapter }, { it.finished }, { it.position }))

/** Ties go to the earlier choice, and so to this device. */
fun keepFurthest(questions: List<Question>): List<Pick> =
    questions.filter { it.key.kind != ItemKind.CATEGORIES }.map { question ->
        val values = question.choices.map { it.item.value }
        Pick(question, values.maxWith { a, b -> FURTHEST.compare(a as? PositionValue, b as? PositionValue) })
    }

internal fun categoryNames(value: ItemValue?): List<String> = (value as? CategoriesValue)?.names.orEmpty()

private fun matchName(name: String) = name.trim().lowercase()

internal fun distinctNames(names: List<String>): List<String> =
    names.filter { it.isNotBlank() }.distinctBy(::matchName)

/** The names in [names] that [other] does not have, ignoring case. */
internal fun namesMissingFrom(names: List<String>, other: List<String>): List<String> {
    val held = other.map(::matchName).toSet()
    return names.filter { matchName(it) !in held }
}

internal fun hasName(names: Collection<String>, name: String) = names.any { matchName(it) == matchName(name) }

fun combinedCategories(question: Question): CategoriesValue =
    CategoriesValue(distinctNames(question.choices.flatMap { categoryNames(it.item.value) }))

/** The engine refuses two or more categories while one-category mode is on. */
internal fun isPickable(value: ItemValue?, oneCategory: Boolean) = !oneCategory || categoryNames(value).size < 2

internal fun goesBackwards(pick: Pick) =
    pick.question.key.kind != ItemKind.CATEGORIES && isBackward(pick.question.local.value, pick.value)

internal fun becomesUnfinished(pick: Pick) =
    (pick.question.local.value as? PositionValue)?.finished == true &&
        (pick.value as? PositionValue)?.finished != true

internal fun losesCategory(pick: Pick) =
    namesMissingFrom(categoryNames(pick.question.local.value), categoryNames(pick.value)).isNotEmpty()

fun summarize(picks: List<Pick>) = ReviewSummary(
    total = picks.size,
    backwards = picks.count(::goesBackwards),
    unfinished = picks.count(::becomesUnfinished),
    losingCategory = picks.count(::losesCategory),
)

/** Every pick comes back stale when applying fails, so then they are worth keeping. */
internal fun nothingApplied(picked: Int, result: ApplyResult) =
    result.publishError != null && result.publishError != DISCARDED && result.stale.size >= picked

/** [picked] is the number of distinct items sent. Kept values are accepted without being listed as applied. */
internal fun describeApply(picked: Int, result: ApplyResult): String {
    val error = result.publishError
    if (error == DISCARDED) return error
    if (nothingApplied(picked, result)) return "Nothing was applied: $error"
    val accepted = picked - result.stale.size
    val parts = mutableListOf("Applied $accepted ${if (accepted == 1) "choice" else "choices"}.")
    if (result.stale.isNotEmpty()) {
        parts += "Not applied: ${result.stale.size}. Any still waiting are listed; check them and choose again."
    }
    error?.let { parts += "Not published: $it" }
    return parts.joinToString(" ")
}
