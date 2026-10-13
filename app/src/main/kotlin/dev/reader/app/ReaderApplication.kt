package dev.reader.app

import android.app.Application
import dev.reader.app.data.ReaderGraph

class ReaderApplication : Application() {
    val graph by lazy { ReaderGraph(this) }
}
