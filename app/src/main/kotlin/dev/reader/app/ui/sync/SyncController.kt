package dev.reader.app.ui.sync

import dev.reader.app.data.AppLog
import dev.reader.data.sync.ApplyResult
import dev.reader.data.sync.Change
import dev.reader.data.sync.Pick
import dev.reader.data.sync.SyncEngine
import dev.reader.data.sync.SyncFolder
import dev.reader.data.sync.SyncReport
import dev.reader.data.sync.SyncState
import dev.reader.data.sync.SyncStateStore
import dev.reader.data.sync.UndoResult
import dev.reader.data.sync.sanitizeDeviceName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

private const val UNNAMED_FOLDER = "Sync folder"
private const val DISCARDED = "Sync was reset while this was running. Sync again."

data class SyncUiState(
    val folderName: String? = null,
    /** This install's ID, to tell its conflict copies apart from other devices. */
    val deviceId: String = "",
    val deviceName: String = "",
    val running: Boolean = false,
    val report: SyncReport = SyncReport(),
    val questionCount: Int = 0,
    val lastChanges: List<Change> = emptyList(),
    val lastSyncAt: Long? = null,
    val error: String? = null,
)

/** What a finished operation changes on screen, given the state saved after it. */
private typealias Outcome = SyncUiState.(SyncState) -> SyncUiState

/** A new sync or push that fails must not leave an earlier run's results looking like its own. */
private val dropLastRun: SyncUiState.() -> SyncUiState = {
    copy(report = report.copy(applied = emptyList(), publishError = null))
}

/** An earlier publish error would contradict the new one. */
private val dropPublishError: SyncUiState.() -> SyncUiState = {
    copy(report = report.copy(publishError = null))
}

/**
 * Runs one sync operation at a time and shows the result; calls made while one is running are ignored.
 * Saved state is only read and changed through [engine], under its lock, so [reset] cannot be overwritten.
 */
