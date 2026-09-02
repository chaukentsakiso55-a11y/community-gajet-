package za.co.cyberpulse.communitygadget.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import za.co.cyberpulse.communitygadget.CommunityGadgetApplication
import za.co.cyberpulse.communitygadget.domain.AlertLevel
import za.co.cyberpulse.communitygadget.domain.CommunityMessage
import za.co.cyberpulse.communitygadget.domain.CommunityMessageCodec
import za.co.cyberpulse.communitygadget.domain.CommunityMessageType
import za.co.cyberpulse.communitygadget.location.EmergencyLocationProvider
import za.co.cyberpulse.communitygadget.network.MeshRuntime
import za.co.cyberpulse.communitygadget.network.MeshService
import java.util.UUID

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CommunityGadgetApplication
    private val locationProvider = EmergencyLocationProvider(application)

    private val _config = MutableStateFlow(app.preferences.loadConfig())
    val config = _config.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending = _isSending.asStateFlow()

    val meshStatus = MeshRuntime.status
    val connectedPeers = MeshRuntime.connectedPeers
    val alerts = MeshRuntime.alerts
    val activeAlert = MeshRuntime.activeAlert
    val progress = MeshRuntime.progress
    val silencedAlertIds = MeshRuntime.silencedAlertIds
    val nearbyReady = MeshRuntime.nearbyReady
    val lanReady = MeshRuntime.lanReady
    val internetAvailable = MeshRuntime.internetAvailable
    val lastTransport = MeshRuntime.lastTransport

    fun completeSetup(terminalName: String, communityCode: String): Result<Unit> = runCatching {
        require(terminalName.trim().length >= 3) { "Enter a terminal or home name" }
        val saved = app.preferences.saveConfig(terminalName, communityCode)
        _config.value = saved
    }

    fun startMesh() {
        if (_config.value != null) MeshService.start(getApplication())
    }

    fun sendAlert(level: AlertLevel) = sendAlertInternal(level, isTest = false)

    fun sendTestAlert() = sendAlertInternal(AlertLevel.EMERGENCY, isTest = true)

    private fun sendAlertInternal(level: AlertLevel, isTest: Boolean) {
        val currentConfig = _config.value ?: return
        if (_isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            try {
                val location = if (level == AlertLevel.EMERGENCY && !isTest) {
                    locationProvider.currentEmergencyLocation()
                } else null
                val alertId = UUID.randomUUID().toString()
                val unsigned = CommunityMessage(
                    messageId = UUID.randomUUID().toString(),
                    alertId = alertId,
                    type = CommunityMessageType.ALERT,
                    actorId = currentConfig.terminalId,
                    actorName = currentConfig.terminalName,
                    createdAtEpochMs = System.currentTimeMillis(),
                    level = level,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    accuracyMeters = location?.accuracy,
                    isTest = isTest
                )
                val signed = CommunityMessageCodec.sign(unsigned, currentConfig.communityKey)
                MeshService.send(getApplication(), CommunityMessageCodec.encode(signed))
            } finally {
                _isSending.value = false
            }
        }
    }

    fun acknowledgeAlert() = MeshService.acknowledge(getApplication())

    fun respondToAlert() = MeshService.responding(getApplication())

    fun endEmergency() = MeshService.endEmergency(getApplication())

    fun resetTerminal() {
        MeshService.stop(getApplication())
        app.preferences.clearConfig()
        _config.value = null
        MeshRuntime.clear()
    }
}
