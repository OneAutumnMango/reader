package dev.reader.app.ui.sync

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.reader.app.ui.library.BookCover
import dev.reader.core.model.Book
import dev.reader.core.sync.AskReason
import dev.reader.core.sync.CategoriesValue
import dev.reader.core.sync.ItemKey
import dev.reader.core.sync.ItemKind
import dev.reader.core.sync.ItemValue
import dev.reader.core.sync.PositionValue
import dev.reader.core.sync.sameValue
import dev.reader.data.sync.ApplyResult
import dev.reader.data.sync.Pick
import dev.reader.data.sync.Question
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private const val PERCENT = 100
private const val SHORT_ID = 6
private const val THIS_DEVICE = "This device"
private const val ANOTHER_COPY = "$THIS_DEVICE (another copy)"
private const val NOT_ALLOWED = "Not allowed: one category per book is on"

/** A book in this library; [chapters] maps a chapter's order index to its title. */
data class MergeBook(val book: Book, val chapters: Map<Int, String>)

/** Picks are only staged here; nothing is written until [onApply], which only the Apply button calls. */
@Composable
fun MergeScreen(
    state: SyncUiState,
    oneCategory: Boolean,
    categoryNames: List<String>,
    checked: Boolean,
    loadBook: suspend (String) -> MergeBook?,
    onApply: (List<Pick>, (ApplyResult) -> Unit) -> Unit,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A new device ID means sync was reset, so earlier picks no longer apply.
    val merge = remember(state.deviceId) { MergeState() }
    val books = remember { mutableStateMapOf<String, MergeBook?>() }
    // Outside [MergeState]: a reset replaces that before the apply it discarded reports back.
    var message by remember { mutableStateOf<String?>(null) }
    val questions = state.report.questions
    val picks = questions.mapNotNull { merge.pickFor(it, oneCategory) }
    val devices = DeviceNames(state)

    LaunchedEffect(merge, questions, oneCategory) { merge.dropOutdated(questions, oneCategory) }

    if (merge.reviewing) {
        BackHandler { merge.reviewing = false }
        Review(
            picks,
            state.running,
            books,
            loadBook,
            onBack = { merge.reviewing = false },
            onApply = {
                val sent = picks
                onApply(sent) { result ->
                    // Only [result] is read: after a reset, the controller's state may be older than it.
                    if (!nothingApplied(sent.size, result)) sent.forEach { merge.staged.remove(it.question.key) }
                    message = describeApply(sent.size, result)
                    merge.reviewing = false
                    onCheck()
                }
            },
            modifier = modifier,
        )
    } else {
        val context = CardContext(merge, state.running, oneCategory, devices, books, loadBook)
        Column(modifier.fillMaxSize()) {
            QuestionList(context, state, checked, onCheck, Modifier.weight(1f))
            Button(
                onClick = {
                    merge.notice = null
                    merge.reviewing = true
                },
                enabled = picks.isNotEmpty() && !state.running,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) { Text("Review and apply (${picks.size})") }
        }
    }

    questions.find { it.key == merge.choosing }?.let { question ->
        ManualDialog(question, categoryNames, oneCategory, merge, state.running)
    }
    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
}

private class MergeState {
    val staged = mutableStateMapOf<ItemKey, Pick>()
    val expanded = mutableStateMapOf<String, Boolean>()
    var filter by mutableStateOf(MergeFilter.ALL)
    var search by mutableStateOf("")
    var reviewing by mutableStateOf(false)
    var choosing by mutableStateOf<ItemKey?>(null)
    var notice by mutableStateOf<String?>(null)

    /** A pick counts only while its question is exactly as it was when picked. */
    fun pickFor(question: Question, oneCategory: Boolean): Pick? =
        staged[question.key]?.takeIf { it.question == question && isPickable(it.value, oneCategory) }

    fun pick(question: Question, value: ItemValue?) {
        staged[question.key] = Pick(question, value)
    }

    /** Stages [picks], leaving the other [listed] items as they were. */
    fun pickAll(listed: List<Question>, picks: List<Pick>, oneCategory: Boolean) {
        val allowed = picks.filter { isPickable(it.value, oneCategory) }
        allowed.forEach { staged[it.question.key] = it }
        notice = "Picked ${allowed.size} of ${listed.size} listed."
    }

    fun dropOutdated(questions: List<Question>, oneCategory: Boolean) {
        val current = questions.mapNotNull { pickFor(it, oneCategory)?.question?.key }.toSet()
        val outdated = staged.keys.filter { it !in current }
        if (outdated.isEmpty()) return
        outdated.forEach { staged.remove(it) }
        notice = if (outdated.size == 1) {
            "1 pick was dropped because the item changed."
        } else {
            "${outdated.size} picks were dropped because the items changed."
        }
    }
}

