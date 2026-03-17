package com.seniorlauncher.app.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationBadgeStore.updateFromNotifications(activeNotifications)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        NotificationBadgeStore.updateFromNotifications(activeNotifications)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        NotificationBadgeStore.updateFromNotifications(activeNotifications)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationBadgeStore.updateFromNotifications(emptyArray())
    }
}
