package com.jmdl.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat

object Notifications {
    const val CHANNEL_ID = "downloads"
    const val NOTIF_ID = 1001
    const val DONE_NOTIF_ID = 1002

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID, "下载", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "JM 下载进度" }
            mgr.createNotificationChannel(ch)
        }
    }

    fun build(
        context: Context,
        title: String,
        text: String,
        done: Int,
        total: Int,
        indeterminate: Boolean,
    ): Notification {
        val b = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (indeterminate) {
            b.setProgress(0, 0, true)
        } else if (total > 0) {
            b.setProgress(total, done, false)
        }
        return b.build()
    }

    /** Dismissible completion notification that opens the app when tapped. */
    fun buildDone(context: Context, title: String, text: String): Notification {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pending = launch?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .apply { if (pending != null) setContentIntent(pending) }
            .build()
    }
}
