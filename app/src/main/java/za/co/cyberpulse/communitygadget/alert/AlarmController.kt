package za.co.cyberpulse.communitygadget.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
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
    private var emergencyRingtone: Ringtone? = null
    private var emergencyActive = false

    private val alarmPulse = object : Runnable {
        override fun run() {
            if (!emergencyActive) return
            if (emergencyRingtone?.isPlaying != true) {
                tone().startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 850)
            }
            handler.postDelayed(this, 1_250L)
        }
    }

    fun announce(level: AlertLevel, isTest: Boolean = false) {
        if (isTest) {
            announceTest()
            return
        }
        when (level) {
            AlertLevel.SECURE -> tone().startTone(ToneGenerator.TONE_PROP_ACK, 180)
            AlertLevel.MONITOR -> {
                tone().startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 500)
                vibrate(longArrayOf(0, 300, 180, 300), repeat = -1)
            }
            AlertLevel.EMERGENCY -> startEmergency()
        }
    }

    private fun announceTest() {
        acknowledge()
        tone().startTone(ToneGenerator.TONE_PROP_BEEP2, 700)
        vibrate(longArrayOf(0, 180, 120, 180), repeat = -1)
    }

    fun startEmergency() {
        if (emergencyActive) return
        emergencyActive = true
        vibrate(longArrayOf(0, 700, 250, 700, 250), repeat = 1)
        emergencyRingtone = createEmergencyRingtone()?.also { ringtone ->
            runCatching { ringtone.play() }
        }
        handler.post(alarmPulse)
    }

    fun acknowledge() {
        emergencyActive = false
        handler.removeCallbacks(alarmPulse)
        emergencyRingtone?.let { ringtone -> runCatching { ringtone.stop() } }
        emergencyRingtone = null
        toneGenerator?.stopTone()
        vibrator().cancel()
    }

    fun release() {
        acknowledge()
        toneGenerator?.release()
        toneGenerator = null
    }

    private fun createEmergencyRingtone(): Ringtone? = runCatching {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        RingtoneManager.getRingtone(context, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
        }
    }.getOrNull()

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
