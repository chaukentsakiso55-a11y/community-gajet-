package za.co.cyberpulse.communitygadget.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import za.co.cyberpulse.communitygadget.domain.EmergencyAlert

object MeshRuntime {
    private val _status = MutableStateFlow("Mesh stopped")
    val status = _status.asStateFlow()

    private val _connectedPeers = MutableStateFlow(0)
    val connectedPeers = _connectedPeers.asStateFlow()

    private val _alerts = MutableStateFlow<List<EmergencyAlert>>(emptyList())
    val alerts = _alerts.asStateFlow()

    internal fun setStatus(value: String) {
        _status.value = value
    }

    internal fun setConnectedPeers(value: Int) {
        _connectedPeers.value = value
    }

    internal fun addAlert(alert: EmergencyAlert) {
        _alerts.update { current ->
            listOf(alert) + current.filterNot { it.id == alert.id }.take(19)
        }
    }

    internal fun clear() {
        _status.value = "Mesh stopped"
        _connectedPeers.value = 0
        _alerts.value = emptyList()
    }
}
