package dev.reader.core.reader

enum class ReadingMode {
    CONTINUOUS_SCROLL,
    PAGED_VERTICAL,
    PAGED_HORIZONTAL_LTR,
    PAGED_HORIZONTAL_RTL,
}

enum class PdfRenderMode {
    REFLOW_TEXT,
    FIXED_PAGE,
}
