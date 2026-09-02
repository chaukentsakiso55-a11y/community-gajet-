package za.co.cyberpulse.communitygadget.network

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import za.co.cyberpulse.communitygadget.CommunityGadgetApplication
import za.co.cyberpulse.communitygadget.alert.AlarmController
import za.co.cyberpulse.communitygadget.alert.AlertNotificationManager
import za.co.cyberpulse.communitygadget.data.TerminalConfig
import za.co.cyberpulse.communitygadget.domain.AlertLevel
import za.co.cyberpulse.communitygadget.domain.CommunityMessage
import za.co.cyberpulse.communitygadget.domain.CommunityMessageCodec
import za.co.cyberpulse.communitygadget.domain.CommunityMessageType
import za.co.cyberpulse.communitygadget.domain.EmergencyAlert
import za.co.cyberpulse.communitygadget.location.EmergencyLocationProvider
import java.util.LinkedHashMap
import java.util.UUID

class MeshService : Service() {
    private lateinit var config: TerminalConfig
    private lateinit var nearbyMeshManager: NearbyMeshManager
    private lateinit var lanMeshManager: LanMeshManager
    private lateinit var alarmController: AlarmController
    private lateinit var notificationManager: AlertNotificationManager
    private lateinit var locationProvider: EmergencyLocationProvider
    private lateinit var connectivityManager: ConnectivityManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var locationJob: Job? = null

