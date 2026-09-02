package za.co.cyberpulse.communitygadget

import android.app.Application
import za.co.cyberpulse.communitygadget.alert.AlertNotificationManager
import za.co.cyberpulse.communitygadget.data.AppPreferences

class CommunityGadgetApplication : Application() {
    lateinit var preferences: AppPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        AlertNotificationManager(this).createChannels()
    }
}
