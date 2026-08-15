package com.shangmentiyu.sportscoach.app.framework

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Notification channel creation helper (data layer).
 *
 * Extracts the common createNotificationChannel boilerplate from 4 Service/Worker classes.
 * Idempotent: Android deduplicates channels with the same ID.
 */
object NotificationUtils {

    fun createChannel(
        context: Context,
        channelId: String,
        name: String,
        description: String = "",
        importance: Int = NotificationManager.IMPORTANCE_DEFAULT,
        enableVibration: Boolean = false,
        enableLights: Boolean = false,
        showBadge: Boolean = true
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, name, importance).apply {
                this.description = description
                this.enableVibration(enableVibration)
                this.enableLights(enableLights)
                this.setShowBadge(showBadge)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
