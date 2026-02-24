package com.housework.tracker.data.model

import com.google.firebase.Timestamp

data class DailyCompletion(
    val id: String = "",
    val itemId: String = "",
    val itemName: String = "",
    val points: Int = 0,
    val userId: String = "",
    val userName: String = "",
    val completedAt: Timestamp? = null
)