class SyncController(
    private val engine: SyncEngine,
    @Suppress("UnusedParameter", "UNUSED_PARAMETER") store: SyncStateStore, // only touched through the engine
    private val openFolder: (String) -> SyncFolder?,
    private val folderName: (String) -> String?,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = mutableState.asStateFlow()

    /** Keeps reading the saved state and showing it in order. Never taken while holding the engine's lock. */
    private val showing = Mutex()

    /** Results of operations that started before the latest reset are not shown. */
    @Volatile private var resets = 0

    init {
        // Undispatched, so that this is first in line to show anything.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            showing.withLock { show { copy(questionCount = it.questionCount) } }
        }
    }

    fun setFolder(uri: String) = operation {
        engine.updateState { it.copy(folderUri = uri, questionCount = 0) }
        return@operation { copy(report = SyncReport(), questionCount = 0, error = null) }
    }

    fun setDeviceName(name: String) = operation {
        engine.updateState { it.copy(deviceName = sanitizeDeviceName(name)) }
        return@operation { copy(error = null) }
    }

    /** Never ignored. Returns once the saved state is reset, even if a sync had to finish first. */
    suspend fun reset() {
        // In our own scope, so a caller that goes away cannot stop the reset.
        scope.launch {
            val failure = attempt { resetAndRun {} }
            if (failure != null) mutableState.update { it.copy(error = failure) }
        }.join()
    }

    /**
     * Resets, then runs [block], with no publish, sync or apply in between: use it to restore a backup. [block]
     * must not call this controller or its engine. Throws if the reset or [block] does.
     */
    suspend fun <T> resetAndRun(block: suspend () -> T): T {
        var wasReset = false
        try {
            return engine.resetAndRun {
                // Counted under the engine's lock, so whoever reads the reset state sees the new count.
                resets++
                wasReset = true
                block()
            }
        } finally {
            if (wasReset) {
                withContext(NonCancellable) {
                    showing.withLock { show { copy(report = SyncReport(), questionCount = 0, error = null) } }
                }
            }
        }
    }

    fun syncNow(onDone: (SyncUiState) -> Unit = {}) = sync(publish = true, onDone)

    fun pullOnly(onDone: (SyncUiState) -> Unit = {}) = sync(publish = false, onDone)

    private fun sync(publish: Boolean, onDone: (SyncUiState) -> Unit) =
        operation({ finished, _ -> onDone(finished) }, dropLastRun) {
            val report = engine.sync(folder(), publish)
            return@operation {
                copy(report = report, questionCount = report.questions.size, error = report.publishError)
            }
        }

    fun pushOnly(onDone: (SyncUiState) -> Unit = {}) = operation({ finished, _ -> onDone(finished) }, dropLastRun) {
        engine.publish(folder())
        return@operation { copy(report = report.copy(publishError = null), error = null) }
    }

    /** Recalculates the open questions, keeping the last error on show. Does nothing without a folder. */
    fun check() = operation(onFailure = dropPublishError) {
        if (engine.state().folderUri == null) return@operation { this }
        val checked = engine.check(folder())
        return@operation {
            copy(
                report = checked.copy(applied = report.applied, publishError = report.publishError),
                questionCount = checked.questions.size,
            )
        }
    }

    /** If applying fails, [onDone] gets every pick back as stale, with the reason as the publish error. */
    fun apply(picks: List<Pick>, onDone: (ApplyResult) -> Unit) {
        var result: ApplyResult? = null
        // The ID on show when the picks were made.
        val deviceId = state.value.deviceId.ifEmpty { null }
        val done: (SyncUiState, String?) -> Unit = { _, failure ->
            val failed = { ApplyResult(emptyList(), picks.map { it.question }.distinctBy { it.key }, failure) }
            onDone(result?.let { if (failure == null) it else it.copy(publishError = failure) } ?: failed())
        }
        operation(done, dropPublishError) {
            val applied = engine.apply(folder(), picks, deviceId).also { result = it }
            val done = picks.map { it.question.key }.toSet() - applied.stale.map { it.key }.toSet()
            return@operation { saved ->
                copy(
                    report = report.copy(
                        questions = report.questions.filter { it.key !in done },
                        publishError = applied.publishError,
                    ),
                    questionCount = saved.questionCount,
                    error = applied.publishError,
                )
            }
        }
    }

    /** If undoing fails, [onDone] gets an empty result with the reason as the publish error. */
    fun undo(onDone: (UndoResult) -> Unit) {
        var result: UndoResult? = null
        val done: (SyncUiState, String?) -> Unit = { _, failure ->
            val undone = result?.let { if (failure == null) it else it.copy(publishError = failure) }
            onDone(undone ?: UndoResult(emptyList(), emptyList(), failure))
        }
        operation(done, dropPublishError) {
            val undone = engine.undo(folder()).also { result = it }
            return@operation {
                copy(
                    report = report.copy(applied = emptyList(), publishError = undone.publishError),
                    error = undone.publishError,
                )
            }
        }
    }

    private suspend fun folder(): SyncFolder {
        val uri = engine.state().folderUri ?: throw IOException("No sync folder is chosen")
        return withContext(Dispatchers.IO) { openFolder(uri) }
            ?: throw IOException("Sync folder is missing or permission was lost")
    }

    /**
     * Runs [block] unless an operation is already running. [onDone] also gets why it failed, if it did, or that a
     * reset discarded its result. [onFailure] removes what a failure makes out of date.
     */
    private fun operation(
        onDone: (SyncUiState, String?) -> Unit = { _, _ -> },
        onFailure: SyncUiState.() -> SyncUiState = { this },
        block: suspend () -> Outcome,
    ) {
        if (mutableState.getAndUpdate { it.copy(running = true) }.running) return
        val startedAt = resets
        scope.launch {
            var failure: String? = null
            var discarded = false
            var finished: SyncUiState
            try {
                var outcome: Outcome = { this }
                failure = attempt { outcome = block() }
                showing.withLock {
                    // Shown even after a failure, which may have saved part of its work.
                    show { saved ->
                        // Checked after reading [saved], so a reset state never gets an older result.
                        discarded = startedAt != resets
                        when {
                            discarded -> this
                            failure != null -> onFailure().copy(error = failure)
                            else -> outcome(saved)
                        }
                    }
                }
            } finally {
                finished = mutableState.updateAndGet { it.copy(running = false) }
            }
            if (discarded) onDone(finished.copy(error = DISCARDED), DISCARDED) else onDone(finished, failure)
        }
    }

    /** Shows the saved state, changed by [outcome]. Must be called while holding [showing]. */
    private suspend fun show(outcome: Outcome) {
        val failure = attempt {
            val saved = engine.state()
            val name = saved.folderUri?.let { uri ->
                withContext(Dispatchers.IO) { folderName(uri) } ?: UNNAMED_FOLDER
            }
            mutableState.update {
                it.copy(
                    folderName = name,
                    deviceId = saved.deviceId,
                    deviceName = saved.deviceName,
                    lastChanges = saved.lastChanges,
                    lastSyncAt = saved.lastSyncAt,
                ).outcome(saved)
            }
        }
        if (failure != null) mutableState.update { it.copy(error = failure) }
    }

    /** Returns why [block] failed, if it did. */
    private suspend fun attempt(block: suspend () -> Unit): String? = try {
        block()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) { // whatever fails is shown to the user rather than crashing the app
        AppLog.warn("Sync failed", "Sync", e)
        e.message ?: "Sync failed"
    }
}
