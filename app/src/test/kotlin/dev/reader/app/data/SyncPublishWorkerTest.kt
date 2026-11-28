package dev.reader.app.data

import androidx.work.ListenableWorker.Result
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals

class SyncPublishWorkerTest {

    @Test fun `publishing or having nothing to publish succeeds`() = runBlocking<Unit> {
        assertEquals(Result.success(), publishResult(0) { true })
    }

    @Test fun `a lost folder fails without retrying`() = runBlocking<Unit> {
        assertEquals(Result.failure(), publishResult(0) { false })
    }

    @Test fun `other IO errors are tried three times`() = runBlocking<Unit> {
        val results = (0..2).map { attempt -> publishResult(attempt) { throw IOException("busy") } }
        assertEquals(listOf(Result.retry(), Result.retry(), Result.failure()), results)
    }
}
