package com.housework.tracker.data.model

import com.google.firebase.Timestamp

data class House(
    val id: String = "",
    val name: String = "",
    val inviteCode: String = "",
    val members: List<String> = emptyList(),
    val createdAt: Timestamp? = null
)
