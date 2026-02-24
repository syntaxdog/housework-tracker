package com.housework.tracker.data.model

import com.google.firebase.Timestamp

data class User(
    val id: String = "",
    val displayName: String = "",
    val email: String = "",
    val houseId: String = "",
    val avatarUrl: String = "",
    val fcmToken: String = "",
    val createdAt: Timestamp? = null
)
