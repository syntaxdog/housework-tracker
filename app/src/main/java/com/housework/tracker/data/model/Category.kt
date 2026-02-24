package com.housework.tracker.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

enum class Category(
    val label: String,
    val icon: ImageVector,
    val displayOrder: Int
) {
    KITCHEN("주방", Icons.Outlined.Kitchen, 0),
    LAUNDRY("빨래", Icons.Outlined.LocalLaundryService, 1),
    CLEANING("청소", Icons.Outlined.CleaningServices, 2),
    LIVING("생활", Icons.Outlined.ShoppingCart, 3),
    OTHER("기타", Icons.Outlined.MoreHoriz, 4);

    companion object {
        fun fromLabel(label: String): Category {
            return entries.find { it.label == label } ?: OTHER
        }

        val orderedEntries: List<Category>
            get() = entries.sortedBy { it.displayOrder }
    }
}
