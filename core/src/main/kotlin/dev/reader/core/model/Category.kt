package dev.reader.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Category(
    val id: CategoryId,
    val name: String,
    val orderIndex: Int = 0,
    val isDefault: Boolean = false,
)
