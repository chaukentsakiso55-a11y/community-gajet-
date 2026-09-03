package za.co.cyberpulse.communitygadget.control.network

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import java.util.Collections

class NearbyControlManager(
    context: Context,
    private val endpointName: String,
    private val onPayload: (sourceEndpointId: String, payload: ByteArray) -> Unit
) {
    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val connected = Collections.synchronizedSet(mutableSetOf<String>())
    private val pending = Collections.synchronizedSet(mutableSetOf<String>())

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { onPayload(endpointId, it) }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            client.acceptConnection(endpointId, payloadCallback).addOnFailureListener { pending.remove(endpointId) }
        }
        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            pending.remove(endpointId)
            if (resolution.status.isSuccess) {
                connected.add(endpointId)
                updateCount()
            }
        }
        override fun onDisconnected(endpointId: String) {
            connected.remove(endpointId)
            updateCount()
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (connected.contains(endpointId) || !pending.add(endpointId)) return
            client.requestConnection(endpointName.take(32), endpointId, connectionCallback)
                .addOnFailureListener { pending.remove(endpointId) }
        }
        override fun onEndpointLost(endpointId: String) { pending.remove(endpointId) }
    }

    fun start() {
        val strategy = Strategy.P2P_CLUSTER
        client.startAdvertising(
            endpointName.take(32),
            SERVICE_ID,
            connectionCallback,
            AdvertisingOptions.Builder().setStrategy(strategy).build()
        ).addOnSuccessListener {
            ControlRuntime.setNearbyReady(true)
        }.addOnFailureListener {
            ControlRuntime.setNearbyReady(false)
        }

        client.startDiscovery(
            SERVICE_ID,
            discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(strategy).build()
        ).addOnSuccessListener {
            ControlRuntime.setNearbyReady(true)
        }.addOnFailureListener {
            ControlRuntime.setNearbyReady(false)
        }
    }

    fun broadcast(payload: ByteArray, excludeEndpointId: String? = null) {
        val targets = synchronized(connected) { connected.filterNot { it == excludeEndpointId } }
        targets.forEach { endpointId ->
            client.sendPayload(endpointId, Payload.fromBytes(payload)).addOnFailureListener {
                connected.remove(endpointId)
                updateCount()
            }
        }
    }

    fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        connected.clear()
        pending.clear()
        ControlRuntime.setNearbyReady(false)
        updateCount()
    }

    private fun updateCount() { ControlRuntime.setConnectedPeers(connected.size) }

    private companion object {
        const val SERVICE_ID = "za.co.cyberpulse.communitygadget.offline.v1"
    }
}
