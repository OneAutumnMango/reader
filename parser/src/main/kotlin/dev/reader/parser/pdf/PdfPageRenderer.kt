package dev.reader.parser.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File

/**
 * Fixed-page mode: renders each PDF page as a bitmap, preserving the original layout.
 * Backed by [android.graphics.pdf.PdfRenderer], so it only runs on a real device/emulator.
 */
class PdfPageRenderer(file: File) : Closeable {

    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)

    val pageCount: Int get() = renderer.pageCount

    private val aspectRatios = HashMap<Int, Float>()

    /** Width / height of [index]; PdfRenderer allows only one open page at a time, hence the lock. */
    @Synchronized
    fun aspectRatio(index: Int): Float = aspectRatios.getOrPut(index) {
        renderer.openPage(index).use { it.width.toFloat() / it.height }
    }

    @Synchronized
    fun renderPage(index: Int, widthPx: Int, heightPx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        renderer.openPage(index).use { page ->
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
        return bitmap
    }

    @Synchronized
    override fun close() {
        renderer.close()
        descriptor.close()
    }
}
