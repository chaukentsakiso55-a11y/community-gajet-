package za.co.cyberpulse.communitygadget.network

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

class NearbyMeshManager(
    context: Context,
    private val endpointName: String,
    private val onPayload: (sourceEndpointId: String, payload: ByteArray) -> Unit
) {
    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val connectedEndpoints = Collections.synchronizedSet(mutableSetOf<String>())
    private val pendingEndpoints = Collections.synchronizedSet(mutableSetOf<String>())

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { bytes -> onPayload(endpointId, bytes) }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            runCatching {
                client.acceptConnection(endpointId, payloadCallback)
                    .addOnFailureListener { pendingEndpoints.remove(endpointId) }
            }.onFailure {
                pendingEndpoints.remove(endpointId)
            }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            pendingEndpoints.remove(endpointId)
            if (resolution.status.isSuccess) {
                connectedEndpoints.add(endpointId)
                updatePeerCount()
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            updatePeerCount()
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (connectedEndpoints.contains(endpointId) || !pendingEndpoints.add(endpointId)) return
            runCatching {
                client.requestConnection(endpointName.take(32), endpointId, connectionCallback)
                    .addOnFailureListener { pendingEndpoints.remove(endpointId) }
            }.onFailure {
                pendingEndpoints.remove(endpointId)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            pendingEndpoints.remove(endpointId)
        }
    }

    fun start() {
        val strategy = Strategy.P2P_CLUSTER

        runCatching {
            client.startAdvertising(
                endpointName.take(32),
                SERVICE_ID,
                connectionCallback,
                AdvertisingOptions.Builder().setStrategy(strategy).build()
            ).addOnFailureListener { error ->
                MeshRuntime.setStatus("Nearby advertising unavailable: ${error.localizedMessage ?: "permission or radio error"}; Wi-Fi LAN still active")
            }
        }.onFailure { error ->
            MeshRuntime.setStatus("Nearby advertising unavailable: ${error.localizedMessage ?: "permission or radio error"}; Wi-Fi LAN still active")
        }

        runCatching {
            client.startDiscovery(
                SERVICE_ID,
                discoveryCallback,
                DiscoveryOptions.Builder().setStrategy(strategy).build()
            ).addOnSuccessListener {
                MeshRuntime.setStatus("Nearby + Wi-Fi LAN listening")
            }.addOnFailureListener { error ->
                MeshRuntime.setStatus("Nearby discovery unavailable: ${error.localizedMessage ?: "permission or radio error"}; Wi-Fi LAN still active")
            }
        }.onFailure { error ->
            MeshRuntime.setStatus("Nearby discovery unavailable: ${error.localizedMessage ?: "permission or radio error"}; Wi-Fi LAN still active")
        }
    }

    fun broadcast(payload: ByteArray, excludeEndpointId: String? = null) {
        val targets = synchronized(connectedEndpoints) {
            connectedEndpoints.filterNot { it == excludeEndpointId }
        }
        targets.forEach { endpointId ->
            runCatching {
                client.sendPayload(endpointId, Payload.fromBytes(payload))
                    .addOnFailureListener {
                        connectedEndpoints.remove(endpointId)
                        updatePeerCount()
                    }
            }.onFailure {
                connectedEndpoints.remove(endpointId)
                updatePeerCount()
            }
        }
    }

    fun stop() {
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        runCatching { client.stopAllEndpoints() }
        connectedEndpoints.clear()
        pendingEndpoints.clear()
        updatePeerCount()
    }

    private fun updatePeerCount() {
        MeshRuntime.setConnectedPeers(connectedEndpoints.size)
    }

    private companion object {
        const val SERVICE_ID = "za.co.cyberpulse.communitygadget.offline.v1"
    }
}
