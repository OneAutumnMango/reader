package dev.reader.app.ui.reader

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.reader.core.model.BookFormat
import dev.reader.core.reader.TapZoneConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempFile

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
                tapZones = TapZoneConfig(),
                showTapZoneLabels = true,
            )
        }

        composeRule.onNodeWithTag("tap-zone-0").assertExists()
    }
}
