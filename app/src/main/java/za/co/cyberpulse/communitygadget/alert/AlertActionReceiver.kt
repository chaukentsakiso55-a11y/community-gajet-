package za.co.cyberpulse.communitygadget.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import za.co.cyberpulse.communitygadget.network.MeshService

class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_ACKNOWLEDGE) MeshService.acknowledge(context)
    }

    companion object {
        const val ACTION_ACKNOWLEDGE = "za.co.cyberpulse.communitygadget.ACKNOWLEDGE"
    }
}
