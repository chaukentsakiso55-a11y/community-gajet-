package za.co.cyberpulse.communitygadget.control

import android.content.Context
import za.co.cyberpulse.communitygadget.control.protocol.CommunityMessageCodec
import java.util.UUID

data class ControlConfig(
    val centerId: String,
    val centerName: String,
    val communityKey: ByteArray
)

class ControlPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("community_control", Context.MODE_PRIVATE)

    fun load(): ControlConfig? {
        val name = prefs.getString("name", null) ?: return null
        val key = prefs.getString("key", null) ?: return null
        val id = prefs.getString("id", null) ?: return null
        return runCatching { ControlConfig(id, name, CommunityMessageCodec.keyFromString(key)) }.getOrNull()
    }

    fun save(name: String, communityCode: String): ControlConfig {
        require(name.trim().length >= 3) { "Enter a control center name" }
        val key = CommunityMessageCodec.deriveCommunityKey(communityCode)
        val id = prefs.getString("id", null) ?: UUID.randomUUID().toString()
        prefs.edit()
            .putString("id", id)
            .putString("name", name.trim())
            .putString("key", CommunityMessageCodec.keyToString(key))
            .apply()
        return ControlConfig(id, name.trim(), key)
    }

    fun clear() { prefs.edit().clear().apply() }
}
