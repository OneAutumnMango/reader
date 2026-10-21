package dev.reader.data.store

import dev.reader.core.stats.GraphAxis
import dev.reader.core.stats.GraphGrouping
import dev.reader.core.stats.GraphMetric
import dev.reader.core.stats.GraphPlot
import dev.reader.core.stats.GraphRange
import dev.reader.core.stats.StatsGraph
import dev.reader.core.theme.AppThemeMode
import dev.reader.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class ReaderStoresTest {

    private val directory = "/reader-preferences-${UUID.randomUUID()}".toPath()
    private val fileSystem = FakeFileSystem().also { it.createDirectories(directory) }

    private fun stores(scope: CoroutineScope) = ReaderStores(fileSystem, directory, scope)

    @Test
    fun `preferences survive a restart`() = runTest {
        val first = CoroutineScope(Job() + UnconfinedTestDispatcher(testScheduler))
        stores(first).preferences.updateData {
            it.copy(
                activeThemePresetName = "Sepia",
                rootFolders = listOf("content://library/tree/ABCD-1234%3ABooks"),
                rootFolderNames = mapOf("content://library/tree/ABCD-1234%3ABooks" to "Books"),
            )
        }
        first.cancel()

        val second = CoroutineScope(Job() + UnconfinedTestDispatcher(testScheduler))
        val preferences = stores(second).preferences.data.first()
        assertEquals("Sepia", preferences.activeThemePresetName)
        assertEquals(
            mapOf("content://library/tree/ABCD-1234%3ABooks" to "Books"),
            preferences.rootFolderNames,
        )
        second.cancel()
    }

    @Test
    fun `defaults apply until changed`() = runTest {
        val preferences = stores(backgroundScope).preferences.data.first()
        assertEquals(AppThemeMode.SYSTEM, preferences.appTheme.mode)
        assertEquals(2, preferences.themePresets.size)
    }

    @Test
    fun `a corrupt file falls back to the default instead of crashing`() = runTest {
        fileSystem.sink(directory / "preferences.json").buffer().use { it.writeUtf8("{ not json") }
        assertEquals("Default", stores(backgroundScope).preferences.data.first().activeThemePresetName)
    }

    @Test
    fun `unknown fields from a newer version are ignored`() = runTest {
        fileSystem.sink(directory / "preferences.json").buffer()
            .use { it.writeUtf8("""{"activeThemePresetName":"Night","somethingNew":42}""") }
        assertEquals("Night", stores(backgroundScope).preferences.data.first().activeThemePresetName)
    }

    @Test
    fun `preferences written by an older version default saved graphs to empty`() = runTest {
        fileSystem.sink(directory / "preferences.json").buffer()
            .use { it.writeUtf8("""{"activeThemePresetName":"Night"}""") }

        val preferences = stores(backgroundScope).preferences.data.first()
        assertEquals(emptyList(), preferences.statsGraphs)
        assertEquals(emptyMap(), preferences.rootFolderNames)
        assertEquals(false, preferences.singleCategoryPerBook)
        assertEquals(false, preferences.automaticBackupsEnabled)
        assertEquals(null, preferences.automaticBackupFolderUri)
    }

    @Test
    fun `saved graphs can be replaced and deleted without changing other preferences`() = runTest {
        val firstScope = CoroutineScope(Job() + UnconfinedTestDispatcher(testScheduler))
        val firstRepository = SettingsRepository(stores(firstScope))
        val firstGraph = StatsGraph(
            id = "reading-time",
            title = "Reading time",
            plot = GraphPlot.AREA,
            xAxis = GraphAxis.BOOK,
            metrics = listOf(GraphMetric.READING_MINUTES, GraphMetric.SESSIONS),
            grouping = GraphGrouping.BOOK,
            bookIds = setOf("book-1", "book-2"),
            range = GraphRange.LAST_30_DAYS,
            independentScales = true,
            splitByBook = true,
        )
        val secondGraph = StatsGraph(id = "words", title = "Words", metrics = listOf(GraphMetric.WORDS))

        firstRepository.update {
            it.copy(
                activeThemePresetName = "Night",
                rootFolders = listOf("content://library"),
                statsGraphs = listOf(firstGraph, secondGraph),
                singleCategoryPerBook = true,
                automaticBackupsEnabled = true,
                automaticBackupFolderUri = "content://tree/backups",
                automaticBackupFrequency = AutomaticBackupFrequency.WEEKLY,
                automaticBackupIncludeBooks = true,
            )
        }
        assertEquals(listOf(firstGraph, secondGraph), firstRepository.preferences.first().statsGraphs)
        firstScope.cancel()

        val secondScope = CoroutineScope(Job() + UnconfinedTestDispatcher(testScheduler))
        val secondRepository = SettingsRepository(stores(secondScope))
        val afterRestart = secondRepository.preferences.first()
        assertEquals(listOf(firstGraph, secondGraph), afterRestart.statsGraphs)
        assertEquals("Night", afterRestart.activeThemePresetName)
        assertEquals(listOf("content://library"), afterRestart.rootFolders)
        assertEquals(true, afterRestart.singleCategoryPerBook)
        assertEquals(true, afterRestart.automaticBackupsEnabled)
        assertEquals("content://tree/backups", afterRestart.automaticBackupFolderUri)
        assertEquals(AutomaticBackupFrequency.WEEKLY, afterRestart.automaticBackupFrequency)
        assertEquals(true, afterRestart.automaticBackupIncludeBooks)

        val updatedGraph = firstGraph.copy(title = "Reading time by book")
        secondRepository.update { preferences ->
            preferences.copy(
                statsGraphs = preferences.statsGraphs.map { if (it.id == firstGraph.id) updatedGraph else it },
            )
        }
        assertEquals(listOf(updatedGraph, secondGraph), secondRepository.preferences.first().statsGraphs)

        secondRepository.update { preferences ->
            preferences.copy(statsGraphs = preferences.statsGraphs.filterNot { it.id == updatedGraph.id })
        }
        val afterDelete = secondRepository.preferences.first()
        assertEquals(listOf(secondGraph), afterDelete.statsGraphs)
        assertEquals("Night", afterDelete.activeThemePresetName)
        assertEquals(listOf("content://library"), afterDelete.rootFolders)
        secondScope.cancel()
    }
}
