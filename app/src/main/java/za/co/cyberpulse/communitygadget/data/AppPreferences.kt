package za.co.cyberpulse.communitygadget.data

import android.content.Context
import za.co.cyberpulse.communitygadget.domain.AlertCodec
import java.util.UUID

data class TerminalConfig(
    val terminalId: String,
    val terminalName: String,
    val communityKey: ByteArray
)

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("community_gadget", Context.MODE_PRIVATE)

    fun loadConfig(): TerminalConfig? {
        val name = preferences.getString(KEY_TERMINAL_NAME, null)?.takeIf { it.isNotBlank() } ?: return null
        val encodedKey = preferences.getString(KEY_COMMUNITY_KEY, null)?.takeIf { it.isNotBlank() } ?: return null
        val id = preferences.getString(KEY_TERMINAL_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            TerminalConfig(id, name, AlertCodec.keyFromString(encodedKey))
        }.getOrNull()
    }

    fun saveConfig(terminalName: String, communityCode: String): TerminalConfig {
        val existingId = preferences.getString(KEY_TERMINAL_ID, null) ?: UUID.randomUUID().toString()
        val key = AlertCodec.deriveCommunityKey(communityCode)
        preferences.edit()
            .putString(KEY_TERMINAL_ID, existingId)
            .putString(KEY_TERMINAL_NAME, terminalName.trim())
            .putString(KEY_COMMUNITY_KEY, AlertCodec.keyToString(key))
            .apply()
        return TerminalConfig(existingId, terminalName.trim(), key)
    }

    fun clearConfig() {
        preferences.edit().remove(KEY_TERMINAL_NAME).remove(KEY_COMMUNITY_KEY).apply()
    }

    private companion object {
        const val KEY_TERMINAL_ID = "terminal_id"
        const val KEY_TERMINAL_NAME = "terminal_name"
        const val KEY_COMMUNITY_KEY = "community_key"
    }
}
