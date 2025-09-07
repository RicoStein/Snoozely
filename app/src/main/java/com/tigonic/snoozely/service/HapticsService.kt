package com.tigonic.snoozely.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.tigonic.snoozely.util.SettingsPreferenceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Kurzlebiger Service für haptisches Feedback zum Timer.
 * - Respektiert das Setting TIMER_VIBRATE
 * - Unterstützt One-Shot und Pattern, API-kompatibel fallbackend
 * - Beendet sich selbst nach Ausführung
 */
class HapticsService : Service() {

    companion object {
        private const val TAG = "HapticsService"
    }

    object Actions {
        const val REMINDER = "com.tigonic.snoozely.haptics.REMINDER"
        const val PRE_FINISH = "com.tigonic.snoozely.haptics.PRE_FINISH"
        const val FINISH = "com.tigonic.snoozely.haptics.FINISH"
        const val TEST = "com.tigonic.snoozely.haptics.TEST"
    }

    object Extras {
        const val EXTRA_AMPLITUDE = "extra_amplitude"      // 1..255 (oder DEFAULT_AMPLITUDE)
        const val EXTRA_DURATION_MS = "extra_duration_ms"  // One-Shot Dauer
        const val EXTRA_PATTERN = "extra_pattern"          // long[]
        const val EXTRA_REPEAT = "extra_repeat"            // -1 = keine
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try {
                val hapticsEnabled = SettingsPreferenceHelper
                    .getTimerVibrate(applicationContext).first()
                if (!hapticsEnabled) {
                    Log.d(TAG, "Haptics skipped: setting disabled.")
                    stopSelf()
                    return@launch
                }

                when (intent?.action) {
                    Actions.REMINDER -> {
                        vibrateOneShot(
                            durationMs = intent.getLongExtra(Extras.EXTRA_DURATION_MS, 80L),
                            amplitude = intent.getIntExtra(
                                Extras.EXTRA_AMPLITUDE,
                                VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                    }
                    Actions.PRE_FINISH -> {
                        val defaultPattern = longArrayOf(0, 70, 60, 120)
                        vibratePattern(
                            pattern = intent.getLongArrayExtra(Extras.EXTRA_PATTERN)
                                ?: defaultPattern,
                            repeat = intent.getIntExtra(Extras.EXTRA_REPEAT, -1)
                        )
                    }
                    Actions.FINISH -> {
                        vibrateOneShot(
                            durationMs = intent.getLongExtra(Extras.EXTRA_DURATION_MS, 200L),
                            amplitude = intent.getIntExtra(
                                Extras.EXTRA_AMPLITUDE,
                                VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                    }
                    Actions.TEST -> {
                        vibrateOneShot(
                            durationMs = intent.getLongExtra(Extras.EXTRA_DURATION_MS, 120L),
                            amplitude = intent.getIntExtra(
                                Extras.EXTRA_AMPLITUDE,
                                VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                    }
                    else -> {
                        // No-op
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Haptics error", t)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // --- Haptics helpers -----------------------------------------------------

    private fun systemVibrator(): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun normalizeAmplitude(amp: Int): Int {
        // Gültig: 1..255 oder DEFAULT_AMPLITUDE
        if (amp == VibrationEffect.DEFAULT_AMPLITUDE) return amp
        return amp.coerceIn(1, 255)
    }

    private fun audioAttrs(): AudioAttributes? {
        return try {
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        } catch (_: Throwable) {
            null
        }
    }

    private fun vibrateOneShot(durationMs: Long, amplitude: Int) {
        val vib = systemVibrator() ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amp = normalizeAmplitude(amplitude)
            val effect = VibrationEffect.createOneShot(durationMs.coerceAtLeast(1L), amp)
            try {
                // Ab O verfügbar; overload mit AudioAttributes ebenfalls ab O
                audioAttrs()?.let { attrs ->
                    vib.vibrate(effect, attrs)
                    return
                }
                vib.vibrate(effect)
            } catch (t: Throwable) {
                Log.w(TAG, "vibrate(effect) failed, fallback to legacy", t)
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(durationMs)
        }
    }

    private fun vibratePattern(pattern: LongArray, repeat: Int) {
        val vib = systemVibrator() ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val safeRepeat = if (repeat >= 0 && repeat < pattern.size) repeat else -1
            val effect = VibrationEffect.createWaveform(pattern, safeRepeat)
            try {
                audioAttrs()?.let { attrs ->
                    vib.vibrate(effect, attrs)
                    return
                }
                vib.vibrate(effect)
            } catch (t: Throwable) {
                Log.w(TAG, "vibrate(waveform) failed, fallback to legacy", t)
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, safeRepeat)
            }
        } else {
            val safeRepeat = if (repeat >= 0 && repeat < pattern.size) repeat else -1
            @Suppress("DEPRECATION")
            vib.vibrate(pattern, safeRepeat)
        }
    }
}