/** Device names come from other devices: ones that repeat or pose as this device also show part of the ID. */
private class DeviceNames(private val state: SyncUiState) {
    private val shared: Set<String> = state.report.questions
        .flatMap { question -> question.choices.flatMap { it.deviceIds } }
        .toSet()
        .filter { it != state.deviceId }
        .groupBy { base(it).lowercase() }
        .filterValues { it.size > 1 }
        .keys

    val localId = state.deviceId

    private fun base(id: String) = state.report.deviceNames[id] ?: "another device"

    fun name(id: String): String {
        if (id == localId) return ANOTHER_COPY
        val name = base(id)
        val unclear = name.lowercase() in shared || name.contains(THIS_DEVICE, ignoreCase = true)
        // The ID goes first so that a long name cannot push it out of sight.
        return if (unclear) "[${id.take(SHORT_ID)}] $name" else name
    }

    /** Who holds the choice at [index]. Only the first choice is this device. */
    fun holders(question: Question, index: Int): String {
        val ids = question.choices[index].deviceIds
        val names = if (index == 0) listOf(THIS_DEVICE) + (ids - localId).map(::name) else ids.map(::name)
        return names.joinToString(limit = 10)
    }

    fun peers(questions: List<Question>): List<String> =
        questions.flatMap { question -> question.choices.flatMap { it.deviceIds } }.distinct() - localId
}

private class CardContext(
    val merge: MergeState,
    val running: Boolean,
    val oneCategory: Boolean,
    val devices: DeviceNames,
    val books: MutableMap<String, MergeBook?>,
    val loadBook: suspend (String) -> MergeBook?,
)

@Composable
private fun QuestionList(
    context: CardContext,
    state: SyncUiState,
    checked: Boolean,
    onCheck: () -> Unit,
    modifier: Modifier,
) {
    val merge = context.merge
    val questions = state.report.questions
    val listed = filterQuestions(questions, merge.filter, merge.search)
    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("merge-list"),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.running) item { Progress() }
        state.error?.let { error ->
            item {
                Column {
                    Bounded(error, lines = 4, error = true)
                    OutlinedButton(onClick = onCheck, enabled = !state.running) { Text("Check again") }
                }
            }
        }
        merge.notice?.let { item { Text(it) } }
        if (questions.isEmpty()) {
            if (state.running || !checked) {
                item { Text("Checking for differences…") }
            } else if (state.error == null) {
                item { Text("Nothing to merge.") }
            }
        } else {
            item { Filters(merge) }
            item { BulkActions(context, listed) }
            if (listed.isEmpty()) item { Text("Nothing matches this filter.") }
            listed.groupBy { it.key.bookHash }.forEach { (hash, own) -> book(context, hash, own) }
        }
    }
}

private fun LazyListScope.book(context: CardContext, hash: String, questions: List<Question>) {
    item(key = "book $hash") { BookHeader(context, hash, questions.first().title) }
    val (categories, positions) = questions.partition { it.key.kind == ItemKind.CATEGORIES }
    val grouped = positions.size > 1
    if (grouped) item(key = "group $hash") { Group(context, hash, positions) }
    val shown = if (grouped && context.merge.expanded[hash] != true) categories else positions + categories
    items(shown, key = { "${it.key.kind} ${it.key.chapter} ${it.key.bookHash}" }) { QuestionCard(context, it) }
}

@Composable
private fun rememberBook(
    hash: String,
    books: MutableMap<String, MergeBook?>,
    loadBook: suspend (String) -> MergeBook?,
): MergeBook? {
    LaunchedEffect(hash) { if (hash !in books) books[hash] = loadBook(hash) }
    return books[hash]
}

