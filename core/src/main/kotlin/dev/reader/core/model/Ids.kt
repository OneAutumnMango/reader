package dev.reader.core.model

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class BookId(val value: String)

@Serializable
@JvmInline
value class ChapterId(val value: String)

@Serializable
@JvmInline
value class CategoryId(val value: String)

@Serializable
@JvmInline
value class BookmarkId(val value: String)

@Serializable
@JvmInline
value class ThemePresetId(val value: String)
