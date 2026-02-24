package com.housework.tracker.data.model

import com.google.firebase.Timestamp

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val sentAt: Timestamp? = null
)
