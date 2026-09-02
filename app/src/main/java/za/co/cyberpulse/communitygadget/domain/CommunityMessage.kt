package za.co.cyberpulse.communitygadget.domain

enum class CommunityMessageType {
    ALERT,
    RECEIVED,
    ACKNOWLEDGED,
    RESPONDING,
    LOCATION_UPDATE,
    END_ALERT
}

data class CommunityMessage(
    val messageId: String,
    val alertId: String,
    val type: CommunityMessageType,
    val actorId: String,
    val actorName: String,
    val createdAtEpochMs: Long,
    val level: AlertLevel? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val isTest: Boolean = false,
    val signature: String = ""
) {
    fun hasLocation(): Boolean = latitude != null && longitude != null
}
