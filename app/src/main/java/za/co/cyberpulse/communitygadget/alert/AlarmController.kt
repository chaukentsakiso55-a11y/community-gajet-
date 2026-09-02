package za.co.cyberpulse.communitygadget.alert

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import za.co.cyberpulse.communitygadget.domain.AlertLevel

class AlarmController(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var toneGenerator: ToneGenerator? = null
    private var emergencyActive = false

    private val alarmPulse = object : Runnable {
        override fun run() {
            if (!emergencyActive) return
            tone().startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 850)
            handler.postDelayed(this, 1_250L)
        }
    }

    fun announce(level: AlertLevel) {
        when (level) {
            AlertLevel.SECURE -> tone().startTone(ToneGenerator.TONE_PROP_ACK, 180)
            AlertLevel.MONITOR -> {
                tone().startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 500)
                vibrate(longArrayOf(0, 300, 180, 300), repeat = -1)
            }
            AlertLevel.EMERGENCY -> startEmergency()
        }
    }

    fun startEmergency() {
        if (emergencyActive) return
        emergencyActive = true
        vibrate(longArrayOf(0, 700, 250, 700, 250), repeat = 1)
        handler.post(alarmPulse)
    }

    fun acknowledge() {
        emergencyActive = false
        handler.removeCallbacks(alarmPulse)
        toneGenerator?.stopTone()
        vibrator().cancel()
    }

    fun release() {
        acknowledge()
        toneGenerator?.release()
        toneGenerator = null
    }

    private fun tone(): ToneGenerator = toneGenerator ?: ToneGenerator(AudioManager.STREAM_ALARM, 100)
        .also { toneGenerator = it }

    private fun vibrate(pattern: LongArray, repeat: Int) {
        val effect = VibrationEffect.createWaveform(pattern, repeat)
        vibrator().vibrate(effect)
    }

    @Suppress("DEPRECATION")
    private fun vibrator(): Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
}
