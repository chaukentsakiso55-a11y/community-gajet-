package za.co.cyberpulse.communitygadget.control.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import za.co.cyberpulse.communitygadget.control.protocol.AlertLevel
import za.co.cyberpulse.communitygadget.control.protocol.CommunityMessage
import za.co.cyberpulse.communitygadget.control.protocol.CommunityMessageType

data class TerminalPresence(
    val terminalId: String,
    val terminalName: String,
    val lastSeenEpochMs: Long,
    val transport: String
)

data class DashboardAlert(
    val alertId: String,
    val originId: String,
    val originName: String,
    val level: AlertLevel,
    val createdAtEpochMs: Long,
    val isTest: Boolean,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val receivedBy: Map<String, String> = emptyMap(),
    val acknowledgedBy: Map<String, String> = emptyMap(),
    val responders: Map<String, String> = emptyMap(),
    val ended: Boolean = false
)

object ControlRuntime {
    private val _status = MutableStateFlow("Control network stopped")
    val status = _status.asStateFlow()
    private val _connectedPeers = MutableStateFlow(0)
    val connectedPeers = _connectedPeers.asStateFlow()
    private val _devices = MutableStateFlow<List<TerminalPresence>>(emptyList())
    val devices = _devices.asStateFlow()
    private val _alerts = MutableStateFlow<List<DashboardAlert>>(emptyList())
    val alerts = _alerts.asStateFlow()
    private val _nearbyReady = MutableStateFlow(false)
    val nearbyReady = _nearbyReady.asStateFlow()
    private val _lanReady = MutableStateFlow(false)
    val lanReady = _lanReady.asStateFlow()
    private val _internetAvailable = MutableStateFlow(false)
    val internetAvailable = _internetAvailable.asStateFlow()
    private val _cellularAvailable = MutableStateFlow(false)
    val cellularAvailable = _cellularAvailable.asStateFlow()
    private val _lastTransport = MutableStateFlow("None")
    val lastTransport = _lastTransport.asStateFlow()

    internal fun setStatus(value: String) { _status.value = value }
    internal fun setConnectedPeers(value: Int) { _connectedPeers.value = value }
    internal fun setNearbyReady(value: Boolean) { _nearbyReady.value = value }
    internal fun setLanReady(value: Boolean) { _lanReady.value = value }
    internal fun setInternetAvailable(value: Boolean) { _internetAvailable.value = value }
    internal fun setCellularAvailable(value: Boolean) { _cellularAvailable.value = value }
    internal fun setLastTransport(value: String) { _lastTransport.value = value }

    internal fun handle(message: CommunityMessage, transport: String) {
        _lastTransport.value = transport
        touchDevice(message.actorId, message.actorName, transport, message.createdAtEpochMs)
        when (message.type) {
            CommunityMessageType.HEARTBEAT -> Unit
            CommunityMessageType.ALERT -> {
                val level = message.level ?: return
                val alert = DashboardAlert(
                    alertId = message.alertId,
                    originId = message.actorId,
                    originName = message.actorName,
                    level = level,
                    createdAtEpochMs = message.createdAtEpochMs,
                    isTest = message.isTest,
                    latitude = message.latitude,
                    longitude = message.longitude,
                    accuracyMeters = message.accuracyMeters
                )
                _alerts.update { current -> listOf(alert) + current.filterNot { it.alertId == alert.alertId }.take(49) }
            }
            CommunityMessageType.LOCATION_UPDATE -> updateAlert(message.alertId) { current ->
                current.copy(
                    latitude = message.latitude ?: current.latitude,
                    longitude = message.longitude ?: current.longitude,
                    accuracyMeters = message.accuracyMeters ?: current.accuracyMeters
                )
            }
            CommunityMessageType.RECEIVED -> updateAlert(message.alertId) { current ->
                current.copy(receivedBy = current.receivedBy + (message.actorId to message.actorName))
            }
            CommunityMessageType.ACKNOWLEDGED -> updateAlert(message.alertId) { current ->
                current.copy(
                    receivedBy = current.receivedBy + (message.actorId to message.actorName),
                    acknowledgedBy = current.acknowledgedBy + (message.actorId to message.actorName)
                )
            }
            CommunityMessageType.RESPONDING -> updateAlert(message.alertId) { current ->
                current.copy(
                    receivedBy = current.receivedBy + (message.actorId to message.actorName),
                    acknowledgedBy = current.acknowledgedBy + (message.actorId to message.actorName),
                    responders = current.responders + (message.actorId to message.actorName)
                )
            }
            CommunityMessageType.END_ALERT -> updateAlert(message.alertId) { it.copy(ended = true) }
        }
    }

    private fun touchDevice(id: String, name: String, transport: String, time: Long) {
        _devices.update { current ->
            val updated = TerminalPresence(id, name, time, transport)
            listOf(updated) + current.filterNot { it.terminalId == id }.take(199)
        }
    }

    private fun updateAlert(alertId: String, change: (DashboardAlert) -> DashboardAlert) {
        _alerts.update { current -> current.map { if (it.alertId == alertId) change(it) else it } }
    }

    internal fun clear() {
        _status.value = "Control network stopped"
        _connectedPeers.value = 0
        _devices.value = emptyList()
        _alerts.value = emptyList()
        _nearbyReady.value = false
        _lanReady.value = false
        _internetAvailable.value = false
        _cellularAvailable.value = false
        _lastTransport.value = "None"
    }
}
