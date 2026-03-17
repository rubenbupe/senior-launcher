package com.seniorlauncher.app.service.sync.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.seniorlauncher.app.MainActivity
import com.seniorlauncher.app.R

class SyncNotifier(
    private val context: Context,
    private val channelId: String,
    private val notificationId: Int,
) {
    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(NotificationManager::class.java)
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Sincronización en segundo plano",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene la app sincronizada con el servidor"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun build(status: String): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val pendingIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), flags
        )

        return NotificationCompat.Builder(context, channelId)
            .setContentTitle("Senior Launcher")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun update(status: String) {
        notificationManager.notify(notificationId, build(status))
    }
}