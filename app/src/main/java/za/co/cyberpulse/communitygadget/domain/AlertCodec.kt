package za.co.cyberpulse.communitygadget.domain

import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object AlertCodec {
    private const val VERSION = 1
    private const val KEY_ITERATIONS = 120_000
    private const val MAX_CLOCK_SKEW_MS = 10 * 60 * 1000L
    private val keySalt = "community-gadget-zone-key-v1".toByteArray(StandardCharsets.UTF_8)

    fun deriveCommunityKey(communityCode: String): ByteArray {
        require(communityCode.trim().length >= 8) { "Community code must contain at least 8 characters" }
        val spec = PBEKeySpec(communityCode.trim().toCharArray(), keySalt, KEY_ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun encodeUnsigned(alert: EmergencyAlert): String = JSONObject().apply {
        put("v", VERSION)
        put("id", alert.id)
        put("originId", alert.originId)
        put("originName", alert.originName)
        put("level", alert.level.name)
        put("createdAt", alert.createdAtEpochMs)
        put("latitude", alert.latitude ?: JSONObject.NULL)
        put("longitude", alert.longitude ?: JSONObject.NULL)
        put("accuracy", alert.accuracyMeters?.toDouble() ?: JSONObject.NULL)
    }.toString()

    fun sign(alert: EmergencyAlert, key: ByteArray): EmergencyAlert {
        require(
            alert.level == AlertLevel.EMERGENCY ||
                (alert.latitude == null && alert.longitude == null && alert.accuracyMeters == null)
        ) { "Location is permitted only for emergency alerts" }
        val signature = hmac(encodeUnsigned(alert.copy(signature = "")), key)
        return alert.copy(signature = signature)
    }

    fun encode(alert: EmergencyAlert): ByteArray {
        require(alert.signature.isNotBlank()) { "Alert must be signed before encoding" }
        return JSONObject(encodeUnsigned(alert)).apply {
            put("signature", alert.signature)
        }.toString().toByteArray(StandardCharsets.UTF_8)
    }

    fun decodeAndVerify(
        payload: ByteArray,
        key: ByteArray,
        nowEpochMs: Long = System.currentTimeMillis(),
        maxClockSkewMs: Long = MAX_CLOCK_SKEW_MS
    ): EmergencyAlert? = runCatching {
        if (payload.size !in 32..4096) return null
        val json = JSONObject(String(payload, StandardCharsets.UTF_8))
        if (json.getInt("v") != VERSION) return null
        val alert = EmergencyAlert(
            id = json.getString("id"),
            originId = json.getString("originId"),
            originName = json.getString("originName"),
            level = AlertLevel.valueOf(json.getString("level")),
            createdAtEpochMs = json.getLong("createdAt"),
            latitude = json.optNullableDouble("latitude"),
            longitude = json.optNullableDouble("longitude"),
            accuracyMeters = json.optNullableDouble("accuracy")?.toFloat(),
            signature = json.getString("signature")
        )
        if (alert.id.length !in 8..80 || alert.originId.length !in 3..80) return null
        if (alert.originName.length !in 1..80) return null
        if (kotlin.math.abs(nowEpochMs - alert.createdAtEpochMs) > maxClockSkewMs) return null
        if (alert.latitude != null && alert.latitude !in -90.0..90.0) return null
        if (alert.longitude != null && alert.longitude !in -180.0..180.0) return null
        if (
            alert.level != AlertLevel.EMERGENCY &&
            (alert.latitude != null || alert.longitude != null || alert.accuracyMeters != null)
        ) return null
        val expected = hmac(encodeUnsigned(alert.copy(signature = "")), key)
        val valid = MessageDigest.isEqual(
            expected.toByteArray(StandardCharsets.UTF_8),
            alert.signature.toByteArray(StandardCharsets.UTF_8)
        )
        alert.takeIf { valid }
    }.getOrNull()

    fun keyToString(key: ByteArray): String = Base64.getEncoder().encodeToString(key)

    fun keyFromString(encoded: String): ByteArray = Base64.getDecoder().decode(encoded)

    private fun hmac(message: String, key: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(message.toByteArray(StandardCharsets.UTF_8)))
    }

    private fun JSONObject.optNullableDouble(name: String): Double? =
        if (isNull(name)) null else getDouble(name)
}
