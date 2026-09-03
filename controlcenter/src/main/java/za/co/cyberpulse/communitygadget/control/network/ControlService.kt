package za.co.cyberpulse.communitygadget.control.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import za.co.cyberpulse.communitygadget.control.ControlConfig
import za.co.cyberpulse.communitygadget.control.ControlPreferences
import za.co.cyberpulse.communitygadget.control.MainActivity
import za.co.cyberpulse.communitygadget.control.protocol.AlertLevel
import za.co.cyberpulse.communitygadget.control.protocol.CommunityMessage
import za.co.cyberpulse.communitygadget.control.protocol.CommunityMessageCodec
import za.co.cyberpulse.communitygadget.control.protocol.CommunityMessageType
import java.util.LinkedHashMap
import java.util.UUID

class ControlService : Service() {
    private lateinit var config: ControlConfig
    private lateinit var nearby: NearbyControlManager
    private lateinit var lan: LanControlManager
    private lateinit var connectivity: ConnectivityManager

    private val seen = object : LinkedHashMap<String, Long>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 1024
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshConnectivity()
        override fun onLost(network: Network) = refreshConnectivity()
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = refreshConnectivity()
    }

    override fun onCreate() {
        super.onCreate()
        config = ControlPreferences(this).load() ?: run {
            stopSelf()
            return
        }
        createChannel()
        startForeground(NOTIFICATION_ID, notification())

        nearby = NearbyControlManager(this, "Control-${config.centerName}", ::receiveNearby)
        lan = LanControlManager(this, ::receiveLan)
        lan.start()
        nearby.start()
        startConnectivityMonitor()
        ControlRuntime.setStatus("Control Center listening for trusted devices")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TEST -> sendTestAlert()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (::nearby.isInitialized) nearby.stop()
        if (::lan.isInitialized) lan.stop()
        if (::connectivity.isInitialized) runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        ControlRuntime.setStatus("Control network stopped")
        super.onDestroy()
    }

    private fun receiveNearby(sourceEndpointId: String, payload: ByteArray) {
        val message = verifyAndMark(payload) ?: return
        ControlRuntime.handle(message, "Nearby / Bluetooth")
        nearby.broadcast(payload, sourceEndpointId)
        lan.broadcast(payload)
    }

    private fun receiveLan(sourceAddress: String, payload: ByteArray) {
        val message = verifyAndMark(payload) ?: return
        ControlRuntime.handle(message, "Wi-Fi LAN $sourceAddress")
        nearby.broadcast(payload)
    }

    private fun sendTestAlert() {
        val message = CommunityMessage(
            messageId = UUID.randomUUID().toString(),
            alertId = UUID.randomUUID().toString(),
            type = CommunityMessageType.ALERT,
            actorId = config.centerId,
            actorName = config.centerName,
            createdAtEpochMs = System.currentTimeMillis(),
            level = AlertLevel.MONITOR,
            isTest = true
        )
        val signed = CommunityMessageCodec.sign(message, config.communityKey)
        val payload = CommunityMessageCodec.encode(signed)
        markNew(signed.messageId)
        ControlRuntime.handle(signed, "Control Center")
        nearby.broadcast(payload)
        lan.broadcast(payload)
        ControlRuntime.setStatus("Test alert broadcast to community network")
    }

    private fun verifyAndMark(payload: ByteArray): CommunityMessage? {
        val message = CommunityMessageCodec.decodeAndVerify(payload, config.communityKey) ?: return null
        return if (markNew(message.messageId)) message else null
    }

    private fun markNew(id: String): Boolean = synchronized(seen) {
        if (seen.containsKey(id)) false else {
            seen[id] = System.currentTimeMillis()
            true
        }
    }

    private fun startConnectivityMonitor() {
        connectivity = getSystemService(ConnectivityManager::class.java)
        runCatching {
            val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            connectivity.registerNetworkCallback(request, networkCallback)
        }
        refreshConnectivity()
    }

    private fun refreshConnectivity() {
        if (!::connectivity.isInitialized) return
        val networks = connectivity.allNetworks.toList()
        val internet = networks.any { network ->
            connectivity.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }
        val cellular = networks.any { network ->
            val caps = connectivity.getNetworkCapabilities(network)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        ControlRuntime.setInternetAvailable(internet)
        ControlRuntime.setCellularAvailable(cellular)
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Community Control Network", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notification(): android.app.Notification {
        val open = PendingIntent.getActivity(
            this,
            70,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Community Control Center active")
            .setContentText("Listening over Nearby/Bluetooth and Wi-Fi LAN")
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "community_control_network"
        private const val NOTIFICATION_ID = 901
        private const val ACTION_START = "za.co.cyberpulse.communitygadget.control.START"
        private const val ACTION_TEST = "za.co.cyberpulse.communitygadget.control.TEST"
        private const val ACTION_STOP = "za.co.cyberpulse.communitygadget.control.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ControlService::class.java).setAction(ACTION_START))
        }

        fun sendTest(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ControlService::class.java).setAction(ACTION_TEST))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ControlService::class.java).setAction(ACTION_STOP))
        }
    }
}
