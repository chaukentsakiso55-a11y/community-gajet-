package za.co.cyberpulse.communitygadget.domain

data class EmergencyAlert(
    val id: String,
    val originId: String,
    val originName: String,
    val level: AlertLevel,
    val createdAtEpochMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val signature: String = ""
) {
    fun hasLocation(): Boolean = latitude != null && longitude != null
}
