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
            NotificationChannel(MESH_CHANNEL, "Community network", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps the community emergency relay ready"
                setSound(null, null)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, "Community emergency alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Urgent alerts from trusted community terminals"
                setSound(null, null)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(TEST_CHANNEL, "Community test alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Clearly marked test alerts"
                setSound(null, null)
                enableVibration(true)
            }
        )
    }

    fun meshNotification(): Notification = NotificationCompat.Builder(context, MESH_CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Community Gadget V2 is ready")
        .setContentText("Nearby + Wi-Fi LAN emergency relay active")
        .setContentIntent(openAppIntent())
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    fun showAlert(alert: EmergencyAlert, isTest: Boolean = false) {
        val title = if (isTest) {
            "TEST ALERT — ${alert.originName}"
        } else {
            when (alert.level) {
                AlertLevel.SECURE -> "All clear — ${alert.originName}"
                AlertLevel.MONITOR -> "Monitor alert — ${alert.originName}"
                AlertLevel.EMERGENCY -> "EMERGENCY — ${alert.originName}"
            }
        }
        val body = if (isTest) {
            "Community Gadget test only — no emergency"
        } else if (alert.level == AlertLevel.EMERGENCY && alert.hasLocation()) {
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
                AlertLevel.EMERGENCY -> "Location unavailable — open Community Gadget for details"
            }
        }

        val acknowledgeIntent = PendingIntent.getBroadcast(
            context,
            44,
            Intent(context, AlertActionReceiver::class.java).setAction(AlertActionReceiver.ACTION_ACKNOWLEDGE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val respondingIntent = PendingIntent.getBroadcast(
            context,
            45,
            Intent(context, AlertActionReceiver::class.java).setAction(AlertActionReceiver.ACTION_RESPONDING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, if (isTest) TEST_CHANNEL else ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent())
            .setPriority(if (isTest) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_MAX)
            .setCategory(if (isTest) NotificationCompat.CATEGORY_STATUS else NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(isTest || alert.level != AlertLevel.EMERGENCY)
            .setOngoing(!isTest && alert.level == AlertLevel.EMERGENCY)
            .addAction(0, "Acknowledge", acknowledgeIntent)

        if (!isTest && alert.level == AlertLevel.EMERGENCY) {
            builder
                .addAction(0, "I'm responding", respondingIntent)
                .setFullScreenIntent(openAppIntent(), true)
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(ALERT_NOTIFICATION_ID, builder.build())
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
        private const val MESH_CHANNEL = "community_mesh_v2"
        private const val ALERT_CHANNEL = "community_alerts_v2"
        private const val TEST_CHANNEL = "community_test_v2"
    }
}
