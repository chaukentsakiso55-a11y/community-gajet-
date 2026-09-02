package za.co.cyberpulse.communitygadget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertCodecTest {
    private val key = AlertCodec.deriveCommunityKey("Zone-Safety-2026")
    private val now = 1_800_000_000_000L

    @Test
    fun signedEmergencyRoundTripsWithLocation() {
        val alert = AlertCodec.sign(
            EmergencyAlert(
                id = "alert-123456",
                originId = "terminal-123",
                originName = "House 14",
                level = AlertLevel.EMERGENCY,
                createdAtEpochMs = now,
                latitude = -23.3021,
                longitude = 30.7186,
                accuracyMeters = 7.5f
            ),
            key
        )

        val decoded = AlertCodec.decodeAndVerify(AlertCodec.encode(alert), key, now)

        assertEquals(AlertLevel.EMERGENCY, decoded?.level)
        assertEquals(-23.3021, decoded?.latitude ?: 0.0, 0.000001)
        assertTrue(decoded?.hasLocation() == true)
    }

    @Test
    fun tamperedAlertIsRejected() {
        val signed = AlertCodec.sign(baseAlert(AlertLevel.SECURE), key)
        val tampered = String(AlertCodec.encode(signed)).replace("SECURE", "EMERGENCY").toByteArray()

        assertNull(AlertCodec.decodeAndVerify(tampered, key, now))
    }

    @Test
    fun nonEmergencyCannotBeSignedWithLocation() {
        val monitorWithLocation = baseAlert(AlertLevel.MONITOR).copy(
            latitude = -23.30,
            longitude = 30.71,
            accuracyMeters = 10f
        )

        assertThrows(IllegalArgumentException::class.java) {
            AlertCodec.sign(monitorWithLocation, key)
        }
    }

    @Test
    fun wrongCommunityKeyIsRejected() {
        val signed = AlertCodec.sign(baseAlert(AlertLevel.SECURE), key)
        val otherKey = AlertCodec.deriveCommunityKey("Different-Zone-2026")

        assertNull(AlertCodec.decodeAndVerify(AlertCodec.encode(signed), otherKey, now))
    }

    private fun baseAlert(level: AlertLevel) = EmergencyAlert(
        id = "alert-123456",
        originId = "terminal-123",
        originName = "House 14",
        level = level,
        createdAtEpochMs = now,
        latitude = null,
        longitude = null,
        accuracyMeters = null
    )
}
