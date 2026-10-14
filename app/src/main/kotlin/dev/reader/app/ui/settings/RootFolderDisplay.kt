package dev.reader.app.ui.settings

import dev.reader.data.store.CollectionKind

/** What the folder picker chose; [uri] is the stable key, [displayName] is what the user sees. */
data class RootFolderDisplay(val uri: String, val displayName: String, val kind: CollectionKind? = null)