@Composable
private fun BookHeader(context: CardContext, hash: String, title: String) {
    val book = rememberBook(hash, context.books, context.loadBook)
    Column {
        HorizontalDivider()
        Row(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            book?.let { BookCover(it.book, Modifier.size(width = 48.dp, height = 68.dp)) }
            Text(
                title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
    }
}

@Composable
private fun Filters(merge: MergeState) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MergeFilter.entries.forEach { filter ->
                FilterChip(
                    selected = merge.filter == filter,
                    onClick = { merge.filter = filter },
                    label = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        OutlinedTextField(
            value = merge.search,
            onValueChange = { merge.search = it },
            label = { Text("Search by title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BulkActions(context: CardContext, listed: List<Question>) {
    fun stage(picks: List<Pick>) = context.merge.pickAll(listed, picks, context.oneCategory)
    val enabled = !context.running && listed.isNotEmpty()
    Column {
        Action("Keep all from this device", enabled) { stage(keepMine(listed)) }
        context.devices.peers(listed).forEach { id ->
            Action("Use all from ${context.devices.name(id)}", enabled) { stage(useDevice(listed, id)) }
        }
        if (listed.any { it.key.kind != ItemKind.CATEGORIES }) {
            Action("Keep furthest", enabled) { stage(keepFurthest(listed)) }
        }
    }
}

@Composable
private fun Action(text: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A book's position and chapter differences, which can be answered together. */
@Composable
private fun Group(context: CardContext, hash: String, questions: List<Question>) {
    val merge = context.merge
    val picked = questions.count { merge.pickFor(it, context.oneCategory) != null }
    val expanded = merge.expanded[hash] == true
    fun stage(picks: List<Pick>) = merge.pickAll(questions, picks, context.oneCategory)
    val enabled = !context.running
    Column {
        Text("Position differences: ${questions.size}. Picked: $picked.")
        Action("Keep this device for this book", enabled) { stage(keepMine(questions)) }
        context.devices.peers(questions).forEach { id ->
            Action("Use ${context.devices.name(id)} for this book", enabled) { stage(useDevice(questions, id)) }
        }
        Action("Keep furthest for this book", enabled) { stage(keepFurthest(questions)) }
        TextButton(onClick = { questions.forEach { merge.staged.remove(it.key) } }, enabled = enabled) {
            Text("Decide later for this book")
        }
        TextButton(onClick = { merge.expanded[hash] = !expanded }) {
            Text(if (expanded) "Hide items" else "Show each item")
        }
    }
}

@Composable
private fun QuestionCard(context: CardContext, question: Question) {
    val merge = context.merge
    val book = rememberBook(question.key.bookHash, context.books, context.loadBook)
    val picked = merge.pickFor(question, context.oneCategory)
    val localNames = categoryNames(question.local.value)
    val chosen = picked?.let { pick -> question.choices.indexOfFirst { it.item.value == pick.value } }
    Column {
        Subject(question, book)
        Detail("Why: ${describeReason(question.reason)}")
        question.choices.forEachIndexed { index, choice ->
            val value = choice.item.value
            val allowed = isPickable(value, context.oneCategory)
            val lines = describeChoice(value, localNames, book) + listOfNotNull(NOT_ALLOWED.takeUnless { allowed })
            Option(
                context.devices.holders(question, index),
                lines,
                selected = chosen == index,
                enabled = allowed && !context.running,
            ) { merge.pick(question, value) }
        }
        if (question.key.kind == ItemKind.CATEGORIES) {
            val combined = combinedCategories(question)
            val combine = isPickable(combined, context.oneCategory)
            val usingCombined = combine && chosen == -1 && picked.value == combined
            if (combine) {
                Option("Combine", describeChoice(combined, localNames, book), usingCombined, !context.running) {
                    merge.pick(question, combined)
                }
            }
            val manual = chosen == -1 && !usingCombined
            Option(
                "Choose manually",
                if (manual) describeChoice(picked.value, localNames, book) else emptyList(),
                manual,
                !context.running,
            ) { merge.choosing = question.key }
        }
        Option("Decide later", emptyList(), picked == null, !context.running) { merge.staged.remove(question.key) }
    }
}

/** What differs: the item, and for a chapter its title. */
@Composable
private fun Subject(question: Question, book: MergeBook?) {
    val title = book?.chapters?.get(question.key.chapter)
    Bounded(describeItem(question.key) + title?.let { ": $it" }.orEmpty())
}

@Composable
private fun Option(title: String, lines: List<String>, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column {
            Bounded(title, lines = 3)
            lines.forEach { Detail(it) }
        }
    }
}

private fun describeReason(reason: AskReason) = when (reason) {
    AskReason.BACKWARDS -> "Goes backwards"
    AskReason.REMOVES_CATEGORY -> "Removes a category"
    AskReason.SEPARATE -> "Changed separately"
    AskReason.FIRST_CONTACT -> "First sync"
    AskReason.ONE_CATEGORY -> "One category per book is on"
    AskReason.SAME_VERSION -> "Same change history, different values"
}

private fun describePosition(value: PositionValue?): String {
    if (value == null) return describeValue(null)
    val finished = if (value.finished) "finished" else "not finished"
    return "Ch. ${value.chapter + 1}, ${(value.position * PERCENT).roundToInt()}%, $finished"
}

/** Added and removed categories, compared with this device's [localNames]. */
private fun categoryChanges(value: ItemValue?, localNames: List<String>): List<String> {
    val names = categoryNames(value)
    val added = namesMissingFrom(names, localNames)
    val removed = namesMissingFrom(localNames, names)
    return listOfNotNull(
        added.takeIf { it.isNotEmpty() }?.let { "Adds: ${it.joinToString(limit = 20)}" },
        removed.takeIf { it.isNotEmpty() }?.let { "Removes: ${it.joinToString(limit = 20)}" },
    )
}

private fun describeChoice(value: ItemValue?, localNames: List<String>, book: MergeBook?): List<String> =
    when (value) {
        is CategoriesValue -> listOf(describeValue(value)) + categoryChanges(value, localNames)
        is PositionValue -> listOfNotNull(
            describePosition(value),
            book?.chapters?.get(value.chapter),
            value.updatedAt.takeIf { it > 0 }?.let {
                "Changed ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))}"
            },
        )
        null -> listOf(describeValue(null))
    }

@Composable
private fun ManualDialog(
    question: Question,
    localNames: List<String>,
    oneCategory: Boolean,
    merge: MergeState,
    running: Boolean,
) {
    val names = distinctNames(question.choices.flatMap { categoryNames(it.item.value) } + localNames)
    var ticked by remember(question) {
        val start = merge.pickFor(question, oneCategory)?.value ?: question.local.value
        mutableStateOf(if (isPickable(start, oneCategory)) categoryNames(start) else emptyList())
    }
    AlertDialog(
        onDismissRequest = { merge.choosing = null },
        title = { Text(if (oneCategory) "Choose one category" else "Choose categories") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(names) { name ->
                    val on = hasName(ticked, name)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = on, role = Role.Checkbox) { tick ->
                                ticked = when {
                                    !tick -> namesMissingFrom(ticked, listOf(name))
                                    oneCategory -> listOf(name)
                                    else -> ticked + name
                                }
                            }
                            .heightIn(min = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Bounded(name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    merge.pick(question, CategoriesValue(ticked))
                    merge.choosing = null
                },
                enabled = !running,
            ) { Text("Use these") }
        },
        dismissButton = { TextButton(onClick = { merge.choosing = null }) { Text("Cancel") } },
    )
}

@Composable
private fun Review(
    picks: List<Pick>,
    running: Boolean,
    books: MutableMap<String, MergeBook?>,
    loadBook: suspend (String) -> MergeBook?,
    onBack: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier,
) {
    val summary = summarize(picks)
    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column {
                    Text("Choices to apply: ${summary.total}", style = MaterialTheme.typography.titleMedium)
                    Text("Go backwards: ${summary.backwards}")
                    Text("Become unfinished: ${summary.unfinished}")
                    Text("Lose a category: ${summary.losingCategory}")
                }
            }
            items(picks) { ReviewItem(it, rememberBook(it.question.key.bookHash, books, loadBook)) }
        }
        if (running) Progress()
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(onClick = onApply, enabled = picks.isNotEmpty() && !running, modifier = Modifier.weight(1f)) {
                Text("Apply")
            }
        }
    }
}

@Composable
private fun ReviewItem(pick: Pick, book: MergeBook?) {
    val before = pick.question.local.value
    val categories = pick.question.key.kind == ItemKind.CATEGORIES
    fun describe(value: ItemValue?): String {
        if (categories) return describeValue(value)
        val position = value as? PositionValue
        return describePosition(position) + book?.chapters?.get(position?.chapter)?.let { " ($it)" }.orEmpty()
    }
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        HorizontalDivider()
        Bounded(pick.question.title)
        Subject(pick.question, book)
        // sameValue ignores jitter and spelling, which is what the engine treats as keeping the value.
        if (sameValue(before, pick.value)) {
            Detail("Keeps this device's value: ${describe(before)}")
        } else {
            Detail("Was: ${describe(before)}")
            Detail("Now: ${describe(pick.value)}")
            if (categories) categoryChanges(pick.value, categoryNames(before)).forEach { Detail(it) }
            val warnings = listOfNotNull(
                "Goes backwards".takeIf { goesBackwards(pick) },
                "Becomes unfinished".takeIf { becomesUnfinished(pick) },
                "Loses a category".takeIf { losesCategory(pick) },
            )
            if (warnings.isNotEmpty()) Bounded(warnings.joinToString(), error = true)
        }
    }
}

@Composable
private fun Progress() {
    LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Syncing" })
}
