package dev.reader.app.ui.data

enum class DangerAction(
    val label: String,
    val consequence: String,
    val finalWarning: String,
    val confirmLabel: String,
) {
    REMOVE_ALL_BOOKS(
        label = "Remove all books",
        consequence = "Every book will be removed from your library, along with all of its chapters, bookmarks " +
            "and reading progress. Your categories, stats and settings stay. The book files on your device " +
            "aren't deleted.",
        finalWarning = "Your entire library, every bookmark and all reading progress will be permanently deleted. " +
            "There is no way to get them back unless you have a backup.",
        confirmLabel = "Yes, remove all books",
    ),
    RESET_STATS(
        label = "Reset all stats",
        consequence = "Every recorded reading session will be deleted. Reading time, words read, pages turned, " +
            "streaks and insights will all go back to zero. Your books and progress stay.",
        finalWarning = "All of your reading history and stats will be permanently deleted. " +
            "There is no way to get them back unless you have a backup.",
        confirmLabel = "Yes, reset all stats",
    ),
}
