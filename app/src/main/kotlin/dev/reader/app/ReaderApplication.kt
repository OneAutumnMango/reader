package dev.reader.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder
import dev.reader.app.data.ReaderGraph

class ReaderApplication : Application(), SingletonImageLoader.Factory {
    val graph by lazy { ReaderGraph(this) }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(SvgDecoder.Factory()) }
        .build()
}
