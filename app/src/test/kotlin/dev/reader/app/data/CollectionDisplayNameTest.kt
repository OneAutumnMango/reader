package dev.reader.app.data

import android.net.Uri
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class CollectionDisplayNameTest {

    private fun tree(documentId: String) =
        Uri.parse("content://com.android.externalstorage.documents/tree/$documentId")

    @Test
    fun `saved label takes precedence over provider and document id`() {
        assertEquals(
            "My books",
            collectionDisplayName(tree("ABCD-1234%3ABooks"), "My books", "ABCD-1234"),
        )
    }

    @Test
    fun `provider label is used unless it is a raw volume or document id`() {
        assertEquals("Archive", collectionDisplayName(tree("ABCD-1234%3ABooks"), providerName = "Archive"))
        assertEquals(
            "Books",
            collectionDisplayName(tree("ABCD-1234%3ABooks"), providerName = "ABCD-1234:Books"),
        )
        assertEquals("Novels", collectionDisplayName(tree("ABCD-1234%3ABooks%2FNovels"), providerName = "ABCD-1234"))
    }

    @Test
    fun `volume roots use readable fallback labels`() {
        assertEquals("Internal storage", collectionDisplayName(tree("primary%3A"), providerName = "primary"))
        assertEquals("SD card", collectionDisplayName(tree("ABCD-1234"), providerName = "ABCD-1234"))
    }

    @Test
    fun `decoded path segments preserve plus characters`() {
        assertEquals("My+Books", collectionDisplayName(tree("primary%3ABooks%2FMy%2BBooks")))
    }
}
