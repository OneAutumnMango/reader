package dev.reader.app.ui.data

enum class DangerAction(
    val label: String,
    val consequence: String,
    val finalWarning: String,
    val confirmLabel: String,
) {
    REMOVE_ALL_BOOKS(
        label = "Remove all books",
        consequence = "Every book will be removed from this app's library, along with the reader data the app " +
            "keeps for it: chapters, bookmarks, reading progress, custom titles, covers and chapter settings. " +
            "Your categories, stats and settings stay.\n\n" +
            "Your original book files are NOT deleted. They stay exactly where they are on your device, " +
            "and you can add them again at any time.",
        finalWarning = "All reader data for every book (bookmarks, reading progress, custom titles and covers) " +
            "will be permanently deleted and can't be recovered without a backup.\n\n" +
            "Your original book files will not be touched.",
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
