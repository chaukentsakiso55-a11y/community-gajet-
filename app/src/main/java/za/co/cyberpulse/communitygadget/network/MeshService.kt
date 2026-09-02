package za.co.cyberpulse.communitygadget.network

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import za.co.cyberpulse.communitygadget.CommunityGadgetApplication
import za.co.cyberpulse.communitygadget.alert.AlarmController
import za.co.cyberpulse.communitygadget.alert.AlertNotificationManager
import za.co.cyberpulse.communitygadget.domain.AlertCodec
import za.co.cyberpulse.communitygadget.domain.AlertLevel
import java.util.LinkedHashMap

class MeshService : Service() {
    private lateinit var meshManager: NearbyMeshManager
    private lateinit var alarmController: AlarmController
    private lateinit var notificationManager: AlertNotificationManager
    private val seenAlerts = object : LinkedHashMap<String, Long>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 256
    }

    override fun onCreate() {
        super.onCreate()
        val config = (application as CommunityGadgetApplication).preferences.loadConfig()
        if (config == null) {
            stopSelf()
            return
        }
        alarmController = AlarmController(this)
        notificationManager = AlertNotificationManager(this).also { it.createChannels() }
        startForeground(AlertNotificationManager.MESH_NOTIFICATION_ID, notificationManager.meshNotification())
        meshManager = NearbyMeshManager(this, config.terminalName, ::receivePayload)
        meshManager.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SEND -> intent.getByteArrayExtra(EXTRA_PAYLOAD)?.let(::sendLocalPayload)
            ACTION_ACKNOWLEDGE -> acknowledgeCurrentAlert()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (::meshManager.isInitialized) meshManager.stop()
        if (::alarmController.isInitialized) alarmController.release()
        MeshRuntime.setStatus("Mesh stopped")
        super.onDestroy()
    }

    private fun sendLocalPayload(payload: ByteArray) {
        val alert = verify(payload) ?: return
        synchronized(seenAlerts) { seenAlerts[alert.id] = System.currentTimeMillis() }
        handleTrustedAlert(alert)
        meshManager.broadcast(payload)
    }

    private fun receivePayload(sourceEndpointId: String, payload: ByteArray) {
        val alert = verify(payload) ?: return
        val isNew = synchronized(seenAlerts) {
            if (seenAlerts.containsKey(alert.id)) false
            else {
                seenAlerts[alert.id] = System.currentTimeMillis()
                true
            }
        }
        if (!isNew) return
        handleTrustedAlert(alert)
        meshManager.broadcast(payload, excludeEndpointId = sourceEndpointId)
    }

    private fun verify(payload: ByteArray) =
        (application as CommunityGadgetApplication).preferences.loadConfig()?.let { config ->
            AlertCodec.decodeAndVerify(payload, config.communityKey)
        }

    private fun handleTrustedAlert(alert: za.co.cyberpulse.communitygadget.domain.EmergencyAlert) {
        MeshRuntime.addAlert(alert)
        alarmController.announce(alert.level)
        notificationManager.showAlert(alert)
        MeshRuntime.setStatus(
            if (alert.level == AlertLevel.EMERGENCY) "Emergency alert active" else "Offline mesh listening"
        )
    }

    private fun acknowledgeCurrentAlert() {
        alarmController.acknowledge()
        notificationManager.cancelAlert()
        MeshRuntime.setStatus("Alert acknowledged — mesh listening")
    }

    companion object {
        private const val ACTION_START = "za.co.cyberpulse.communitygadget.START_MESH"
        private const val ACTION_SEND = "za.co.cyberpulse.communitygadget.SEND_ALERT"
        private const val ACTION_ACKNOWLEDGE = "za.co.cyberpulse.communitygadget.ACK_ALERT"
        private const val ACTION_STOP = "za.co.cyberpulse.communitygadget.STOP_MESH"
        private const val EXTRA_PAYLOAD = "signed_alert_payload"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MeshService::class.java).setAction(ACTION_START)
            )
        }

        fun send(context: Context, payload: ByteArray) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MeshService::class.java)
                    .setAction(ACTION_SEND)
                    .putExtra(EXTRA_PAYLOAD, payload)
            )
        }

        fun acknowledge(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MeshService::class.java).setAction(ACTION_ACKNOWLEDGE)
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MeshService::class.java).setAction(ACTION_STOP))
        }
    }
}
