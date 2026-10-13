package dev.reader.core.reader

import kotlinx.serialization.Serializable

@Serializable
enum class ReadingMode {
    CONTINUOUS_SCROLL,
    PAGED_VERTICAL,
    PAGED_HORIZONTAL_LTR,
    PAGED_HORIZONTAL_RTL,
}

@Serializable
enum class PdfRenderMode {
    REFLOW_TEXT,
    FIXED_PAGE,
}