    private val seenMessages = object : LinkedHashMap<String, Long>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 512
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshInternetState()
        override fun onLost(network: Network) = refreshInternetState()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refreshInternetState()
    }

    override fun onCreate() {
        super.onCreate()
        config = (application as CommunityGadgetApplication).preferences.loadConfig() ?: run {
            stopSelf()
            return
        }
        alarmController = AlarmController(this)
        notificationManager = AlertNotificationManager(this).also { it.createChannels() }
        locationProvider = EmergencyLocationProvider(this)
        startForeground(AlertNotificationManager.MESH_NOTIFICATION_ID, notificationManager.meshNotification())

        nearbyMeshManager = NearbyMeshManager(this, config.terminalName, ::receiveNearbyPayload)
        lanMeshManager = LanMeshManager(this, ::receiveLanPayload)
        lanMeshManager.start()
        nearbyMeshManager.start()
        MeshRuntime.setStatus("Community emergency network ready")
        startConnectivityMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SEND -> intent.getByteArrayExtra(EXTRA_PAYLOAD)?.let(::sendLocalPayload)
            ACTION_ACKNOWLEDGE -> acknowledgeCurrentAlert()
            ACTION_RESPONDING -> respondToCurrentAlert()
            ACTION_END_ALERT -> endCurrentEmergency()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        locationJob?.cancel()
        if (::nearbyMeshManager.isInitialized) nearbyMeshManager.stop()
        if (::lanMeshManager.isInitialized) lanMeshManager.stop()
        if (::alarmController.isInitialized) alarmController.release()
        if (::connectivityManager.isInitialized) runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        serviceScope.cancel()
        MeshRuntime.setStatus("Mesh stopped")
        super.onDestroy()
    }

    private fun sendLocalPayload(payload: ByteArray) {
        val message = verify(payload) ?: return
        if (!markNew(message.messageId)) return
        handleTrustedMessage(message, remote = false)
        broadcastEverywhere(payload)
    }

    private fun receiveNearbyPayload(sourceEndpointId: String, payload: ByteArray) {
        val message = verifyAndMarkNew(payload) ?: return
        MeshRuntime.setLastTransport("Nearby mesh")
        handleTrustedMessage(message, remote = message.actorId != config.terminalId)
        nearbyMeshManager.broadcast(payload, excludeEndpointId = sourceEndpointId)
        lanMeshManager.broadcast(payload)
    }

    private fun receiveLanPayload(sourceAddress: String, payload: ByteArray) {
        val message = verifyAndMarkNew(payload) ?: return
        MeshRuntime.setLastTransport("Wi-Fi LAN")
        handleTrustedMessage(message, remote = message.actorId != config.terminalId)
        nearbyMeshManager.broadcast(payload)
        if (message.type == CommunityMessageType.ALERT) {
            MeshRuntime.setStatus("Alert received over Wi-Fi LAN")
        }
    }

    private fun handleTrustedMessage(message: CommunityMessage, remote: Boolean) {
        MeshRuntime.handleMessage(message, config.terminalId)
        when (message.type) {
            CommunityMessageType.ALERT -> {
                val alert = message.toEmergencyAlert() ?: return
                if (remote) {
                    alarmController.announce(alert.level, message.isTest)
                    notificationManager.showAlert(alert, message.isTest)
                    sendControl(CommunityMessageType.RECEIVED, message.alertId)
                } else if (message.isTest) {
                    alarmController.announce(AlertLevel.EMERGENCY, isTest = true)
                    notificationManager.showAlert(alert, isTest = true)
                }
                if (!remote && alert.level == AlertLevel.EMERGENCY && !message.isTest) {
                    startLiveLocation(message.alertId)
                }
            }

            CommunityMessageType.END_ALERT -> {
                if (MeshRuntime.activeAlert.value?.alert?.id != message.alertId) {
                    locationJob?.cancel()
                }
                alarmController.acknowledge()
                notificationManager.cancelAlert()
            }

            CommunityMessageType.LOCATION_UPDATE,
            CommunityMessageType.RECEIVED,
            CommunityMessageType.ACKNOWLEDGED,
            CommunityMessageType.RESPONDING -> Unit
        }
    }

    private fun acknowledgeCurrentAlert() {
        val active = MeshRuntime.activeAlert.value ?: return
        alarmController.acknowledge()
        notificationManager.cancelAlert()
        MeshRuntime.silenceAlert(active.alert.id)
        if (!active.localOrigin) sendControl(CommunityMessageType.ACKNOWLEDGED, active.alert.id)
        MeshRuntime.setStatus("Alert acknowledged — network still listening")
    }

    private fun respondToCurrentAlert() {
        val active = MeshRuntime.activeAlert.value ?: return
        alarmController.acknowledge()
        notificationManager.cancelAlert()
        MeshRuntime.silenceAlert(active.alert.id)
        if (!active.localOrigin) sendControl(CommunityMessageType.RESPONDING, active.alert.id)
        MeshRuntime.setStatus("Responder status shared with community")
    }

    private fun endCurrentEmergency() {
        val active = MeshRuntime.activeAlert.value ?: return
        if (!active.localOrigin) return
        sendControl(CommunityMessageType.END_ALERT, active.alert.id)
        locationJob?.cancel()
        alarmController.acknowledge()
        notificationManager.cancelAlert()
        MeshRuntime.setStatus("Emergency ended — network listening")
    }

    private fun sendControl(type: CommunityMessageType, alertId: String) {
        val unsigned = CommunityMessage(
            messageId = UUID.randomUUID().toString(),
            alertId = alertId,
            type = type,
            actorId = config.terminalId,
            actorName = config.terminalName,
            createdAtEpochMs = System.currentTimeMillis()
        )
        emitSigned(unsigned)
    }

    private fun startLiveLocation(alertId: String) {
        locationJob?.cancel()
        locationJob = serviceScope.launch {
            while (isActive) {
                val active = MeshRuntime.activeAlert.value
                if (active?.alert?.id != alertId || !active.localOrigin) break
                val location = locationProvider.currentEmergencyLocation()
                if (location != null) {
                    emitSigned(
                        CommunityMessage(
                            messageId = UUID.randomUUID().toString(),
                            alertId = alertId,
                            type = CommunityMessageType.LOCATION_UPDATE,
                            actorId = config.terminalId,
                            actorName = config.terminalName,
                            createdAtEpochMs = System.currentTimeMillis(),
                            level = AlertLevel.EMERGENCY,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracyMeters = location.accuracy
                        )
                    )
                }
                delay(15_000L)
            }
        }
    }

    private fun emitSigned(unsigned: CommunityMessage) {
        val signed = CommunityMessageCodec.sign(unsigned, config.communityKey)
        val payload = CommunityMessageCodec.encode(signed)
        if (!markNew(signed.messageId)) return
        handleTrustedMessage(signed, remote = false)
        broadcastEverywhere(payload)
    }

    private fun broadcastEverywhere(payload: ByteArray) {
        nearbyMeshManager.broadcast(payload)
        lanMeshManager.broadcast(payload)
    }

    private fun verifyAndMarkNew(payload: ByteArray): CommunityMessage? {
        val message = verify(payload) ?: return null
        return if (markNew(message.messageId)) message else null
    }

    private fun markNew(messageId: String): Boolean = synchronized(seenMessages) {
        if (seenMessages.containsKey(messageId)) false else {
            seenMessages[messageId] = System.currentTimeMillis()
            true
        }
    }

    private fun verify(payload: ByteArray): CommunityMessage? =
        CommunityMessageCodec.decodeAndVerify(payload, config.communityKey)

    private fun CommunityMessage.toEmergencyAlert(): EmergencyAlert? {
        val alertLevel = level ?: return null
        return EmergencyAlert(
            id = alertId,
            originId = actorId,
            originName = actorName,
            level = alertLevel,
            createdAtEpochMs = createdAtEpochMs,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters
        )
    }

    private fun startConnectivityMonitor() {
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }
        refreshInternetState()
    }

    private fun refreshInternetState() {
        if (!::connectivityManager.isInitialized) return
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let(connectivityManager::getNetworkCapabilities)
        MeshRuntime.setInternetAvailable(
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        )
    }

    companion object {
        private const val ACTION_START = "za.co.cyberpulse.communitygadget.START_MESH"
        private const val ACTION_SEND = "za.co.cyberpulse.communitygadget.SEND_V2"
        private const val ACTION_ACKNOWLEDGE = "za.co.cyberpulse.communitygadget.ACK_ALERT"
        private const val ACTION_RESPONDING = "za.co.cyberpulse.communitygadget.RESPONDING"
        private const val ACTION_END_ALERT = "za.co.cyberpulse.communitygadget.END_ALERT"
        private const val ACTION_STOP = "za.co.cyberpulse.communitygadget.STOP_MESH"
        private const val EXTRA_PAYLOAD = "signed_v2_payload"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MeshService::class.java).setAction(ACTION_START))
        }

        fun send(context: Context, payload: ByteArray) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MeshService::class.java).setAction(ACTION_SEND).putExtra(EXTRA_PAYLOAD, payload)
            )
        }

        fun acknowledge(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MeshService::class.java).setAction(ACTION_ACKNOWLEDGE))
        }

        fun responding(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MeshService::class.java).setAction(ACTION_RESPONDING))
        }

        fun endEmergency(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MeshService::class.java).setAction(ACTION_END_ALERT))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MeshService::class.java).setAction(ACTION_STOP))
        }
    }
}
