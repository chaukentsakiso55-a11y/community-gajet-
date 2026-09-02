package za.co.cyberpulse.communitygadget.alert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import za.co.cyberpulse.communitygadget.MainActivity
import za.co.cyberpulse.communitygadget.R
import za.co.cyberpulse.communitygadget.domain.AlertLevel
import za.co.cyberpulse.communitygadget.domain.EmergencyAlert
import java.util.Locale

class AlertNotificationManager(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        manager.createNotificationChannel(
            NotificationChannel(MESH_CHANNEL, "Offline mesh", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps the community emergency relay ready"
                setSound(null, null)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, "Community alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Urgent alerts from trusted community terminals"
                setSound(null, null)
                enableVibration(true)
            }
        )
    }

    fun meshNotification(): Notification = NotificationCompat.Builder(context, MESH_CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Community Gadget is ready")
        .setContentText("Offline mesh listening for trusted alerts")
        .setContentIntent(openAppIntent())
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    fun showAlert(alert: EmergencyAlert) {
        val title = when (alert.level) {
            AlertLevel.SECURE -> "All clear — ${alert.originName}"
            AlertLevel.MONITOR -> "Monitor alert — ${alert.originName}"
            AlertLevel.EMERGENCY -> "EMERGENCY — ${alert.originName}"
        }
        val body = if (alert.level == AlertLevel.EMERGENCY && alert.hasLocation()) {
            String.format(
                Locale.US,
                "Location %.6f, %.6f • accuracy ±%.0f m",
                alert.latitude,
                alert.longitude,
                alert.accuracyMeters ?: 0f
            )
        } else {
            when (alert.level) {
                AlertLevel.SECURE -> "Zone reports normal conditions"
                AlertLevel.MONITOR -> "Stay observant and follow the community response plan"
                AlertLevel.EMERGENCY -> "GPS location was unavailable; check the terminal identity"
            }
        }

        val acknowledgeIntent = Intent(context, AlertActionReceiver::class.java).apply {
            action = AlertActionReceiver.ACTION_ACKNOWLEDGE
        }
        val acknowledgePendingIntent = PendingIntent.getBroadcast(
            context,
            44,
            acknowledgeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(alert.level != AlertLevel.EMERGENCY)
            .setOngoing(alert.level == AlertLevel.EMERGENCY)
            .addAction(0, "Acknowledge", acknowledgePendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(ALERT_NOTIFICATION_ID, notification)
        }
    }

    fun cancelAlert() {
        NotificationManagerCompat.from(context).cancel(ALERT_NOTIFICATION_ID)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            43,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val MESH_NOTIFICATION_ID = 401
        private const val ALERT_NOTIFICATION_ID = 402
        private const val MESH_CHANNEL = "community_mesh"
        private const val ALERT_CHANNEL = "community_alerts"
    }
}
