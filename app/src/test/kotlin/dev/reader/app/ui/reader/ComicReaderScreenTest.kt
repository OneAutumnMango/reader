package dev.reader.app.ui.reader

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.reader.core.model.BookFormat
import dev.reader.core.reader.ReaderSettings
import dev.reader.core.reader.ReadingMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempFile
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ComicReaderScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun buildCbz(): File {
        val file = createTempFile(suffix = ".cbz").toFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("page1.jpg"))
            zip.write(ByteArray(0))
            zip.closeEntry()
        }
        return file
    }

    @Test
    fun `renders the tap zone overlay alongside the comic pager`() {
        composeRule.setContent {
            ComicReaderScreen(
                file = buildCbz(),
                format = BookFormat.CBZ,
                pages = listOf("page1.jpg"),
                settings = ReaderSettings(),
                showTapZoneLabels = true,
            )
        }

        composeRule.onNodeWithTag("tap-zone-0").assertExists()
    }

    @Test
    fun `a fitting strip with a broken page is still reported`() {
        var reported: Pair<Int, Int>? = null
        composeRule.setContent {
            ComicReaderScreen(
                file = buildCbz(),
                format = BookFormat.CBZ,
                pages = listOf("missing.jpg"),
                settings = ReaderSettings(readingMode = ReadingMode.CONTINUOUS_SCROLL),
                // Taller than the failed page's placeholder.
                modifier = Modifier.requiredSize(300.dp, 600.dp),
                onPageChanged = { index, count, _ -> reported = index to count },
            )
        }

        composeRule.waitUntil(5_000) { reported != null }
        assertEquals(0 to 1, reported)
    }
}
