package com.seniorlauncher.app.service

import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object NotificationBadgeStore {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts

    fun updateFromNotifications(notifications: Array<StatusBarNotification>?) {
        if (notifications == null) {
            _counts.value = emptyMap()
            return
        }
        val next = linkedMapOf<String, Int>()
        notifications.forEach { sbn ->
            val packageName = sbn.packageName?.trim().orEmpty()
            if (packageName.isBlank()) return@forEach
            if (sbn.isOngoing) return@forEach
            val rawNumber = sbn.notification?.number ?: 0
            val increment = if (rawNumber > 0) rawNumber else 1
            next[packageName] = (next[packageName] ?: 0) + increment
        }
        _counts.value = next
    }
}
