package com.seniorlauncher.app.data.model

data class SmsConversation(
    val threadId: Long,
    val address: String,
    val preview: String,
    val timestamp: Long,
    val unreadCount: Int
)
