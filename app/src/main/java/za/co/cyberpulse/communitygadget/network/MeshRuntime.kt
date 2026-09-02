package za.co.cyberpulse.communitygadget.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import za.co.cyberpulse.communitygadget.domain.AlertLevel
import za.co.cyberpulse.communitygadget.domain.CommunityMessage
import za.co.cyberpulse.communitygadget.domain.CommunityMessageType
import za.co.cyberpulse.communitygadget.domain.EmergencyAlert

data class ActiveAlertState(
    val alert: EmergencyAlert,
    val isTest: Boolean,
    val localOrigin: Boolean
)

data class AlertProgress(
    val alertId: String = "",
    val receivedBy: Map<String, String> = emptyMap(),
    val acknowledgedBy: Map<String, String> = emptyMap(),
    val responders: Map<String, String> = emptyMap()
)

object MeshRuntime {
    private val _status = MutableStateFlow("Mesh stopped")
    val status = _status.asStateFlow()

    private val _connectedPeers = MutableStateFlow(0)
    val connectedPeers = _connectedPeers.asStateFlow()

    private val _alerts = MutableStateFlow<List<EmergencyAlert>>(emptyList())
    val alerts = _alerts.asStateFlow()

    private val _activeAlert = MutableStateFlow<ActiveAlertState?>(null)
    val activeAlert = _activeAlert.asStateFlow()

    private val _progress = MutableStateFlow(AlertProgress())
    val progress = _progress.asStateFlow()

    private val _silencedAlertIds = MutableStateFlow<Set<String>>(emptySet())
    val silencedAlertIds = _silencedAlertIds.asStateFlow()

    private val _nearbyReady = MutableStateFlow(false)
    val nearbyReady = _nearbyReady.asStateFlow()

    private val _lanReady = MutableStateFlow(false)
    val lanReady = _lanReady.asStateFlow()

    private val _internetAvailable = MutableStateFlow(false)
    val internetAvailable = _internetAvailable.asStateFlow()

    private val _lastTransport = MutableStateFlow("None")
    val lastTransport = _lastTransport.asStateFlow()

    internal fun setStatus(value: String) { _status.value = value }
    internal fun setConnectedPeers(value: Int) { _connectedPeers.value = value }
    internal fun setNearbyReady(value: Boolean) { _nearbyReady.value = value }
    internal fun setLanReady(value: Boolean) { _lanReady.value = value }
    internal fun setInternetAvailable(value: Boolean) { _internetAvailable.value = value }
    internal fun setLastTransport(value: String) { _lastTransport.value = value }

    internal fun handleMessage(message: CommunityMessage, localTerminalId: String) {
        when (message.type) {
            CommunityMessageType.ALERT -> {
                val level = message.level ?: return
                val alert = EmergencyAlert(
                    id = message.alertId,
                    originId = message.actorId,
                    originName = message.actorName,
                    level = level,
                    createdAtEpochMs = message.createdAtEpochMs,
                    latitude = message.latitude,
                    longitude = message.longitude,
                    accuracyMeters = message.accuracyMeters
                )
                _alerts.update { current -> listOf(alert) + current.filterNot { it.id == alert.id }.take(24) }
                _progress.value = AlertProgress(alertId = message.alertId)
                if (level == AlertLevel.EMERGENCY || message.isTest) {
                    _activeAlert.value = ActiveAlertState(
                        alert = alert,
                        isTest = message.isTest,
                        localOrigin = message.actorId == localTerminalId
                    )
                }
            }

            CommunityMessageType.LOCATION_UPDATE -> {
                val current = _activeAlert.value ?: return
                if (current.alert.id != message.alertId) return
                _activeAlert.value = current.copy(
                    alert = current.alert.copy(
                        latitude = message.latitude ?: current.alert.latitude,
                        longitude = message.longitude ?: current.alert.longitude,
                        accuracyMeters = message.accuracyMeters ?: current.alert.accuracyMeters
                    )
                )
                _alerts.update { alerts ->
                    alerts.map { alert ->
                        if (alert.id == message.alertId) current.alert.copy(
                            latitude = message.latitude ?: current.alert.latitude,
                            longitude = message.longitude ?: current.alert.longitude,
                            accuracyMeters = message.accuracyMeters ?: current.alert.accuracyMeters
                        ) else alert
                    }
                }
            }

            CommunityMessageType.RECEIVED -> updateProgress(message) { progress ->
                progress.copy(receivedBy = progress.receivedBy + (message.actorId to message.actorName))
            }

            CommunityMessageType.ACKNOWLEDGED -> updateProgress(message) { progress ->
                progress.copy(
                    receivedBy = progress.receivedBy + (message.actorId to message.actorName),
                    acknowledgedBy = progress.acknowledgedBy + (message.actorId to message.actorName)
                )
            }

            CommunityMessageType.RESPONDING -> updateProgress(message) { progress ->
                progress.copy(
                    receivedBy = progress.receivedBy + (message.actorId to message.actorName),
                    acknowledgedBy = progress.acknowledgedBy + (message.actorId to message.actorName),
                    responders = progress.responders + (message.actorId to message.actorName)
                )
            }

            CommunityMessageType.END_ALERT -> {
                if (_activeAlert.value?.alert?.id == message.alertId) _activeAlert.value = null
                _silencedAlertIds.update { it - message.alertId }
            }
        }
    }

    private fun updateProgress(message: CommunityMessage, change: (AlertProgress) -> AlertProgress) {
        val current = _progress.value
        if (current.alertId != message.alertId) return
        _progress.value = change(current)
    }

    internal fun silenceAlert(alertId: String) {
        _silencedAlertIds.update { it + alertId }
    }

    internal fun clear() {
        _status.value = "Mesh stopped"
        _connectedPeers.value = 0
        _alerts.value = emptyList()
        _activeAlert.value = null
        _progress.value = AlertProgress()
        _silencedAlertIds.value = emptySet()
        _nearbyReady.value = false
        _lanReady.value = false
        _internetAvailable.value = false
        _lastTransport.value = "None"
    }
}
