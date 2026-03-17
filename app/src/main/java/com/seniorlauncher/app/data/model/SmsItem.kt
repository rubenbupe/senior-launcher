package com.seniorlauncher.app.data.model

data class SmsItem(
    val id: Long,
    val body: String,
    val timestamp: Long,
    val isSentByMe: Boolean
)
