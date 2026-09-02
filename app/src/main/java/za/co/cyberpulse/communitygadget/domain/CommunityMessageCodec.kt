package za.co.cyberpulse.communitygadget.domain

import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object CommunityMessageCodec {
    private const val VERSION = 2
    private const val MAX_CLOCK_SKEW_MS = 30 * 60 * 1000L

    private fun encodeUnsigned(message: CommunityMessage): String = JSONObject().apply {
        put("v", VERSION)
        put("messageId", message.messageId)
        put("alertId", message.alertId)
        put("type", message.type.name)
        put("actorId", message.actorId)
        put("actorName", message.actorName)
        put("createdAt", message.createdAtEpochMs)
        put("level", message.level?.name ?: JSONObject.NULL)
        put("latitude", message.latitude ?: JSONObject.NULL)
        put("longitude", message.longitude ?: JSONObject.NULL)
        put("accuracy", message.accuracyMeters?.toDouble() ?: JSONObject.NULL)
        put("isTest", message.isTest)
    }.toString()

    fun sign(message: CommunityMessage, key: ByteArray): CommunityMessage {
        require(message.messageId.length in 8..80)
        require(message.alertId.length in 8..80)
        require(message.actorId.length in 3..80)
        require(message.actorName.length in 1..80)
        require(
            message.type == CommunityMessageType.ALERT ||
                message.type == CommunityMessageType.LOCATION_UPDATE ||
                (message.latitude == null && message.longitude == null && message.accuracyMeters == null)
        ) { "Location is only valid on alert/location messages" }
        val signature = hmac(encodeUnsigned(message.copy(signature = "")), key)
        return message.copy(signature = signature)
    }

    fun encode(message: CommunityMessage): ByteArray {
        require(message.signature.isNotBlank())
        return JSONObject(encodeUnsigned(message)).apply {
            put("signature", message.signature)
        }.toString().toByteArray(StandardCharsets.UTF_8)
    }

    fun decodeAndVerify(
        payload: ByteArray,
        key: ByteArray,
        nowEpochMs: Long = System.currentTimeMillis()
    ): CommunityMessage? = runCatching {
        if (payload.size !in 32..8192) return null
        val json = JSONObject(String(payload, StandardCharsets.UTF_8))
        if (json.getInt("v") != VERSION) return null
        val message = CommunityMessage(
            messageId = json.getString("messageId"),
            alertId = json.getString("alertId"),
            type = CommunityMessageType.valueOf(json.getString("type")),
            actorId = json.getString("actorId"),
            actorName = json.getString("actorName"),
            createdAtEpochMs = json.getLong("createdAt"),
            level = if (json.isNull("level")) null else AlertLevel.valueOf(json.getString("level")),
            latitude = json.optNullableDouble("latitude"),
            longitude = json.optNullableDouble("longitude"),
            accuracyMeters = json.optNullableDouble("accuracy")?.toFloat(),
            isTest = json.optBoolean("isTest", false),
            signature = json.getString("signature")
        )
        if (message.messageId.length !in 8..80 || message.alertId.length !in 8..80) return null
        if (message.actorId.length !in 3..80 || message.actorName.length !in 1..80) return null
        if (kotlin.math.abs(nowEpochMs - message.createdAtEpochMs) > MAX_CLOCK_SKEW_MS) return null
        if (message.latitude != null && message.latitude !in -90.0..90.0) return null
        if (message.longitude != null && message.longitude !in -180.0..180.0) return null
        if (message.type == CommunityMessageType.ALERT && message.level == null) return null
        if (
            message.type !in setOf(CommunityMessageType.ALERT, CommunityMessageType.LOCATION_UPDATE) &&
            (message.latitude != null || message.longitude != null || message.accuracyMeters != null)
        ) return null
        val expected = hmac(encodeUnsigned(message.copy(signature = "")), key)
        val valid = MessageDigest.isEqual(
            expected.toByteArray(StandardCharsets.UTF_8),
            message.signature.toByteArray(StandardCharsets.UTF_8)
        )
        message.takeIf { valid }
    }.getOrNull()

    private fun hmac(message: String, key: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(message.toByteArray(StandardCharsets.UTF_8)))
    }

    private fun JSONObject.optNullableDouble(name: String): Double? =
        if (isNull(name)) null else getDouble(name)
}
