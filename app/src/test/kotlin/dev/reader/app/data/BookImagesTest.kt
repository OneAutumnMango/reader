package dev.reader.app.data

import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import dev.reader.core.model.BookFormat
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.parser.epub.EpubParser
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookImagesTest {

    @Test
    fun `fb2 images load their embedded binary`() {
        val file = File.createTempFile("illustrated", ".fb2")
        try {
            file.writeText("""<FictionBook><binary id="art">aGVsbG8=</binary></FictionBook>""")
            val document = BookImages.resolve(
                RuntimeEnvironment.getApplication(), file, BookFormat.FB2,
                RichDocument(listOf(Block.Image("#art"))),
            )
            val path = (document.blocks.single() as Block.Image).path
            assertEquals("hello", File(path).readText())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `epub images resolve relative to chapters and extract to cache`() = runBlocking {
        val file = File.createTempFile("illustrated", ".epub")
        val image = ByteArrayOutputStream().use { output ->
            val bitmap = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
        val entries = mapOf(
            "META-INF/container.xml" to """<container><rootfile full-path="OPS/book.opf"/></container>""",
            "OPS/book.opf" to """<package><manifest><item id="c" href="text/chapter.xhtml"/></manifest>
                <spine><itemref idref="c"/></spine></package>""",
            "OPS/text/chapter.xhtml" to """<p>Before<img src="../images/map%20one.png" alt="Map">After</p>""",
        )
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (path, text) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("OPS/images/map one.png"))
            zip.write(image)
            zip.closeEntry()
        }
        try {
            val chapter = requireNotNull(EpubParser.parseChapter(file, 0))
            val parsed = chapter.document.blocks.filterIsInstance<Block.Image>().single()
            assertEquals("OPS/images/map one.png", parsed.path)
            val document = BookImages.resolve(
                RuntimeEnvironment.getApplication(), file, BookFormat.EPUB, chapter.document,
            )
            val resolved = document.blocks.filterIsInstance<Block.Image>().single()
            assertContentEquals(image, File(resolved.path).readBytes())
            assertEquals("Map", resolved.alt)
            val context = RuntimeEnvironment.getApplication()
            val loader = ImageLoader(context)
            try {
                val decoded = assertIs<SuccessResult>(
                    loader.execute(ImageRequest.Builder(context).data(resolved.path).build()),
                )
                assertEquals(2, decoded.image.width)
                assertEquals(3, decoded.image.height)
            } finally {
                loader.shutdown()
            }
            assertEquals(
                document,
                BookImages.resolve(RuntimeEnvironment.getApplication(), file, BookFormat.EPUB, chapter.document),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `html images resolve encoded paths beside their source`() {
        val file = File("/books/chapter.html")
        val document = BookImages.resolve(
            RuntimeEnvironment.getApplication(), file, BookFormat.HTML,
            RichDocument(listOf(Block.Image("images/map%20one.png"))),
        )
        assertEquals("file:/books/images/map%20one.png", (document.blocks.single() as Block.Image).path)
    }
}
