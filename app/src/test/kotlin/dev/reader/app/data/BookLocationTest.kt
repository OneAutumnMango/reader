package dev.reader.app.data

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class BookLocationTest {

    private val storage = "content://com.android.externalstorage.documents"

    @Test
    fun `a picked file on internal storage reads as a path and opens its parent folder`() {
        val location = BookLocations.locate("$storage/document/primary%3ABooks%2FNovels%2FDune.epub", isFolder = false)

        assertEquals("Internal storage/Books/Novels/Dune.epub", location.label)
        assertEquals("$storage/document/primary%3ABooks%2FNovels", location.folder.toString())
    }

    @Test
    fun `a library folder entry on an sd card opens the folder it sits in`() {
        val entry = "$storage/tree/1A2B-3C4D%3ABooks/document/1A2B-3C4D%3ABooks%2FDune.epub"

        val location = BookLocations.locate(entry, isFolder = false)

        assertEquals("SD card (1A2B-3C4D)/Books/Dune.epub", location.label)
        assertEquals("$storage/document/1A2B-3C4D%3ABooks", location.folder.toString())
    }

    @Test
    fun `a folder book opens its own folder and a root file opens the volume root`() {
        assertEquals(
            "$storage/document/primary%3ANovels%2FDune",
            BookLocations.locate("$storage/tree/primary%3ANovels%2FDune", isFolder = true).folder.toString(),
        )
        assertEquals(
            "$storage/document/primary%3A",
            BookLocations.locate("$storage/document/primary%3ADune.epub", isFolder = false).folder.toString(),
        )
    }

    @Test
    fun `other providers get a readable name but no folder`() {
        val downloads = BookLocations.locate(
            "content://com.android.providers.downloads.documents/document/" +
                "raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2FDune.epub",
            isFolder = false,
        )
        assertEquals("/storage/emulated/0/Download/Dune.epub", downloads.label)
        assertNull(downloads.folder)

        val drive = BookLocations.locate(
            "content://com.google.android.apps.docs.storage/document/acc%3D1%3Bdoc%3D9",
            isFolder = false,
        )
        assertNull(drive.folder)
    }
}
