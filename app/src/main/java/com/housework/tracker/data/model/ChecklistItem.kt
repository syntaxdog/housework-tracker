package com.housework.tracker.data.model

data class ChecklistItem(
    val id: String = "",
    val name: String = "",
    val points: Int = 1,
    val category: String = "",
    val isDefault: Boolean = false,
    val order: Int = 0
)
