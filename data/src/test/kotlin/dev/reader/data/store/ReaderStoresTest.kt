package dev.reader.data.store

import dev.reader.core.theme.AppThemeMode
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
import kotlin.test.assertEquals

class ReaderStoresTest {

    private val directory = "/reader-preferences".toPath()
    private val fileSystem = FakeFileSystem().also { it.createDirectories(directory) }

    private fun stores(scope: CoroutineScope) = ReaderStores(fileSystem, directory, scope)

    @Test
    fun `preferences survive a restart`() = runTest {
        val first = CoroutineScope(Job() + UnconfinedTestDispatcher(testScheduler))
        stores(first).preferences.updateData { it.copy(activeThemePresetName = "Sepia") }
        first.cancel()

        val second = CoroutineScope(Job() + UnconfinedTestDispatcher(testScheduler))
        assertEquals("Sepia", stores(second).preferences.data.first().activeThemePresetName)
        second.cancel()
    }

    @Test
    fun `defaults apply until changed`() = runTest {
        val preferences = stores(backgroundScope).preferences.data.first()
        assertEquals(AppThemeMode.SYSTEM, preferences.appTheme.mode)
        assertEquals(3, preferences.themePresets.size)
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
}
