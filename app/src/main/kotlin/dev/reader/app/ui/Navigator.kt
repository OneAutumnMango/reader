package dev.reader.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A minimal stack navigator: no back-stack library, just a list of destinations. */
class Navigator(start: Destination = Destination.Library) {

    var stack by mutableStateOf(listOf(start))
        private set

    val current: Destination get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    /** System back: pops the stack, or returns to the library from another drawer root. */
    val handlesSystemBack: Boolean get() = canGoBack || current != Destination.Library

    fun push(destination: Destination) {
        stack = stack + destination
    }

    /** Switches to a root destination, clearing anything pushed on top of it. */
    fun switchRoot(destination: Destination) {
        stack = listOf(destination)
    }

    fun pop(): Boolean {
        if (!canGoBack) return false
        stack = stack.dropLast(1)
        return true
    }

    fun back() {
        if (!pop()) switchRoot(Destination.Library)
    }
}
