package dev.reader.app.data

import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AppLogRecordingTest {

    @Before
    fun prepare() = AppLog.clear()

    @After
    fun cleanUp() = AppLog.clear()

    @Test
    fun `records every severity newest first with source and details`() {
        AppLog.debug("Start", "Test")
        AppLog.info("Complete", "Test")
        AppLog.warn("Fallback", "Test")
        AppLog.error("Failed", "Test", IllegalStateException("Failure details"))

        assertEquals(LogLevel.entries.reversed(), AppLog.entries.map { it.level })
        assertTrue(AppLog.entries.all { it.source == "Test" })
        assertTrue(requireNotNull(AppLog.entries.first().details).contains("Failure details"))
    }

    @Test
    fun `history stays bounded and returned entries are a stable snapshot`() {
        AppLog.info("Original")
        val snapshot = AppLog.entries
        repeat(250) { AppLog.debug("Entry $it") }
        assertEquals(200, AppLog.entries.size)
        assertEquals("Entry 249", AppLog.entries.first().message)
        assertEquals("Original", snapshot.single().message)
        AppLog.clear()
        assertTrue(AppLog.entries.isEmpty())
    }

    @Test
    fun `cancellation propagates without logging a failure`() {
        assertFailsWith<CancellationException> { AppLog.error("Cancelled", error = CancellationException()) }
        assertTrue(AppLog.entries.isEmpty())
    }
}
