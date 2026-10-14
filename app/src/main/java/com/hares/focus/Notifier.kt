package com.hares.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Notifier {
    private const val CH_STATUS = "hares_status"
    private const val CH_WARN = "hares_warn"
    private var warnId = 100

    fun channels(c: Context) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH_STATUS, "حالة الحارس", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_WARN, "تنبيهات الوقت", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun open(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 0, Intent(c, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun status(c: Context, text: String): Notification =
        Notification.Builder(c, CH_STATUS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(c.getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open(c))
            .build()

    fun updateStatus(c: Context, text: String) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(1, status(c, text))
        } catch (e: Exception) {
        }
    }

    fun warn(c: Context, text: String) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = Notification.Builder(c, CH_WARN)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(c.getString(R.string.app_name))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open(c))
            .build()
        try {
            nm.notify(warnId++, n)
            if (warnId > 140) warnId = 100
        } catch (e: Exception) {
        }
    }
}
