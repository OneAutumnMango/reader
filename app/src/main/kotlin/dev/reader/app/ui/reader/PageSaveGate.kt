package dev.reader.app.ui.reader

/** Decides which position reports are real reading, so merely opening a chapter saves nothing. */
internal class PageSaveGate {
    private var lastIndex: Int? = null
    private var lastCount: Int? = null
    private var onePageSaved = false

    /** True when this report should be saved. userScrolled is null for paged, comic and PDF views. */
    fun onReport(
        index: Int,
        pageCount: Int,
        isActive: Boolean,
        savedFinished: Boolean,
        userScrolled: Boolean? = null,
    ): Boolean {
        // A changed page count is a repagination, not a page turn.
        val turned = lastCount == pageCount && lastIndex != index
        lastIndex = index
        lastCount = pageCount
        if (!isActive || userScrolled == false) return false
        // A one-page chapter can't be turned, so showing it is reading it.
        if (pageCount == 1) {
            if (savedFinished || onePageSaved) return false
            onePageSaved = true
            return true
        }
        return turned
    }
}
