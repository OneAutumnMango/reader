package dev.reader.parser.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import kotlin.math.roundToInt

/** Largest size with the page's aspect ratio that fits inside the given bounds (each side at least 1px). */
fun fitWithin(pageWidth: Int, pageHeight: Int, maxWidth: Int, maxHeight: Int): Pair<Int, Int> {
    val scale = minOf(maxWidth.toFloat() / pageWidth, maxHeight.toFloat() / pageHeight)
    return (pageWidth * scale).roundToInt().coerceAtLeast(1) to (pageHeight * scale).roundToInt().coerceAtLeast(1)
}

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

    /** Renders [index] at the largest size fitting [maxWidthPx] x [maxHeightPx] without distorting it. */
    @Synchronized
    fun renderPage(index: Int, maxWidthPx: Int, maxHeightPx: Int): Bitmap = renderer.openPage(index).use { page ->
        val (width, height) = fitWithin(page.width, page.height, maxWidthPx, maxHeightPx)
        val scale = minOf(width.toFloat() / page.width, height.toFloat() / page.height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bitmap
    }

    @Synchronized
    override fun close() {
        renderer.close()
        descriptor.close()
    }
}
