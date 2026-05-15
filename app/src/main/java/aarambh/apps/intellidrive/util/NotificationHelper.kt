package aarambh.apps.intellidrive.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import aarambh.apps.intellidrive.MainActivity

object NotificationHelper {

    const val CHANNEL_ID_ALERTS = "INTELLIDRIVE_ALERTS"
    const val CHANNEL_ID_SESSION = "INTELLIDRIVE_SESSION"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertsChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "Driving Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for harsh braking, overspeed, and route deviation."
            }

            val sessionChannel = NotificationChannel(
                CHANNEL_ID_SESSION,
                "Active Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification while driving session is active."
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(alertsChannel)
            manager.createNotificationChannel(sessionChannel)
        }
    }

    fun showNotification(context: Context, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // Replace with your app icon later
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
